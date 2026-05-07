package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class OperatorDashboardDto {
    private Long operatorId;
    private String operatorName;
    private String operatorEmail;
    private Long zoneId;
    private String zoneName;
    private OperatorZoneCapacityDto zoneCapacity;
    private List<OperatorAttractionDto> attractions;
}
