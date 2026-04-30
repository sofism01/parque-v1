package com.techpark.service;

import com.techpark.datastructures.Graph;
import com.techpark.dto.GraphEdgeDto;
import com.techpark.dto.GraphNodeDto;
import com.techpark.dto.GraphSnapshotDto;
import com.techpark.dto.GraphZoneDto;
import com.techpark.model.Attraction;
import com.techpark.model.Zone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class GraphService {
    private static final int INTRA_ZONE_NEIGHBORS = 3;
    private static final int MAX_BRIDGE_NODES_PER_ZONE = 2;

    @Autowired
    private AttractionService attractionService;

    public void addAttractionVertex(Attraction attraction) {
        if (attraction != null) {
            attractionService.getAttractionGraph().addNode(attraction);
        }
    }

    public boolean removeAttractionVertex(Long attractionId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return false;
        }

        attractionService.getAttractionGraph().removeNode(attraction);
        return true;
    }

    public GraphSnapshotDto getGraphSnapshot() {
        Graph<Attraction> graph = attractionService.getAttractionGraph();
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphEdgeDto> edges = new ArrayList<>();
        List<GraphZoneDto> zones = new ArrayList<>();
        Set<String> renderedEdges = new HashSet<>();

        for (Attraction attraction : graph.getAllNodes()) {
            Zone zone = attraction.getZone() != null ? attraction.getZone() : attractionService.getZoneById(attraction.getZoneId());
            nodes.add(new GraphNodeDto(
                    attraction.getId(),
                    attraction.getName(),
                    attraction.getType() != null ? attraction.getType().name() : null,
                    attraction.getStatus() != null ? attraction.getStatus().name() : null,
                    zone != null ? zone.getId() : attraction.getZoneId(),
                    zone != null ? zone.getName() : null,
                    attraction.getPosX(),
                    attraction.getPosY()
            ));

            for (Graph.Edge<Attraction> edge : graph.getNeighbors(attraction)) {
                if (edge.destination == null || edge.destination.getId() == null || attraction.getId() == null) {
                    continue;
                }

                String edgeKey = attraction.getId() < edge.destination.getId()
                        ? attraction.getId() + "-" + edge.destination.getId()
                        : edge.destination.getId() + "-" + attraction.getId();

                if (renderedEdges.add(edgeKey)) {
                    edges.add(new GraphEdgeDto(
                            attraction.getId(),
                            edge.destination.getId(),
                            edge.weight
                    ));
                }
            }
        }

        for (Zone zone : attractionService.getAllZones()) {
            if (!attractionService.isRenderableZone(zone)) {
                continue;
            }
            zones.add(new GraphZoneDto(
                    zone.getId(),
                    zone.getName(),
                    zone.getPosX(),
                    zone.getPosY(),
                    attractionService.getZoneLogicalWidth(),
                    attractionService.getZoneLogicalHeight(),
                    zone.getAttractionIds() != null ? new ArrayList<>(zone.getAttractionIds()) : new ArrayList<>()
            ));
        }

        return new GraphSnapshotDto(nodes, edges, zones);
    }

    public void recargarGrafo() {
        Graph<Attraction> rebuiltGraph = new Graph<>();
        List<Attraction> allAttractions = attractionService.getAllAttractions().stream()
                .filter(Objects::nonNull)
                .toList();

        for (Attraction attraction : allAttractions) {
            rebuiltGraph.addNode(attraction);
        }

        Map<Long, List<Attraction>> attractionsByZone = groupAttractionsByZone(allAttractions);

        for (List<Attraction> zoneAttractions : attractionsByZone.values()) {
            connectZoneInternally(rebuiltGraph, zoneAttractions);
        }

        connectZonesWithBridges(rebuiltGraph, attractionsByZone);
        attractionService.replaceAttractionGraph(rebuiltGraph);
    }

    private Map<Long, List<Attraction>> groupAttractionsByZone(List<Attraction> attractions) {
        Map<Long, List<Attraction>> attractionsByZone = new HashMap<>();
        for (Attraction attraction : attractions) {
            if (attraction == null || attraction.getZoneId() == null) {
                continue;
            }
            attractionsByZone.computeIfAbsent(attraction.getZoneId(), ignored -> new ArrayList<>()).add(attraction);
        }
        return attractionsByZone;
    }

    private void connectZoneInternally(Graph<Attraction> graph, List<Attraction> zoneAttractions) {
        if (zoneAttractions == null || zoneAttractions.size() <= 1) {
            return;
        }

        if (zoneAttractions.size() <= INTRA_ZONE_NEIGHBORS + 1) {
            for (int i = 0; i < zoneAttractions.size(); i++) {
                for (int j = i + 1; j < zoneAttractions.size(); j++) {
                    connectIfPossible(graph, zoneAttractions.get(i), zoneAttractions.get(j));
                }
            }
            return;
        }

        for (Attraction source : zoneAttractions) {
            List<Attraction> nearestNeighbors = zoneAttractions.stream()
                    .filter(candidate -> candidate != null)
                    .filter(candidate -> !Objects.equals(candidate.getId(), source.getId()))
                    .sorted(Comparator.comparingDouble(candidate -> distanceBetween(source, candidate)))
                    .limit(INTRA_ZONE_NEIGHBORS)
                    .toList();

            for (Attraction neighbor : nearestNeighbors) {
                connectIfPossible(graph, source, neighbor);
            }
        }
    }

    private void connectZonesWithBridges(Graph<Attraction> graph, Map<Long, List<Attraction>> attractionsByZone) {
        Map<Long, Zone> renderableZones = new HashMap<>();
        for (Zone zone : attractionService.getAllZones()) {
            if (attractionService.isRenderableZone(zone)) {
                renderableZones.put(zone.getId(), zone);
            }
        }

        Set<String> connectedZonePairs = new HashSet<>();
        for (Map.Entry<Long, List<Attraction>> sourceEntry : attractionsByZone.entrySet()) {
            Zone sourceZone = renderableZones.get(sourceEntry.getKey());
            if (sourceZone == null || sourceEntry.getValue().isEmpty()) {
                continue;
            }

            Zone nearestNeighborZone = findNearestNeighborZone(sourceZone, renderableZones);
            if (nearestNeighborZone == null) {
                continue;
            }

            String pairKey = buildZonePairKey(sourceZone.getId(), nearestNeighborZone.getId());
            if (!connectedZonePairs.add(pairKey)) {
                continue;
            }

            List<Attraction> sourceBridges = selectBridgeCandidates(
                    sourceEntry.getValue(),
                    nearestNeighborZone.getPosX(),
                    nearestNeighborZone.getPosY()
            );
            List<Attraction> targetBridges = selectBridgeCandidates(
                    attractionsByZone.getOrDefault(nearestNeighborZone.getId(), new ArrayList<>()),
                    sourceZone.getPosX(),
                    sourceZone.getPosY()
            );

            int bridgeCount = Math.min(sourceBridges.size(), targetBridges.size());
            for (int index = 0; index < bridgeCount; index++) {
                connectIfPossible(graph, sourceBridges.get(index), targetBridges.get(index));
            }
        }
    }

    private Zone findNearestNeighborZone(Zone sourceZone, Map<Long, Zone> zones) {
        Zone nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Zone candidate : zones.values()) {
            if (candidate == null || Objects.equals(candidate.getId(), sourceZone.getId())) {
                continue;
            }

            double distance = distanceBetween(sourceZone.getPosX(), sourceZone.getPosY(), candidate.getPosX(), candidate.getPosY());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    private List<Attraction> selectBridgeCandidates(List<Attraction> attractions, Double targetX, Double targetY) {
        if (attractions == null || attractions.isEmpty()) {
            return new ArrayList<>();
        }

        return attractions.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> candidate.getPosX() != null && candidate.getPosY() != null)
                .sorted(Comparator.comparingDouble(candidate -> distanceBetween(candidate.getPosX(), candidate.getPosY(), targetX, targetY)))
                .limit(MAX_BRIDGE_NODES_PER_ZONE)
                .toList();
    }

    private void connectIfPossible(Graph<Attraction> graph, Attraction source, Attraction destination) {
        if (source == null || destination == null || Objects.equals(source.getId(), destination.getId())) {
            return;
        }
        if (graph.hasEdge(source, destination)) {
            return;
        }

        graph.addEdge(source, destination, attractionService.calculateEdgeWeight(source, destination));
    }

    private double distanceBetween(Attraction source, Attraction destination) {
        return distanceBetween(
                source != null ? source.getPosX() : null,
                source != null ? source.getPosY() : null,
                destination != null ? destination.getPosX() : null,
                destination != null ? destination.getPosY() : null
        );
    }

    private double distanceBetween(Double sourceX, Double sourceY, Double targetX, Double targetY) {
        if (sourceX == null || sourceY == null || targetX == null || targetY == null) {
            return Double.MAX_VALUE;
        }
        return Math.hypot(targetX - sourceX, targetY - sourceY);
    }

    private String buildZonePairKey(Long sourceZoneId, Long targetZoneId) {
        long minId = Math.min(sourceZoneId, targetZoneId);
        long maxId = Math.max(sourceZoneId, targetZoneId);
        return minId + "-" + maxId;
    }
}
