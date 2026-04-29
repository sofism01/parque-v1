package com.techpark;

public class Launcher {
    public static void main(String[] args) {
        // Esto "engaña" a Java para que cargue las librerías de Maven primero
        // y luego llame a tu aplicación de JavaFX de forma segura.
        TechParkApplication.main(args);
    }
}