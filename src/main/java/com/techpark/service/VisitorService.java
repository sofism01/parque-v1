package com.techpark.service;

import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.OperationResult;
import com.techpark.model.Visitor;
import com.techpark.model.Zone;
import org.springframework.stereotype.Service;

@Service
public class VisitorService {
    private final AuthService authService;
    private final AttractionService attractionService;

    public VisitorService(AuthService authService, AttractionService attractionService) {
        this.authService = authService;
        this.attractionService = attractionService;
    }

    public OperationResult authorizeEntry(Long visitorId, Long attractionId) {
        Visitor visitor = authService.getVisitor(visitorId);
        Attraction attraction = attractionService.getAttractionById(attractionId);

        if (visitor == null || attraction == null) {
            return new OperationResult(false, "Visitante o atraccion no encontrado");
        }

        Zone zone = attractionService.getZoneById(attraction.getZoneId());
        if (zone == null) {
            return new OperationResult(false, "La zona asociada no existe");
        }

        if (zone.isAtFullCapacity()) {
            return new OperationResult(false, "La zona se encuentra en aforo maximo");
        }

        if (!zone.isOperational()) {
            return new OperationResult(false, "La zona no esta operativa por falta de operadores");
        }

        if (!AttractionStatus.ACTIVA.equals(attraction.getStatus())) {
            return new OperationResult(false, "La atraccion no esta activa");
        }

        OperationResult accessResult = visitor.canAccessAttraction(attraction);
        if (!accessResult.isSuccess()) {
            return accessResult;
        }

        OperationResult paymentResult = visitor.payForAttraction(attraction);
        if (!paymentResult.isSuccess()) {
            return paymentResult;
        }

        zone.addVisitor();
        return attractionService.registerVisitorEntry(attractionId, visitorId);
    }
}
