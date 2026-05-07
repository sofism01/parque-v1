package com.techpark.service;

import com.techpark.datastructures.BinarySearchTree;
import com.techpark.datastructures.Graph;
import com.techpark.datastructures.LinkedList;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import com.techpark.model.OperationResult;
import com.techpark.model.Operator;
import com.techpark.model.QueueEntry;
import com.techpark.model.Visitor;
import com.techpark.model.Zone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Servicio de Gestion de Atracciones
 */
@Service
public class AttractionService {
    public static final long UNASSIGNED_ZONE_ID = 0L;
    private static final double ZONE_LOGICAL_WIDTH = 400.0;
    private static final double ZONE_LOGICAL_HEIGHT = 400.0;
    private static final double AUTO_POSITION_RADIUS = 120.0;
    private static final double MIN_POSITION_SEARCH_RADIUS = 100.0;
    private static final double COLLISION_DISTANCE_THRESHOLD = 80.0;
    private static final double POSITION_SEARCH_RADIUS_STEP = 50.0;
    private static final double POSITION_SEARCH_ANGLE_STEP = Math.PI / 4.0;

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

    @Autowired
    @Lazy
    private ParkDataBootstrapService parkDataBootstrapService;

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
        aventura.setPosX(170.0);
        aventura.setPosY(160.0);
        aventura.addOperator(2L);
        zones.put(1L, aventura);

        Zone infantil = new Zone("Infantil", 300);
        infantil.setId(2L);
        infantil.setPosX(180.0);
        infantil.setPosY(360.0);
        zones.put(2L, infantil);

        Zone acuatica = new Zone("Acuatica", 400);
        acuatica.setId(3L);
        acuatica.setPosX(470.0);
        acuatica.setPosY(260.0);
        zones.put(3L, acuatica);

        Attraction montanaRusa = new Attraction("Montana Rusa", AttractionType.MECANICA, 24, 1.30, 8, 5.00, 1L);
        montanaRusa.setId(1L);
        montanaRusa.setPosX(120.0);
        montanaRusa.setPosY(120.0);
        storeAttraction(montanaRusa, false);

        Attraction rioSalvaje = new Attraction("Rio Salvaje", AttractionType.ACUATICA, 30, 0.0, 0, 0.0, 3L);
        rioSalvaje.setId(2L);
        rioSalvaje.setPosX(470.0);
        rioSalvaje.setPosY(210.0);
        storeAttraction(rioSalvaje, false);

        Attraction tierraSky = new Attraction("Tierra Sky", AttractionType.MECANICA_ALTURA, 20, 1.20, 6, 3.00, 1L);
        tierraSky.setId(3L);
        tierraSky.setPosX(240.0);
        tierraSky.setPosY(140.0);
        storeAttraction(tierraSky, false);

