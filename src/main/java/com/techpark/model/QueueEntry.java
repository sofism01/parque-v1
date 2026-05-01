package com.techpark.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad: Entrada en la fila virtual de una atraccion
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueEntry implements Comparable<QueueEntry> {
    private Long visitorId;
    private String visitorName;
    private TicketType ticketType;
    private int priority;
    private int positionInQueue;
    private long timestamp;

    public QueueEntry(Long visitorId, String visitorName, TicketType ticketType, int positionInQueue) {
        this.visitorId = visitorId;
        this.visitorName = visitorName;
        this.ticketType = ticketType;
        this.priority = resolvePriority(ticketType);
        this.positionInQueue = positionInQueue;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public int compareTo(QueueEntry other) {
        int priorityComparison = Integer.compare(this.priority, other.priority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        return Long.compare(this.timestamp, other.timestamp);
    }

    private int resolvePriority(TicketType ticketType) {
        if (TicketType.FAST_PASS.equals(ticketType)) {
            return 1;
        }
        if (TicketType.FAMILIAR.equals(ticketType)) {
            return 2;
        }
        return 3;
    }
}
