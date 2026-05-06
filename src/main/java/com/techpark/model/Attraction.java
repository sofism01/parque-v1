package com.techpark.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Entidad: Atraccion del parque
 */
@Data
public class Attraction implements Comparable<Attraction> {
    private static final int SEGUNDOS_POR_PERSONA = 20;
    private static final Comparator<Visitor> VISITOR_PRIORITY_COMPARATOR = Comparator
            .comparingInt(Attraction::resolveVisitorPriority)
            .thenComparing(visitor -> visitor != null ? visitor.getId() : Long.MAX_VALUE);

    private Long id;
    private String name;
    private AttractionType type;
    private int maxCapacityPerCycle;
    private double minHeight;
    private int minAge;
    private double additionalCost;
    private int visitantesTotales;
    private AttractionStatus status;
    private String estado;
    private ClosureReason closureReason;
    private boolean climateOverrideActive;
    private Long zoneId;
    private Zone zone;
    private Double posX;
    private Double posY;
    private transient PriorityQueue<Visitor> virtualQueue = new PriorityQueue<>(VISITOR_PRIORITY_COMPARATOR);

    public Attraction() {
        resetRuntimeState();
    }

    public Attraction(Long id, String name, AttractionType type, int maxCapacityPerCycle, double minHeight,
                      int minAge, double additionalCost, int accumulatedVisitors, int estimatedWaitTime,
                      AttractionStatus status, String estado, ClosureReason closureReason,
                      boolean climateOverrideActive, Long zoneId, Zone zone, Double posX, Double posY) {
        this();
        this.id = id;
        this.name = name;
        this.type = type;
        this.maxCapacityPerCycle = maxCapacityPerCycle;
        this.minHeight = minHeight;
        this.minAge = minAge;
        this.additionalCost = additionalCost;
        this.visitantesTotales = accumulatedVisitors;
        this.status = status;
        this.estado = estado;
        this.closureReason = closureReason;
        this.climateOverrideActive = climateOverrideActive;
        this.zoneId = zoneId;
        this.zone = zone;
        this.posX = posX;
        this.posY = posY;
        setEstimatedWaitTime(estimatedWaitTime);
    }

    public Attraction(String name) {
        this();
        this.name = name;
        this.status = AttractionStatus.ACTIVA;
        syncEstadoFromStatus();
    }

    public Attraction(String name, AttractionType type, int maxCapacityPerCycle,
                      double minHeight, int minAge, double additionalCost, Long zoneId) {
        this();
        this.name = name;
        this.type = type;
        this.maxCapacityPerCycle = maxCapacityPerCycle;
        this.minHeight = minHeight;
        this.minAge = minAge;
        this.additionalCost = additionalCost;
        this.zoneId = zoneId;
        this.visitantesTotales = 0;
        this.status = AttractionStatus.ACTIVA;
        this.closureReason = ClosureReason.NINGUNO;
        this.climateOverrideActive = false;
        syncEstadoFromStatus();
    }

    public boolean needsMaintenance() {
        return visitantesTotales >= 500;
    }

    public void addVisitor() {
        visitantesTotales++;
        if (visitantesTotales >= 500) {
            changeStatus(AttractionStatus.MANTENIMIENTO, ClosureReason.TECNICO);
        }
    }

    public void resetMaintenance() {
        visitantesTotales = 0;
        changeStatus(AttractionStatus.ACTIVA, ClosureReason.NINGUNO);
        climateOverrideActive = false;
    }

    public int getVisitantesTotales() {
        return visitantesTotales;
    }

    public void setVisitantesTotales(int visitantesTotales) {
        this.visitantesTotales = Math.max(visitantesTotales, 0);
    }

    public int getAccumulatedVisitors() {
        return visitantesTotales;
    }

    public void setAccumulatedVisitors(int accumulatedVisitors) {
        setVisitantesTotales(accumulatedVisitors);
    }

    public void changeStatus(AttractionStatus newStatus, ClosureReason reason) {
        this.status = newStatus;
        this.closureReason = reason;
        syncEstadoFromStatus();
    }

    public boolean isAvailable() {
        return "ABIERTA".equalsIgnoreCase(getEstado());
    }

