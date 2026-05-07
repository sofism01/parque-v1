package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OperatorRestrictionCheckDto {
    private boolean allowed;
    private String icon;
    private String message;
    private Long visitorId;
    private String visitorName;
    private Long attractionId;
    private String attractionName;
    private boolean meetsAge;
    private boolean meetsHeight;
    private boolean hasSufficientBalance;
    private int visitorAge;
    private double visitorHeight;
    private double visitorBalance;
    private int requiredAge;
    private double requiredHeight;
    private double requiredBalance;
}
