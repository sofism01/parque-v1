package com.techpark.datastructures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas Unitarias: Cola de Prioridad
 */
public class PriorityQueueTest {
    private PriorityQueue<Integer> queue;

    @BeforeEach
    public void setUp() {
        queue = new PriorityQueue<>();
    }

    @Test
    public void testEnqueueAndDequeue() {
        queue.enqueue(5);
        queue.enqueue(3);
        queue.enqueue(7);
        queue.enqueue(1);
        
        assertEquals(1, queue.dequeue());
        assertEquals(3, queue.dequeue());
        assertEquals(5, queue.dequeue());
        assertEquals(7, queue.dequeue());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testPeek() {
        queue.enqueue(5);
        queue.enqueue(3);
        queue.enqueue(7);
        
        assertEquals(3, queue.peek());
        assertEquals(3, queue.peek()); // No debe remover
    }

    @Test
    public void testIsEmpty() {
        assertTrue(queue.isEmpty());
        
        queue.enqueue(1);
        assertFalse(queue.isEmpty());
        
        queue.dequeue();
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testSize() {
        assertEquals(0, queue.size());
        
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);
        assertEquals(3, queue.size());
        
        queue.dequeue();
        assertEquals(2, queue.size());
    }
}
