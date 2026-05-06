package com.techpark.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.techpark.datastructures.Graph;
import com.techpark.datastructures.LinkedList;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import com.techpark.model.Operator;
import com.techpark.model.QueueEntry;
import com.techpark.model.TicketType;
import com.techpark.model.Visitor;
import com.techpark.model.Zone;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ParkDataBootstrapService {
    private static final Path DATA_FILE_PATH = Path.of("data.json");
    private static final Path USERS_FILE_PATH = Path.of("usuarios.json");
    private static final String DEFAULT_DATA_JSON = "{\"attractions\":[], \"zones\":[], \"operators\":[], \"connections\":[], \"ingresosTotales\":0}";
    private final AttractionService attractionService;
    private final GraphService graphService;
    private final AuthService authService;
    private final QueueService queueService;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ParkDataBootstrapService(
            AttractionService attractionService,
            GraphService graphService,
            AuthService authService,
            QueueService queueService
    ) {
        this.attractionService = attractionService;
        this.graphService = graphService;
        this.authService = authService;
        this.queueService = queueService;
    }

    public void importData(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        importContent(file.getName(), content);
    }

    public void importDataFile() {
        try {
            ensureVisitorsSeedFile();
            String content = readOrInitializeDataFile();
            importContent(DATA_FILE_PATH.getFileName().toString(), content);
            saveData();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible cargar los datos iniciales", exception);
        }
    }

    public void reloadDataFromDisk() {
        try {
            String content = readOrInitializeDataFile();
            importContent(DATA_FILE_PATH.getFileName().toString(), content);
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible recargar data.json", exception);
        }
    }

    private void importContent(String sourceName, String content) throws IOException {
        ParkImportData data = sourceName.toLowerCase().endsWith(".json")
                ? parseJson(content)
                : parseText(content);
        data = normalizeData(data);
        data.visitors = loadVisitorsFromUsersFile();
        data.queues = ensureSeededQueues(data.queues, data.attractions);

        attractionService.resetParkData();

        for (ZoneData zoneData : data.zones) {
            if (zoneData == null) {
                continue;
            }
            Zone zone = new Zone(zoneData.id, zoneData.name, zoneData.maxCapacity, 0,
                    new com.techpark.datastructures.LinkedList<>(), new ArrayList<>(), zoneData.posX, zoneData.posY);
            attractionService.addZone(zone);
        }

        for (AttractionData attractionData : data.attractions) {
            if (attractionData == null) {
                continue;
            }
            Attraction attraction = new Attraction();
            attraction.setId(attractionData.id);
            attraction.setName(attractionData.name);
            attraction.setType(AttractionType.valueOf(attractionData.type.toUpperCase()));
            attraction.setMaxCapacityPerCycle(attractionData.maxCapacityPerCycle);
            attraction.setMinHeight(attractionData.estaturaMinima != null ? attractionData.estaturaMinima : attractionData.minHeight);
            attraction.setMinAge(attractionData.edadMinima != null ? attractionData.edadMinima : attractionData.minAge);
            attraction.setAdditionalCost(attractionData.additionalCost);
            attraction.setVisitantesTotales(
                    attractionData.visitantesTotales != null
                            ? attractionData.visitantesTotales
                            : attractionData.accumulatedVisitors
            );
            attraction.setEstimatedWaitTime(attractionData.estimatedWaitTime);
            if (attractionData.estado != null && !attractionData.estado.isBlank()) {
                attraction.setEstado(attractionData.estado);
            } else {
                attraction.setStatus(AttractionStatus.valueOf(attractionData.status.toUpperCase()));
            }
            attraction.setClosureReason(attractionData.closureReason == null
                    ? ClosureReason.NINGUNO
                    : ClosureReason.valueOf(attractionData.closureReason.toUpperCase()));
            attraction.setClimateOverrideActive(attractionData.climateOverrideActive != null && attractionData.climateOverrideActive);
            attraction.setZoneId(attractionData.zoneId);
            attraction.setZone(attractionService.getZoneById(attractionData.zoneId));
            attraction.setPosX(attractionData.posX);
            attraction.setPosY(attractionData.posY);
            attractionService.sincronizarJerarquiaEspacial(attraction);
            attractionService.addAttraction(attraction);
        }

        repairAttractionCoordinates();
        initializeGraphOnLoad(data.connections);

        List<Operator> operators = new ArrayList<>();
        for (OperatorData operatorData : data.operators) {
            Operator operator = new Operator();
            operator.setId(operatorData.id);
            operator.setUsername(operatorData.username);
            operator.setPassword(operatorData.password);
            operator.setEmail(operatorData.email);
            operator.setRole("OPERATOR");
            operator.setActive(operatorData.active == null || operatorData.active);
            operator.setZoneId(operatorData.zoneId);

            LinkedList<Long> assignedAttractions = new LinkedList<>();
            if (operatorData.assignedAttractionsIds != null) {
                for (Long attractionId : operatorData.assignedAttractionsIds) {
                    assignedAttractions.add(attractionId);
                }
            }
            operator.setAssignedAttractionsIds(assignedAttractions);
            operators.add(operator);
        }

        authService.replaceOperators(operators);

        List<Visitor> visitors = new ArrayList<>();
        for (VisitorData visitorData : data.visitors) {
            Visitor visitor = new Visitor();
            visitor.setId(visitorData.id);
            visitor.setUsername(visitorData.username);
            visitor.setPassword(visitorData.password);
            visitor.setEmail(visitorData.email);
            visitor.setRole("VISITOR");
            visitor.setActive(visitorData.active == null || visitorData.active);
            visitor.setDocument(visitorData.document);
            visitor.setAge(visitorData.age);
            visitor.setHeight(visitorData.height);
            visitor.setVirtualBalance(visitorData.virtualBalance);
            visitor.setPhotoPath(visitorData.photoPath);
            visitor.setTicketType(visitorData.ticketType == null
                    ? TicketType.GENERAL
                    : TicketType.valueOf(visitorData.ticketType.toUpperCase()));

            com.techpark.datastructures.CustomSet<Long> favorites = new com.techpark.datastructures.CustomSet<>();
            if (visitorData.favoriteAttractions != null) {
                for (Long favoriteAttractionId : visitorData.favoriteAttractions) {
                    favorites.add(favoriteAttractionId);
                }
            }
            visitor.setFavoriteAttractions(favorites);

            LinkedList<Long> visitHistory = new LinkedList<>();
            if (visitorData.visitHistory != null) {
                for (Long attractionId : visitorData.visitHistory) {
                    visitHistory.add(attractionId);
                }
            }
            visitor.setVisitHistory(visitHistory);
            visitor.setHistorialAtracciones(visitorData.historialAtracciones != null
                    ? new ArrayList<>(visitorData.historialAtracciones)
                    : new ArrayList<>());
            visitor.setNotifications(visitorData.notifications != null ? new ArrayList<>(visitorData.notifications) : new ArrayList<>());
            visitor.setMensajeAlerta(visitorData.mensajeAlerta);
            visitor.setPositionInQueue(visitorData.positionInQueue);
            visitor.setCurrentQueueAttractionId(visitorData.currentQueueAttractionId);
            visitor.setCurrentLocationAttractionId(visitorData.currentLocationAttractionId);
            visitors.add(visitor);
        }

        authService.replaceVisitors(visitors);
        queueService.setIngresosTotales(data.ingresosTotales != null ? data.ingresosTotales : 0.0);

        for (Operator operator : operators) {
            if (operator.getZoneId() == null) {
                continue;
            }
            Zone zone = attractionService.getZoneById(operator.getZoneId());
            if (zone != null) {
                zone.addOperator(operator.getId());
            }
        }

        queueService.restoreQueues(buildQueueSnapshot(data.queues));
    }

    public void saveData() {
        try {
            repairAttractionCoordinates();
            ParkImportData data = new ParkImportData();
            data.zones = buildZoneData();
            data.attractions = buildAttractionData();
            data.connections = buildConnectionData();
            data.operators = buildOperatorData();
            data.visitors = buildVisitorData();
            data.queues = buildQueueData();

            Path outputPath = DATA_FILE_PATH;
            JsonObject root = buildPersistedJson(data, readExistingDataJson(outputPath));
            Files.writeString(outputPath, gson.toJson(root), StandardCharsets.UTF_8);
            saveUsersFile(data.visitors);
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible guardar data.json", exception);
        }
    }

    public void repairAndSaveAttractionCoordinates() {
        repairAttractionCoordinates();
        saveData();
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
        zone.posX = parts.length > 4 ? Double.parseDouble(parts[4].trim()) : null;
        zone.posY = parts.length > 5 ? Double.parseDouble(parts[5].trim()) : null;
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
        attraction.estaturaMinima = attraction.minHeight;
        attraction.edadMinima = attraction.minAge;
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
        private List<OperatorData> operators = new ArrayList<>();
        private List<VisitorData> visitors = new ArrayList<>();
        private List<QueueData> queues = new ArrayList<>();
        private Double ingresosTotales;
    }

    private static class ZoneData {
        private Long id;
        private String name;
        private int maxCapacity;
        private List<Long> operatorIds;
        private List<Long> attractionIds;
        private Double posX;
        private Double posY;

        private Long getIdSafe() {
            return id != null ? id : Long.MAX_VALUE;
        }
    }

    private static class AttractionData {
        private Long id;
        private String name;
        private String type;
        private int maxCapacityPerCycle;
        private double minHeight;
        private int minAge;
        private Double estaturaMinima;
        private Integer edadMinima;
        private double additionalCost;
        private Long zoneId;
        private String status;
        private String estado;
        private String closureReason;
        private Boolean climateOverrideActive;
        private int accumulatedVisitors;
        private Integer visitantesTotales;
        private int estimatedWaitTime;
        private Double posX;
        private Double posY;

        private Long getIdSafe() {
            return id != null ? id : Long.MAX_VALUE;
        }
    }

    private static class ConnectionData {
        private Long sourceId;
        private Long destinationId;
        private int weight;

        private Long getSourceIdSafe() {
            return sourceId != null ? sourceId : Long.MAX_VALUE;
        }

        private Long getDestinationIdSafe() {
            return destinationId != null ? destinationId : Long.MAX_VALUE;
        }
    }

    private static class OperatorData {
        private Long id;
        private String username;
        private String password;
        private String email;
        private Boolean active;
        private Long zoneId;
        private List<Long> assignedAttractionsIds;

        private Long getIdSafe() {
            return id != null ? id : Long.MAX_VALUE;
        }
    }

    private static class VisitorData {
        private Long id;
        private String name;
        private String username;
        private String password;
        private String email;
        private Boolean active;
        private String document;
        private int age;
        private double height;
        private double virtualBalance;
        private String photoPath;
        private String ticketType;
        private List<Long> favoriteAttractions;
        private List<Long> visitHistory;
        private List<String> historialAtracciones;
        private List<String> notifications;
        private String mensajeAlerta;
        private int positionInQueue;
        private Long currentQueueAttractionId;
        private Long currentLocationAttractionId;

        private Long getIdSafe() {
            return id != null ? id : Long.MAX_VALUE;
        }
    }

    private static class QueueData {
        private Long attractionId;
        private List<QueueEntryData> entries = new ArrayList<>();

        private Long getAttractionIdSafe() {
            return attractionId != null ? attractionId : Long.MAX_VALUE;
        }
    }

    private static class QueueEntryData {
        private Long visitorId;
        private String visitorName;
        private String ticketType;
        private int priority;
        private int positionInQueue;
        private long timestamp;
    }

    private List<ZoneData> buildZoneData() {
        List<ZoneData> zones = new ArrayList<>();
        for (Zone zone : attractionService.getAllZones()) {
            if (!attractionService.isRenderableZone(zone)) {
                continue;
            }
            ZoneData zoneData = new ZoneData();
            zoneData.id = zone.getId();
            zoneData.name = zone.getName();
            zoneData.maxCapacity = zone.getMaxCapacity();
            zoneData.operatorIds = zone.getOperatorIds() != null ? zone.getOperatorIds().toList() : new ArrayList<>();
            zoneData.attractionIds = zone.getAttractionIds() != null ? new ArrayList<>(zone.getAttractionIds()) : new ArrayList<>();
            zoneData.posX = zone.getPosX();
            zoneData.posY = zone.getPosY();
            zones.add(zoneData);
        }
        zones.sort(Comparator.comparing(ZoneData::getIdSafe));
        return zones;
    }

    private List<AttractionData> buildAttractionData() {
        List<AttractionData> attractions = new ArrayList<>();
        for (Attraction attraction : attractionService.getAllAttractions()) {
            AttractionData attractionData = new AttractionData();
            attractionData.id = attraction.getId();
            attractionData.name = attraction.getName();
            attractionData.type = attraction.getType() != null ? attraction.getType().name() : null;
            attractionData.maxCapacityPerCycle = attraction.getMaxCapacityPerCycle();
            attractionData.minHeight = attraction.getMinHeight();
            attractionData.minAge = attraction.getMinAge();
            attractionData.estaturaMinima = attraction.getMinHeight();
            attractionData.edadMinima = attraction.getMinAge();
            attractionData.additionalCost = attraction.getAdditionalCost();
            attractionData.zoneId = attraction.getZoneId();
            attractionData.status = attraction.getStatus() != null ? attraction.getStatus().name() : null;
            attractionData.estado = attraction.getEstado();
            attractionData.closureReason = attraction.getClosureReason() != null ? attraction.getClosureReason().name() : null;
            attractionData.climateOverrideActive = attraction.isClimateOverrideActive();
            attractionData.accumulatedVisitors = attraction.getAccumulatedVisitors();
            attractionData.visitantesTotales = attraction.getVisitantesTotales();
            attractionData.estimatedWaitTime = attraction.getEstimatedWaitTime();
            attractionData.posX = attraction.getPosX();
            attractionData.posY = attraction.getPosY();
            attractions.add(attractionData);
        }
        attractions.sort(Comparator.comparing(AttractionData::getIdSafe));
        return attractions;
    }

    private List<ConnectionData> buildConnectionData() {
        List<ConnectionData> connections = new ArrayList<>();
        Set<String> processedEdges = new HashSet<>();
        Graph<Attraction> graph = attractionService.getAttractionGraph();

        for (Attraction source : graph.getAllNodes()) {
            for (Graph.Edge<Attraction> edge : graph.getNeighbors(source)) {
                Attraction destination = edge.destination;
                if (source == null || destination == null || source.getId() == null || destination.getId() == null) {
                    continue;
                }

                Long minId = Math.min(source.getId(), destination.getId());
                Long maxId = Math.max(source.getId(), destination.getId());
                String edgeKey = minId + "-" + maxId;
                if (!processedEdges.add(edgeKey)) {
                    continue;
                }

                ConnectionData connectionData = new ConnectionData();
                connectionData.sourceId = minId;
                connectionData.destinationId = maxId;
                connectionData.weight = edge.weight;
                connections.add(connectionData);
            }
        }

        connections.sort(Comparator
                .comparing(ConnectionData::getSourceIdSafe)
                .thenComparing(ConnectionData::getDestinationIdSafe));
        return connections;
    }

    private List<OperatorData> buildOperatorData() {
        List<OperatorData> operators = new ArrayList<>();
        for (Operator operator : authService.getAllOperators()) {
            OperatorData operatorData = new OperatorData();
            operatorData.id = operator.getId();
            operatorData.username = operator.getUsername();
            operatorData.password = operator.getPassword();
            operatorData.email = operator.getEmail();
            operatorData.active = operator.isActive();
            operatorData.zoneId = operator.getZoneId();
            operatorData.assignedAttractionsIds = operator.getAssignedAttractionsIds() != null
                    ? operator.getAssignedAttractionsIds().toList()
                    : new ArrayList<>();
            operators.add(operatorData);
        }
        operators.sort(Comparator.comparing(OperatorData::getIdSafe));
        return operators;
    }

    private List<VisitorData> buildVisitorData() {
        List<VisitorData> visitors = new ArrayList<>();
        for (Visitor visitor : authService.getAllVisitors()) {
            VisitorData visitorData = new VisitorData();
            visitorData.id = visitor.getId();
            visitorData.name = visitor.getUsername();
            visitorData.username = visitor.getUsername();
            visitorData.password = visitor.getPassword();
            visitorData.email = visitor.getEmail();
            visitorData.active = visitor.isActive();
            visitorData.document = visitor.getDocument();
            visitorData.age = visitor.getAge();
            visitorData.height = visitor.getHeight();
            visitorData.virtualBalance = visitor.getVirtualBalance();
            visitorData.photoPath = visitor.getPhotoPath();
            visitorData.ticketType = visitor.getTicketType() != null ? visitor.getTicketType().name() : null;
            visitorData.favoriteAttractions = visitor.getFavoriteAttractions() != null
                    ? visitor.getFavoriteAttractions().toList()
                    : new ArrayList<>();
            visitorData.visitHistory = visitor.getVisitHistory() != null
                    ? visitor.getVisitHistory().toList()
                    : new ArrayList<>();
            visitorData.historialAtracciones = visitor.getHistorialAtracciones() != null
                    ? new ArrayList<>(visitor.getHistorialAtracciones())
                    : new ArrayList<>();
            visitorData.notifications = visitor.getNotifications() != null
                    ? new ArrayList<>(visitor.getNotifications())
                    : new ArrayList<>();
            visitorData.mensajeAlerta = visitor.getMensajeAlerta();
            visitorData.positionInQueue = visitor.getPositionInQueue();
            visitorData.currentQueueAttractionId = visitor.getCurrentQueueAttractionId();
            visitorData.currentLocationAttractionId = visitor.getCurrentLocationAttractionId();
            visitors.add(visitorData);
        }
        visitors.sort(Comparator.comparing(VisitorData::getIdSafe));
        return visitors;
    }

    private List<QueueData> buildQueueData() {
        List<QueueData> queues = new ArrayList<>();
        for (Map.Entry<Long, List<QueueEntry>> entry : queueService.snapshotQueues().entrySet()) {
            QueueData queueData = new QueueData();
            queueData.attractionId = entry.getKey();
            queueData.entries = new ArrayList<>();

            for (QueueEntry queueEntry : entry.getValue()) {
                QueueEntryData queueEntryData = new QueueEntryData();
                queueEntryData.visitorId = queueEntry.getVisitorId();
                queueEntryData.visitorName = queueEntry.getVisitorName();
                queueEntryData.ticketType = queueEntry.getTicketType() != null ? queueEntry.getTicketType().name() : null;
                queueEntryData.priority = queueEntry.getPriority();
                queueEntryData.positionInQueue = queueEntry.getPositionInQueue();
                queueEntryData.timestamp = queueEntry.getTimestamp();
                queueData.entries.add(queueEntryData);
            }
            queues.add(queueData);
        }
        queues.sort(Comparator.comparing(QueueData::getAttractionIdSafe));
        return queues;
    }

    private Map<Long, List<QueueEntry>> buildQueueSnapshot(List<QueueData> queuesData) {
        Map<Long, List<QueueEntry>> snapshot = new HashMap<>();
        if (queuesData == null) {
            return snapshot;
        }

        for (QueueData queueData : queuesData) {
            if (queueData == null || queueData.attractionId == null) {
                continue;
            }

            List<QueueEntry> entries = new ArrayList<>();
            if (queueData.entries != null) {
                for (QueueEntryData entryData : queueData.entries) {
                    if (entryData == null || entryData.visitorId == null) {
                        continue;
                    }
                    TicketType ticketType = entryData.ticketType == null
                            ? TicketType.GENERAL
                            : TicketType.valueOf(entryData.ticketType.toUpperCase());
                    entries.add(new QueueEntry(
                            entryData.visitorId,
                            entryData.visitorName,
                            ticketType,
                            entryData.priority,
                            entryData.positionInQueue,
                            entryData.timestamp
                    ));
                }
            }
            snapshot.put(queueData.attractionId, entries);
        }
        return snapshot;
    }

    private ParkImportData normalizeData(ParkImportData data) {
        ParkImportData normalized = data != null ? data : new ParkImportData();
        if (normalized.zones == null) {
            normalized.zones = new ArrayList<>();
        }
        if (normalized.attractions == null) {
            normalized.attractions = new ArrayList<>();
        }
        if (normalized.connections == null) {
            normalized.connections = new ArrayList<>();
        }
        if (normalized.operators == null) {
            normalized.operators = new ArrayList<>();
        }
        if (normalized.visitors == null) {
            normalized.visitors = new ArrayList<>();
        }
        if (normalized.queues == null) {
            normalized.queues = new ArrayList<>();
        }
        normalized.zones = deduplicateZones(normalized.zones);
        normalized.attractions = deduplicateAttractions(normalized.attractions);
        normalized.connections = deduplicateConnections(normalized.connections);
        normalized.operators = deduplicateOperators(normalized.operators);
        normalized.visitors = deduplicateVisitors(normalized.visitors);
        normalized.queues = deduplicateQueues(normalized.queues);
        return normalized;
    }

    private void initializeGraphOnLoad(List<ConnectionData> persistedConnections) {
        List<Attraction> attractions = attractionService.getAllAttractions();
        Graph<Attraction> zoneGraph = graphService.buildZoneConstrainedGraph(attractions);
        List<ConnectionData> normalizedConnections = deduplicateConnections(persistedConnections);

        attractionService.replaceAttractionGraph(new Graph<>());
        if (shouldUsePersistedConnections(normalizedConnections, zoneGraph)) {
            attractionService.replaceAttractionGraph(buildGraphFromConnections(attractions, normalizedConnections));
            return;
        }

        attractionService.replaceAttractionGraph(zoneGraph);
    }

    private boolean shouldUsePersistedConnections(List<ConnectionData> persistedConnections, Graph<Attraction> expectedGraph) {
        if (persistedConnections == null || persistedConnections.isEmpty()) {
            return false;
        }

        Set<String> expectedEdgeKeys = buildEdgeKeys(expectedGraph);
        Set<String> persistedEdgeKeys = new HashSet<>();

        for (ConnectionData connectionData : persistedConnections) {
            if (connectionData == null
                    || connectionData.sourceId == null
                    || connectionData.destinationId == null
                    || Objects.equals(connectionData.sourceId, connectionData.destinationId)) {
                return false;
            }

            Attraction source = attractionService.getAttractionById(connectionData.sourceId);
            Attraction destination = attractionService.getAttractionById(connectionData.destinationId);
            if (source == null || destination == null) {
                return false;
            }

            persistedEdgeKeys.add(buildEdgeKey(connectionData.sourceId, connectionData.destinationId));
        }

        return expectedEdgeKeys.equals(persistedEdgeKeys);
    }

    private Graph<Attraction> buildGraphFromConnections(List<Attraction> attractions, List<ConnectionData> connections) {
        Graph<Attraction> graph = new Graph<>();
        if (attractions != null) {
            for (Attraction attraction : attractions) {
                if (attraction != null) {
                    graph.addNode(attraction);
                }
            }
        }

        if (connections == null) {
            return graph;
        }

        for (ConnectionData connectionData : connections) {
            if (connectionData == null || connectionData.sourceId == null || connectionData.destinationId == null) {
                continue;
            }

            Attraction source = attractionService.getAttractionById(connectionData.sourceId);
            Attraction destination = attractionService.getAttractionById(connectionData.destinationId);
            if (source == null || destination == null || Objects.equals(source.getId(), destination.getId())) {
                continue;
            }

            int weight = connectionData.weight > 0
                    ? connectionData.weight
                    : attractionService.calculateEdgeWeight(source, destination);
            graph.addEdge(source, destination, weight);
        }

        return graph;
    }

    private Set<String> buildEdgeKeys(Graph<Attraction> graph) {
        Set<String> edgeKeys = new HashSet<>();
        if (graph == null) {
            return edgeKeys;
        }

        for (Attraction source : graph.getAllNodes()) {
            if (source == null || source.getId() == null) {
                continue;
            }
            for (Graph.Edge<Attraction> edge : graph.getNeighbors(source)) {
                Attraction destination = edge.destination;
                if (destination == null || destination.getId() == null) {
                    continue;
                }
                edgeKeys.add(buildEdgeKey(source.getId(), destination.getId()));
            }
        }

        return edgeKeys;
    }

    private String buildEdgeKey(Long sourceId, Long destinationId) {
        long minId = Math.min(sourceId, destinationId);
        long maxId = Math.max(sourceId, destinationId);
        return minId + "-" + maxId;
    }

    private List<ZoneData> deduplicateZones(List<ZoneData> zones) {
        Map<Long, ZoneData> uniqueZones = new LinkedHashMap<>();
        for (ZoneData zoneData : zones) {
            if (zoneData == null || zoneData.id == null) {
                continue;
            }
            uniqueZones.put(zoneData.id, zoneData);
        }
        return new ArrayList<>(uniqueZones.values());
    }

    private List<AttractionData> deduplicateAttractions(List<AttractionData> attractions) {
        Map<Long, AttractionData> uniqueAttractions = new LinkedHashMap<>();
        for (AttractionData attractionData : attractions) {
            if (attractionData == null || attractionData.id == null) {
                continue;
            }
            uniqueAttractions.put(attractionData.id, attractionData);
        }
        return new ArrayList<>(uniqueAttractions.values());
    }

    private List<ConnectionData> deduplicateConnections(List<ConnectionData> connections) {
        Map<String, ConnectionData> uniqueConnections = new LinkedHashMap<>();
        if (connections == null) {
            return new ArrayList<>();
        }

        for (ConnectionData connectionData : connections) {
            if (connectionData == null
                    || connectionData.sourceId == null
                    || connectionData.destinationId == null
                    || Objects.equals(connectionData.sourceId, connectionData.destinationId)) {
                continue;
            }

            Long minId = Math.min(connectionData.sourceId, connectionData.destinationId);
            Long maxId = Math.max(connectionData.sourceId, connectionData.destinationId);
            ConnectionData normalizedConnection = new ConnectionData();
            normalizedConnection.sourceId = minId;
            normalizedConnection.destinationId = maxId;
            normalizedConnection.weight = connectionData.weight;
            uniqueConnections.put(buildEdgeKey(minId, maxId), normalizedConnection);
        }

        return new ArrayList<>(uniqueConnections.values());
    }

    private List<OperatorData> deduplicateOperators(List<OperatorData> operators) {
        Map<String, OperatorData> uniqueOperators = new LinkedHashMap<>();
        for (OperatorData operatorData : operators) {
            if (operatorData == null) {
                continue;
            }
            String key = operatorData.id != null ? "id:" + operatorData.id : "username:" + operatorData.username;
            uniqueOperators.put(key, operatorData);
        }
        return new ArrayList<>(uniqueOperators.values());
    }

    private List<VisitorData> deduplicateVisitors(List<VisitorData> visitors) {
        Map<String, VisitorData> uniqueVisitors = new LinkedHashMap<>();
        for (VisitorData visitorData : visitors) {
            if (visitorData == null) {
                continue;
            }
            String key = visitorData.id != null ? "id:" + visitorData.id : "username:" + visitorData.username;
            uniqueVisitors.put(key, visitorData);
        }
        return new ArrayList<>(uniqueVisitors.values());
    }

    private List<QueueData> deduplicateQueues(List<QueueData> queues) {
        Map<Long, QueueData> uniqueQueues = new LinkedHashMap<>();
        for (QueueData queueData : queues) {
            if (queueData == null || queueData.attractionId == null) {
                continue;
            }
            uniqueQueues.put(queueData.attractionId, queueData);
        }
        return new ArrayList<>(uniqueQueues.values());
    }

    public void repairAttractionCoordinates() {
        List<Zone> orderedZones = new ArrayList<>(attractionService.getAllZones());
        orderedZones.sort(Comparator.comparing(zone -> zone.getId() != null ? zone.getId() : Long.MAX_VALUE));

        for (Zone zone : orderedZones) {
            if (zone == null) {
                continue;
            }
            if (zone.getPosX() == null || zone.getPosY() == null) {
                int zoneIndex = zone.getId() != null ? Math.max(0, zone.getId().intValue() - 1) : 0;
                zone.setPosX(zone.getPosX() != null ? zone.getPosX() : 100.0 + (zoneIndex % 3) * 300.0);
                zone.setPosY(zone.getPosY() != null ? zone.getPosY() : 100.0 + (zoneIndex / 3) * 220.0);
            }
        }

        for (Attraction attraction : attractionService.getAllAttractions()) {
            if (attraction == null || attraction.getZoneId() == null) {
                continue;
            }

            attractionService.sincronizarJerarquiaEspacial(attraction);
            if (!attractionService.validarPosicionEnZona(attraction)) {
                attraction.setPosX(null);
                attraction.setPosY(null);
                attractionService.sincronizarJerarquiaEspacial(attraction);
            }
        }
    }

    private void ensureVisitorsSeedFile() throws IOException {
        List<VisitorData> persistedVisitors = readUsersFile();
        if (persistedVisitors.size() == 100) {
            return;
        }

        List<VisitorData> generatedVisitors = generateDefaultVisitors();
        saveUsersFile(generatedVisitors);
    }

    private List<VisitorData> loadVisitorsFromUsersFile() throws IOException {
        List<VisitorData> visitors = readUsersFile();
        if (visitors.size() == 100) {
            return visitors;
        }

        List<VisitorData> generatedVisitors = generateDefaultVisitors();
        saveUsersFile(generatedVisitors);
        return generatedVisitors;
    }

    private List<VisitorData> readUsersFile() throws IOException {
        if (!Files.exists(USERS_FILE_PATH) || Files.size(USERS_FILE_PATH) == 0) {
            return new ArrayList<>();
        }

        String content = Files.readString(USERS_FILE_PATH, StandardCharsets.UTF_8);
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }

        UsuariosFileRecord[] users = gson.fromJson(content, UsuariosFileRecord[].class);
        List<VisitorData> visitors = new ArrayList<>();
        if (users == null) {
            return visitors;
        }

        for (UsuariosFileRecord user : users) {
            if (user == null) {
                continue;
            }
            VisitorData visitorData = new VisitorData();
            visitorData.id = user.id;
            visitorData.name = user.nombre;
            visitorData.username = user.username != null ? user.username : user.nombre;
            visitorData.password = user.password != null ? user.password : "user123";
            visitorData.email = user.email;
            visitorData.active = user.active == null || user.active;
            visitorData.document = user.documento;
            visitorData.age = user.edad;
            visitorData.height = user.estatura;
            visitorData.virtualBalance = user.saldo;
            visitorData.ticketType = user.tipo;
            visitorData.favoriteAttractions = user.favoritos != null ? new ArrayList<>(user.favoritos) : new ArrayList<>();
            visitorData.visitHistory = user.historial != null ? new ArrayList<>(user.historial) : new ArrayList<>();
            visitorData.historialAtracciones = user.historialAtracciones != null
                    ? new ArrayList<>(user.historialAtracciones)
                    : new ArrayList<>();
            visitorData.notifications = user.notificaciones != null ? new ArrayList<>(user.notificaciones) : new ArrayList<>();
            visitorData.mensajeAlerta = user.mensajeAlerta;
            visitorData.positionInQueue = user.posicionEnFila;
            visitorData.currentQueueAttractionId = user.atraccionFilaActual;
            visitorData.currentLocationAttractionId = user.ubicacionActual != null ? user.ubicacionActual : 1L;
            visitors.add(visitorData);
        }
        visitors.sort(Comparator.comparing(VisitorData::getIdSafe));
        return visitors;
    }

    private void saveUsersFile(List<VisitorData> visitors) throws IOException {
        List<UsuariosFileRecord> users = new ArrayList<>();
        for (VisitorData visitor : visitors) {
            if (visitor == null) {
                continue;
            }
            UsuariosFileRecord record = new UsuariosFileRecord();
            record.id = visitor.id;
            record.nombre = visitor.name != null ? visitor.name : visitor.username;
            record.username = visitor.username;
            record.password = visitor.password;
            record.email = visitor.email;
            record.active = visitor.active;
            record.documento = visitor.document;
            record.edad = visitor.age;
            record.estatura = visitor.height;
            record.saldo = visitor.virtualBalance;
            record.tipo = visitor.ticketType;
            record.ubicacionActual = visitor.currentLocationAttractionId;
            record.favoritos = visitor.favoriteAttractions != null ? new ArrayList<>(visitor.favoriteAttractions) : new ArrayList<>();
            record.historial = visitor.visitHistory != null ? new ArrayList<>(visitor.visitHistory) : new ArrayList<>();
            record.historialAtracciones = visitor.historialAtracciones != null
                    ? new ArrayList<>(visitor.historialAtracciones)
                    : new ArrayList<>();
            record.notificaciones = visitor.notifications != null ? new ArrayList<>(visitor.notifications) : new ArrayList<>();
            record.mensajeAlerta = visitor.mensajeAlerta;
            record.posicionEnFila = visitor.positionInQueue;
            record.atraccionFilaActual = visitor.currentQueueAttractionId;
            users.add(record);
        }

        users.sort(Comparator.comparing(user -> user.id != null ? user.id : Long.MAX_VALUE));
        Files.writeString(USERS_FILE_PATH, gson.toJson(users), StandardCharsets.UTF_8);
    }

    private List<VisitorData> generateDefaultVisitors() {
        List<VisitorData> visitors = new ArrayList<>();
        for (int index = 1; index <= 100; index++) {
            VisitorData visitor = new VisitorData();
            visitor.id = 100L + index;
            visitor.name = "Visitante " + index;
            visitor.username = "visitante" + String.format("%03d", index);
            visitor.password = "user123";
            visitor.email = visitor.username + "@techpark.local";
            visitor.active = true;
            visitor.document = "DOC" + String.format("%06d", index);
            visitor.age = 5 + ((index - 1) % 66);
            visitor.height = Math.min(2.10, 1.00 + (((index - 1) * 11) % 111) / 100.0);
            visitor.virtualBalance = 100.0;
            visitor.ticketType = resolveGeneratedTicketType(index).name();
            visitor.favoriteAttractions = new ArrayList<>();
            visitor.visitHistory = new ArrayList<>();
            visitor.historialAtracciones = new ArrayList<>();
            visitor.notifications = new ArrayList<>();
            visitor.mensajeAlerta = null;
            visitor.positionInQueue = -1;
            visitor.currentQueueAttractionId = null;
            visitor.currentLocationAttractionId = 1L;
            visitors.add(visitor);
        }
        return visitors;
    }

    private TicketType resolveGeneratedTicketType(int index) {
        if (index <= 10) {
            return TicketType.FAST_PASS;
        }
        if (index <= 90) {
            return TicketType.GENERAL;
        }
        return TicketType.FAMILIAR;
    }

    private List<QueueData> ensureSeededQueues(List<QueueData> existingQueues, List<AttractionData> attractionData) {
        List<QueueData> queues = deduplicateQueues(existingQueues);
        if (!queues.isEmpty()) {
            return queues;
        }

        List<AttractionData> attractions = attractionData != null ? new ArrayList<>(attractionData) : new ArrayList<>();
        if (attractions.size() < 3) {
            return queues;
        }

        attractions.sort(Comparator.comparing(AttractionData::getIdSafe));
        queues.add(buildSeedQueue(attractions.get(0).id, 0, 0));
        queues.add(buildSeedQueue(attractions.get(1).id, 200, 1_000L));
        queues.add(buildSeedQueue(attractions.get(2).id, 499, 2_000L));
        queues.sort(Comparator.comparing(QueueData::getAttractionIdSafe));
        return queues;
    }

    private QueueData buildSeedQueue(Long attractionId, int size, long baseVisitorId) {
        QueueData queueData = new QueueData();
        queueData.attractionId = attractionId;
        queueData.entries = new ArrayList<>();

        for (int index = 1; index <= size; index++) {
            QueueEntryData entry = new QueueEntryData();
            entry.visitorId = baseVisitorId + index;
            entry.visitorName = "Simulado " + index;
            entry.ticketType = index % 10 == 0 ? TicketType.FAST_PASS.name() : TicketType.GENERAL.name();
            entry.priority = TicketType.FAST_PASS.name().equals(entry.ticketType) ? 1 : 2;
            entry.positionInQueue = index;
            entry.timestamp = index;
            queueData.entries.add(entry);
        }

        return queueData;
    }

    private static class UsuariosFileRecord {
        private Long id;
        private String nombre;
        private String username;
        private String password;
        private String email;
        private Boolean active;
        private String documento;
        private int edad;
        private double estatura;
        private double saldo;
        private String tipo;
        private Long ubicacionActual;
        private List<Long> favoritos;
        private List<Long> historial;
        private List<String> historialAtracciones;
        private List<String> notificaciones;
        private String mensajeAlerta;
        private int posicionEnFila;
        private Long atraccionFilaActual;
    }

    private JsonObject readExistingDataJson(Path outputPath) throws IOException {
        if (!Files.exists(outputPath)) {
            return JsonParser.parseString(readOrInitializeDataFile()).getAsJsonObject();
        }

        String currentContent = Files.readString(outputPath, StandardCharsets.UTF_8);
        if (currentContent == null || currentContent.isBlank()) {
            return JsonParser.parseString(readOrInitializeDataFile()).getAsJsonObject();
        }

        try {
            return JsonParser.parseString(currentContent).getAsJsonObject();
        } catch (RuntimeException exception) {
            return new JsonObject();
        }
    }

    public JsonObject readDataJson() {
        try {
            return JsonParser.parseString(readOrInitializeDataFile()).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible leer data.json", exception);
        }
    }

    private String readOrInitializeDataFile() throws IOException {
        if (!Files.exists(DATA_FILE_PATH) || Files.size(DATA_FILE_PATH) == 0) {
            Files.writeString(DATA_FILE_PATH, DEFAULT_DATA_JSON, StandardCharsets.UTF_8);
            return DEFAULT_DATA_JSON;
        }

        String content = Files.readString(DATA_FILE_PATH, StandardCharsets.UTF_8);
        if (content == null || content.isBlank()) {
            Files.writeString(DATA_FILE_PATH, DEFAULT_DATA_JSON, StandardCharsets.UTF_8);
            return DEFAULT_DATA_JSON;
        }

        return content;
    }

    private JsonObject buildPersistedJson(ParkImportData data, JsonObject existingRoot) {
        JsonObject root = new JsonObject();
        root.add("zones", gson.toJsonTree(data.zones));
        root.add("attractions", gson.toJsonTree(data.attractions));
        root.add("connections", gson.toJsonTree(data.connections));
        root.add("operators", gson.toJsonTree(data.operators));
        root.addProperty("ingresosTotales", queueService.getIngresosTotales());

        boolean shouldPersistVisitors = (existingRoot != null && existingRoot.has("visitors")) || !data.visitors.isEmpty();
        boolean shouldPersistQueues = (existingRoot != null && existingRoot.has("queues")) || !data.queues.isEmpty();

        if (shouldPersistVisitors) {
            root.add("visitors", gson.toJsonTree(data.visitors));
        }
        if (shouldPersistQueues) {
            root.add("queues", gson.toJsonTree(data.queues));
        }

        return root;
    }
}