        Attraction carruselMagico = new Attraction("Carrusel Magico", AttractionType.INFANTIL, 50, 0.0, 0, 0.0, 2L);
        carruselMagico.setId(4L);
        carruselMagico.setPosX(170.0);
        carruselMagico.setPosY(320.0);
        storeAttraction(carruselMagico, false);

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
        validateAttractionLocation(attraction);
        storeAttraction(attraction, true);
    }

    private void storeAttraction(Attraction attraction, boolean autoConnect) {
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
                attraction.setZone(zone);
                zone.addAttraction(attraction.getId());
            }
        }

        if (autoConnect) {
            autoConnectAttraction(attraction);
        }
    }

    public Attraction getAttractionById(Long id) {
        Attraction attraction = attractionsById.get(id);
        if (attraction != null) {
            attraction.setZone(resolveZone(attraction));
        }
        return attraction;
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
        List<Attraction> attractions = attractionTree.getSortedList();
        attractions.forEach((attraction) -> attraction.setZone(resolveZone(attraction)));
        return attractions;
    }

    public List<Attraction> getAttractionsByZone(Long zoneId) {
        List<Attraction> result = new ArrayList<>();
        for (Attraction attraction : attractionsById.values()) {
            if (Objects.equals(attraction.getZoneId(), zoneId)) {
                result.add(attraction);
            }
        }
        return result;
    }

    public void updateAttraction(Attraction attraction) {
        validateAttractionLocation(attraction);
        Attraction existing = attractionsById.get(attraction.getId());
        if (existing == null) {
            throw new IllegalArgumentException("La atraccion indicada no existe");
        }

        Long previousZoneId = existing.getZoneId();
        List<Graph.Edge<Attraction>> existingEdges = new ArrayList<>(attractionGraph.getNeighbors(existing));
        attractionsById.put(attraction.getId(), attraction);
        attractionTree.delete(existing);
        attractionTree.insert(attraction);
        attractionGraph.removeNode(existing);
        attractionGraph.addNode(attraction);

        for (Graph.Edge<Attraction> edge : existingEdges) {
            if (edge.destination != null) {
                attractionGraph.addEdge(attraction, edge.destination, edge.weight);
            }
        }

        if (!Objects.equals(previousZoneId, attraction.getZoneId())) {
            removeAttractionFromZone(previousZoneId, attraction.getId());
            attachAttractionToZone(attraction.getZoneId(), attraction.getId());
        }

        attraction.setZone(resolveZone(attraction));
    }

    public Attraction updateAttraction(Long id, Map<String, Object> request) {
        Attraction existing = attractionsById.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("La atraccion indicada no existe");
        }

        Attraction attraction = new Attraction();
        attraction.setId(id);
        attraction.setName(readNonBlankString(request, new String[]{"name", "nombre"}, existing.getName()));
        attraction.setType(readAttractionType(request, existing.getType()));
        attraction.setMaxCapacityPerCycle(readInt(request, new String[]{"maxCapacityPerCycle", "capacity", "capacidad"}, existing.getMaxCapacityPerCycle()));
        attraction.setMinHeight(readDouble(request, new String[]{"minHeight", "alturaMinima"}, existing.getMinHeight()));
        attraction.setMinAge(readInt(request, new String[]{"minAge", "edadMinima"}, existing.getMinAge()));
        attraction.setAdditionalCost(readDouble(request, new String[]{"additionalCost", "cost", "costo"}, existing.getAdditionalCost()));
        attraction.setAccumulatedVisitors(readInt(request, new String[]{"accumulatedVisitors", "visitantesAcumulados"}, existing.getAccumulatedVisitors()));
        String estado = readNonBlankString(request, new String[]{"estado"}, existing.getEstado());
        attraction.setEstado(estado);
        attraction.setStatus(readAttractionStatus(request, attraction.getStatus()));
        attraction.setClosureReason(readClosureReason(request, existing.getClosureReason()));
        attraction.setClimateOverrideActive(readBoolean(request, new String[]{"climateOverrideActive"}, existing.isClimateOverrideActive()));

        Long zoneId = readLong(request, new String[]{"zoneId", "zonaId", "zona"}, existing.getZoneId());
        Zone zone = resolveZoneById(zoneId, existing.getZone());
        if (zone == null) {
            throw new IllegalArgumentException("La zona indicada no existe");
        }
        attraction.setZoneId(zone.getId());
        attraction.setZone(zone);
        attraction.setPosX(readNullableDoublePreservingExisting(request, new String[]{"posX"}, existing.getPosX()));
        attraction.setPosY(readNullableDoublePreservingExisting(request, new String[]{"posY"}, existing.getPosY()));
        prepareCoordinatesForUpdate(existing, attraction);

        updateAttraction(attraction);
        return attraction;
    }

    public Attraction updateAttraction(Long id, Attraction incomingAttraction) {
        Attraction existing = attractionsById.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("La atraccion indicada no existe");
        }
        if (incomingAttraction == null) {
            throw new IllegalArgumentException("La atraccion es obligatoria");
        }

        Attraction attraction = new Attraction();
        attraction.setId(id);
        attraction.setName(isNonBlank(incomingAttraction.getName()) ? incomingAttraction.getName().trim() : existing.getName());
        attraction.setType(incomingAttraction.getType() != null ? incomingAttraction.getType() : existing.getType());
        attraction.setMaxCapacityPerCycle(incomingAttraction.getMaxCapacityPerCycle());
        attraction.setMinHeight(incomingAttraction.getMinHeight());
        attraction.setMinAge(incomingAttraction.getMinAge());
        attraction.setAdditionalCost(incomingAttraction.getAdditionalCost());
        attraction.setAccumulatedVisitors(existing.getAccumulatedVisitors());
        attraction.setEstado(incomingAttraction.getEstado() != null ? incomingAttraction.getEstado() : existing.getEstado());
        attraction.setStatus(incomingAttraction.getStatus() != null ? incomingAttraction.getStatus() : attraction.getStatus());
        attraction.setClosureReason(incomingAttraction.getClosureReason() != null ? incomingAttraction.getClosureReason() : existing.getClosureReason());
        attraction.setClimateOverrideActive(existing.isClimateOverrideActive());

        Long zoneId = incomingAttraction.getZoneId() != null ? incomingAttraction.getZoneId() : existing.getZoneId();
        Zone zone = resolveZoneById(zoneId, existing.getZone());
        if (zone == null) {
            throw new IllegalArgumentException("La zona indicada no existe");
        }
        attraction.setZoneId(zone.getId());
        attraction.setZone(zone);
        attraction.setPosX(incomingAttraction.getPosX());
        attraction.setPosY(incomingAttraction.getPosY());
        prepareCoordinatesForUpdate(existing, attraction);

        updateAttraction(attraction);
        return attraction;
    }

    public boolean deleteAttraction(Long attractionId) {
        Attraction attraction = attractionsById.get(attractionId);
        if (attraction == null) {
            return false;
        }

        deleteAttractionInternal(attraction);
        healGraphAfterDeletion();

        return true;
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

    public boolean deleteZone(Long zoneId) {
        if (zoneId == null || Objects.equals(zoneId, UNASSIGNED_ZONE_ID)) {
            return false;
        }

        Zone zoneToDelete = zones.get(zoneId);
        if (zoneToDelete == null) {
            return false;
        }

        List<Attraction> allAttractions = new ArrayList<>(attractionsById.values());
        for (Attraction attraction : allAttractions) {
            if (attraction != null && Objects.equals(attraction.getZoneId(), zoneId) && attraction.getId() != null) {
                clearQueueAndVisitorReferences(attraction);
                clearOperatorAssignments(attraction.getId());
            }
        }

        allAttractions.removeIf(attraction ->
                attraction != null && Objects.equals(attraction.getZoneId(), zoneId));

        rebuildAttractionIndexes(allAttractions);
        zones.remove(zoneId);
        return true;
    }

    public boolean isRenderableZone(Zone zone) {
        return zone != null
                && zone.getId() != null
                && !Objects.equals(zone.getId(), UNASSIGNED_ZONE_ID)
                && zone.getName() != null
                && !zone.getName().isBlank();
    }

    public void addZone(Zone zone) {
        if (zone.getId() == null) {
            zone.setId(nextZoneId());
        }
        if (!Objects.equals(zone.getId(), UNASSIGNED_ZONE_ID)) {
            assignZoneAnchorIfMissing(zone);
        }
        if (zone.getOperatorIds() == null) {
            zone.setOperatorIds(new LinkedList<>());
        }
        if (zone.getAttractionIds() == null) {
            zone.setAttractionIds(new ArrayList<>());
        }
        zones.put(zone.getId(), zone);
    }

    public void updateZone(Zone zone) {
        if (!Objects.equals(zone.getId(), UNASSIGNED_ZONE_ID)) {
            assignZoneAnchorIfMissing(zone);
        }
        if (zone.getOperatorIds() == null) {
            zone.setOperatorIds(new LinkedList<>());
        }
        if (zone.getAttractionIds() == null) {
            zone.setAttractionIds(new ArrayList<>());
        }
        zones.put(zone.getId(), zone);

        for (Attraction attraction : attractionsById.values()) {
            if (Objects.equals(attraction.getZoneId(), zone.getId())) {
                attraction.setZone(zone);
            }
        }
    }

    public Graph<Attraction> getAttractionGraph() {
        return attractionGraph;
    }

    public void replaceAttractionGraph(Graph<Attraction> attractionGraph) {
        this.attractionGraph = attractionGraph != null ? attractionGraph : new Graph<>();
    }

    public void connectAttractions(Long sourceAttractionId, Long destinationAttractionId, int weight) {
        Attraction source = getAttractionById(sourceAttractionId);
        Attraction destination = getAttractionById(destinationAttractionId);

        if (source != null && destination != null) {
            attractionGraph.addEdge(source, destination, weight > 0 ? weight : calculateEdgeWeight(source, destination));
        }
    }

    public int calculateEdgeWeight(Attraction source, Attraction destination) {
        if (source == null || destination == null
                || source.getPosX() == null || source.getPosY() == null
                || destination.getPosX() == null || destination.getPosY() == null) {
            return 1;
        }

        double deltaX = destination.getPosX() - source.getPosX();
        double deltaY = destination.getPosY() - source.getPosY();
        return Math.max(1, (int) Math.round(Math.hypot(deltaX, deltaY)));
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
                closeAttractionAndEvictQueue(attraction, AttractionStatus.MANTENIMIENTO, ClosureReason.TECNICO);
                needsMaintenance.add(attraction);
            }
        }

        return needsMaintenance;
    }

    public void closeAttractionsByWeather(String weatherAlert) {
        for (Attraction attraction : attractionsById.values()) {
            if (AttractionType.MECANICA_ALTURA.equals(attraction.getType())
                    || AttractionType.ACUATICA.equals(attraction.getType())) {
                attraction.setClimateOverrideActive(false);
                notifyAffectedVisitorsByWeather(attraction, weatherAlert);
            }
        }
    }

    public Attraction reopenAttraction(Long id) {
        Attraction attraction = getAttractionById(id);
        if (attraction == null) {
            throw new IllegalArgumentException("La atraccion indicada no existe");
        }

        attraction.setStatus(AttractionStatus.ACTIVA);
        attraction.setClosureReason(ClosureReason.NINGUNO);
        attraction.setClimateOverrideActive(true);
        updateAttraction(attraction);
        return attraction;
    }

    public OperationResult registerVisitorEntry(Long attractionId, Long visitorId) {
        Attraction attraction = getAttractionById(attractionId);
        Visitor visitor = authService.getVisitor(visitorId);

        if (attraction == null || visitor == null) {
            return new OperationResult(false, "Atraccion o visitante no encontrado");
        }

        attraction.addVisitor();
        visitor.addVisit(attractionId);
        visitor.setCurrentLocationAttractionId(attractionId);
        if (Objects.equals(visitor.getCurrentQueueAttractionId(), attractionId)) {
            queueService.removeVisitorFromQueue(attractionId, visitorId);
            visitor.setCurrentQueueAttractionId(null);
            visitor.setPositionInQueue(-1);
        }

        if (attraction.needsMaintenance()) {
            closeAttractionAndEvictQueue(attraction, AttractionStatus.MANTENIMIENTO, ClosureReason.TECNICO);
            reportService.addMaintenanceAlert("Alerta de Mantenimiento: " + attraction.getName()
                    + " requiere revision tecnica por alta demanda");
        }

        parkDataBootstrapService.saveData();
        return new OperationResult(true, "Ingreso registrado correctamente");
    }

    public void updateEstimatedWaitTimes(int averageCycleTimeMinutes) {
        for (Attraction attraction : attractionsById.values()) {
            attraction.setEstimatedWaitTime(0);
        }
    }

    public boolean validarPosicionEnZona(Attraction attraction) {
        if (attraction == null) {
            return false;
        }

        Zone zone = resolveZone(attraction);
        if (zone == null || zone.getPosX() == null || zone.getPosY() == null
                || attraction.getPosX() == null || attraction.getPosY() == null) {
            return zone != null && Objects.equals(zone.getId(), UNASSIGNED_ZONE_ID)
                    && attraction.getPosX() != null && attraction.getPosY() != null;
        }

        double left = zone.getPosX() - (ZONE_LOGICAL_WIDTH / 2.0);
        double right = zone.getPosX() + (ZONE_LOGICAL_WIDTH / 2.0);
        double top = zone.getPosY() - (ZONE_LOGICAL_HEIGHT / 2.0);
        double bottom = zone.getPosY() + (ZONE_LOGICAL_HEIGHT / 2.0);

        return attraction.getPosX() >= left && attraction.getPosX() <= right
                && attraction.getPosY() >= top && attraction.getPosY() <= bottom;
    }

    public double getZoneLogicalWidth() {
        return ZONE_LOGICAL_WIDTH;
    }

    public double getZoneLogicalHeight() {
        return ZONE_LOGICAL_HEIGHT;
    }

    public void sincronizarJerarquiaEspacial(Attraction attraction) {
        if (attraction == null) {
            return;
        }
        Zone zone = resolveZone(attraction);
        if (zone == null) {
            return;
        }
        attraction.setZone(zone);
        assignZoneAnchorIfMissing(zone);
        assignAttractionPositionIfMissing(attraction, zone);
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
        List<QueueEntry> cancelledEntries = closeAttractionAndEvictQueue(
                attraction,
                AttractionStatus.CERRADA,
                ClosureReason.CLIMA
        );

        for (QueueEntry entry : cancelledEntries) {
            Visitor visitor = authService.getVisitor(entry.getVisitorId());
            if (visitor != null) {
                visitor.addNotification("Reserva cancelada por clima en " + attraction.getName()
                        + ". Motivo: " + weatherAlert);
            }
        }
    }

    private List<QueueEntry> closeAttractionAndEvictQueue(Attraction attraction,
                                                          AttractionStatus status,
                                                          ClosureReason reason) {
        if (attraction == null) {
            return new ArrayList<>();
        }

        attraction.changeStatus(status, reason);
        return queueService.cancelQueueWithAlert(
                attraction.getId(),
                "La atraccion " + attraction.getName() + " ha cerrado por "
                        + resolveClosureLabel(reason) + ". Has sido removido de la fila."
        );
    }

    private String resolveClosureLabel(ClosureReason reason) {
        if (ClosureReason.CLIMA.equals(reason)) {
            return "CLIMA";
        }
        return "MANTENIMIENTO";
    }

    private void deleteAttractionInternal(Attraction attraction) {
        if (attraction == null || attraction.getId() == null) {
            return;
        }

        Long attractionId = attraction.getId();
        attractionsById.remove(attractionId);
        attractionTree.delete(attraction);
        attractionGraph.removeNode(attraction);
        removeAttractionFromZone(attraction.getZoneId(), attractionId);
        clearQueueAndVisitorReferences(attraction);
        clearOperatorAssignments(attractionId);
    }

    private void rebuildAttractionIndexes(List<Attraction> attractions) {
        this.attractionTree = new BinarySearchTree<>();
        this.attractionsById = new HashMap<>();
        this.attractionGraph = new Graph<>();

        if (attractions == null) {
            return;
        }

        for (Attraction attraction : attractions) {
            if (attraction == null || attraction.getId() == null) {
                continue;
            }

            attraction.setZone(resolveZone(attraction));
            attractionTree.insert(attraction);
            attractionsById.put(attraction.getId(), attraction);
            attractionGraph.addNode(attraction);
        }
    }

    private void clearQueueAndVisitorReferences(Attraction attraction) {
        if (attraction == null || attraction.getId() == null) {
            return;
        }

        queueService.clearQueue(attraction.getId());
        for (Visitor visitor : authService.getAllVisitors()) {
            if (visitor == null) {
                continue;
            }
            visitor.removeFavorite(attraction.getId());
            if (Objects.equals(visitor.getCurrentQueueAttractionId(), attraction.getId())) {
                visitor.setCurrentQueueAttractionId(null);
                visitor.setPositionInQueue(-1);
            }
        }
    }

    private void clearOperatorAssignments(Long attractionId) {
        if (attractionId == null) {
            return;
        }

        for (Operator operator : authService.getAllOperators()) {
            if (operator != null) {
                operator.removeAttraction(attractionId);
            }
        }
    }

    private void validateAttractionLocation(Attraction attraction) {
        if (attraction == null) {
            throw new IllegalArgumentException("La atraccion es obligatoria");
        }
        if (attraction.getZoneId() == null && attraction.getZone() != null) {
            attraction.setZoneId(attraction.getZone().getId());
        }
        Zone zone = resolveZone(attraction);
        if (attraction.getZoneId() == null || zone == null) {
            throw new IllegalArgumentException("Cada atraccion debe pertenecer a una zona valida");
        }
        attraction.setZone(zone);
        if (!Objects.equals(zone.getId(), UNASSIGNED_ZONE_ID)) {
            assignZoneAnchorIfMissing(zone);
        }
        assignAttractionPositionIfMissing(attraction, zone);
        if (!validarPosicionEnZona(attraction)) {
            throw new IllegalArgumentException("La posicion de la atraccion debe permanecer dentro de los limites logicos de su zona");
        }
    }

    private void assignZoneAnchorIfMissing(Zone zone) {
        if (zone.getPosX() != null && zone.getPosY() != null) {
            return;
        }

        int zoneIndex = zone.getId() != null ? Math.max(0, zone.getId().intValue() - 1) : zones.size();
        int columns = 3;
        double baseX = 100.0 + (zoneIndex % columns) * 300.0;
        double baseY = 100.0 + (zoneIndex / columns) * 220.0;
        zone.setPosX(zone.getPosX() != null ? zone.getPosX() : baseX);
        zone.setPosY(zone.getPosY() != null ? zone.getPosY() : baseY);
    }

    private Zone ensureDefaultZone() {
        Zone defaultZone = zones.get(UNASSIGNED_ZONE_ID);
        if (defaultZone != null) {
            if (defaultZone.getOperatorIds() == null) {
                defaultZone.setOperatorIds(new LinkedList<>());
            }
            if (defaultZone.getAttractionIds() == null) {
                defaultZone.setAttractionIds(new ArrayList<>());
            }
            return defaultZone;
        }

        defaultZone = new Zone();
        defaultZone.setId(UNASSIGNED_ZONE_ID);
        defaultZone.setName("Sin Zona");
        defaultZone.setMaxCapacity(0);
        defaultZone.setCurrentOccupancy(0);
        defaultZone.setOperatorIds(new LinkedList<>());
        defaultZone.setAttractionIds(new ArrayList<>());
        defaultZone.setPosX(null);
        defaultZone.setPosY(null);
        zones.put(defaultZone.getId(), defaultZone);
        return defaultZone;
    }

    private void removeAttractionFromZone(Long zoneId, Long attractionId) {
        if (zoneId == null || attractionId == null) {
            return;
        }
        Zone zone = zones.get(zoneId);
        if (zone != null && zone.getAttractionIds() != null) {
            zone.getAttractionIds().remove(attractionId);
        }
    }

    private void attachAttractionToZone(Long zoneId, Long attractionId) {
        if (zoneId == null || attractionId == null) {
            return;
        }
        Zone zone = zones.get(zoneId);
        if (zone != null) {
            zone.addAttraction(attractionId);
        }
    }

    private Zone resolveZone(Attraction attraction) {
        if (attraction == null) {
            return null;
        }
        if (attraction.getZone() != null && attraction.getZone().getId() != null) {
            Zone mappedZone = zones.get(attraction.getZone().getId());
            if (mappedZone != null) {
                return mappedZone;
            }
        }
        return attraction.getZoneId() != null ? zones.get(attraction.getZoneId()) : null;
    }

    private void assignAttractionPositionIfMissing(Attraction attraction, Zone zone) {
        if (!hasMissingCoordinates(attraction)) {
            return;
        }
        if (zone == null || zone.getPosX() == null || zone.getPosY() == null) {
            return;
        }

        List<Attraction> todasLasAtracciones = getAllAttractions().stream()
                .filter(existingAttraction -> existingAttraction != null)
                .filter(existingAttraction -> !Objects.equals(existingAttraction.getId(), attraction.getId()))
                .toList();

        double attemptRadius = Math.max(MIN_POSITION_SEARCH_RADIUS, AUTO_POSITION_RADIUS);
        double angle = 0.0;
        boolean positionOccupied = true;

        while (positionOccupied) {
            double newX = zone.getPosX() + attemptRadius * Math.cos(angle);
            double newY = zone.getPosY() + attemptRadius * Math.sin(angle);

            positionOccupied = todasLasAtracciones.stream().anyMatch(existingAttraction ->
                    existingAttraction.getPosX() != null
                            && existingAttraction.getPosY() != null
                            && Math.hypot(existingAttraction.getPosX() - newX, existingAttraction.getPosY() - newY)
                            < COLLISION_DISTANCE_THRESHOLD
            );

            if (positionOccupied) {
                angle += POSITION_SEARCH_ANGLE_STEP;
                if (angle >= 2 * Math.PI) {
                    angle = 0.0;
                    attemptRadius += POSITION_SEARCH_RADIUS_STEP;
                }
                continue;
            }

            attraction.setPosX(newX);
            attraction.setPosY(newY);
        }
    }

    private boolean hasMissingCoordinates(Attraction attraction) {
        if (attraction == null) {
            return true;
        }
        return attraction.getPosX() == null || attraction.getPosY() == null
                || Double.compare(attraction.getPosX(), 0.0) == 0
                || Double.compare(attraction.getPosY(), 0.0) == 0;
    }

    private void prepareCoordinatesForUpdate(Attraction existing, Attraction attractionToUpdate) {
        if (existing == null || attractionToUpdate == null) {
            return;
        }

        boolean zoneChanged = !Objects.equals(existing.getZoneId(), attractionToUpdate.getZoneId());
        if (!zoneChanged) {
            attractionToUpdate.setPosX(existing.getPosX());
            attractionToUpdate.setPosY(existing.getPosY());
            return;
        }

        attractionToUpdate.setPosX(null);
        attractionToUpdate.setPosY(null);
    }

    private void autoConnectAttraction(Attraction attraction) {
        Attraction nearestAttraction = findNearestConnectableAttraction(attraction);
        if (nearestAttraction != null) {
            connectAttractions(attraction.getId(), nearestAttraction.getId(), calculateEdgeWeight(attraction, nearestAttraction));
        }

        boolean firstAttractionInZone = countAttractionsInZone(attraction.getZoneId(), attraction.getId()) == 0;
        if (firstAttractionInZone) {
            Attraction bridgeNode = findNearestAttractionInDifferentZone(attraction);
            connectIfPossible(attraction, bridgeNode);
        }

        if (attractionGraph.esAislado(attraction)) {
            Attraction nearestOutsideIsland = findNearestAttractionOutsideIsland(attraction);
            connectIfPossible(attraction, nearestOutsideIsland);
        }
    }

    private void healGraphAfterDeletion() {
        List<Attraction> remainingAttractions = getAllAttractions();
        if (remainingAttractions.size() <= 1) {
            return;
        }

        for (Attraction attraction : remainingAttractions) {
            if (!hasZeroDegree(attraction)) {
                continue;
            }
            Attraction nearestAvailable = findNearestAvailableAttraction(attraction, remainingAttractions);
            connectIfPossible(attraction, nearestAvailable);
        }

        Set<Long> processedComponentRoots = new HashSet<>();
        for (Attraction attraction : getAllAttractions()) {
            if (attraction == null || attraction.getId() == null || !processedComponentRoots.add(attraction.getId())) {
                continue;
            }
            if (!attractionGraph.esAislado(attraction)) {
                continue;
            }

            List<Attraction> component = attractionGraph.bfs(attraction);
            processedComponentRoots.addAll(component.stream()
                    .filter(node -> node != null && node.getId() != null)
                    .map(Attraction::getId)
                    .toList());

            Attraction bridgeSource = findBestBridgeSource(component);
            Attraction bridgeTarget = findNearestAttractionOutsideIsland(bridgeSource);
            connectIfPossible(bridgeSource, bridgeTarget);
        }
    }

    private boolean hasZeroDegree(Attraction attraction) {
        if (attraction == null) {
            return true;
        }
        return attractionGraph.getNeighbors(attraction).isEmpty();
    }

    private Attraction findBestBridgeSource(List<Attraction> component) {
        if (component == null || component.isEmpty()) {
            return null;
        }

        Attraction candidateWithCoordinates = component.stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getPosX() != null && node.getPosY() != null)
                .findFirst()
                .orElse(null);
        return candidateWithCoordinates != null ? candidateWithCoordinates : component.get(0);
    }

    private Attraction findNearestAvailableAttraction(Attraction attraction, List<Attraction> candidates) {
        if (attraction == null || candidates == null) {
            return null;
        }

        List<Attraction> activeCandidates = candidates.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> !Objects.equals(candidate.getId(), attraction.getId()))
                .filter(this::isTransitable)
                .toList();
        Attraction nearestActive = findNearestAttraction(attraction, activeCandidates);
        if (nearestActive != null) {
            return nearestActive;
        }

        List<Attraction> fallbackCandidates = candidates.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> !Objects.equals(candidate.getId(), attraction.getId()))
                .toList();
        return findNearestAttraction(attraction, fallbackCandidates);
    }

    private Zone findNearestZoneForAttraction(Attraction attraction, Zone removedZone, List<Zone> candidateZones) {
        if (candidateZones == null || candidateZones.isEmpty()) {
            return null;
        }

        Double originX = attraction != null ? attraction.getPosX() : null;
        Double originY = attraction != null ? attraction.getPosY() : null;
        if ((originX == null || originY == null) && removedZone != null) {
            originX = removedZone.getPosX();
            originY = removedZone.getPosY();
        }

        Zone nearestZone = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Zone candidateZone : candidateZones) {
            if (candidateZone == null) {
                continue;
            }

            assignZoneAnchorIfMissing(candidateZone);
            if (candidateZone.getPosX() == null || candidateZone.getPosY() == null) {
                continue;
            }

            double distance;
            if (originX == null || originY == null) {
                distance = 0.0;
            } else {
                distance = Math.hypot(candidateZone.getPosX() - originX, candidateZone.getPosY() - originY);
            }

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestZone = candidateZone;
            }
        }

        return nearestZone;
    }

    private Attraction findNearestConnectableAttraction(Attraction attraction) {
        if (attraction == null || attraction.getId() == null) {
            return null;
        }

        List<Attraction> parkCandidates = getAllAttractions().stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> !Objects.equals(candidate.getId(), attraction.getId()))
                .toList();
        return findNearestAttraction(attraction, parkCandidates);
    }

    private Attraction findNearestAttractionInDifferentZone(Attraction attraction) {
        if (attraction == null) {
            return null;
        }
        List<Attraction> candidates = getAllAttractions().stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> !Objects.equals(candidate.getId(), attraction.getId()))
                .filter(candidate -> !Objects.equals(candidate.getZoneId(), attraction.getZoneId()))
                .toList();
        return findNearestAttraction(attraction, candidates);
    }

    private Attraction findNearestAttractionOutsideIsland(Attraction attraction) {
        if (attraction == null) {
            return null;
        }
        List<Attraction> sameComponent = attractionGraph.bfs(attraction);
        List<Attraction> candidates = getAllAttractions().stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> !Objects.equals(candidate.getId(), attraction.getId()))
                .filter(candidate -> !sameComponent.contains(candidate))
                .toList();
        return findNearestAttraction(attraction, candidates);
    }

    private long countAttractionsInZone(Long zoneId, Long attractionIdToExclude) {
        return getAllAttractions().stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> Objects.equals(candidate.getZoneId(), zoneId))
                .filter(candidate -> !Objects.equals(candidate.getId(), attractionIdToExclude))
                .count();
    }

    private void connectIfPossible(Attraction source, Attraction destination) {
        if (source == null || destination == null) {
            return;
        }
        if (attractionGraph.hasEdge(source, destination)) {
            return;
        }
        connectAttractions(source.getId(), destination.getId(), calculateEdgeWeight(source, destination));
    }

    private Attraction findNearestAttraction(Attraction origin, List<Attraction> candidates) {
        Attraction nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Attraction candidate : candidates) {
            if (candidate == null || candidate.getPosX() == null || candidate.getPosY() == null
                    || origin.getPosX() == null || origin.getPosY() == null) {
                continue;
            }
            double distance = Math.hypot(
                    candidate.getPosX() - origin.getPosX(),
                    candidate.getPosY() - origin.getPosY()
            );
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private Zone resolveZoneById(Long zoneId, Zone fallback) {
        if (zoneId == null) {
            return fallback;
        }
        Zone resolved = zones.get(zoneId);
        return resolved != null ? resolved : fallback;
    }

    private String readString(Map<String, Object> request, String[] keys, String fallback) {
        for (String key : keys) {
            if (request.containsKey(key) && request.get(key) != null) {
                return String.valueOf(request.get(key));
            }
        }
        return fallback;
    }

    private String readNonBlankString(Map<String, Object> request, String[] keys, String fallback) {
        for (String key : keys) {
            if (!request.containsKey(key) || request.get(key) == null) {
                continue;
            }
            String text = String.valueOf(request.get(key)).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return fallback;
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int readInt(Map<String, Object> request, String[] keys, int fallback) {
        for (String key : keys) {
            if (!request.containsKey(key) || request.get(key) == null) {
                continue;
            }
            Object value = request.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        }
        return fallback;
    }

    private double readDouble(Map<String, Object> request, String[] keys, double fallback) {
        for (String key : keys) {
            if (!request.containsKey(key) || request.get(key) == null) {
                continue;
            }
            Object value = request.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return Double.parseDouble(String.valueOf(value));
        }
        return fallback;
    }

    private Long readLong(Map<String, Object> request, String[] keys, Long fallback) {
        for (String key : keys) {
            if (!request.containsKey(key) || request.get(key) == null) {
                continue;
            }
            Object value = request.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value);
            return text.isBlank() ? null : Long.parseLong(text);
        }
        return fallback;
    }

    private Double readNullableDoublePreservingExisting(Map<String, Object> request, String[] keys, Double fallback) {
        for (String key : keys) {
            if (!request.containsKey(key)) {
                continue;
            }
            Object value = request.get(key);
            if (value == null) {
                return fallback;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            String text = String.valueOf(value);
            return text.isBlank() ? fallback : Double.parseDouble(text);
        }
        return fallback;
    }

    private boolean readBoolean(Map<String, Object> request, String[] keys, boolean fallback) {
        for (String key : keys) {
            if (!request.containsKey(key) || request.get(key) == null) {
                continue;
            }
            Object value = request.get(key);
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return fallback;
    }

    private AttractionType readAttractionType(Map<String, Object> request, AttractionType fallback) {
        String value = readNonBlankString(request, new String[]{"type", "tipo"}, null);
        return value != null ? AttractionType.valueOf(value.toUpperCase()) : fallback;
    }

    private AttractionStatus readAttractionStatus(Map<String, Object> request, AttractionStatus fallback) {
        String value = readNonBlankString(request, new String[]{"status", "estado"}, null);
        if (value == null) {
            return fallback;
        }
        return switch (value.toUpperCase()) {
            case "ABIERTA", "ACTIVA" -> AttractionStatus.ACTIVA;
            case "CLIMA", "CERRADA" -> AttractionStatus.CERRADA;
            case "MANTENIMIENTO" -> AttractionStatus.MANTENIMIENTO;
            default -> AttractionStatus.valueOf(value.toUpperCase());
        };
    }

    private ClosureReason readClosureReason(Map<String, Object> request, ClosureReason fallback) {
        String value = readNonBlankString(request, new String[]{"closureReason", "motivoCierre"}, null);
        return value != null ? ClosureReason.valueOf(value.toUpperCase()) : fallback;
    }
}
