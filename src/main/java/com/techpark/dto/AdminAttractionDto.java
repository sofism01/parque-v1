package com.techpark.dto;

import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAttractionDto {
    private Long id;
    private String name;
    private AttractionType type;
    private int maxCapacityPerCycle;
    private double minHeight;
    private int minAge;
    private double additionalCost;
    private int accumulatedVisitors;
    private int estimatedWaitTime;
    private String formattedWaitTime;
    private AttractionStatus status;
    private String estado;
    private ClosureReason closureReason;
    private boolean climateOverrideActive;
    private Long zoneId;
    private Double posX;
    private Double posY;
    private int totalFila;
    private int conteoFastPass;
    private int conteoFamiliar;
    private int conteoGeneral;

    public static AdminAttractionDto from(Attraction attraction, AdminQueueBreakdownDto queueBreakdown) {
        AdminQueueBreakdownDto safeBreakdown = queueBreakdown != null
                ? queueBreakdown
                : new AdminQueueBreakdownDto(0, 0, 0, 0);

        return new AdminAttractionDto(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                attraction.getMaxCapacityPerCycle(),
                attraction.getMinHeight(),
                attraction.getMinAge(),
                attraction.getAdditionalCost(),
                attraction.getAccumulatedVisitors(),
                attraction.getEstimatedWaitTime(),
                attraction.getFormattedWaitTime(),
                attraction.getStatus(),
                attraction.getEstado(),
                attraction.getClosureReason(),
                attraction.isClimateOverrideActive(),
                attraction.getZoneId(),
                attraction.getPosX(),
                attraction.getPosY(),
                safeBreakdown.getTotalFila(),
                safeBreakdown.getConteoFastPass(),
                safeBreakdown.getConteoFamiliar(),
                safeBreakdown.getConteoGeneral()
        );
    }
}
