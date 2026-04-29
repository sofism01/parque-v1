package com.techpark.controller;

import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.ClosureReason;
import com.techpark.model.Zone;
import com.techpark.service.AttractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para Atracciones y Zonas
 */
@RestController
@RequestMapping("/api/attractions")
@CrossOrigin(origins = "*")
public class AttractionController {

    @Autowired
    private AttractionService attractionService;

    @GetMapping
    public ResponseEntity<List<Attraction>> getAllAttractions() {
        List<Attraction> attractions = attractionService.getAllAttractions();
        return ResponseEntity.ok(attractions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Attraction> getAttractionById(@PathVariable Long id) {
        Attraction attraction = attractionService.getAttractionById(id);
        if (attraction != null) {
            return ResponseEntity.ok(attraction);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Attraction>> searchAttractions(@RequestParam String name) {
        List<Attraction> attractions = attractionService.searchAttractionByName(name);
        return ResponseEntity.ok(attractions);
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<Attraction>> getAttractionsByZone(@PathVariable Long zoneId) {
        List<Attraction> attractions = attractionService.getAttractionsByZone(zoneId);
        return ResponseEntity.ok(attractions);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Attraction> updateAttraction(@PathVariable Long id, @RequestBody Attraction attraction) {
        attraction.setId(id);
        attractionService.updateAttraction(attraction);
        return ResponseEntity.ok(attraction);
    }

    @PostMapping("/change-status/{id}")
    public ResponseEntity<Map<String, String>> changeAttractionStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusRequest) {
        Attraction attraction = attractionService.getAttractionById(id);
        if (attraction != null) {
            AttractionStatus status = AttractionStatus.valueOf(statusRequest.get("status").toUpperCase());
            String reasonValue = statusRequest.get("reason");
            ClosureReason reason = reasonValue == null
                    ? ClosureReason.NINGUNO
                    : ClosureReason.valueOf(reasonValue.toUpperCase());
            attraction.setStatus(status);
            attraction.setClosureReason(reason);
            attractionService.updateAttraction(attraction);
            return ResponseEntity.ok(Map.of("message", "Estado actualizado"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/zones")
    public ResponseEntity<List<Zone>> getAllZones() {
        List<Zone> zones = attractionService.getAllZones();
        return ResponseEntity.ok(zones);
    }

    @GetMapping("/zones/{id}")
    public ResponseEntity<Zone> getZoneById(@PathVariable Long id) {
        Zone zone = attractionService.getZoneById(id);
        if (zone != null) {
            return ResponseEntity.ok(zone);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/zones")
    public ResponseEntity<Zone> createZone(@RequestBody Zone zone) {
        attractionService.addZone(zone);
        return ResponseEntity.ok(zone);
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<Zone> updateZone(@PathVariable Long id, @RequestBody Zone zone) {
        zone.setId(id);
        attractionService.updateZone(zone);
        return ResponseEntity.ok(zone);
    }

    @PostMapping("/check-maintenance")
    public ResponseEntity<List<Attraction>> checkMaintenanceRequirements() {
        List<Attraction> needsMaintenance = attractionService.checkMaintenanceRequirements();
        return ResponseEntity.ok(needsMaintenance);
    }

    @PostMapping("/close-by-weather")
    public ResponseEntity<Map<String, String>> closeAttractionsByWeather(@RequestBody Map<String, String> weather) {
        attractionService.closeAttractionsByWeather(weather.get("alert"));
        return ResponseEntity.ok(Map.of("message", "Atracciones cerradas por clima"));
    }
}
