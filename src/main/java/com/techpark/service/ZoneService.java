package com.techpark.service;

import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.ClosureReason;
import com.techpark.model.OperationResult;
import com.techpark.model.Zone;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZoneService {
    private final AttractionService attractionService;

    public ZoneService(AttractionService attractionService) {
        this.attractionService = attractionService;
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
}
