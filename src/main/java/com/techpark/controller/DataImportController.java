package com.techpark.controller;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import com.techpark.model.Zone;
import com.techpark.service.AttractionService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Component
@Scope("prototype")
public class DataImportController {
    private final AttractionService attractionService;
    private final Gson gson = new Gson();

    @FXML
    private Label importStatusLabel;

    public DataImportController(AttractionService attractionService) {
        this.attractionService = attractionService;
    }

    public void importData(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        importContent(file.getName(), content);
    }

    public void importDataResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Recurso no encontrado: " + resourcePath);
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            importContent(resourcePath, content);
        }
    }

    private void importContent(String sourceName, String content) throws IOException {
        ParkImportData data = sourceName.toLowerCase().endsWith(".json")
                ? parseJson(content)
                : parseText(content);

        attractionService.resetParkData();

        for (ZoneData zoneData : data.zones) {
            Zone zone = new Zone(zoneData.id, zoneData.name, zoneData.maxCapacity, 0,
                    new com.techpark.datastructures.LinkedList<>(), new ArrayList<>());
            attractionService.addZone(zone);
        }

        for (AttractionData attractionData : data.attractions) {
            Attraction attraction = new Attraction();
            attraction.setId(attractionData.id);
            attraction.setName(attractionData.name);
            attraction.setType(AttractionType.valueOf(attractionData.type.toUpperCase()));
            attraction.setMaxCapacityPerCycle(attractionData.maxCapacityPerCycle);
            attraction.setMinHeight(attractionData.minHeight);
            attraction.setMinAge(attractionData.minAge);
            attraction.setAdditionalCost(attractionData.additionalCost);
            attraction.setAccumulatedVisitors(attractionData.accumulatedVisitors);
            attraction.setEstimatedWaitTime(attractionData.estimatedWaitTime);
            attraction.setStatus(AttractionStatus.valueOf(attractionData.status.toUpperCase()));
            attraction.setClosureReason(attractionData.closureReason == null
                    ? ClosureReason.NINGUNO
                    : ClosureReason.valueOf(attractionData.closureReason.toUpperCase()));
            attraction.setZoneId(attractionData.zoneId);
            attraction.setPosX(attractionData.posX);
            attraction.setPosY(attractionData.posY);
            attractionService.addAttraction(attraction);
        }

        for (ConnectionData connectionData : data.connections) {
            attractionService.connectAttractions(
                    connectionData.sourceId,
                    connectionData.destinationId,
                    connectionData.weight);
        }
    }

    public void importDataAsync(File file, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                importData(file);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            if (importStatusLabel != null) {
                importStatusLabel.setText("Datos importados correctamente");
            }
            if (onSuccess != null) {
                onSuccess.run();
            }
        });

        task.setOnFailed(event -> {
            if (importStatusLabel != null) {
                importStatusLabel.setText("Error importando datos");
            }
            if (onError != null) {
                onError.accept(task.getException());
            }
        });

        Thread worker = new Thread(task, "park-data-import");
        worker.setDaemon(true);
        worker.start();
    }

    private ParkImportData parseJson(String content) throws IOException {
        try {
            ParkImportData data = gson.fromJson(content, ParkImportData.class);
            if (data == null) {
                throw new IOException("Archivo JSON vacio o invalido");
            }
            return data;
        } catch (JsonSyntaxException ex) {
            throw new IOException("No fue posible interpretar el JSON", ex);
        }
    }

    private ParkImportData parseText(String content) throws IOException {
        ParkImportData data = new ParkImportData();
        data.zones = new ArrayList<>();
        data.attractions = new ArrayList<>();
        data.connections = new ArrayList<>();

        String[] lines = content.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\\|");
            if (parts.length == 0) {
                continue;
            }

            switch (parts[0].trim().toUpperCase()) {
                case "ZONE" -> data.zones.add(parseZone(parts));
                case "ATTRACTION" -> data.attractions.add(parseAttraction(parts));
                case "CONNECTION" -> data.connections.add(parseConnection(parts));
                default -> throw new IOException("Tipo de registro no soportado: " + parts[0]);
            }
        }

        return data;
    }

    private ZoneData parseZone(String[] parts) throws IOException {
        if (parts.length < 4) {
            throw new IOException("Registro ZONE invalido");
        }

        ZoneData zone = new ZoneData();
        zone.id = Long.parseLong(parts[1].trim());
        zone.name = parts[2].trim();
        zone.maxCapacity = Integer.parseInt(parts[3].trim());
        return zone;
    }

    private AttractionData parseAttraction(String[] parts) throws IOException {
        if (parts.length < 10) {
            throw new IOException("Registro ATTRACTION invalido");
        }

        AttractionData attraction = new AttractionData();
        attraction.id = Long.parseLong(parts[1].trim());
        attraction.name = parts[2].trim();
        attraction.type = parts[3].trim();
        attraction.maxCapacityPerCycle = Integer.parseInt(parts[4].trim());
        attraction.minHeight = Double.parseDouble(parts[5].trim());
        attraction.minAge = Integer.parseInt(parts[6].trim());
        attraction.additionalCost = Double.parseDouble(parts[7].trim());
        attraction.zoneId = Long.parseLong(parts[8].trim());
        attraction.status = parts[9].trim();
        attraction.closureReason = parts.length > 10 ? parts[10].trim() : "NINGUNO";
        attraction.accumulatedVisitors = parts.length > 11 ? Integer.parseInt(parts[11].trim()) : 0;
        attraction.estimatedWaitTime = parts.length > 12 ? Integer.parseInt(parts[12].trim()) : 0;
        attraction.posX = parts.length > 13 ? Double.parseDouble(parts[13].trim()) : null;
        attraction.posY = parts.length > 14 ? Double.parseDouble(parts[14].trim()) : null;
        return attraction;
    }

    private ConnectionData parseConnection(String[] parts) throws IOException {
        if (parts.length < 4) {
            throw new IOException("Registro CONNECTION invalido");
        }

        ConnectionData connection = new ConnectionData();
        connection.sourceId = Long.parseLong(parts[1].trim());
        connection.destinationId = Long.parseLong(parts[2].trim());
        connection.weight = Integer.parseInt(parts[3].trim());
        return connection;
    }

    private static class ParkImportData {
        private List<ZoneData> zones = new ArrayList<>();
        private List<AttractionData> attractions = new ArrayList<>();
        private List<ConnectionData> connections = new ArrayList<>();
    }

    private static class ZoneData {
        private Long id;
        private String name;
        private int maxCapacity;
    }

    private static class AttractionData {
        private Long id;
        private String name;
        private String type;
        private int maxCapacityPerCycle;
        private double minHeight;
        private int minAge;
        private double additionalCost;
        private Long zoneId;
        private String status;
        private String closureReason;
        private int accumulatedVisitors;
        private int estimatedWaitTime;
        private Double posX;
        private Double posY;
    }

    private static class ConnectionData {
        private Long sourceId;
        private Long destinationId;
        private int weight;
    }
}
