package com.techpark.controller;

import com.techpark.model.QueueEntry;
import com.techpark.model.Visitor;
import com.techpark.service.AuthService;
import com.techpark.service.ParkDataBootstrapService;
import com.techpark.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para Colas Virtuales
 */
@RestController
@RequestMapping("/api/queue")
@CrossOrigin(origins = "*")
public class QueueController {

    @Autowired
    private QueueService queueService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ParkDataBootstrapService parkDataBootstrapService;

    @PostMapping("/add-visitor")
    public ResponseEntity<Map<String, Object>> addVisitorToQueue(@RequestBody Map<String, Object> request) {
        Long attractionId = ((Number) request.get("attractionId")).longValue();
        Long visitorId = ((Number) request.get("visitorId")).longValue();
        Visitor visitor = authService.getVisitor(visitorId);
        if (visitor == null) {
            return ResponseEntity.notFound().build();
        }

        int position;
        try {
            position = queueService.addVisitorToQueue(attractionId, visitor);
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
        if (position < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "La atraccion no se encuentra disponible para unirse a la fila"
            ));
        }
        parkDataBootstrapService.saveData();

        return ResponseEntity.ok(Map.of(
                "position", position,
                "message", "Visitante agregado a la fila"
        ));
    }

    @GetMapping("/next/{attractionId}")
    public ResponseEntity<QueueEntry> getNextInQueue(@PathVariable Long attractionId) {
        QueueEntry entry = queueService.getNextInQueue(attractionId);
        if (entry != null) {
            return ResponseEntity.ok(entry);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/position/{attractionId}/{visitorId}")
    public ResponseEntity<Map<String, Object>> getQueuePosition(
            @PathVariable Long attractionId,
            @PathVariable Long visitorId) {
        int position = queueService.getQueuePosition(attractionId, visitorId);
        int totalInQueue = queueService.getQueueSize(attractionId);

        return ResponseEntity.ok(Map.of(
                "position", position,
                "totalInQueue", totalInQueue
        ));
    }

    @GetMapping("/size/{attractionId}")
    public ResponseEntity<Map<String, Object>> getQueueSize(@PathVariable Long attractionId) {
        int size = queueService.getQueueSize(attractionId);
        return ResponseEntity.ok(Map.of("size", size));
    }

    @GetMapping("/full/{attractionId}")
    public ResponseEntity<List<QueueEntry>> getFullQueue(@PathVariable Long attractionId) {
        List<QueueEntry> queue = queueService.getFullQueue(attractionId);
        return ResponseEntity.ok(queue);
    }

    @DeleteMapping("/remove-visitor/{attractionId}/{visitorId}")
    public ResponseEntity<Map<String, String>> removeVisitorFromQueue(
            @PathVariable Long attractionId,
            @PathVariable Long visitorId) {
        boolean removed = queueService.removeVisitorFromQueue(attractionId, visitorId);
        if (removed) {
            return ResponseEntity.ok(Map.of("message", "Visitante removido de la fila"));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/estimate-wait-time/{attractionId}")
    public ResponseEntity<Map<String, Object>> estimateWaitTime(
            @PathVariable Long attractionId,
            @RequestBody Map<String, Integer> request) {
        int capacityPerCycle = request.get("capacity");
        int cycleTime = request.get("cycleTime");

        int estimatedTime = queueService.estimateWaitTime(attractionId, capacityPerCycle, cycleTime);

        return ResponseEntity.ok(Map.of(
                "estimatedWaitTime", estimatedTime,
                "minutes", estimatedTime
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<Long, Integer>> getQueueStats() {
        Map<Long, Integer> stats = queueService.getQueueStats();
        return ResponseEntity.ok(stats);
    }
}
