package com.techpark.dto;

import com.techpark.model.TicketType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class VisitorProfileDto {
    private Long id;
    private String username;
    private String email;
    private String document;
    private int age;
    private double height;
    private double virtualBalance;
    private TicketType ticketType;
    private List<Long> favoriteAttractions;
    private List<Long> visitHistory;
    private List<String> notifications;
    private int positionInQueue;
    private Long currentQueueAttractionId;
}
