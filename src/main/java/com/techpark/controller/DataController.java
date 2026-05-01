package com.techpark.controller;

import com.techpark.service.ParkDataBootstrapService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class DataController {
    private final ParkDataBootstrapService parkDataBootstrapService;

    public DataController(ParkDataBootstrapService parkDataBootstrapService) {
        this.parkDataBootstrapService = parkDataBootstrapService;
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getDataFile() {
        return ResponseEntity.ok(parkDataBootstrapService.readDataJson().toString());
    }
}
