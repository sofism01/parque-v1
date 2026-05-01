package com.techpark.service;

import com.techpark.datastructures.PriorityQueue;
import com.techpark.model.QueueEntry;
import com.techpark.model.TicketType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de Gestion de Colas Virtuales
 */
@Service
public class QueueService {
    private Map<Long, PriorityQueue<QueueEntry>> attractionQueues;
    private Map<Long, Map<Long, Integer>> visitorPositionsByAttraction;

    public QueueService() {
        this.attractionQueues = new HashMap<>();
        this.visitorPositionsByAttraction = new HashMap<>();
    }

    public int addVisitorToQueue(Long attractionId, Long visitorId, String visitorName, TicketType ticketType) {
        PriorityQueue<QueueEntry> queue = attractionQueues.computeIfAbsent(attractionId, k -> new PriorityQueue<>());
        QueueEntry entry = new QueueEntry(visitorId, visitorName, ticketType, queue.size() + 1);
        queue.enqueue(entry);
        updatePositions(attractionId);
        return getQueuePosition(attractionId, visitorId);
    }

    public QueueEntry getNextInQueue(Long attractionId) {
        PriorityQueue<QueueEntry> queue = attractionQueues.get(attractionId);

        if (queue != null && !queue.isEmpty()) {
            QueueEntry nextEntry = queue.dequeue();
            removeVisitorPosition(attractionId, nextEntry.getVisitorId());
            updatePositions(attractionId);
            return nextEntry;
        }

        return null;
    }

    public int getQueuePosition(Long attractionId, Long visitorId) {
        return visitorPositionsByAttraction
                .getOrDefault(attractionId, new HashMap<>())
                .getOrDefault(visitorId, -1);
    }

    public int getQueueSize(Long attractionId) {
        PriorityQueue<QueueEntry> queue = attractionQueues.get(attractionId);
        return queue != null ? queue.size() : 0;
    }

    public List<QueueEntry> getFullQueue(Long attractionId) {
        PriorityQueue<QueueEntry> queue = attractionQueues.get(attractionId);

        if (queue != null) {
            return queue.getAllElements();
        }

        return new ArrayList<>();
    }

    public boolean removeVisitorFromQueue(Long attractionId, Long visitorId) {
        PriorityQueue<QueueEntry> queue = attractionQueues.get(attractionId);

        if (queue != null && !queue.isEmpty()) {
            List<QueueEntry> entries = queue.getAllElements();
            List<QueueEntry> remainingEntries = new ArrayList<>();
            boolean removed = false;
            for (QueueEntry entry : entries) {
                if (entry.getVisitorId().equals(visitorId)) {
                    removeVisitorPosition(attractionId, visitorId);
                    removed = true;
                } else {
                    remainingEntries.add(entry);
                }
            }

            if (removed) {
                PriorityQueue<QueueEntry> rebuiltQueue = new PriorityQueue<>();
                for (QueueEntry entry : remainingEntries) {
                    rebuiltQueue.enqueue(entry);
                }
                attractionQueues.put(attractionId, rebuiltQueue);
                updatePositions(attractionId);
                return true;
            }
        }

        return false;
    }

    public void clearQueue(Long attractionId) {
        PriorityQueue<QueueEntry> queue = attractionQueues.get(attractionId);

        if (queue != null) {
            attractionQueues.remove(attractionId);
            visitorPositionsByAttraction.remove(attractionId);
        }
    }

    public int estimateWaitTime(Long attractionId, int capacityPerCycle, int cycleTimeMinutes) {
        int queueSize = getQueueSize(attractionId);
        int cycles = (int) Math.ceil((double) queueSize / capacityPerCycle);
        return cycles * cycleTimeMinutes;
    }

    public Map<Long, Integer> getQueueStats() {
        Map<Long, Integer> stats = new HashMap<>();
        for (Map.Entry<Long, PriorityQueue<QueueEntry>> entry : attractionQueues.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    public QueueEntry findVisitorEntry(Long attractionId, Long visitorId) {
        if (attractionId == null || visitorId == null) {
            return null;
        }

        for (QueueEntry entry : getFullQueue(attractionId)) {
            if (visitorId.equals(entry.getVisitorId())) {
                return entry;
            }
        }
        return null;
    }

    public void restoreQueues(Map<Long, List<QueueEntry>> queuesByAttraction) {
        attractionQueues.clear();
        visitorPositionsByAttraction.clear();

        if (queuesByAttraction == null) {
            return;
        }

        for (Map.Entry<Long, List<QueueEntry>> queueEntry : queuesByAttraction.entrySet()) {
            Long attractionId = queueEntry.getKey();
            if (attractionId == null) {
                continue;
            }

            PriorityQueue<QueueEntry> rebuiltQueue = new PriorityQueue<>();
            List<QueueEntry> entries = queueEntry.getValue();
            if (entries != null) {
                for (QueueEntry entry : entries) {
                    if (entry != null) {
                        rebuiltQueue.enqueue(entry);
                    }
                }
            }

            attractionQueues.put(attractionId, rebuiltQueue);
            updatePositions(attractionId);
        }
    }

    public Map<Long, List<QueueEntry>> snapshotQueues() {
        Map<Long, List<QueueEntry>> snapshot = new HashMap<>();
        for (Map.Entry<Long, PriorityQueue<QueueEntry>> entry : attractionQueues.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().getAllElements());
        }
        return snapshot;
    }

    public List<QueueEntry> cancelQueue(Long attractionId) {
        List<QueueEntry> cancelledEntries = getFullQueue(attractionId);
        clearQueue(attractionId);
        return cancelledEntries;
    }

    private void updatePositions(Long attractionId) {
        PriorityQueue<QueueEntry> queue = attractionQueues.get(attractionId);
        if (queue == null) {
            visitorPositionsByAttraction.remove(attractionId);
            return;
        }

        List<QueueEntry> orderedEntries = new ArrayList<>(queue.getAllElements());
        orderedEntries.sort(QueueEntry::compareTo);

        Map<Long, Integer> positions = new HashMap<>();
        for (int i = 0; i < orderedEntries.size(); i++) {
            QueueEntry entry = orderedEntries.get(i);
            entry.setPositionInQueue(i + 1);
            positions.put(entry.getVisitorId(), i + 1);
        }
        visitorPositionsByAttraction.put(attractionId, positions);
    }

    private void removeVisitorPosition(Long attractionId, Long visitorId) {
        Map<Long, Integer> positions = visitorPositionsByAttraction.get(attractionId);
        if (positions != null) {
            positions.remove(visitorId);
            if (positions.isEmpty()) {
                visitorPositionsByAttraction.remove(attractionId);
            }
        }
    }
}
