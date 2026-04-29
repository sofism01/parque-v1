package com.techpark.controller;

import com.techpark.model.Visitor;
import com.techpark.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para Visitantes
 */
@RestController
@RequestMapping("/api/visitors")
@CrossOrigin(origins = "*")
public class VisitorController {
    
    @Autowired
    private AuthService authService;

    @GetMapping("/{id}")
    public ResponseEntity<Visitor> getVisitor(@PathVariable Long id) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor != null) {
            return ResponseEntity.ok(visitor);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<Visitor>> getAllVisitors() {
        List<Visitor> visitors = authService.getAllVisitors();
        return ResponseEntity.ok(visitors);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerVisitor(@RequestBody Visitor visitor) {
        if (authService.registerVisitor(visitor)) {
            return ResponseEntity.ok(Map.of("message", "Visitante registrado exitosamente"));
        }
        return ResponseEntity.status(400).body(Map.of("error", "El usuario ya existe"));
    }

    @PutMapping("/{id}/add-favorite")
    public ResponseEntity<Map<String, String>> addFavoriteAttraction(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor != null) {
            Long attractionId = request.get("attractionId");
            visitor.addFavorite(attractionId);
            return ResponseEntity.ok(Map.of("message", "Atracción agregada a favoritos"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/remove-favorite")
    public ResponseEntity<Map<String, String>> removeFavoriteAttraction(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor != null) {
            Long attractionId = request.get("attractionId");
            visitor.removeFavorite(attractionId);
            return ResponseEntity.ok(Map.of("message", "Atracción removida de favoritos"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/add-balance")
    public ResponseEntity<Map<String, Object>> addBalance(
            @PathVariable Long id,
            @RequestBody Map<String, Double> request) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor != null) {
            Double amount = request.get("amount");
            visitor.addBalance(amount);
            return ResponseEntity.ok(Map.of(
                "message", "Saldo actualizado",
                "newBalance", visitor.getVirtualBalance()
            ));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<Long>> getVisitHistory(@PathVariable Long id) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor != null) {
            return ResponseEntity.ok(visitor.getVisitHistory().toList());
        }
        return ResponseEntity.notFound().build();
    }
}
