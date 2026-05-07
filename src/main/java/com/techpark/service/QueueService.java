package com.techpark.service;

import com.techpark.dto.AdminQueueBreakdownDto;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.ClosureReason;
import com.techpark.model.QueueEntry;
import com.techpark.model.TicketType;
import com.techpark.model.Visitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de Gestion de Colas Virtuales
 */
@Service
public class QueueService {
    private static final int MAINTENANCE_VISITOR_THRESHOLD = 500;

    private final AttractionService attractionService;
    private final AuthService authService;
    private final ParkDataBootstrapService parkDataBootstrapService;
    private double ingresosTotales;

    public QueueService() {
        this.attractionService = null;
        this.authService = null;
        this.parkDataBootstrapService = null;
        this.ingresosTotales = 0.0;
    }

    @Autowired
    public QueueService(@Lazy AttractionService attractionService,
                        AuthService authService,
                        @Lazy ParkDataBootstrapService parkDataBootstrapService) {
        this.attractionService = attractionService;
        this.authService = authService;
        this.parkDataBootstrapService = parkDataBootstrapService;
        this.ingresosTotales = 0.0;
    }

    public QueueService(AttractionService attractionService, AuthService authService) {
        this(attractionService, authService, null);
    }

    public int addVisitorToQueue(Long attractionId, Visitor visitor) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null || visitor == null) {
            return -1;
        }
        if (!AttractionStatus.ACTIVA.equals(attraction.getStatus())) {
            return -1;
        }
        chargeVisitorForQueue(attraction, visitor);

        attraction.enqueueVisitor(visitor);
        updatePositions(attraction);
        return getQueuePosition(attractionId, visitor.getId());
    }

    public int addVisitorToQueue(Long attractionId, Long visitorId, String username, TicketType ticketType) {
        if (visitorId == null) {
            return -1;
        }

        Visitor visitor = authService != null ? authService.getVisitor(visitorId) : null;
        if (visitor == null) {
            visitor = new Visitor();
            visitor.setId(visitorId);
            visitor.setUsername(username != null ? username : "Visitante " + visitorId);
            visitor.setPassword("user123");
            visitor.setRole("VISITOR");
            visitor.setActive(true);
            visitor.setTicketType(ticketType != null ? ticketType : TicketType.GENERAL);
            visitor.setPositionInQueue(-1);
        } else if (ticketType != null) {
            visitor.setTicketType(ticketType);
        }

        return addVisitorToQueue(attractionId, visitor);
    }

    public QueueEntry getNextInQueue(Long attractionId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return null;
        }

        Visitor visitor = advanceQueue(attraction);
        return visitor != null ? toQueueEntry(visitor, -1) : null;
    }

    public int getQueuePosition(Long attractionId, Long visitorId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null || visitorId == null) {
            return -1;
        }

        List<Visitor> orderedQueue = attraction.getOrderedQueueSnapshot();
        for (int index = 0; index < orderedQueue.size(); index++) {
            Visitor visitor = orderedQueue.get(index);
            if (visitor != null && visitorId.equals(visitor.getId())) {
                return index + 1;
            }
        }
        return -1;
    }

    public int getQueueSize(Long attractionId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        return attraction != null ? attraction.getPeopleWaiting() : 0;
    }

    public AdminQueueBreakdownDto getQueueBreakdown(Long attractionId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return new AdminQueueBreakdownDto(0, 0, 0, 0);
        }

        int conteoFastPass = 0;
        int conteoFamiliar = 0;
        int conteoGeneral = 0;

        for (Visitor visitor : attraction.getOrderedQueueSnapshot()) {
            TicketType ticketType = visitor != null && visitor.getTicketType() != null
                    ? visitor.getTicketType()
                    : TicketType.GENERAL;

            switch (ticketType) {
                case FAST_PASS -> conteoFastPass++;
                case FAMILIAR -> conteoFamiliar++;
                default -> conteoGeneral++;
            }
        }

        return new AdminQueueBreakdownDto(
                conteoFastPass + conteoFamiliar + conteoGeneral,
                conteoFastPass,
                conteoFamiliar,
                conteoGeneral
        );
    }

    public List<QueueEntry> getFullQueue(Long attractionId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return new ArrayList<>();
        }

        List<QueueEntry> entries = new ArrayList<>();
        List<Visitor> orderedQueue = attraction.getOrderedQueueSnapshot();
        for (int index = 0; index < orderedQueue.size(); index++) {
            entries.add(toQueueEntry(orderedQueue.get(index), index + 1));
        }
        entries.sort(QueueEntry::compareTo);
        return entries;
    }

    public boolean removeVisitorFromQueue(Long attractionId, Long visitorId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null || visitorId == null) {
            return false;
        }

        boolean removed = attraction.removeVisitorFromQueue(visitorId);
        if (removed) {
            Visitor visitor = authService.getVisitor(visitorId);
            if (visitor != null) {
                visitor.setPositionInQueue(-1);
            }
            updatePositions(attraction);
        }
        return removed;
    }

    public void clearQueue(Long attractionId) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return;
        }

        clearQueueState(attraction);
    }

    public int estimateWaitTime(Long attractionId, int capacityPerCycle, int cycleTimeMinutes) {
        int queueSize = getQueueSize(attractionId);
        if (capacityPerCycle <= 0 || cycleTimeMinutes <= 0) {
            return 0;
        }
        int cycles = (int) Math.ceil((double) queueSize / capacityPerCycle);
        return cycles * cycleTimeMinutes;
    }

    public Map<Long, Integer> getQueueStats() {
        Map<Long, Integer> stats = new LinkedHashMap<>();
        List<Attraction> attractions = new ArrayList<>(attractionService.getAllAttractions());
        attractions.sort(Comparator.comparing(Attraction::getId));
        for (Attraction attraction : attractions) {
            stats.put(attraction.getId(), attraction.getPeopleWaiting());
        }
        return stats;
    }

    public QueueEntry findVisitorEntry(Long attractionId, Long visitorId) {
        if (attractionId == null || visitorId == null) {
            return null;
        }

        List<QueueEntry> queueEntries = getFullQueue(attractionId);
        for (QueueEntry entry : queueEntries) {
            if (visitorId.equals(entry.getVisitorId())) {
                return entry;
            }
        }
        return null;
    }

    public void restoreQueues(Map<Long, List<QueueEntry>> queuesByAttraction) {
        for (Attraction attraction : attractionService.getAllAttractions()) {
            attraction.restoreQueue(new ArrayList<>());
        }

        if (queuesByAttraction == null) {
            return;
        }

        for (Map.Entry<Long, List<QueueEntry>> queueEntry : queuesByAttraction.entrySet()) {
            Attraction attraction = attractionService.getAttractionById(queueEntry.getKey());
            if (attraction == null) {
                continue;
            }

            List<Visitor> restoredVisitors = new ArrayList<>();
            List<QueueEntry> entries = queueEntry.getValue();
            if (entries != null) {
                entries.sort(QueueEntry::compareTo);
                for (QueueEntry entry : entries) {
                    Visitor visitor = resolveQueueVisitor(entry);
                    if (visitor != null) {
                        restoredVisitors.add(visitor);
                    }
                }
            }
            attraction.restoreQueue(restoredVisitors);
            updatePositions(attraction);
        }
    }

    public Map<Long, List<QueueEntry>> snapshotQueues() {
        Map<Long, List<QueueEntry>> snapshot = new LinkedHashMap<>();
        for (Attraction attraction : attractionService.getAllAttractions()) {
            snapshot.put(attraction.getId(), getFullQueue(attraction.getId()));
        }
        return snapshot;
    }

    public List<QueueEntry> cancelQueue(Long attractionId) {
        List<QueueEntry> cancelledEntries = getFullQueue(attractionId);
        clearQueue(attractionId);
        return cancelledEntries;
    }

    public List<QueueEntry> cancelQueueWithAlert(Long attractionId, String mensajeAlerta) {
        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return new ArrayList<>();
        }

        List<QueueEntry> cancelledEntries = getFullQueue(attractionId);
        for (Visitor visitor : attraction.getOrderedQueueSnapshot()) {
            if (visitor == null) {
                continue;
            }
            visitor.setMensajeAlerta(mensajeAlerta);
        }
        clearQueueState(attraction);
        return cancelledEntries;
    }

    public double getIngresosTotales() {
        return ingresosTotales;
    }

    public void setIngresosTotales(double ingresosTotales) {
        this.ingresosTotales = Math.max(ingresosTotales, 0.0);
    }

    @Scheduled(fixedRate = 30000)
    public synchronized void procesarAvanceDeColas() {
        if (attractionService == null) {
            return;
        }

        boolean hasChanges = false;
        for (Attraction attraction : attractionService.getAllAttractions()) {
            if (!isAttractionEligibleForAdvance(attraction)) {
                continue;
            }

            Visitor processedVisitor = advanceQueue(attraction);
            if (processedVisitor != null) {
                hasChanges = true;
                applyAutomaticMaintenance(attraction);
            }
        }

        if (hasChanges && parkDataBootstrapService != null) {
            parkDataBootstrapService.saveData();
        }
    }

    private void updatePositions(Attraction attraction) {
        if (attraction == null) {
            return;
        }

        List<Visitor> orderedQueue = attraction.getOrderedQueueSnapshot();
        for (int index = 0; index < orderedQueue.size(); index++) {
            Visitor visitor = orderedQueue.get(index);
            if (visitor != null) {
                visitor.setPositionInQueue(index + 1);
                visitor.setCurrentQueueAttractionId(attraction.getId());
            }
        }
    }

    private QueueEntry toQueueEntry(Visitor visitor, int position) {
        if (visitor == null) {
            return null;
        }
        return new QueueEntry(
                visitor.getId(),
                visitor.getUsername(),
                visitor.getTicketType(),
                resolvePriority(visitor.getTicketType()),
                position,
                position > 0 ? position : System.currentTimeMillis()
        );
    }

    private Visitor resolveQueueVisitor(QueueEntry entry) {
        if (entry == null || entry.getVisitorId() == null) {
            return null;
        }

        Visitor visitor = authService.getVisitor(entry.getVisitorId());
        if (visitor != null) {
            if (entry.getTicketType() != null) {
                visitor.setTicketType(entry.getTicketType());
            }
            return visitor;
        }

        Visitor transientVisitor = new Visitor();
        transientVisitor.setId(entry.getVisitorId());
        transientVisitor.setUsername(entry.getVisitorName() != null ? entry.getVisitorName() : "Visitante " + entry.getVisitorId());
        transientVisitor.setPassword("user123");
        transientVisitor.setRole("VISITOR");
        transientVisitor.setActive(true);
        transientVisitor.setTicketType(entry.getTicketType() != null ? entry.getTicketType() : TicketType.GENERAL);
        transientVisitor.setPositionInQueue(entry.getPositionInQueue());
        transientVisitor.setCurrentQueueAttractionId(null);
        return transientVisitor;
    }

    private int resolvePriority(TicketType ticketType) {
        return TicketType.FAST_PASS.equals(ticketType) ? 1 : 2;
    }

    private boolean isAttractionEligibleForAdvance(Attraction attraction) {
        return attraction != null
                && AttractionStatus.ACTIVA.equals(attraction.getStatus())
                && attraction.getPeopleWaiting() > 0;
    }

    private Visitor advanceQueue(Attraction attraction) {
        if (attraction == null) {
            return null;
        }

        Visitor visitor = attraction.dequeueVisitor();
        if (visitor == null) {
            return null;
        }

        attraction.addVisitor();
        visitor.addVisit(attraction.getId());
        visitor.addHistorialAtraccion(attraction.getName());
        visitor.setCurrentLocationAttractionId(attraction.getId());
        visitor.setPositionInQueue(-1);
        visitor.setCurrentQueueAttractionId(null);
        updatePositions(attraction);
        return visitor;
    }

    private void applyAutomaticMaintenance(Attraction attraction) {
        if (attraction == null || attraction.getVisitantesTotales() < MAINTENANCE_VISITOR_THRESHOLD) {
            return;
        }
        if (!AttractionStatus.MANTENIMIENTO.equals(attraction.getStatus())) {
            attraction.changeStatus(AttractionStatus.MANTENIMIENTO, ClosureReason.TECNICO);
        }
        if (attraction.getPeopleWaiting() > 0) {
            cancelQueueWithAlert(attraction.getId(), buildClosureAlertMessage(attraction, "MANTENIMIENTO"));
        }
    }

    private void clearQueueState(Attraction attraction) {
        for (Visitor visitor : attraction.getOrderedQueueSnapshot()) {
            if (visitor != null) {
                visitor.setPositionInQueue(-1);
                visitor.setCurrentQueueAttractionId(null);
            }
        }
        attraction.restoreQueue(new ArrayList<>());
    }

    private String buildClosureAlertMessage(Attraction attraction, String razon) {
        return "La atraccion " + attraction.getName() + " ha cerrado por " + razon + ". Has sido removido de la fila.";
    }

    private void chargeVisitorForQueue(Attraction attraction, Visitor visitor) {
        if (attraction == null || visitor == null) {
            return;
        }
        if (visitor.getId() == null || authService == null || authService.getVisitor(visitor.getId()) == null) {
            return;
        }

        double costo = attraction.getAdditionalCost();
        if (visitor.getVirtualBalance() < costo) {
            throw new IllegalStateException("Saldo insuficiente para esta atraccion");
        }

        visitor.setVirtualBalance(visitor.getVirtualBalance() - costo);
        ingresosTotales += costo;
    }
}
