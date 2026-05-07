package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OperatorQueuePreviewDto {
    private Long visitorId;
    private String visitorName;
    private String ticketType;
    private Integer positionInQueue;
    private boolean fastPass;
}
