package com.techpark.service;

import com.techpark.datastructures.Graph;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import com.techpark.model.OperationResult;
import com.techpark.model.TicketType;
import com.techpark.model.Visitor;
import com.techpark.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ParqueServiceTest {

    private AttractionService attractionService;
    private Attraction atraccionInicio;
    private Attraction atraccionIntermedia;
    private Attraction atraccionDestino;

    @BeforeEach
    void setUp() {
        attractionService = new AttractionService();
        attractionService.resetParkData();

        Zone zonaPrincipal = new Zone("Zona Principal", 100);
        zonaPrincipal.setId(1L);
        zonaPrincipal.setPosX(200.0);
        zonaPrincipal.setPosY(200.0);
        attractionService.addZone(zonaPrincipal);

        atraccionInicio = crearAtraccion(1L, "Entrada", 100.0, 100.0);
        atraccionIntermedia = crearAtraccion(2L, "Carrusel", 200.0, 100.0);
        atraccionDestino = crearAtraccion(3L, "Montana Rusa", 300.0, 100.0);

        attractionService.addAttraction(atraccionInicio);
        attractionService.addAttraction(atraccionIntermedia);
        attractionService.addAttraction(atraccionDestino);

        Graph<Attraction> graph = new Graph<>();
        graph.addNode(atraccionInicio);
        graph.addNode(atraccionIntermedia);
        graph.addNode(atraccionDestino);
        graph.addEdge(atraccionInicio, atraccionIntermedia, 5);
        graph.addEdge(atraccionIntermedia, atraccionDestino, 5);
        graph.addEdge(atraccionInicio, atraccionDestino, 20);
        attractionService.replaceAttractionGraph(graph);
    }

    @Test
    void debeRetornarRutaMasCortaEnOrdenEsperado() {
        List<Attraction> ruta = attractionService.findShortestPath(atraccionInicio.getId(), atraccionDestino.getId());

        assertEquals(List.of(atraccionInicio, atraccionIntermedia, atraccionDestino), ruta);
        assertThat(ruta).extracting(Attraction::getName)
                .containsExactly("Entrada", "Carrusel", "Montana Rusa");
    }

    @Test
    void debeRetornarFalsoCuandoAlturaEsInsuficiente() {
        Visitor visitante = new Visitor("visitante", "1234", "visitante@test.com");
        visitante.setHeight(1.10);
        visitante.setAge(18);
        visitante.setTicketType(TicketType.GENERAL);

        Attraction atraccionExigente = new Attraction("Torre Extrema", AttractionType.MECANICA_ALTURA, 12, 1.40, 12, 0.0, 1L);
        atraccionExigente.setStatus(AttractionStatus.ACTIVA);

        OperationResult resultado = visitante.canAccessAttraction(atraccionExigente);

        assertFalse(resultado.isSuccess());
        assertThat(resultado.getMessage()).isEqualTo("No cumple con la altura minima");
    }

    @Test
    void debeCalcularTiempoDeEsperaSegunPersonasEnFila() {
        Attraction atraccion = new Attraction("Splash", AttractionType.ACUATICA, 10, 0.0, 0, 0.0, 1L);

        atraccion.enqueueVisitor(crearVisitanteEnFila(10L, "ana"));
        atraccion.enqueueVisitor(crearVisitanteEnFila(11L, "luis"));
        atraccion.enqueueVisitor(crearVisitanteEnFila(12L, "maria"));

        assertThat(atraccion.getPeopleWaiting()).isEqualTo(3);
        assertThat(atraccion.getTiempoEspera()).isEqualTo(60);
    }

    @Test
    void debeActualizarEstadoCuandoAtraccionSeCierra() {
        Attraction atraccion = new Attraction("Troncos", AttractionType.ACUATICA, 8, 0.0, 0, 0.0, 1L);
        atraccion.setStatus(AttractionStatus.ACTIVA);

        atraccion.changeStatus(AttractionStatus.CERRADA, ClosureReason.CLIMA);

        assertThat(atraccion.getStatus()).isEqualTo(AttractionStatus.CERRADA);
        assertThat(atraccion.getEstado()).isEqualTo("CLIMA");
    }

    private Attraction crearAtraccion(Long id, String nombre, double posX, double posY) {
        Attraction attraction = new Attraction(nombre, AttractionType.MECANICA, 20, 1.0, 8, 0.0, 1L);
        attraction.setId(id);
        attraction.setPosX(posX);
        attraction.setPosY(posY);
        attraction.setStatus(AttractionStatus.ACTIVA);
        return attraction;
    }

    private Visitor crearVisitanteEnFila(Long id, String username) {
        Visitor visitor = new Visitor(username, "1234", username + "@test.com");
        visitor.setId(id);
        visitor.setAge(20);
        visitor.setHeight(1.70);
        visitor.setTicketType(TicketType.GENERAL);
        return visitor;
    }
}
