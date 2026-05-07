package com.techpark.model;

import com.techpark.datastructures.CustomSet;
import com.techpark.datastructures.LinkedList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidad: Visitante del parque
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Visitor extends User {
    private String document;
    private int age;
    private double height;
    private double virtualBalance;
    private String photoPath;
    private TicketType ticketType;
    private CustomSet<Long> favoriteAttractions;
    private LinkedList<Long> visitHistory;
    private List<String> historialAtracciones;
    private List<String> notifications;
    private String mensajeAlerta;
    private int positionInQueue;
    private Long currentQueueAttractionId;
    private Long currentLocationAttractionId;

    public Visitor(String username, String password, String email) {
        super(null, username, password, email, "VISITOR", true, null);
        this.favoriteAttractions = new CustomSet<>();
        this.visitHistory = new LinkedList<>();
        this.historialAtracciones = new ArrayList<>();
        this.notifications = new ArrayList<>();
        this.mensajeAlerta = null;
        this.virtualBalance = 0.0;
        this.ticketType = TicketType.GENERAL;
        this.positionInQueue = -1;
        this.currentQueueAttractionId = null;
        this.currentLocationAttractionId = null;
    }

    public void addFavorite(Long attractionId) {
        favoriteAttractions.add(attractionId);
    }

    public void removeFavorite(Long attractionId) {
        favoriteAttractions.remove(attractionId);
    }

    public void addVisit(Long attractionId) {
        visitHistory.add(attractionId);
    }

    public void addHistorialAtraccion(String nombreAtraccion) {
        if (nombreAtraccion == null || nombreAtraccion.isBlank()) {
            return;
        }
        if (historialAtracciones == null) {
            historialAtracciones = new ArrayList<>();
        }
        historialAtracciones.add(nombreAtraccion);
    }

    public void addNotification(String message) {
        if (notifications == null) {
            notifications = new ArrayList<>();
        }
        notifications.add(message);
    }

    public OperationResult canAccessAttraction(Attraction attraction) {
        if (attraction == null) {
            return new OperationResult(false, "La atraccion no existe");
        }
        if (!AttractionStatus.ACTIVA.equals(attraction.getStatus())) {
            return new OperationResult(false, "La atraccion no esta activa");
        }
        if (height < attraction.getMinHeight()) {
            return new OperationResult(false, "No cumple con la altura minima");
        }
        if (age < attraction.getMinAge()) {
            return new OperationResult(false, "No cumple con la edad minima");
        }
        return new OperationResult(true, "Acceso permitido");
    }

    public OperationResult payForAttraction(Attraction attraction) {
        if (attraction == null) {
            return new OperationResult(false, "La atraccion no existe");
        }

        double costToPay = attraction.getAdditionalCost();

        if (TicketType.FAST_PASS.equals(ticketType)) {
            return new OperationResult(true, "FAST_PASS sin costo adicional");
        }

        if (TicketType.FAMILIAR.equals(ticketType)) {
            costToPay = costToPay * 0.85;
        }

        if (virtualBalance < costToPay) {
            return new OperationResult(false, "Saldo insuficiente para cubrir el costo adicional");
        }

        virtualBalance -= costToPay;
        return new OperationResult(true, "Pago realizado correctamente");
    }

    public void addBalance(double amount) {
        virtualBalance += amount;
    }
}
