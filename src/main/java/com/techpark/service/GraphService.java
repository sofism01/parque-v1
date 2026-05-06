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
                    attraction.getEstado(),
                    attraction.getEstado(),
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
        Graph<Attraction> rebuiltGraph = buildZoneConstrainedGraph(attractionService.getAllAttractions());
        attractionService.replaceAttractionGraph(rebuiltGraph);
    }

    public Graph<Attraction> buildZoneConstrainedGraph(List<Attraction> attractions) {
        Graph<Attraction> rebuiltGraph = new Graph<>();
        List<Attraction> allAttractions = attractions == null
                ? new ArrayList<>()
                : attractions.stream()
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
        return rebuiltGraph;
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

        for (int i = 0; i < zoneAttractions.size(); i++) {
            for (int j = i + 1; j < zoneAttractions.size(); j++) {
                connectIfPossible(graph, zoneAttractions.get(i), zoneAttractions.get(j));
            }
        }
    }

    private void connectZonesWithBridges(Graph<Attraction> graph, Map<Long, List<Attraction>> attractionsByZone) {
        Map<Long, Zone> renderableZones = resolveRenderableZonesWithAttractions(attractionsByZone);
        List<Zone> orderedZones = renderableZones.values().stream()
                .sorted(Comparator.comparing(Zone::getId))
                .toList();
        Set<String> connectedZonePairs = new HashSet<>();

        for (Zone sourceZone : orderedZones) {
            if (sourceZone == null || sourceZone.getId() == null) {
                continue;
            }

            Zone nearestNeighborZone = findNearestNeighborZone(sourceZone, orderedZones, connectedZonePairs);
            if (nearestNeighborZone == null) {
                continue;
            }

            connectZonePair(
                    graph,
                    attractionsByZone.getOrDefault(sourceZone.getId(), new ArrayList<>()),
                    attractionsByZone.getOrDefault(nearestNeighborZone.getId(), new ArrayList<>()),
                    sourceZone.getId(),
                    nearestNeighborZone.getId(),
                    connectedZonePairs
            );
        }

        ensureZoneConnectivity(graph, orderedZones, attractionsByZone, connectedZonePairs);
    }

    private Map<Long, Zone> resolveRenderableZonesWithAttractions(Map<Long, List<Attraction>> attractionsByZone) {
        Map<Long, Zone> renderableZones = new HashMap<>();
        for (Zone zone : attractionService.getAllZones()) {
            if (!attractionService.isRenderableZone(zone) || zone.getId() == null) {
                continue;
            }
            List<Attraction> zoneAttractions = attractionsByZone.get(zone.getId());
            if (zoneAttractions == null || zoneAttractions.isEmpty()) {
                continue;
            }
            renderableZones.put(zone.getId(), zone);
        }
        return renderableZones;
    }

    private Zone findNearestNeighborZone(Zone sourceZone, List<Zone> zones, Set<String> connectedZonePairs) {
        Zone nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Zone candidate : zones) {
            if (candidate == null || Objects.equals(candidate.getId(), sourceZone.getId())) {
                continue;
            }
            if (connectedZonePairs.contains(buildZonePairKey(sourceZone.getId(), candidate.getId()))) {
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

    private void ensureZoneConnectivity(
            Graph<Attraction> graph,
            List<Zone> orderedZones,
            Map<Long, List<Attraction>> attractionsByZone,
            Set<String> connectedZonePairs
    ) {
        if (orderedZones.size() <= 1) {
            return;
        }

        while (true) {
            List<Set<Long>> components = buildZoneComponents(graph, orderedZones, attractionsByZone);
            if (components.size() <= 1) {
                return;
            }

            ZonePairCandidate candidate = findClosestDisconnectedZonePair(components, orderedZones);
            if (candidate == null) {
                return;
            }

            connectZonePair(
                    graph,
                    attractionsByZone.getOrDefault(candidate.sourceZone().getId(), new ArrayList<>()),
                    attractionsByZone.getOrDefault(candidate.targetZone().getId(), new ArrayList<>()),
                    candidate.sourceZone().getId(),
                    candidate.targetZone().getId(),
                    connectedZonePairs
            );
        }
    }

    private List<Set<Long>> buildZoneComponents(Graph<Attraction> graph, List<Zone> orderedZones, Map<Long, List<Attraction>> attractionsByZone) {
        List<Set<Long>> components = new ArrayList<>();
        Set<Long> visitedZones = new HashSet<>();

        for (Zone zone : orderedZones) {
            if (zone == null || zone.getId() == null || !visitedZones.add(zone.getId())) {
                continue;
            }

            Set<Long> component = new HashSet<>();
            List<Attraction> zoneAttractions = attractionsByZone.getOrDefault(zone.getId(), new ArrayList<>());
            if (zoneAttractions.isEmpty()) {
                component.add(zone.getId());
                components.add(component);
                continue;
            }

            Attraction seed = zoneAttractions.get(0);
            List<Attraction> reachable = graph.bfs(seed);
            for (Attraction attraction : reachable) {
                if (attraction == null || attraction.getZoneId() == null) {
                    continue;
                }
                component.add(attraction.getZoneId());
                visitedZones.add(attraction.getZoneId());
            }
            component.add(zone.getId());
            components.add(component);
        }

        return components;
    }

    private ZonePairCandidate findClosestDisconnectedZonePair(List<Set<Long>> components, List<Zone> orderedZones) {
        ZonePairCandidate bestCandidate = null;

        for (int i = 0; i < components.size(); i++) {
            for (int j = i + 1; j < components.size(); j++) {
                Set<Long> leftComponent = components.get(i);
                Set<Long> rightComponent = components.get(j);

                for (Zone leftZone : orderedZones) {
                    if (leftZone == null || !leftComponent.contains(leftZone.getId())) {
                        continue;
                    }
                    for (Zone rightZone : orderedZones) {
                        if (rightZone == null || !rightComponent.contains(rightZone.getId())) {
                            continue;
                        }

                        double zoneDistance = distanceBetween(
                                leftZone.getPosX(),
                                leftZone.getPosY(),
                                rightZone.getPosX(),
                                rightZone.getPosY()
                        );

                        if (bestCandidate == null || zoneDistance < bestCandidate.distance()) {
                            bestCandidate = new ZonePairCandidate(leftZone, rightZone, zoneDistance);
                        }
                    }
                }
            }
        }

        return bestCandidate;
    }

    private void connectZonePair(
            Graph<Attraction> graph,
            List<Attraction> sourceAttractions,
            List<Attraction> targetAttractions,
            Long sourceZoneId,
            Long targetZoneId,
            Set<String> connectedZonePairs
    ) {
        if (sourceZoneId == null || targetZoneId == null) {
            return;
        }

        String pairKey = buildZonePairKey(sourceZoneId, targetZoneId);
        if (connectedZonePairs.contains(pairKey)) {
            return;
        }

        BridgeCandidate bestBridge = findClosestBridgeCandidate(sourceAttractions, targetAttractions);
        if (bestBridge == null) {
            return;
        }

        connectIfPossible(graph, bestBridge.source(), bestBridge.destination());
        connectedZonePairs.add(pairKey);
    }

    private BridgeCandidate findClosestBridgeCandidate(List<Attraction> sourceAttractions, List<Attraction> targetAttractions) {
        if (sourceAttractions == null || targetAttractions == null || sourceAttractions.isEmpty() || targetAttractions.isEmpty()) {
            return null;
        }

        BridgeCandidate bestCandidate = null;
        for (Attraction source : sourceAttractions) {
            if (!hasCoordinates(source)) {
                continue;
            }
            for (Attraction target : targetAttractions) {
                if (!hasCoordinates(target)) {
                    continue;
                }

                double distance = distanceBetween(source, target);
                if (bestCandidate == null || distance < bestCandidate.distance()) {
                    bestCandidate = new BridgeCandidate(source, target, distance);
                }
            }
        }

        return bestCandidate;
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

    private boolean hasCoordinates(Attraction attraction) {
        return attraction != null && attraction.getPosX() != null && attraction.getPosY() != null;
    }

    private record BridgeCandidate(Attraction source, Attraction destination, double distance) {
    }

    private record ZonePairCandidate(Zone sourceZone, Zone targetZone, double distance) {
    }
}
