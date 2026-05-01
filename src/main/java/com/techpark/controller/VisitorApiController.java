package com.techpark.controller;

import com.techpark.dto.GraphNodeDto;
import com.techpark.dto.GraphSnapshotDto;
import com.techpark.dto.AdminZoneDto;
import com.techpark.dto.VisitorPathDto;
import com.techpark.dto.VisitorProfileDto;
import com.techpark.dto.VisitorQueueStatusDto;
import com.techpark.model.Attraction;
import com.techpark.model.OperationResult;
import com.techpark.model.QueueEntry;
import com.techpark.model.User;
import com.techpark.model.Visitor;
import com.techpark.service.AttractionService;
import com.techpark.service.AuthService;
import com.techpark.service.GraphService;
import com.techpark.service.ParkDataBootstrapService;
import com.techpark.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/visitor")
@CrossOrigin(origins = "*")
public class VisitorApiController {
    @Autowired
    private AuthService authService;

    @Autowired
    private AttractionService attractionService;

    @Autowired
    private GraphService graphService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ParkDataBootstrapService parkDataBootstrapService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "id", required = false) Long visitorId) {
        if (visitorId == null && (token == null || token.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes enviar el id del visitante"));
        }

        Visitor visitor = resolveVisitor(token, visitorId);
        if (visitor == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Visitante no encontrado"));
        }

        return ResponseEntity.ok(toProfileDto(visitor));
    }

    @GetMapping("/graph")
    public ResponseEntity<?> getGraphSnapshot() {
        GraphSnapshotDto snapshot = graphService.getGraphSnapshot();
        if (snapshot == null || snapshot.getNodes() == null || snapshot.getEdges() == null || snapshot.getZones() == null) {
            return ResponseEntity.status(500).body(Map.of("message", "El grafo del parque no esta disponible"));
        }
        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/attractions")
    public ResponseEntity<?> getAttractions() {
        List<Attraction> attractions = attractionService.getAllAttractions();
        if (attractions == null) {
            return ResponseEntity.status(500).body(Map.of("message", "No fue posible obtener las atracciones"));
        }
        return ResponseEntity.ok(attractions);
    }

    @GetMapping("/zones")
    public ResponseEntity<?> getZones() {
        List<AdminZoneDto> zones = new ArrayList<>();
        attractionService.getAllZones().forEach((zone) -> zones.add(new AdminZoneDto(
                zone.getId(),
                zone.getName(),
                zone.getMaxCapacity(),
                zone.getCurrentOccupancy(),
                zone.getOperatorIds() != null ? zone.getOperatorIds().toList() : new ArrayList<>(),
                zone.getAttractionIds() != null ? zone.getAttractionIds() : new ArrayList<>(),
                zone.getPosX(),
                zone.getPosY()
        )));
        return ResponseEntity.ok(zones);
    }

    @GetMapping("/path")
    public ResponseEntity<?> getShortestPath(
            @RequestParam(value = "origin", required = false) Long originAttractionId,
            @RequestParam(value = "destination", required = false) Long destinationAttractionId) {
        if (originAttractionId == null || destinationAttractionId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes enviar origin y destination"));
        }

        Attraction start = attractionService.getAttractionById(originAttractionId);
        Attraction end = attractionService.getAttractionById(destinationAttractionId);
        if (start == null || end == null) {
            return ResponseEntity.status(404).body(Map.of("message", "No existe el nodo origen o destino solicitado"));
        }

        List<Attraction> path = attractionService.findShortestPath(originAttractionId, destinationAttractionId);
        if (path == null || path.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No hay ruta disponible entre los nodos seleccionados"));
        }

        List<GraphNodeDto> pathNodes = new ArrayList<>();
        List<String> pathNames = new ArrayList<>();
        for (Attraction attraction : path) {
            pathNodes.add(new GraphNodeDto(
                    attraction.getId(),
                    attraction.getName(),
                    attraction.getType() != null ? attraction.getType().name() : null,
                    attraction.getStatus() != null ? attraction.getStatus().name() : null,
                    attraction.getZone() != null ? attraction.getZone().getId() : attraction.getZoneId(),
                    attraction.getZone() != null ? attraction.getZone().getName() : null,
                    attraction.getPosX(),
                    attraction.getPosY()
            ));
            pathNames.add(attraction.getName());
        }

        return ResponseEntity.ok(new VisitorPathDto(
                pathNodes,
                pathNames,
                calculateDistance(path),
                true,
                "Ruta calculada correctamente"
        ));
    }

    @PostMapping("/queue/join")
    public ResponseEntity<?> joinQueue(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request) {
        Long visitorId = request.get("visitorId") instanceof Number number ? number.longValue() : null;
        Long attractionId = request.get("attractionId") instanceof Number number ? number.longValue() : null;

        Visitor visitor = resolveVisitor(token, visitorId);
        Attraction attraction = attractionId != null ? attractionService.getAttractionById(attractionId) : null;
        if (visitor == null || attraction == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Visitante o atraccion no encontrados"));
        }

        OperationResult accessResult = visitor.canAccessAttraction(attraction);
        if (!accessResult.isSuccess()) {
            return ResponseEntity.badRequest().body(Map.of("message", accessResult.getMessage()));
        }

        if (visitor.getCurrentQueueAttractionId() != null) {
            QueueEntry existingEntry = queueService.findVisitorEntry(visitor.getCurrentQueueAttractionId(), visitor.getId());
            if (existingEntry != null && !visitor.getCurrentQueueAttractionId().equals(attractionId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "El visitante ya tiene una fila virtual activa"));
            }
            if (existingEntry != null) {
                return ResponseEntity.ok(buildQueueStatus(visitor, attraction, "Ya estabas en esta fila"));
            }
        }

        OperationResult paymentResult = visitor.payForAttraction(attraction);
        if (!paymentResult.isSuccess()) {
            return ResponseEntity.badRequest().body(Map.of("message", paymentResult.getMessage()));
        }

        int position = queueService.addVisitorToQueue(
                attractionId,
                visitor.getId(),
                visitor.getUsername(),
                visitor.getTicketType()
        );

        visitor.setPositionInQueue(position);
        visitor.setCurrentQueueAttractionId(attractionId);
        parkDataBootstrapService.saveData();

        return ResponseEntity.ok(buildQueueStatus(visitor, attraction, paymentResult.getMessage()));
    }

    @GetMapping("/queue/status")
    public ResponseEntity<?> getQueueStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "id", required = false) Long visitorId) {
        if (visitorId == null && (token == null || token.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes enviar el id del visitante"));
        }

        Visitor visitor = resolveVisitor(token, visitorId);
        if (visitor == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Visitante no encontrado"));
        }

        if (visitor.getCurrentQueueAttractionId() == null) {
            return ResponseEntity.ok(new VisitorQueueStatusDto(
                    null,
                    null,
                    null,
                    null,
                    null,
                    visitor.getTicketType(),
                    visitor.getVirtualBalance(),
                    "No tienes una fila activa"
            ));
        }

        Attraction attraction = attractionService.getAttractionById(visitor.getCurrentQueueAttractionId());
        if (attraction == null) {
            visitor.setCurrentQueueAttractionId(null);
            visitor.setPositionInQueue(-1);
            parkDataBootstrapService.saveData();
            return ResponseEntity.status(404).body(Map.of("message", "La atraccion asociada a la fila ya no existe"));
        }

        QueueEntry entry = queueService.findVisitorEntry(attraction.getId(), visitor.getId());
        if (entry == null) {
            visitor.setCurrentQueueAttractionId(null);
            visitor.setPositionInQueue(-1);
            parkDataBootstrapService.saveData();
            return ResponseEntity.ok(new VisitorQueueStatusDto(
                    null,
                    null,
                    null,
                    null,
                    null,
                    visitor.getTicketType(),
                    visitor.getVirtualBalance(),
                    "No tienes una fila activa"
            ));
        }

        return ResponseEntity.ok(buildQueueStatus(visitor, attraction, "Estado de fila actualizado"));
    }

    @PostMapping("/queue/leave")
    public ResponseEntity<?> leaveQueue(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) Map<String, Object> request) {
        Long visitorId = request != null && request.get("visitorId") instanceof Number number
                ? number.longValue()
                : null;
        Visitor visitor = resolveVisitor(token, visitorId);
        if (visitor == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Visitante no encontrado"));
        }

        if (visitor.getCurrentQueueAttractionId() == null) {
            return ResponseEntity.ok(Map.of("message", "No tienes una fila activa"));
        }

        queueService.removeVisitorFromQueue(visitor.getCurrentQueueAttractionId(), visitor.getId());
        visitor.setCurrentQueueAttractionId(null);
        visitor.setPositionInQueue(-1);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of("message", "Salida de la fila registrada"));
    }

    private Visitor resolveVisitor(String token, Long visitorId) {
        if (token != null && !token.isBlank()) {
            User user = authService.validateToken(token);
            if (user instanceof Visitor visitor) {
                return visitor;
            }
        }

        if (visitorId != null) {
            return authService.getVisitor(visitorId);
        }

        return null;
    }

    private VisitorProfileDto toProfileDto(Visitor visitor) {
        return new VisitorProfileDto(
                visitor.getId(),
                visitor.getUsername(),
                visitor.getEmail(),
                visitor.getDocument(),
                visitor.getAge(),
                visitor.getHeight(),
                visitor.getVirtualBalance(),
                visitor.getTicketType(),
                visitor.getFavoriteAttractions() != null ? visitor.getFavoriteAttractions().toList() : new ArrayList<>(),
                visitor.getVisitHistory() != null ? visitor.getVisitHistory().toList() : new ArrayList<>(),
                visitor.getNotifications() != null ? visitor.getNotifications() : new ArrayList<>(),
                visitor.getPositionInQueue(),
                visitor.getCurrentQueueAttractionId()
        );
    }

    private int calculateDistance(List<Attraction> path) {
        if (path.size() < 2) {
            return 0;
        }

        int totalDistance = 0;
        for (int index = 0; index < path.size() - 1; index++) {
            Attraction current = path.get(index);
            Attraction next = path.get(index + 1);
            for (com.techpark.datastructures.Graph.Edge<Attraction> edge : attractionService.getAttractionGraph().getNeighbors(current)) {
                if (next.equals(edge.destination)) {
                    totalDistance += edge.weight;
                    break;
                }
            }
        }
        return totalDistance;
    }

    private VisitorQueueStatusDto buildQueueStatus(Visitor visitor, Attraction attraction, String message) {
        int position = queueService.getQueuePosition(attraction.getId(), visitor.getId());
        int totalInQueue = queueService.getQueueSize(attraction.getId());
        visitor.setPositionInQueue(position);

        return new VisitorQueueStatusDto(
                attraction.getId(),
                attraction.getName(),
                position >= 0 ? position : null,
                totalInQueue,
                attraction.getEstimatedWaitTime(),
                visitor.getTicketType(),
                visitor.getVirtualBalance(),
                message
        );
    }
}
