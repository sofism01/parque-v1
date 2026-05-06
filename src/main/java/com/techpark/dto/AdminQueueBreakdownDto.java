package com.techpark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQueueBreakdownDto {
    private int totalFila;
    private int conteoFastPass;
    private int conteoFamiliar;
    private int conteoGeneral;
}
