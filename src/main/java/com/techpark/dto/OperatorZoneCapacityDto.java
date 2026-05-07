package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OperatorZoneCapacityDto {
    private Long zoneId;
    private String zoneName;
    private int currentOccupancy;
    private int maxCapacity;
    private boolean full;
    private double ratio;
}
