package com.techpark.service;

import com.techpark.dto.AdminOperatorDto;
import com.techpark.dto.OperatorAttractionDto;
import com.techpark.dto.OperatorDashboardDto;
import com.techpark.dto.OperatorZoneCapacityDto;
import com.techpark.model.Attraction;
import com.techpark.model.Operator;
import com.techpark.model.Visitor;
import com.techpark.model.Zone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class OperatorService {
    @Autowired
    private AuthService authService;

    @Autowired
    private AttractionService attractionService;

    @Autowired
    private QueueService queueService;

    public List<Operator> getAllOperators() {
        return authService.getAllOperators();
    }

    public Operator createOperator(String username, String password, String email, Long zoneId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El correo del operador es obligatorio");
        }

        if (username == null || username.isBlank()) {
            username = email.trim();
        }
        String resolvedPassword = (password == null || password.isBlank()) ? "operator123" : password;
        Operator operator = new Operator(username.trim(), resolvedPassword, email.trim(), zoneId);
        boolean created = authService.registerOperator(operator);
        if (!created) {
            throw new IllegalArgumentException("Ya existe un operador con ese usuario o correo");
        }

        return operator;
    }

    public List<AdminOperatorDto> getOperatorAssignments() {
        List<AdminOperatorDto> assignments = new ArrayList<>();

        for (Operator operator : authService.getAllOperators()) {
            Zone zone = attractionService.getZoneById(operator.getZoneId());
            List<Long> attractionIds = operator.getAssignedAttractionsIds() != null
                    ? operator.getAssignedAttractionsIds().toList()
                    : new ArrayList<>();
            List<String> attractionNames = new ArrayList<>();

            for (Long attractionId : attractionIds) {
                Attraction attraction = attractionService.getAttractionById(attractionId);
                if (attraction != null) {
                    attractionNames.add(attraction.getName());
                }
            }

            assignments.add(new AdminOperatorDto(
                    operator.getId(),
                    operator.getUsername(),
                    operator.getEmail(),
                    operator.getZoneId(),
                    zone != null ? zone.getName() : null,
                    attractionIds,
                    attractionNames,
                    operator.isActive()
            ));
        }

        return assignments;
    }

    public OperatorDashboardDto getDashboardForOperator(Operator operator) {
        if (operator == null || operator.getZoneId() == null) {
            throw new IllegalArgumentException("El operador no tiene una zona asignada");
        }

        Zone zone = attractionService.getZoneById(operator.getZoneId());
        if (zone == null) {
            throw new IllegalArgumentException("La zona asignada al operador no existe");
        }

        List<OperatorAttractionDto> attractions = new ArrayList<>();
        attractionService.getAttractionsByZone(zone.getId()).stream()
                .sorted(Comparator.comparing(Attraction::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(attraction -> attractions.add(
                        OperatorAttractionDto.from(attraction, zone, queueService.getFullQueue(attraction.getId()))
                ));

        return new OperatorDashboardDto(
                operator.getId(),
                operator.getUsername(),
                operator.getEmail(),
                zone.getId(),
                zone.getName(),
                buildZoneCapacity(zone.getId()),
                attractions
        );
    }

    public boolean operatorCanManageAttraction(Operator operator, Long attractionId) {
        if (operator == null || attractionId == null) {
            return false;
        }

        Attraction attraction = attractionService.getAttractionById(attractionId);
        return attraction != null && Objects.equals(operator.getZoneId(), attraction.getZoneId());
    }

    public OperatorZoneCapacityDto buildZoneCapacity(Long zoneId) {
        Zone zone = attractionService.getZoneById(zoneId);
        if (zone == null) {
            return new OperatorZoneCapacityDto(zoneId, null, 0, 0, false, 0.0);
        }

        int occupancy = 0;
        for (Visitor visitor : authService.getAllVisitors()) {
            if (visitor == null || visitor.getCurrentLocationAttractionId() == null) {
                continue;
            }
            Attraction currentAttraction = attractionService.getAttractionById(visitor.getCurrentLocationAttractionId());
            if (currentAttraction != null && Objects.equals(currentAttraction.getZoneId(), zoneId)) {
                occupancy++;
            }
        }

        zone.setCurrentOccupancy(occupancy);
        int maxCapacity = Math.max(zone.getMaxCapacity(), 0);
        double ratio = maxCapacity == 0 ? 0.0 : (double) occupancy / maxCapacity;
        return new OperatorZoneCapacityDto(
                zone.getId(),
                zone.getName(),
                occupancy,
                maxCapacity,
                maxCapacity > 0 && occupancy >= maxCapacity,
                ratio
        );
    }
}
