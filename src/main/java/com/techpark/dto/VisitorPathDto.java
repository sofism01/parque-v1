package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class VisitorPathDto {
    private List<GraphNodeDto> path;
    private List<String> pathNames;
    private int totalDistance;
    private boolean reachable;
    private String message;
}
