package com.techpark.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad: Ticket/Entrada del parque
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    private Long id;
    private TicketType type;
    private double price;
    private String description;
    private int maxCapacity;
    private int currentCount;
    private boolean active;

    public boolean isAvailable() {
        return active && currentCount < maxCapacity;
    }

    public void incrementCount() {
        if (currentCount < maxCapacity) {
            currentCount++;
        }
    }
}
