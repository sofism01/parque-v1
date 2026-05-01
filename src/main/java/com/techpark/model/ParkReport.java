package com.techpark.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

/**
 * Entidad: Reporte del parque
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkReport {
    private Long id;
    private LocalDate reportDate;
    private double dailyRevenue;
    private int totalVisitors;
    private Map<String, Integer> mostVisitedAttractions;
    private Map<String, Double> averageWaitTimes;
    private List<String> weatherClosures;
    private List<String> maintenanceAlerts;
    private List<String> operativeIncidents;
    private int capacityPercentage;

    public ParkReport(LocalDate reportDate) {
        this.reportDate = reportDate;
        this.dailyRevenue = 0;
        this.totalVisitors = 0;
        this.mostVisitedAttractions = new java.util.HashMap<>();
        this.averageWaitTimes = new java.util.HashMap<>();
        this.weatherClosures = new java.util.ArrayList<>();
        this.maintenanceAlerts = new java.util.ArrayList<>();
        this.operativeIncidents = new java.util.ArrayList<>();
        this.capacityPercentage = 0;
    }
}
