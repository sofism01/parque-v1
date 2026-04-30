package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminZoneDto {
    private Long id;
    private String name;
    private int maxCapacity;
    private int currentOccupancy;
    private List<Long> operatorIds;
    private List<Long> attractionIds;
    private Double posX;
    private Double posY;
}
