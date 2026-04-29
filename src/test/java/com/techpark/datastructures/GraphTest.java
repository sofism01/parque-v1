package com.techpark.datastructures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas Unitarias: Grafo
 */
public class GraphTest {
    private Graph<String> graph;

    @BeforeEach
    public void setUp() {
        graph = new Graph<>();
    }

    @Test
    public void testAddNode() {
        graph.addNode("A");
        graph.addNode("B");
        
        assertTrue(graph.getAllNodes().contains("A"));
        assertTrue(graph.getAllNodes().contains("B"));
    }

    @Test
    public void testAddEdge() {
        graph.addEdge("A", "B", 10);
        graph.addEdge("B", "C", 20);
        
        List<Graph.Edge<String>> neighborsA = graph.getNeighbors("A");
        assertEquals(1, neighborsA.size());
        assertEquals("B", neighborsA.get(0).destination);
        assertEquals(10, neighborsA.get(0).weight);
    }

    @Test
    public void testBFS() {
        graph.addEdge("A", "B", 10);
        graph.addEdge("B", "C", 20);
        graph.addEdge("A", "D", 15);
        
        List<String> result = graph.bfs("A");
        
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
        assertTrue(result.contains("C"));
        assertTrue(result.contains("D"));
    }

    @Test
    public void testDijkstra() {
        graph.addEdge("A", "B", 4);
        graph.addEdge("A", "C", 2);
        graph.addEdge("B", "C", 1);
        graph.addEdge("B", "D", 5);
        graph.addEdge("C", "D", 8);
        
        List<String> path = graph.dijkstra("A", "D");

        assertEquals(List.of("A", "C", "B", "D"), path);
    }

    @Test
    public void testDijkstraNoPath() {
        graph.addEdge("A", "B", 4);
        graph.addNode("Z");

        List<String> path = graph.dijkstra("A", "Z");

        assertTrue(path.isEmpty());
    }
}
