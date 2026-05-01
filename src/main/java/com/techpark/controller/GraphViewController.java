package com.techpark.controller;

import com.techpark.datastructures.Graph;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.service.AttractionService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Scope("prototype")
public class GraphViewController {
    private static final double NODE_RADIUS = 16.0;

    private final AttractionService attractionService;
    private final Map<Long, Point> nodePositions = new HashMap<>();

    @FXML
    private Pane canvas;

    @FXML
    private Label pathStatusLabel;

    private List<Attraction> highlightedPath;
    private Long selectedStartAttractionId;
    private Long selectedDestinationAttractionId;

    public GraphViewController(AttractionService attractionService) {
        this.attractionService = attractionService;
    }

    @FXML
    public void initialize() {
        if (canvas != null) {
            renderMap(canvas);
        }
    }

    public void renderMap(Pane canvas) {
        this.canvas = canvas;
        canvas.getChildren().clear();

        Graph<Attraction> graph = attractionService.getAttractionGraph();
        List<Attraction> attractions = attractionService.getAllAttractions();
        calculateLayout(attractions, canvas);

        drawEdges(graph);
        drawPath();
        drawNodes(attractions);
    }

    public void renderShortestPathAsync(Long startAttractionId, Long destinationAttractionId) {
        this.selectedStartAttractionId = startAttractionId;
        this.selectedDestinationAttractionId = destinationAttractionId;

        Task<List<Attraction>> pathTask = new Task<>() {
            @Override
            protected List<Attraction> call() {
                return attractionService.findShortestPath(startAttractionId, destinationAttractionId);
            }
        };

        pathTask.setOnSucceeded(event -> {
            highlightedPath = pathTask.getValue();
            if (pathStatusLabel != null) {
                pathStatusLabel.setText(highlightedPath.isEmpty()
                        ? "No hay ruta activa disponible"
                        : "Ruta resaltada: " + highlightedPath.size() + " atracciones");
            }
            if (canvas != null) {
                renderMap(canvas);
            }
        });

        pathTask.setOnFailed(event -> {
            highlightedPath = List.of();
            if (pathStatusLabel != null) {
                pathStatusLabel.setText("Error calculando la ruta");
            }
        });

        Thread worker = new Thread(pathTask, "graph-shortest-path");
        worker.setDaemon(true);
        worker.start();
    }

    public void refreshMap() {
        if (canvas != null) {
            renderMap(canvas);
        }
    }

    private void drawEdges(Graph<Attraction> graph) {
        Set<String> renderedEdges = new HashSet<>();

        for (Attraction source : graph.getAllNodes()) {
            Point sourcePoint = nodePositions.get(source.getId());
            if (sourcePoint == null) {
                continue;
            }

            for (Graph.Edge<Attraction> edge : graph.getNeighbors(source)) {
                Attraction destination = edge.destination;
                Point destinationPoint = nodePositions.get(destination.getId());
                if (destinationPoint == null) {
                    continue;
                }

                String edgeKey = buildEdgeKey(source.getId(), destination.getId());
                if (!renderedEdges.add(edgeKey)) {
                    continue;
                }

                Line line = new Line(sourcePoint.x, sourcePoint.y, destinationPoint.x, destinationPoint.y);
                line.setStroke(Color.web("#94a3b8"));
                line.setStrokeWidth(3);
                canvas.getChildren().add(line);
            }
        }
    }

    private void drawPath() {
        if (highlightedPath == null || highlightedPath.size() < 2 || canvas == null) {
            return;
        }

        for (int i = 0; i < highlightedPath.size() - 1; i++) {
            Attraction source = highlightedPath.get(i);
            Attraction destination = highlightedPath.get(i + 1);
            Point sourcePoint = nodePositions.get(source.getId());
            Point destinationPoint = nodePositions.get(destination.getId());

            if (sourcePoint == null || destinationPoint == null) {
                continue;
            }

            Line line = new Line(sourcePoint.x, sourcePoint.y, destinationPoint.x, destinationPoint.y);
            line.setStroke(Color.web("#2563eb"));
            line.setStrokeWidth(6);
            canvas.getChildren().add(line);
        }
    }

    private void drawNodes(List<Attraction> attractions) {
        for (Attraction attraction : attractions) {
            Point point = nodePositions.get(attraction.getId());
            if (point == null) {
                continue;
            }

            Circle node = new Circle(point.x, point.y, NODE_RADIUS);
            node.setFill(resolveStatusColor(attraction.getStatus()));
            node.setStroke(isPathNode(attraction) ? Color.web("#1d4ed8") : Color.web("#0f172a"));
            node.setStrokeWidth(isPathNode(attraction) ? 4 : 2);
            node.setOnMouseClicked(event -> {
                if (selectedStartAttractionId == null) {
                    selectedStartAttractionId = attraction.getId();
                    if (pathStatusLabel != null) {
                        pathStatusLabel.setText("Origen seleccionado: " + attraction.getName());
                    }
                    return;
                }

                renderShortestPathAsync(selectedStartAttractionId, attraction.getId());
            });

            canvas.getChildren().add(node);
        }
    }

    private void calculateLayout(List<Attraction> attractions, Pane canvas) {
        nodePositions.clear();
        int total = attractions.size();
        if (total == 0) {
            return;
        }

        double width = canvas.getPrefWidth() > 0 ? canvas.getPrefWidth() : 800;
        double height = canvas.getPrefHeight() > 0 ? canvas.getPrefHeight() : 600;
        double centerX = width / 2;
        double centerY = height / 2;
        double radius = Math.max(120, Math.min(width, height) * 0.32);

        for (int i = 0; i < total; i++) {
            Attraction attraction = attractions.get(i);
            if (attraction.getPosX() != null && attraction.getPosY() != null) {
                nodePositions.put(attraction.getId(), new Point(attraction.getPosX(), attraction.getPosY()));
                continue;
            }

            double angle = (2 * Math.PI * i) / total;
            double x = centerX + (radius * Math.cos(angle));
            double y = centerY + (radius * Math.sin(angle));
            nodePositions.put(attraction.getId(), new Point(x, y));
        }
    }

    private boolean isPathNode(Attraction attraction) {
        return highlightedPath != null && highlightedPath.contains(attraction);
    }

    private Color resolveStatusColor(AttractionStatus status) {
        if (AttractionStatus.CERRADA.equals(status)) {
            return Color.web("#dc2626");
        }
        if (AttractionStatus.MANTENIMIENTO.equals(status)) {
            return Color.web("#f97316");
        }
        return Color.web("#16a34a");
    }

    private String buildEdgeKey(Long sourceId, Long destinationId) {
        return sourceId < destinationId
                ? sourceId + "-" + destinationId
                : destinationId + "-" + sourceId;
    }

    private record Point(double x, double y) {
    }
}
