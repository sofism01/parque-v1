package com.techpark.datastructures;

import java.util.*;

/**
 * Estructura de Datos: Cola de Prioridad para gestionar la fila de ingreso a atracciones.
 * Prioridad 1: Fast-Pass; Prioridad 2: General
 */
public class PriorityQueue<T extends Comparable<T>> {
    private List<T> heap;

    public PriorityQueue() {
        this.heap = new ArrayList<>();
    }

    // Agregar elemento respetando prioridad
    public void enqueue(T element) {
        heap.add(element);
        siftUp(heap.size() - 1);
    }

    // Obtener y remover el elemento de mayor prioridad
    public T dequeue() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cola vacía");
        }
        
        T root = heap.get(0);
        T lastElement = heap.remove(heap.size() - 1);
        
        if (!heap.isEmpty()) {
            heap.set(0, lastElement);
            siftDown(0);
        }
        
        return root;
    }

    // Obtener el elemento de mayor prioridad sin remover
    public T peek() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cola vacía");
        }
        return heap.get(0);
    }

    // Verificar si la cola está vacía
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    // Obtener tamaño de la cola
    public int size() {
        return heap.size();
    }

    // Subir elemento hasta su posición correcta
    private void siftUp(int index) {
        while (index > 0) {
            int parentIndex = (index - 1) / 2;
            if (heap.get(index).compareTo(heap.get(parentIndex)) < 0) {
                swap(index, parentIndex);
                index = parentIndex;
            } else {
                break;
            }
        }
    }

    // Bajar elemento hasta su posición correcta
    private void siftDown(int index) {
        while (true) {
            int smallest = index;
            int leftChild = 2 * index + 1;
            int rightChild = 2 * index + 2;

            if (leftChild < heap.size() && 
                heap.get(leftChild).compareTo(heap.get(smallest)) < 0) {
                smallest = leftChild;
            }

            if (rightChild < heap.size() && 
                heap.get(rightChild).compareTo(heap.get(smallest)) < 0) {
                smallest = rightChild;
            }

            if (smallest != index) {
                swap(index, smallest);
                index = smallest;
            } else {
                break;
            }
        }
    }

    // Intercambiar dos elementos
    private void swap(int i, int j) {
        T temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
    }

    // Obtener todos los elementos (sin remover)
    public List<T> getAllElements() {
        return new ArrayList<>(heap);
    }
}
