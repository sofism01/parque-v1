package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphZoneDto {
    private Long id;
    private String name;
    private Double posX;
    private Double posY;
    private Double width;
    private Double height;
    private List<Long> attractionIds;
}
