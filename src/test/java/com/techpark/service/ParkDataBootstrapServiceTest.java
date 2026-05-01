package com.techpark.service;

import com.techpark.model.Attraction;
import com.techpark.model.Zone;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkDataBootstrapServiceTest {

    @Test
    void importData_rebuildsInvalidPersistedConnectionsAndDeduplicatesAttractions() throws Exception {
        AttractionService attractionService = new AttractionService();
        GraphService graphService = new GraphService();
        ReflectionTestUtils.setField(graphService, "attractionService", attractionService);

        ParkDataBootstrapService bootstrapService = new ParkDataBootstrapService(
                attractionService,
                graphService,
                new AuthService(),
                new QueueService()
        );

        Path tempFile = Files.createTempFile("techpark-import-", ".json");
        Files.writeString(tempFile, """
                {
                  "zones": [
                    { "id": 1, "name": "Zona Norte", "maxCapacity": 100, "posX": 100.0, "posY": 100.0 },
                    { "id": 2, "name": "Zona Sur", "maxCapacity": 100, "posX": 400.0, "posY": 100.0 }
                  ],
                  "attractions": [
                    { "id": 1, "name": "A1", "type": "MECANICA", "maxCapacityPerCycle": 10, "minHeight": 1.0, "minAge": 8, "additionalCost": 0.0, "zoneId": 1, "status": "ACTIVA", "closureReason": "NINGUNO", "posX": 100.0, "posY": 100.0 },
                    { "id": 2, "name": "A2", "type": "MECANICA", "maxCapacityPerCycle": 10, "minHeight": 1.0, "minAge": 8, "additionalCost": 0.0, "zoneId": 1, "status": "ACTIVA", "closureReason": "NINGUNO", "posX": 120.0, "posY": 100.0 },
                    { "id": 2, "name": "A2 duplicada", "type": "MECANICA", "maxCapacityPerCycle": 10, "minHeight": 1.0, "minAge": 8, "additionalCost": 0.0, "zoneId": 1, "status": "ACTIVA", "closureReason": "NINGUNO", "posX": 121.0, "posY": 100.0 },
                    { "id": 3, "name": "B1", "type": "INFANTIL", "maxCapacityPerCycle": 10, "minHeight": 0.0, "minAge": 0, "additionalCost": 0.0, "zoneId": 2, "status": "ACTIVA", "closureReason": "NINGUNO", "posX": 400.0, "posY": 100.0 }
                  ],
                  "connections": [
                    { "sourceId": 1, "destinationId": 2, "weight": 20 },
                    { "sourceId": 1, "destinationId": 3, "weight": 300 },
                    { "sourceId": 2, "destinationId": 3, "weight": 280 }
                  ],
                  "operators": [],
                  "visitors": [],
                  "queues": []
                }
                """, StandardCharsets.UTF_8);

        try {
            bootstrapService.importData(tempFile.toFile());

            List<Attraction> attractions = attractionService.getAllAttractions();
            assertEquals(3, attractions.size());

            Attraction attractionOne = attractionService.getAttractionById(1L);
            Attraction attractionTwo = attractionService.getAttractionById(2L);
            Attraction attractionThree = attractionService.getAttractionById(3L);
            assertNotNull(attractionOne);
            assertNotNull(attractionTwo);
            assertNotNull(attractionThree);

            assertTrue(attractionService.getAttractionGraph().hasEdge(attractionOne, attractionTwo));
            assertTrue(attractionService.getAttractionGraph().hasEdge(attractionTwo, attractionThree));
            assertFalse(attractionService.getAttractionGraph().hasEdge(attractionOne, attractionThree));

            Zone zoneOne = attractionService.getZoneById(1L);
            assertNotNull(zoneOne);
            assertEquals(List.of(1L, 2L), zoneOne.getAttractionIds());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
