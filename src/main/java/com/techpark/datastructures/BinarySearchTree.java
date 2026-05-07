package com.techpark.datastructures;

import java.util.ArrayList;
import java.util.List;

/**
 * Estructura de Datos: Arbol Binario de Busqueda para busquedas rapidas
 * Organiza el catalogo de atracciones por nombre o ID
 */
public class BinarySearchTree<T extends Comparable<T>> {
    private Node<T> root;

    public BinarySearchTree() {
        this.root = null;
    }

    public void insert(T value) {
        root = insertRecursive(root, value);
    }

    private Node<T> insertRecursive(Node<T> node, T value) {
        if (node == null) {
            return new Node<>(value);
        }

        int comparison = value.compareTo(node.value);
        if (comparison < 0) {
            node.left = insertRecursive(node.left, value);
        } else if (comparison > 0) {
            node.right = insertRecursive(node.right, value);
        }

        return node;
    }

    public boolean search(T value) {
        return get(value) != null;
    }

    public T get(T value) {
        Node<T> current = root;

        while (current != null) {
            int comparison = value.compareTo(current.value);
            if (comparison < 0) {
                current = current.left;
            } else if (comparison > 0) {
                current = current.right;
            } else {
                return current.value;
            }
        }

        return null;
    }

    public void delete(T value) {
        root = deleteRecursive(root, value);
    }

    private Node<T> deleteRecursive(Node<T> node, T value) {
        if (node == null) {
            return null;
        }

        int comparison = value.compareTo(node.value);
        if (comparison < 0) {
            node.left = deleteRecursive(node.left, value);
        } else if (comparison > 0) {
            node.right = deleteRecursive(node.right, value);
        } else {
            if (node.left == null) {
                return node.right;
            } else if (node.right == null) {
                return node.left;
            }

            T minValue = findMin(node.right);
            node.value = minValue;
            node.right = deleteRecursive(node.right, minValue);
        }

        return node;
    }

    private T findMin(Node<T> node) {
        while (node.left != null) {
            node = node.left;
        }
        return node.value;
    }

    public List<T> inOrder() {
        return getSortedList();
    }

    public List<T> getSortedList() {
        List<T> result = new ArrayList<>();
        inOrderRecursive(root, result);
        return result;
    }

    private void inOrderRecursive(Node<T> node, List<T> result) {
        if (node != null) {
            inOrderRecursive(node.left, result);
            result.add(node.value);
            inOrderRecursive(node.right, result);
        }
    }

    public List<T> preOrder() {
        List<T> result = new ArrayList<>();
        preOrderRecursive(root, result);
        return result;
    }

    private void preOrderRecursive(Node<T> node, List<T> result) {
        if (node != null) {
            result.add(node.value);
            preOrderRecursive(node.left, result);
            preOrderRecursive(node.right, result);
        }
    }

    public List<T> postOrder() {
        List<T> result = new ArrayList<>();
        postOrderRecursive(root, result);
        return result;
    }

    private void postOrderRecursive(Node<T> node, List<T> result) {
        if (node != null) {
            postOrderRecursive(node.left, result);
            postOrderRecursive(node.right, result);
            result.add(node.value);
        }
    }

    public int height() {
        return heightRecursive(root);
    }

    private int heightRecursive(Node<T> node) {
        if (node == null) {
            return -1;
        }
        return 1 + Math.max(heightRecursive(node.left), heightRecursive(node.right));
    }

    private static class Node<T> {
        private T value;
        private Node<T> left;
        private Node<T> right;

        Node(T value) {
            this.value = value;
        }
    }
}
