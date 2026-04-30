package com.techpark.controller;
import com.techpark.dto.AdminOperatorDto;
import com.techpark.dto.AdminZoneDto;
import com.techpark.dto.GraphSnapshotDto;
import com.techpark.model.Attraction;
import com.techpark.model.Operator;
import com.techpark.model.Zone;
import com.techpark.service.AttractionService;
import com.techpark.service.AuthService;
import com.techpark.service.GraphService;
import com.techpark.service.OperatorService;
import com.techpark.service.ParkDataBootstrapService;
import com.techpark.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminApiController {
    @Autowired
    private AttractionService attractionService;

    @Autowired
    private GraphService graphService;

    @Autowired
    private OperatorService operatorService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ParkDataBootstrapService parkDataBootstrapService;

    @GetMapping("/attractions")
    public ResponseEntity<List<Attraction>> getAllAttractions() {
        return ResponseEntity.ok(attractionService.getAllAttractions());
    }

    @PostMapping("/attractions")
    public ResponseEntity<Attraction> createAttraction(@RequestBody Attraction attraction) {
        attractionService.addAttraction(attraction);
        graphService.addAttractionVertex(attraction);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(attraction);
    }

    @DeleteMapping("/attractions/{id}")
    public ResponseEntity<Map<String, String>> deleteAttraction(@PathVariable Long id) {
        Attraction attraction = attractionService.getAttractionById(id);
        if (attraction == null) {
            return ResponseEntity.notFound().build();
        }

        attractionService.deleteAttraction(id);
        parkDataBootstrapService.saveData();
        graphService.recargarGrafo();
        return ResponseEntity.ok(Map.of("message", "Atraccion eliminada correctamente"));
    }

    @PutMapping("/attractions/{id}")
    public ResponseEntity<?> updateAttraction(@PathVariable Long id, @RequestBody Attraction attraction) {
        try {
            Attraction updatedAttraction = attractionService.updateAttraction(id, attraction);
            parkDataBootstrapService.saveData();
            graphService.recargarGrafo();
            return ResponseEntity.ok(updatedAttraction);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/zones")
    public ResponseEntity<List<AdminZoneDto>> getZonesWithOccupancy() {
        List<AdminZoneDto> zones = new ArrayList<>();

        for (Zone zone : attractionService.getAllZones()) {
            if (!attractionService.isRenderableZone(zone)) {
                continue;
            }
            int currentQueueOccupancy = 0;
            List<Long> attractionIds = zone.getAttractionIds() != null
                    ? zone.getAttractionIds()
                    : new ArrayList<>();

            for (Long attractionId : attractionIds) {
                currentQueueOccupancy += queueService.getQueueSize(attractionId);
            }

            zones.add(new AdminZoneDto(
                    zone.getId(),
                    zone.getName(),
                    zone.getMaxCapacity(),
                    currentQueueOccupancy,
                    zone.getOperatorIds() != null ? zone.getOperatorIds().toList() : new ArrayList<>(),
                    attractionIds,
                    zone.getPosX(),
                    zone.getPosY()
            ));
        }

        return ResponseEntity.ok(zones);
    }

    @GetMapping("/operators")
    public ResponseEntity<List<AdminOperatorDto>> getOperators() {
        return ResponseEntity.ok(operatorService.getOperatorAssignments());
    }

    @PostMapping("/operators")
    public ResponseEntity<?> createOperator(@RequestBody Map<String, Object> request) {
        String username = valueAsString(request.get("username"));
        String password = valueAsString(request.get("password"));
        String email = valueAsString(request.get("email"));
        Long zoneId = valueAsLong(request.get("zoneId"));

        try {
            if (zoneId != null) {
                Zone zone = attractionService.getZoneById(zoneId);
                if (zone == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", "La zona indicada no existe"));
                }
            }

            Operator operator = operatorService.createOperator(username, password, email, zoneId);
            if (zoneId != null) {
                Zone zone = attractionService.getZoneById(zoneId);
                if (zone != null) {
                    zone.addOperator(operator.getId());
                }
            }
            parkDataBootstrapService.saveData();
            return ResponseEntity.ok(operator);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/graph")
    public ResponseEntity<GraphSnapshotDto> getGraph() {
        return ResponseEntity.ok(graphService.getGraphSnapshot());
    }

    @PostMapping("/alerts/storm")
    public ResponseEntity<Map<String, String>> triggerStormAlert() {
        attractionService.closeAttractionsByWeather("Tormenta reportada desde panel de administracion");
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of("message", "Alerta de tormenta aplicada"));
    }

    @GetMapping("/alerts/maintenance")
    public ResponseEntity<List<Attraction>> getMaintenanceAlerts() {
        List<Attraction> alerts = attractionService.checkMaintenanceRequirements();
        if (!alerts.isEmpty()) {
            parkDataBootstrapService.saveData();
        }
        return ResponseEntity.ok(alerts);
    }

    private String valueAsString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Long valueAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.parseLong(stringValue);
        }
        return null;
    }
}
