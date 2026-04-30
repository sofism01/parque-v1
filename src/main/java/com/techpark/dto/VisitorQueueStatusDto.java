package com.techpark.dto;

import com.techpark.model.TicketType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VisitorQueueStatusDto {
    private Long attractionId;
    private String attractionName;
    private Integer position;
    private Integer totalInQueue;
    private Integer estimatedWaitTime;
    private TicketType ticketType;
    private Double remainingBalance;
    private String message;
}
