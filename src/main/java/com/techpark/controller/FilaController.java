package com.techpark.controller;

import com.techpark.model.Attraction;
import com.techpark.model.OperationResult;
import com.techpark.model.Visitor;
import com.techpark.service.AttractionService;
import com.techpark.service.AuthService;
import com.techpark.service.ParkDataBootstrapService;
import com.techpark.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fila")
@CrossOrigin(origins = "*")
public class FilaController {
    private final QueueService queueService;
    private final AuthService authService;
    private final AttractionService attractionService;
    private final ParkDataBootstrapService parkDataBootstrapService;

    public FilaController(
            QueueService queueService,
            AuthService authService,
            AttractionService attractionService,
            ParkDataBootstrapService parkDataBootstrapService
    ) {
        this.queueService = queueService;
        this.authService = authService;
        this.attractionService = attractionService;
        this.parkDataBootstrapService = parkDataBootstrapService;
    }

    @PostMapping("/unirse")
    public ResponseEntity<?> unirse(@RequestBody Map<String, Object> request) {
        Long userId = request.get("userId") instanceof Number number
                ? number.longValue()
                : request.get("visitorId") instanceof Number visitorNumber
                ? visitorNumber.longValue()
                : null;
        Long attractionId = request.get("attractionId") instanceof Number number
                ? number.longValue()
                : null;

        if (userId == null || attractionId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes enviar userId y attractionId"));
        }

        Visitor visitor = authService.getVisitor(userId);
        if (visitor == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Usuario no encontrado"));
        }

        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Atraccion no encontrada"));
        }

        if (!attraction.isAvailable()) {
            return ResponseEntity.badRequest().body(Map.of("message", resolveClosedAttractionMessage(attraction)));
        }

        OperationResult accessResult = visitor.canAccessAttraction(attraction);
        if (!accessResult.isSuccess()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Error: El visitante no cumple los requisitos fisicos para esta atraccion"
            ));
        }

        int position;
        try {
            position = queueService.addVisitorToQueue(attractionId, visitor);
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
        if (position < 0) {
            return ResponseEntity.status(404).body(Map.of("message", "Atraccion no encontrada"));
        }

        visitor.setCurrentQueueAttractionId(attractionId);
        visitor.setPositionInQueue(position);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of(
                "message", "Usuario agregado a la fila virtual",
                "position", position
        ));
    }

    private String resolveClosedAttractionMessage(Attraction attraction) {
        if (attraction == null || attraction.getEstado() == null) {
            return "La atraccion no se encuentra disponible en este momento";
        }
        if ("MANTENIMIENTO".equalsIgnoreCase(attraction.getEstado())) {
            return "Esta atraccion se encuentra cerrada por mantenimiento tecnico. Disculpe las molestias.";
        }
        if ("CLIMA".equalsIgnoreCase(attraction.getEstado())) {
            return "Atraccion temporalmente cerrada debido a condiciones climaticas adversas por seguridad.";
        }
        return "La atraccion no se encuentra disponible en este momento";
    }
}
