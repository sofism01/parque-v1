package com.techpark.datastructures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas Unitarias: Árbol Binario de Búsqueda
 */
public class BinarySearchTreeTest {
    private BinarySearchTree<Integer> tree;

    @BeforeEach
    public void setUp() {
        tree = new BinarySearchTree<>();
    }

    @Test
    public void testInsertAndSearch() {
        tree.insert(5);
        tree.insert(3);
        tree.insert(7);
        tree.insert(1);
        tree.insert(9);
        
        assertTrue(tree.search(5));
        assertTrue(tree.search(3));
        assertTrue(tree.search(7));
        assertTrue(tree.search(1));
        assertTrue(tree.search(9));
        assertFalse(tree.search(10));
    }

    @Test
    public void testInOrder() {
        tree.insert(5);
        tree.insert(3);
        tree.insert(7);
        tree.insert(1);
        tree.insert(9);
        
        List<Integer> result = tree.getSortedList();
        
        assertEquals(5, result.size());
        assertEquals(1, result.get(0));
        assertEquals(3, result.get(1));
        assertEquals(5, result.get(2));
        assertEquals(7, result.get(3));
        assertEquals(9, result.get(4));
    }

    @Test
    public void testDelete() {
        tree.insert(5);
        tree.insert(3);
        tree.insert(7);
        
        tree.delete(3);
        
        assertFalse(tree.search(3));
        assertTrue(tree.search(5));
        assertTrue(tree.search(7));
    }

    @Test
    public void testHeight() {
        assertEquals(-1, tree.height()); // árbol vacío
        
        tree.insert(5);
        assertEquals(0, tree.height());
        
        tree.insert(3);
        tree.insert(7);
        assertEquals(1, tree.height());
    }
}
