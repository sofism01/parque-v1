package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphSnapshotDto {
    private List<GraphNodeDto> nodes;
    private List<GraphEdgeDto> edges;
    private List<GraphZoneDto> zones;
}
