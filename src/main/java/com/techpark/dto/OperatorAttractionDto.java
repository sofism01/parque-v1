package com.techpark.dto;

import com.techpark.model.Attraction;
import com.techpark.model.QueueEntry;
import com.techpark.model.Zone;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class OperatorAttractionDto {
    private Long id;
    private String name;
    private String type;
    private String estado;
    private String status;
    private Long zoneId;
    private String zoneName;
    private int visitantesTotales;
    private int maintenanceThreshold;
    private int peopleWaiting;
    private int estimatedWaitTime;
    private double minHeight;
    private int minAge;
    private double additionalCost;
    private List<OperatorQueuePreviewDto> queuePreview;

    public static OperatorAttractionDto from(Attraction attraction, Zone zone, List<QueueEntry> queueEntries) {
        List<OperatorQueuePreviewDto> preview = new ArrayList<>();
        if (queueEntries != null) {
            for (int index = 0; index < Math.min(3, queueEntries.size()); index++) {
                QueueEntry entry = queueEntries.get(index);
                if (entry != null) {
                    preview.add(new OperatorQueuePreviewDto(
                            entry.getVisitorId(),
                            entry.getVisitorName(),
                            entry.getTicketType() != null ? entry.getTicketType().name() : null,
                            entry.getPositionInQueue(),
                            entry.getTicketType() != null && "FAST_PASS".equalsIgnoreCase(entry.getTicketType().name())
                    ));
                }
            }
        }

        return new OperatorAttractionDto(
                attraction.getId(),
                attraction.getName(),
                attraction.getType() != null ? attraction.getType().name() : null,
                attraction.getEstado(),
                attraction.getStatus() != null ? attraction.getStatus().name() : null,
                attraction.getZoneId(),
                zone != null ? zone.getName() : null,
                attraction.getVisitantesTotales(),
                500,
                attraction.getPeopleWaiting(),
                attraction.getEstimatedWaitTime(),
                attraction.getMinHeight(),
                attraction.getMinAge(),
                attraction.getAdditionalCost(),
                preview
        );
    }
}
