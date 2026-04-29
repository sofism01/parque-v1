package com.techpark.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Estructura de Datos: Grafo para representar el mapa del parque.
 * Nodos = Atracciones; Aristas = Caminos con peso (distancia o tiempo)
 */
public class Graph<T> {
    private Map<T, List<Edge<T>>> adjacencyList;

    public Graph() {
        this.adjacencyList = new HashMap<>();
    }

    public void addNode(T node) {
        adjacencyList.putIfAbsent(node, new ArrayList<>());
    }

    public void addEdge(T source, T destination, int weight) {
        addNode(source);
        addNode(destination);

        adjacencyList.get(source).add(new Edge<>(destination, weight));
        adjacencyList.get(destination).add(new Edge<>(source, weight));
    }

    public List<Edge<T>> getNeighbors(T node) {
        return adjacencyList.getOrDefault(node, new ArrayList<>());
    }

    public List<T> dijkstra(T start, T end) {
        return dijkstra(start, end, node -> true);
    }

    public List<T> dijkstra(T start, T end, Predicate<T> isTransitable) {
        List<T> emptyPath = new ArrayList<>();
        if (start == null || end == null || !adjacencyList.containsKey(start) || !adjacencyList.containsKey(end)) {
            return emptyPath;
        }
        if (!isTransitable.test(start) || !isTransitable.test(end)) {
            return emptyPath;
        }

        Map<T, Integer> distances = new HashMap<>();
        Map<T, T> predecessors = new HashMap<>();
        PriorityQueue<NodeDistance<T>> queue = new PriorityQueue<>();

        for (T node : adjacencyList.keySet()) {
            distances.put(node, Integer.MAX_VALUE);
        }

        distances.put(start, 0);
        queue.enqueue(new NodeDistance<>(start, 0));

        while (!queue.isEmpty()) {
            NodeDistance<T> current = queue.dequeue();
            T currentNode = current.node;
            int currentDistance = current.distance;

            if (currentDistance > distances.getOrDefault(currentNode, Integer.MAX_VALUE)) {
                continue;
            }

            if (currentNode.equals(end)) {
                break;
            }

            for (Edge<T> edge : getNeighbors(currentNode)) {
                if (!isTransitable.test(edge.destination)) {
                    continue;
                }

                if (currentDistance == Integer.MAX_VALUE) {
                    continue;
                }

                int newDistance = currentDistance + edge.weight;
                if (newDistance < distances.getOrDefault(edge.destination, Integer.MAX_VALUE)) {
                    distances.put(edge.destination, newDistance);
                    predecessors.put(edge.destination, currentNode);
                    queue.enqueue(new NodeDistance<>(edge.destination, newDistance));
                }
            }
        }

        if (distances.getOrDefault(end, Integer.MAX_VALUE) == Integer.MAX_VALUE) {
            return emptyPath;
        }

        return reconstructPath(start, end, predecessors);
    }

    public List<T> bfs(T start) {
        List<T> result = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        java.util.Queue<T> queue = new java.util.LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            T node = queue.poll();
            result.add(node);

            for (Edge<T> edge : getNeighbors(node)) {
                if (!visited.contains(edge.destination)) {
                    visited.add(edge.destination);
                    queue.add(edge.destination);
                }
            }
        }

        return result;
    }

    public Set<T> getAllNodes() {
        return adjacencyList.keySet();
    }

    private List<T> reconstructPath(T start, T end, Map<T, T> predecessors) {
        List<T> reversedPath = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        T current = end;

        while (current != null) {
            if (!visited.add(current)) {
                return new ArrayList<>();
            }

            reversedPath.add(current);
            if (current.equals(start)) {
                break;
            }
            current = predecessors.get(current);
        }

        if (reversedPath.isEmpty() || !reversedPath.get(reversedPath.size() - 1).equals(start)) {
            return new ArrayList<>();
        }

        List<T> path = new ArrayList<>();
        for (int i = reversedPath.size() - 1; i >= 0; i--) {
            path.add(reversedPath.get(i));
        }
        return path;
    }

    public static class Edge<T> {
        public T destination;
        public int weight;

        public Edge(T destination, int weight) {
            this.destination = destination;
            this.weight = weight;
        }
    }

    private static class NodeDistance<T> implements Comparable<NodeDistance<T>> {
        private T node;
        private int distance;

        NodeDistance(T node, int distance) {
            this.node = node;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeDistance<T> other) {
            return Integer.compare(this.distance, other.distance);
        }
    }
}