    public String getEstado() {
        if ((estado == null || estado.isBlank()) && status != null) {
            syncEstadoFromStatus();
        }
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado != null ? estado.trim().toUpperCase() : null;
        syncStatusFromEstado();
    }

    public void setStatus(AttractionStatus status) {
        this.status = status;
        syncEstadoFromStatus();
    }

    public int getEstimatedWaitTime() {
        return getTiempoEspera();
    }

    public int getTiempoEspera() {
        return calculateWaitTimeSeconds();
    }

    public void setEstimatedWaitTime(int estimatedWaitTime) {
        // Compatibilidad: el tiempo de espera ya no se persiste.
    }

    public String getFormattedWaitTime() {
        return getTiempoEspera() + " segundos";
    }

    public void enqueueVisitor(Visitor visitor) {
        if (visitor == null) {
            return;
        }
        ensureVirtualQueue();
        virtualQueue.offer(visitor);
    }

    public Visitor dequeueVisitor() {
        ensureVirtualQueue();
        return virtualQueue.poll();
    }

    public boolean removeVisitorFromQueue(Long visitorId) {
        ensureVirtualQueue();
        return virtualQueue.removeIf(visitor -> visitor != null && visitorId != null && visitorId.equals(visitor.getId()));
    }

    public int getPeopleWaiting() {
        ensureVirtualQueue();
        return virtualQueue.size();
    }

    public List<Visitor> getOrderedQueueSnapshot() {
        ensureVirtualQueue();
        List<Visitor> orderedVisitors = new ArrayList<>(virtualQueue);
        orderedVisitors.sort(VISITOR_PRIORITY_COMPARATOR);
        return orderedVisitors;
    }

    public void restoreQueue(List<Visitor> visitors) {
        this.virtualQueue = new PriorityQueue<>(VISITOR_PRIORITY_COMPARATOR);
        if (visitors == null) {
            return;
        }
        for (Visitor visitor : visitors) {
            if (visitor != null) {
                this.virtualQueue.offer(visitor);
            }
        }
    }

    private void ensureVirtualQueue() {
        if (virtualQueue == null) {
            virtualQueue = new PriorityQueue<>(VISITOR_PRIORITY_COMPARATOR);
        }
    }

    private void resetRuntimeState() {
        this.virtualQueue = new PriorityQueue<>(VISITOR_PRIORITY_COMPARATOR);
    }

    private int calculateWaitTimeSeconds() {
        int peopleWaiting = getPeopleWaiting();
        if (peopleWaiting <= 0) {
            return 0;
        }
        return peopleWaiting * SEGUNDOS_POR_PERSONA;
    }

    private void syncEstadoFromStatus() {
        if (AttractionStatus.MANTENIMIENTO.equals(status)) {
            estado = "MANTENIMIENTO";
            return;
        }
        if (AttractionStatus.CERRADA.equals(status) || ClosureReason.CLIMA.equals(closureReason)) {
            estado = "CLIMA";
            return;
        }
        estado = "ABIERTA";
    }

    private void syncStatusFromEstado() {
        if ("MANTENIMIENTO".equalsIgnoreCase(estado)) {
            this.status = AttractionStatus.MANTENIMIENTO;
            if (closureReason == null || ClosureReason.NINGUNO.equals(closureReason)) {
                this.closureReason = ClosureReason.MANTENIMIENTO;
            }
            return;
        }
        if ("CLIMA".equalsIgnoreCase(estado)) {
            this.status = AttractionStatus.CERRADA;
            this.closureReason = ClosureReason.CLIMA;
            return;
        }
        this.status = AttractionStatus.ACTIVA;
        this.closureReason = ClosureReason.NINGUNO;
    }

    private static int resolveVisitorPriority(Visitor visitor) {
        if (visitor != null && TicketType.FAST_PASS.equals(visitor.getTicketType())) {
            return 1;
        }
        return 2;
    }

    @Override
    public int compareTo(Attraction other) {
        if (this.name == null && other.name == null) {
            return 0;
        }
        if (this.name == null) {
            return -1;
        }
        if (other.name == null) {
            return 1;
        }
        return this.name.compareToIgnoreCase(other.name);
    }

    @Override
    public String toString() {
        return name != null ? name : "Atraccion";
    }
}
