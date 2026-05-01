package com.techpark.controller;

import com.techpark.model.ParkReport;
import com.techpark.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador REST para Reportes
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {
    
    @Autowired
    private ReportService reportService;

    @PostMapping("/generate")
    public ResponseEntity<ParkReport> generateDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ParkReport report = reportService.generateDailyReport(date);
        return ResponseEntity.ok(report);
    }

    @GetMapping
    public ResponseEntity<List<ParkReport>> getAllReports() {
        List<ParkReport> reports = reportService.getAllReports();
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/by-date")
    public ResponseEntity<ParkReport> getReportByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ParkReport report = reportService.getReportByDate(date);
        if (report != null) {
            return ResponseEntity.ok(report);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/latest")
    public ResponseEntity<ParkReport> getLatestReport() {
        ParkReport report = reportService.getLatestReport();
        if (report != null) {
            return ResponseEntity.ok(report);
        }
        return ResponseEntity.ok(reportService.generateDailyReport(LocalDate.now()));
    }
}
