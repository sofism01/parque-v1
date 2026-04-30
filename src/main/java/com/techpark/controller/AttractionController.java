package com.techpark.controller;
import com.techpark.dto.AdminZoneDto;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.ClosureReason;
import com.techpark.model.Zone;
import com.techpark.service.AttractionService;
import com.techpark.service.GraphService;
import com.techpark.service.ParkDataBootstrapService;
import com.techpark.service.QueueService;
import com.techpark.service.ZoneService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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

    @Autowired
    private QueueService queueService;

    @Autowired
    private ParkDataBootstrapService parkDataBootstrapService;

    @Autowired
    private GraphService graphService;

    @Autowired
    private ZoneService zoneService;

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

    @PostMapping
    public ResponseEntity<?> createAttraction(@RequestBody Attraction attraction) {
        try {
            attractionService.addAttraction(attraction);
            parkDataBootstrapService.saveData();
            graphService.recargarGrafo();
            return ResponseEntity.ok(attraction);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
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
            parkDataBootstrapService.saveData();
            return ResponseEntity.ok(Map.of("message", "Estado actualizado"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/zones")
    public ResponseEntity<List<AdminZoneDto>> getAllZones() {
        List<AdminZoneDto> zones = new ArrayList<>();
        for (Zone zone : attractionService.getAllZones()) {
            if (!attractionService.isRenderableZone(zone)) {
                continue;
            }
            zones.add(new AdminZoneDto(
                    zone.getId(),
                    zone.getName(),
                    zone.getMaxCapacity(),
                    zone.getCurrentOccupancy() > 0 ? zone.getCurrentOccupancy() : calculateQueueOccupancy(zone),
                    zone.getOperatorIds() != null ? zone.getOperatorIds().toList() : new ArrayList<>(),
                    zone.getAttractionIds() != null ? zone.getAttractionIds() : new ArrayList<>(),
                    zone.getPosX(),
                    zone.getPosY()
            ));
        }
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
    public ResponseEntity<?> createZone(@RequestBody Map<String, Object> request) {
        try {
            Zone zone = new Zone();
            zone.setName(request.get("name") != null ? String.valueOf(request.get("name")).trim() : null);

            Object maxCapacityValue = request.get("maxCapacity");
            if (maxCapacityValue == null) {
                throw new IllegalArgumentException("La capacidad maxima es obligatoria");
            }
            zone.setMaxCapacity(maxCapacityValue instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(maxCapacityValue)));

            Object operatorIdValue = request.get("operatorId");
            Long operatorId = operatorIdValue instanceof Number number
                    ? number.longValue()
                    : operatorIdValue != null ? Long.parseLong(String.valueOf(operatorIdValue)) : null;

            Zone createdZone = zoneService.createZone(zone, operatorId);
            graphService.recargarGrafo();
            return ResponseEntity.ok(createdZone);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<Zone> updateZone(@PathVariable Long id, @RequestBody Zone zone) {
        zone.setId(id);
        attractionService.updateZone(zone);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(zone);
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<Map<String, String>> deleteZone(@PathVariable Long id) {
        com.techpark.model.OperationResult result = zoneService.deleteZone(id);
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(Map.of("message", result.getMessage()));
        }

        graphService.recargarGrafo();
        return ResponseEntity.ok(Map.of("message", result.getMessage()));
    }

    @PostMapping("/check-maintenance")
    public ResponseEntity<List<Attraction>> checkMaintenanceRequirements() {
        List<Attraction> needsMaintenance = attractionService.checkMaintenanceRequirements();
        if (!needsMaintenance.isEmpty()) {
            parkDataBootstrapService.saveData();
        }
        return ResponseEntity.ok(needsMaintenance);
    }

    @PostMapping("/close-by-weather")
    public ResponseEntity<Map<String, String>> closeAttractionsByWeather(@RequestBody Map<String, String> weather) {
        attractionService.closeAttractionsByWeather(weather.get("alert"));
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of("message", "Atracciones cerradas por clima"));
    }

    @PostMapping("/{id}/repair")
    public ResponseEntity<Map<String, String>> repairAttraction(@PathVariable Long id) {
        Attraction attraction = attractionService.getAttractionById(id);
        if (attraction == null) {
            return ResponseEntity.notFound().build();
        }

        attraction.resetMaintenance();
        attractionService.updateAttraction(attraction);
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(Map.of("message", "Contador de mantenimiento reiniciado"));
    }

    @PutMapping("/{id}/reopen")
    public ResponseEntity<?> reopenAttraction(@PathVariable Long id) {
        try {
            Attraction attraction = attractionService.reopenAttraction(id);
            parkDataBootstrapService.saveData();
            graphService.recargarGrafo();
            return ResponseEntity.ok(attraction);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    private int calculateQueueOccupancy(Zone zone) {
        if (zone.getAttractionIds() == null) {
            return 0;
        }

        int occupancy = 0;
        for (Long attractionId : zone.getAttractionIds()) {
            occupancy += queueService.getQueueSize(attractionId);
        }
        return occupancy;
    }
}
