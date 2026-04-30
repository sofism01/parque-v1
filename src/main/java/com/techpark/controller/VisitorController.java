package com.techpark.controller;

import com.techpark.model.Visitor;
import com.techpark.service.AuthService;
import com.techpark.service.ParkDataBootstrapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/visitors")
@CrossOrigin(origins = "*")
public class VisitorController {
    @Autowired
    private AuthService authService;

    @Autowired
    private ParkDataBootstrapService parkDataBootstrapService;

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
        return ResponseEntity.ok(authService.getAllVisitors());
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerVisitor(@RequestBody Visitor visitor) {
        if (!authService.registerVisitor(visitor)) {
            return ResponseEntity.status(400).body(Map.of("error", "El usuario ya existe"));
        }

        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of("message", "Visitante registrado exitosamente"));
    }

    @PutMapping("/{id}/add-favorite")
    public ResponseEntity<Map<String, String>> addFavoriteAttraction(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor == null) {
            return ResponseEntity.notFound().build();
        }

        Long attractionId = request.get("attractionId");
        visitor.addFavorite(attractionId);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of("message", "Atraccion agregada a favoritos"));
    }

    @PutMapping("/{id}/remove-favorite")
    public ResponseEntity<Map<String, String>> removeFavoriteAttraction(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor == null) {
            return ResponseEntity.notFound().build();
        }

        Long attractionId = request.get("attractionId");
        visitor.removeFavorite(attractionId);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of("message", "Atraccion removida de favoritos"));
    }

    @PutMapping("/{id}/add-balance")
    public ResponseEntity<Map<String, Object>> addBalance(
            @PathVariable Long id,
            @RequestBody Map<String, Double> request) {
        Visitor visitor = authService.getVisitor(id);
        if (visitor == null) {
            return ResponseEntity.notFound().build();
        }

        Double amount = request.get("amount");
        visitor.addBalance(amount);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of(
                "message", "Saldo actualizado",
                "newBalance", visitor.getVirtualBalance()
        ));
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
