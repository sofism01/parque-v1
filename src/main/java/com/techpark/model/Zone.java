package com.techpark.model;

import com.techpark.datastructures.LinkedList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Entidad: Zona del parque
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Zone {
    private Long id;
    private String name;
    private int maxCapacity;
    private int currentOccupancy;
    private LinkedList<Long> operatorIds;
    private List<Long> attractionIds;
    private Double posX;
    private Double posY;

    public Zone(String name, int maxCapacity) {
        this.name = name;
        this.maxCapacity = maxCapacity;
        this.currentOccupancy = 0;
        this.operatorIds = new LinkedList<>();
        this.attractionIds = new java.util.ArrayList<>();
        this.posX = null;
        this.posY = null;
    }

    public void addOperator(Long operatorId) {
        if (!operatorIds.toList().contains(operatorId)) {
            operatorIds.add(operatorId);
        }
    }

    public void removeOperator(Long operatorId) {
        for (int i = 0; i < operatorIds.size(); i++) {
            if (operatorIds.get(i).equals(operatorId)) {
                operatorIds.remove(i);
                break;
            }
        }
    }

    public void addAttraction(Long attractionId) {
        if (!attractionIds.contains(attractionId)) {
            attractionIds.add(attractionId);
        }
    }

    public boolean hasOperators() {
        return operatorIds.size() > 0;
    }

    public boolean isAtFullCapacity() {
        return currentOccupancy >= maxCapacity;
    }

    public boolean isOperational() {
        return hasOperators();
    }

    public boolean canAcceptMoreVisitors() {
        return isOperational() && !isAtFullCapacity();
    }

    public void addVisitor() {
        if (canAcceptMoreVisitors()) {
            currentOccupancy++;
        }
    }

    public void removeVisitor() {
        if (currentOccupancy > 0) {
            currentOccupancy--;
        }
    }
}
