package com.techpark.service;

import com.techpark.datastructures.BinarySearchTree;
import com.techpark.datastructures.Graph;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import com.techpark.model.OperationResult;
import com.techpark.model.QueueEntry;
import com.techpark.model.Visitor;
import com.techpark.model.Zone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de Gestion de Atracciones
 */
@Service
public class AttractionService {
    private BinarySearchTree<Attraction> attractionTree;
    private Map<Long, Attraction> attractionsById;
    private Graph<Attraction> attractionGraph;
    private Map<Long, Zone> zones;

    @Autowired
    private QueueService queueService;

    @Autowired
    private AuthService authService;

    @Autowired
    @Lazy
    private ReportService reportService;

    public AttractionService() {
        this.attractionTree = new BinarySearchTree<>();
        this.attractionsById = new HashMap<>();
        this.attractionGraph = new Graph<>();
        this.zones = new HashMap<>();
        initializeSampleData();
    }

    private void initializeSampleData() {
        Zone aventura = new Zone("Aventura", 500);
        aventura.setId(1L);
        aventura.addOperator(2L);
        zones.put(1L, aventura);

        Zone infantil = new Zone("Infantil", 300);
        infantil.setId(2L);
        zones.put(2L, infantil);

        Zone acuatica = new Zone("Acuatica", 400);
        acuatica.setId(3L);
        zones.put(3L, acuatica);

        Attraction montanaRusa = new Attraction("Montana Rusa", AttractionType.MECANICA, 24, 1.30, 8, 5.00, 1L);
        montanaRusa.setId(1L);
        addAttraction(montanaRusa);

        Attraction rioSalvaje = new Attraction("Rio Salvaje", AttractionType.ACUATICA, 30, 0.0, 0, 0.0, 3L);
        rioSalvaje.setId(2L);
        addAttraction(rioSalvaje);

        Attraction tierraSky = new Attraction("Tierra Sky", AttractionType.MECANICA_ALTURA, 20, 1.20, 6, 3.00, 1L);
        tierraSky.setId(3L);
        addAttraction(tierraSky);

        Attraction carruselMagico = new Attraction("Carrusel Magico", AttractionType.INFANTIL, 50, 0.0, 0, 0.0, 2L);
        carruselMagico.setId(4L);
        addAttraction(carruselMagico);

        attractionGraph.addNode(montanaRusa);
        attractionGraph.addNode(rioSalvaje);
        attractionGraph.addNode(tierraSky);
        attractionGraph.addNode(carruselMagico);

        attractionGraph.addEdge(montanaRusa, tierraSky, 50);
        attractionGraph.addEdge(tierraSky, carruselMagico, 100);
        attractionGraph.addEdge(carruselMagico, rioSalvaje, 150);
        attractionGraph.addEdge(rioSalvaje, montanaRusa, 200);

        aventura.addAttraction(1L);
        aventura.addAttraction(3L);
        infantil.addAttraction(4L);
        acuatica.addAttraction(2L);
    }

    public void addAttraction(Attraction attraction) {
        if (attraction.getId() == null) {
            attraction.setId(nextAttractionId());
        }
        if (attraction.getStatus() == null) {
            attraction.setStatus(AttractionStatus.ACTIVA);
        }
        if (attraction.getClosureReason() == null) {
            attraction.setClosureReason(ClosureReason.NINGUNO);
        }
        attractionTree.insert(attraction);
        attractionsById.put(attraction.getId(), attraction);
        attractionGraph.addNode(attraction);

        if (attraction.getZoneId() != null) {
            Zone zone = zones.get(attraction.getZoneId());
            if (zone != null) {
                zone.addAttraction(attraction.getId());
            }
        }
    }

    public Attraction getAttractionById(Long id) {
        return attractionsById.get(id);
    }

    public List<Attraction> searchAttractionByName(String name) {
        List<Attraction> results = new ArrayList<>();
        Attraction attraction = attractionTree.get(new Attraction(name));
        if (attraction != null) {
            results.add(attraction);
        }
        return results;
    }

    public List<Attraction> getAllAttractions() {
        return attractionTree.getSortedList();
    }

    public List<Attraction> getAttractionsByZone(Long zoneId) {
        List<Attraction> result = new ArrayList<>();
        for (Attraction attraction : attractionsById.values()) {
            if (attraction.getZoneId().equals(zoneId)) {
                result.add(attraction);
            }
        }
        return result;
    }

    public void updateAttraction(Attraction attraction) {
        attractionsById.put(attraction.getId(), attraction);
    }

