package com.techpark.service;

import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.TicketType;
import com.techpark.model.Visitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QueueServiceTest {

    @Test
    void procesarAvanceDeColas_cierraAtraccionPorMantenimientoYPersiste() {
        AttractionService attractionService = new AttractionService();
        AuthService authService = new AuthService();
        ParkDataBootstrapService bootstrapService = mock(ParkDataBootstrapService.class);
        QueueService queueService = new QueueService(attractionService, authService, bootstrapService);

        Attraction attraction = attractionService.getAttractionById(1L);
        Visitor visitor = authService.getVisitor(3L);
        assertNotNull(attraction);
        assertNotNull(visitor);

        attraction.setVisitantesTotales(499);
        attraction.setStatus(AttractionStatus.ACTIVA);
        visitor.setCurrentQueueAttractionId(attraction.getId());
        queueService.addVisitorToQueue(attraction.getId(), visitor);

        Visitor waitingVisitor = new Visitor();
        waitingVisitor.setId(999L);
        waitingVisitor.setUsername("esperando");
        waitingVisitor.setPassword("user123");
        waitingVisitor.setRole("VISITOR");
        waitingVisitor.setActive(true);
        waitingVisitor.setTicketType(TicketType.GENERAL);
        queueService.addVisitorToQueue(attraction.getId(), waitingVisitor);

        queueService.procesarAvanceDeColas();

        assertEquals(500, attraction.getVisitantesTotales());
        assertEquals(AttractionStatus.MANTENIMIENTO, attraction.getStatus());
        assertEquals(0, queueService.getQueueSize(attraction.getId()));
        assertEquals(-1, visitor.getPositionInQueue());
        assertNull(visitor.getCurrentQueueAttractionId());
        assertFalse(visitor.getVisitHistory().toList().isEmpty());
        assertEquals(attraction.getId(), visitor.getVisitHistory().toList().get(0));
        assertEquals(attraction.getName(), visitor.getHistorialAtracciones().get(0));
        assertEquals("La atraccion " + attraction.getName()
                + " ha cerrado por MANTENIMIENTO. Has sido removido de la fila.", waitingVisitor.getMensajeAlerta());
        assertEquals(-1, waitingVisitor.getPositionInQueue());
        assertNull(waitingVisitor.getCurrentQueueAttractionId());
        verify(bootstrapService, times(1)).saveData();
    }

    @Test
    void addVisitorToQueue_rechazaAtraccionEnMantenimiento() {
        AttractionService attractionService = new AttractionService();
        AuthService authService = new AuthService();
        QueueService queueService = new QueueService(attractionService, authService, mock(ParkDataBootstrapService.class));

        Attraction attraction = attractionService.getAttractionById(1L);
        assertNotNull(attraction);
        attraction.setStatus(AttractionStatus.MANTENIMIENTO);

        int position = queueService.addVisitorToQueue(1L, 999L, "bloqueado", TicketType.GENERAL);

        assertEquals(-1, position);
        assertTrue(queueService.getFullQueue(1L).isEmpty());
    }

    @Test
    void addVisitorToQueue_descuentaCostoYAcumulaIngresos() {
        AttractionService attractionService = new AttractionService();
        AuthService authService = new AuthService();
        QueueService queueService = new QueueService(attractionService, authService, mock(ParkDataBootstrapService.class));

        Attraction attraction = attractionService.getAttractionById(1L);
        Visitor visitor = authService.getVisitor(3L);
        assertNotNull(attraction);
        assertNotNull(visitor);

        visitor.setVirtualBalance(20.0);
        int position = queueService.addVisitorToQueue(attraction.getId(), visitor);

        assertEquals(1, position);
        assertEquals(12.0, visitor.getVirtualBalance());
        assertEquals(8.0, queueService.getIngresosTotales());
    }

    @Test
    void addVisitorToQueue_rechazaCuandoSaldoEsInsuficiente() {
        AttractionService attractionService = new AttractionService();
        AuthService authService = new AuthService();
        QueueService queueService = new QueueService(attractionService, authService, mock(ParkDataBootstrapService.class));

        Attraction attraction = attractionService.getAttractionById(1L);
        Visitor visitor = authService.getVisitor(3L);
        assertNotNull(attraction);
        assertNotNull(visitor);

        visitor.setVirtualBalance(3.0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> queueService.addVisitorToQueue(attraction.getId(), visitor)
        );

        assertEquals("Saldo insuficiente para esta atraccion", exception.getMessage());
        assertEquals(3.0, visitor.getVirtualBalance());
        assertEquals(0.0, queueService.getIngresosTotales());
    }
}
