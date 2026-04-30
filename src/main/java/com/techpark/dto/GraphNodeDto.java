package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeDto {
    private Long id;
    private String name;
    private String type;
    private String status;
    private Long zoneId;
    private String zoneName;
    private Double posX;
    private Double posY;
}
