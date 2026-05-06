package com.techpark.dto;

import com.techpark.model.Attraction;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VisitorAttractionDto {
    private Long id;
    private String name;
    private String type;
    private String status;
    private String estado;
    private Long zoneId;
    private String zoneName;
    private String nombreZona;
    private Double posX;
    private Double posY;
    private double minHeight;
    private int minAge;
    private double estaturaMinima;
    private int edadMinima;
    private double additionalCost;
    private int accumulatedVisitors;
    private int estimatedWaitTime;
    private String formattedWaitTime;
    private int peopleWaiting;

    public static VisitorAttractionDto from(Attraction attraction, int peopleWaiting) {
        return new VisitorAttractionDto(
                attraction.getId(),
                attraction.getName(),
                attraction.getType() != null ? attraction.getType().name() : null,
                attraction.getEstado(),
                attraction.getEstado(),
                attraction.getZoneId(),
                attraction.getZone() != null ? attraction.getZone().getName() : null,
                attraction.getZone() != null ? attraction.getZone().getName() : null,
                attraction.getPosX(),
                attraction.getPosY(),
                attraction.getMinHeight(),
                attraction.getMinAge(),
                attraction.getMinHeight(),
                attraction.getMinAge(),
                attraction.getAdditionalCost(),
                attraction.getAccumulatedVisitors(),
                attraction.getEstimatedWaitTime(),
                attraction.getFormattedWaitTime(),
                peopleWaiting
        );
    }
}
