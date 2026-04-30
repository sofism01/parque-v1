package com.techpark.service;

import com.techpark.dto.AdminOperatorDto;
import com.techpark.model.Attraction;
import com.techpark.model.Operator;
import com.techpark.model.Zone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OperatorService {
    @Autowired
    private AuthService authService;

    @Autowired
    private AttractionService attractionService;

    public List<Operator> getAllOperators() {
        return authService.getAllOperators();
    }

    public Operator createOperator(String username, String password, String email, Long zoneId) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("username y password son obligatorios");
        }

        Operator operator = new Operator(username.trim(), password, email, zoneId);
        boolean created = authService.registerOperator(operator);
        if (!created) {
            throw new IllegalArgumentException("Ya existe un usuario con ese username");
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
}
