package com.techpark.datastructures;

import java.util.*;

/**
 * Estructura de Datos: Set para gestionar Atracciones Favoritas de cada visitante
 */
public class CustomSet<T> {
    private List<T> elements;

    public CustomSet() {
        this.elements = new ArrayList<>();
    }

    // Agregar elemento
    public boolean add(T element) {
        if (!contains(element)) {
            elements.add(element);
            return true;
        }
        return false;
    }

    // Remover elemento
    public boolean remove(T element) {
        return elements.remove(element);
    }

    // Verificar si contiene elemento
    public boolean contains(T element) {
        return elements.contains(element);
    }

    // Obtener tamaño
    public int size() {
        return elements.size();
    }

    // Verificar si está vacío
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    // Limpiar el set
    public void clear() {
        elements.clear();
    }

    // Convertir a List
    public List<T> toList() {
        return new ArrayList<>(elements);
    }

    // Unión con otro set
    public CustomSet<T> union(CustomSet<T> other) {
        CustomSet<T> result = new CustomSet<>();
        for (T element : elements) {
            result.add(element);
        }
        for (T element : other.elements) {
            result.add(element);
        }
        return result;
    }

    // Intersección con otro set
    public CustomSet<T> intersection(CustomSet<T> other) {
        CustomSet<T> result = new CustomSet<>();
        for (T element : elements) {
            if (other.contains(element)) {
                result.add(element);
            }
        }
        return result;
    }

    // Diferencia con otro set
    public CustomSet<T> difference(CustomSet<T> other) {
        CustomSet<T> result = new CustomSet<>();
        for (T element : elements) {
            if (!other.contains(element)) {
                result.add(element);
            }
        }
        return result;
    }

    // Iterador
    public Iterator<T> iterator() {
        return elements.iterator();
    }

    @Override
    public String toString() {
        return elements.toString();
    }
}
