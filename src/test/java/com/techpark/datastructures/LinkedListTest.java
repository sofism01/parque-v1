package com.techpark.datastructures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas Unitarias: Listas Enlazadas
 */
public class LinkedListTest {
    private LinkedList<Integer> list;

    @BeforeEach
    public void setUp() {
        list = new LinkedList<>();
    }

    @Test
    public void testAdd() {
        list.add(1);
        list.add(2);
        list.add(3);
        
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }

    @Test
    public void testAddFirst() {
        list.add(2);
        list.add(3);
        list.addFirst(1);
        
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }

    @Test
    public void testRemove() {
        list.add(1);
        list.add(2);
        list.add(3);
        
        int removed = list.remove(1);
        
        assertEquals(2, removed);
        assertEquals(2, list.size());
        assertEquals(1, list.get(0));
        assertEquals(3, list.get(1));
    }

    @Test
    public void testClear() {
        list.add(1);
        list.add(2);
        list.add(3);
        
        list.clear();
        
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
    }
}