    public List<Attraction> findShortestPath(Long startAttractionId, Long endAttractionId) {
        Attraction start = getAttractionById(startAttractionId);
        Attraction end = getAttractionById(endAttractionId);

        if (start == null || end == null) {
            return new ArrayList<>();
        }

        return attractionGraph.dijkstra(start, end, this::isTransitable);
    }

    public Zone getZoneById(Long id) {
        return zones.get(id);
    }

    public List<Zone> getAllZones() {
        return new ArrayList<>(zones.values());
    }

    public void addZone(Zone zone) {
        if (zone.getId() == null) {
            zone.setId(nextZoneId());
        }
        zones.put(zone.getId(), zone);
    }

    public void updateZone(Zone zone) {
        zones.put(zone.getId(), zone);
    }

    public Graph<Attraction> getAttractionGraph() {
        return attractionGraph;
    }

    public void connectAttractions(Long sourceAttractionId, Long destinationAttractionId, int weight) {
        Attraction source = getAttractionById(sourceAttractionId);
        Attraction destination = getAttractionById(destinationAttractionId);

        if (source != null && destination != null) {
            attractionGraph.addEdge(source, destination, weight);
        }
    }

    public void resetParkData() {
        this.attractionTree = new BinarySearchTree<>();
        this.attractionsById = new HashMap<>();
        this.attractionGraph = new Graph<>();
        this.zones = new HashMap<>();
    }

    public List<Attraction> checkMaintenanceRequirements() {
        List<Attraction> needsMaintenance = new ArrayList<>();

        for (Attraction attraction : attractionsById.values()) {
            if (attraction.needsMaintenance()) {
                attraction.changeStatus(AttractionStatus.MANTENIMIENTO, ClosureReason.TECNICO);
                needsMaintenance.add(attraction);
            }
        }

        return needsMaintenance;
    }

    public void closeAttractionsByWeather(String weatherAlert) {
        for (Attraction attraction : attractionsById.values()) {
            if (AttractionType.MECANICA_ALTURA.equals(attraction.getType())
                    || AttractionType.ACUATICA.equals(attraction.getType())) {
                attraction.changeStatus(AttractionStatus.CERRADA, ClosureReason.CLIMA);
                notifyAffectedVisitorsByWeather(attraction, weatherAlert);
            }
        }
    }

    public OperationResult registerVisitorEntry(Long attractionId, Long visitorId) {
        Attraction attraction = getAttractionById(attractionId);
        Visitor visitor = authService.getVisitor(visitorId);

        if (attraction == null || visitor == null) {
            return new OperationResult(false, "Atraccion o visitante no encontrado");
        }

        attraction.addVisitor();
        visitor.addVisit(attractionId);

        if (attraction.needsMaintenance()) {
            attraction.changeStatus(AttractionStatus.MANTENIMIENTO, ClosureReason.TECNICO);
            reportService.addMaintenanceAlert("Alerta de Mantenimiento: " + attraction.getName()
                    + " requiere revision tecnica por alta demanda");
        }

        return new OperationResult(true, "Ingreso registrado correctamente");
    }

    public void updateEstimatedWaitTimes(int averageCycleTimeMinutes) {
        for (Attraction attraction : attractionsById.values()) {
            int queueSize = queueService.getQueueSize(attraction.getId());
            int capacityPerCycle = attraction.getMaxCapacityPerCycle();

            if (capacityPerCycle <= 0 || averageCycleTimeMinutes <= 0) {
                attraction.setEstimatedWaitTime(0);
                continue;
            }

            int cycles = (int) Math.ceil((double) queueSize / capacityPerCycle);
            attraction.setEstimatedWaitTime(cycles * averageCycleTimeMinutes);
        }
    }

    private boolean isTransitable(Attraction attraction) {
        return attraction != null && AttractionStatus.ACTIVA.equals(attraction.getStatus());
    }

    private Long nextAttractionId() {
        long maxId = 0L;
        for (Long attractionId : attractionsById.keySet()) {
            if (attractionId != null && attractionId > maxId) {
                maxId = attractionId;
            }
        }
        return maxId + 1;
    }

    private Long nextZoneId() {
        long maxId = 0L;
        for (Long zoneId : zones.keySet()) {
            if (zoneId != null && zoneId > maxId) {
                maxId = zoneId;
            }
        }
        return maxId + 1;
    }

    private void notifyAffectedVisitorsByWeather(Attraction attraction, String weatherAlert) {
        List<QueueEntry> cancelledEntries = queueService.cancelQueue(attraction.getId());

        for (QueueEntry entry : cancelledEntries) {
            Visitor visitor = authService.getVisitor(entry.getVisitorId());
            if (visitor != null) {
                visitor.addNotification("Reserva cancelada por clima en " + attraction.getName()
                        + ". Motivo: " + weatherAlert);
            }
        }
    }
}
