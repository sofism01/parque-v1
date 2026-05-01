package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminOperatorDto {
    private Long id;
    private String username;
    private String email;
    private Long zoneId;
    private String zoneName;
    private List<Long> assignedAttractionIds;
    private List<String> assignedAttractionNames;
    private boolean active;
}
