package com.techpark.service;

import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.ClosureReason;
import com.techpark.model.Operator;
import com.techpark.model.OperationResult;
import com.techpark.model.Zone;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZoneService {
    private final AttractionService attractionService;
    private final ParkDataBootstrapService parkDataBootstrapService;
    private final AuthService authService;

    public ZoneService(AttractionService attractionService, ParkDataBootstrapService parkDataBootstrapService, AuthService authService) {
        this.attractionService = attractionService;
        this.parkDataBootstrapService = parkDataBootstrapService;
        this.authService = authService;
    }

    public OperationResult removeOperatorFromZone(Long zoneId, Long operatorId) {
        Zone zone = attractionService.getZoneById(zoneId);
        if (zone == null) {
            return new OperationResult(false, "Zona no encontrada");
        }

        if (!zone.getOperatorIds().toList().contains(operatorId)) {
            return new OperationResult(false, "El operador no pertenece a la zona");
        }

        if (zone.getOperatorIds().size() <= 1) {
            return new OperationResult(false, "No se puede eliminar al ultimo operador de la zona");
        }

        zone.removeOperator(operatorId);
        enforceZoneOperationalState(zone);
        return new OperationResult(true, "Operador removido correctamente");
    }

    public void enforceZoneOperationalState(Zone zone) {
        List<Attraction> attractions = attractionService.getAttractionsByZone(zone.getId());
        if (!zone.hasOperators()) {
            for (Attraction attraction : attractions) {
                if (AttractionStatus.ACTIVA.equals(attraction.getStatus())) {
                    attraction.setStatus(AttractionStatus.MANTENIMIENTO);
                    attraction.setClosureReason(ClosureReason.MANTENIMIENTO);
                }
            }
        }
    }

    public OperationResult deleteZone(Long zoneId) {
        Zone zone = attractionService.getZoneById(zoneId);
        if (zone == null) {
            return new OperationResult(false, "Zona no encontrada");
        }

        for (Operator operator : authService.getAllOperators()) {
            if (operator != null && zoneId.equals(operator.getZoneId())) {
                operator.setZoneId(null);
            }
        }

        boolean deleted = attractionService.deleteZone(zoneId);
        if (!deleted) {
            return new OperationResult(false, "No fue posible eliminar la zona");
        }

        parkDataBootstrapService.saveData();
        return new OperationResult(true, "Zona eliminada correctamente");
    }

    public Zone createZone(Zone zone, Long operatorId) {
        if (zone == null) {
            throw new IllegalArgumentException("La zona es obligatoria");
        }
        if (zone.getName() == null || zone.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre de la zona es obligatorio");
        }
        if (operatorId == null) {
            throw new IllegalArgumentException("La zona debe crearse con un operador asignado");
        }

        Operator operator = authService.getOperator(operatorId);
        if (operator == null) {
            throw new IllegalArgumentException("El operador seleccionado no existe");
        }
        if (operator.getZoneId() != null) {
            throw new IllegalArgumentException("El operador seleccionado ya tiene una zona asignada");
        }

        attractionService.addZone(zone);
        zone.addOperator(operator.getId());
        operator.setZoneId(zone.getId());
        parkDataBootstrapService.saveData();
        return zone;
    }
}
