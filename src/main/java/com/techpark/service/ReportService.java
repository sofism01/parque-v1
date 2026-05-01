package com.techpark.service;

import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.ClosureReason;
import com.techpark.model.ParkReport;
import com.techpark.model.Visitor;
import com.techpark.model.Zone;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de Reportes del Parque
 */
@Service
public class ReportService {
    private List<ParkReport> reports;
    private List<String> maintenanceAlerts;
    private AttractionService attractionService;
    private AuthService authService;
    private QueueService queueService;

    public ReportService(AttractionService attractionService, AuthService authService, QueueService queueService) {
        this.reports = new ArrayList<>();
        this.maintenanceAlerts = new ArrayList<>();
        this.attractionService = attractionService;
        this.authService = authService;
        this.queueService = queueService;
    }

    public ParkReport generateDailyReport(LocalDate date) {
        ParkReport report = new ParkReport(date);
        calculateRevenue(report);
        calculateVisitors(report);
        report.setMostVisitedAttractions(getMostVisitedAttractions());
        report.setAverageWaitTimes(getAverageWaitTimes());
        report.setWeatherClosures(getWeatherClosures());
        report.setMaintenanceAlerts(getMaintenanceAlerts());
        report.setCapacityPercentage(calculateCapacityPercentage());
        reports.add(report);
        return report;
    }

    private void calculateRevenue(ParkReport report) {
        double revenue = 0;
        List<Visitor> visitors = authService.getAllVisitors();

        for (Visitor visitor : visitors) {
            revenue += 100 - visitor.getVirtualBalance();
        }

        report.setDailyRevenue(revenue);
    }

    private void calculateVisitors(ParkReport report) {
        List<Visitor> visitors = authService.getAllVisitors();
        report.setTotalVisitors(visitors.size());
    }

    private Map<String, Integer> getMostVisitedAttractions() {
        Map<String, Integer> visitCount = new HashMap<>();
        List<Attraction> attractions = attractionService.getAllAttractions();

        for (Attraction attraction : attractions) {
            visitCount.put(attraction.getName(), attraction.getAccumulatedVisitors());
        }

        return visitCount.entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }

    private Map<String, Double> getAverageWaitTimes() {
        Map<String, Double> waitTimes = new HashMap<>();
        List<Attraction> attractions = attractionService.getAllAttractions();

        for (Attraction attraction : attractions) {
            waitTimes.put(attraction.getName(), (double) attraction.getEstimatedWaitTime());
        }

        return waitTimes;
    }

    private List<String> getWeatherClosures() {
        List<String> closures = new ArrayList<>();
        List<Attraction> attractions = attractionService.getAllAttractions();

        for (Attraction attraction : attractions) {
            if (AttractionStatus.CERRADA.equals(attraction.getStatus())
                    && ClosureReason.CLIMA.equals(attraction.getClosureReason())) {
                closures.add(attraction.getName());
            }
        }

        return closures;
    }

    private List<String> getMaintenanceAlerts() {
        return new ArrayList<>(maintenanceAlerts);
    }

    private int calculateCapacityPercentage() {
        int totalCapacity = 0;
        int currentOccupancy = 0;

        for (Zone zone : attractionService.getAllZones()) {
            totalCapacity += zone.getMaxCapacity();
            currentOccupancy += zone.getCurrentOccupancy();
        }

        if (totalCapacity == 0) {
            return 0;
        }
        return (currentOccupancy * 100) / totalCapacity;
    }

    public List<ParkReport> getAllReports() {
        return reports;
    }

    public ParkReport getReportByDate(LocalDate date) {
        return reports.stream()
                .filter(r -> r.getReportDate().equals(date))
                .findFirst()
                .orElse(null);
    }

    public ParkReport getLatestReport() {
        if (reports.isEmpty()) {
            return null;
        }
        return reports.get(reports.size() - 1);
    }

    public void addMaintenanceAlert(String alert) {
        maintenanceAlerts.add(alert);
    }

    public List<String> getMaintenanceAlertsHistory() {
        return new ArrayList<>(maintenanceAlerts);
    }

    public double getCurrentRevenue() {
        double revenue = 0;
        for (Visitor visitor : authService.getAllVisitors()) {
            revenue += 100 - visitor.getVirtualBalance();
        }
        return revenue;
    }

    public Attraction getMostVisitedAttraction() {
        Attraction mostVisited = null;

        for (Attraction attraction : attractionService.getAllAttractions()) {
            if (mostVisited == null
                    || attraction.getAccumulatedVisitors() > mostVisited.getAccumulatedVisitors()) {
                mostVisited = attraction;
            }
        }

        return mostVisited;
    }
}
