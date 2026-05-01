package com.techpark.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad: Atraccion del parque
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attraction implements Comparable<Attraction> {
    private Long id;
    private String name;
    private AttractionType type;
    private int maxCapacityPerCycle;
    private double minHeight;
    private int minAge;
    private double additionalCost;
    private int accumulatedVisitors;
    private int estimatedWaitTime;
    private AttractionStatus status;
    private ClosureReason closureReason;
    private boolean climateOverrideActive;
    private Long zoneId;
    private Zone zone;
    private Double posX;
    private Double posY;

    public Attraction(String name) {
        this.name = name;
    }

    public Attraction(String name, AttractionType type, int maxCapacityPerCycle,
                      double minHeight, int minAge, double additionalCost, Long zoneId) {
        this.name = name;
        this.type = type;
        this.maxCapacityPerCycle = maxCapacityPerCycle;
        this.minHeight = minHeight;
        this.minAge = minAge;
        this.additionalCost = additionalCost;
        this.zoneId = zoneId;
        this.accumulatedVisitors = 0;
        this.estimatedWaitTime = 0;
        this.status = AttractionStatus.ACTIVA;
        this.closureReason = ClosureReason.NINGUNO;
        this.climateOverrideActive = false;
    }

    public boolean needsMaintenance() {
        return accumulatedVisitors >= 500;
    }

    public void addVisitor() {
        accumulatedVisitors++;
        if (accumulatedVisitors >= 500) {
            status = AttractionStatus.MANTENIMIENTO;
            closureReason = ClosureReason.TECNICO;
        }
    }

    public void resetMaintenance() {
        accumulatedVisitors = 0;
        status = AttractionStatus.ACTIVA;
        closureReason = ClosureReason.NINGUNO;
        climateOverrideActive = false;
    }

    public void changeStatus(AttractionStatus newStatus, ClosureReason reason) {
        this.status = newStatus;
        this.closureReason = reason;
    }

    public boolean isAvailable() {
        return AttractionStatus.ACTIVA.equals(status);
    }

    @Override
    public int compareTo(Attraction other) {
        if (this.name == null && other.name == null) {
            return 0;
        }
        if (this.name == null) {
            return -1;
        }
        if (other.name == null) {
            return 1;
        }
        return this.name.compareToIgnoreCase(other.name);
    }

    @Override
    public String toString() {
        return name != null ? name : "Atraccion";
    }
}
