package com.techpark.service;

import com.techpark.model.Attraction;
import com.techpark.model.OperationResult;
import com.techpark.model.Operator;
import com.techpark.model.Visitor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ZoneServiceTest {

    @Test
    void deleteZone_removesAttractionsQueuesEdgesAndPersistsImmediately() {
        AuthService authService = new AuthService();
        AttractionService attractionService = new AttractionService();
        QueueService queueService = new QueueService(attractionService, authService);
        GraphService graphService = new GraphService();
        ReflectionTestUtils.setField(attractionService, "queueService", queueService);
        ReflectionTestUtils.setField(attractionService, "authService", authService);
        ReflectionTestUtils.setField(graphService, "attractionService", attractionService);

        ParkDataBootstrapService parkDataBootstrapService = mock(ParkDataBootstrapService.class);
        ZoneService zoneService = new ZoneService(attractionService, graphService, parkDataBootstrapService, authService);

        Visitor visitor = authService.getVisitor(3L);
        assertNotNull(visitor);
        visitor.addFavorite(1L);
        visitor.setCurrentQueueAttractionId(1L);
        visitor.setPositionInQueue(1);
        queueService.addVisitorToQueue(1L, visitor.getId(), visitor.getUsername(), visitor.getTicketType());

        Attraction attractionOne = attractionService.getAttractionById(1L);
        Attraction attractionThree = attractionService.getAttractionById(3L);
        Attraction attractionFour = attractionService.getAttractionById(4L);
        assertNotNull(attractionOne);
        assertNotNull(attractionThree);
        assertNotNull(attractionFour);
        assertTrue(attractionService.getAttractionGraph().hasEdge(attractionOne, attractionThree));

        Operator operator = authService.getOperator(2L);
        assertNotNull(operator);
        operator.addAttraction(1L);
        operator.addAttraction(3L);

        OperationResult result = zoneService.deleteZone(1L);

        assertTrue(result.isSuccess());
        assertNull(attractionService.getZoneById(1L));
        assertTrue(attractionService.getAttractionsByZone(1L).isEmpty());
        assertNull(attractionService.getAttractionById(1L));
        assertNull(attractionService.getAttractionById(3L));
        assertEquals(0, queueService.getQueueSize(1L));
        assertFalse(visitor.getFavoriteAttractions().contains(1L));
        assertNull(visitor.getCurrentQueueAttractionId());
        assertEquals(-1, visitor.getPositionInQueue());
        assertNull(operator.getZoneId());
        assertFalse(operator.managesAttraction(1L));
        assertFalse(operator.managesAttraction(3L));
        assertFalse(attractionService.getAttractionGraph().hasEdge(attractionFour, attractionOne));

        verify(parkDataBootstrapService, times(1)).saveData();
    }
}
