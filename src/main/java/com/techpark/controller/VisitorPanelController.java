package com.techpark.controller;

import com.techpark.model.Attraction;
import com.techpark.model.Visitor;
import com.techpark.service.AttractionService;
import com.techpark.service.AuthService;
import com.techpark.service.QueueService;
import jakarta.annotation.PreDestroy;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.util.Duration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope("prototype")
public class VisitorPanelController {
    private final AuthService authService;
    private final AttractionService attractionService;
    private final QueueService queueService;

    @FXML
    private Label balanceLabel;

    @FXML
    private Label ticketTypeLabel;

    @FXML
    private Label queuePositionLabel;

    @FXML
    private Label queueStatusLabel;

    @FXML
    private ComboBox<Attraction> attractionSelector;

    @FXML
    private ListView<String> favoritesListView;

    @FXML
    private Button joinQueueButton;

    private Timeline queueRefreshTimeline;
    private Long visitorId = 3L;

    public VisitorPanelController(AuthService authService,
                                  AttractionService attractionService,
                                  QueueService queueService) {
        this.authService = authService;
        this.attractionService = attractionService;
        this.queueService = queueService;
    }

    @FXML
    public void initialize() {
        if (attractionSelector != null) {
            attractionSelector.setItems(FXCollections.observableArrayList(attractionService.getAllAttractions()));
            attractionSelector.setOnAction(event -> refreshQueuePosition());
        }

        startRealtimeRefresh();
        refreshView();
    }

    public void setVisitorId(Long visitorId) {
        this.visitorId = visitorId;
        refreshView();
    }

    @FXML
    public void joinSelectedQueue() {
        Visitor visitor = getCurrentVisitor();
        Attraction selectedAttraction = attractionSelector != null ? attractionSelector.getValue() : null;

        if (visitor == null || selectedAttraction == null) {
            if (queueStatusLabel != null) {
                queueStatusLabel.setText("Selecciona visitante y atraccion");
            }
            return;
        }

        int position;
        try {
            position = queueService.addVisitorToQueue(
                    selectedAttraction.getId(),
                    visitor.getId(),
                    visitor.getUsername(),
                    visitor.getTicketType());
        } catch (IllegalStateException exception) {
            visitor.setPositionInQueue(-1);
            if (queueStatusLabel != null) {
                queueStatusLabel.setText(exception.getMessage());
            }
            refreshQueuePosition();
            return;
        }

        if (position < 0) {
            visitor.setPositionInQueue(-1);
            if (queueStatusLabel != null) {
                queueStatusLabel.setText("La atraccion no esta disponible para fila");
            }
            refreshQueuePosition();
            return;
        }

        visitor.setPositionInQueue(position);
        if (queueStatusLabel != null) {
            queueStatusLabel.setText("Te uniste a la fila de " + selectedAttraction.getName());
        }
        refreshQueuePosition();
    }

    public void refreshView() {
        Visitor visitor = getCurrentVisitor();
        if (visitor == null) {
            return;
        }

        if (balanceLabel != null) {
            balanceLabel.setText(String.format("$ %.2f", visitor.getVirtualBalance()));
        }
        if (ticketTypeLabel != null) {
            ticketTypeLabel.setText(visitor.getTicketType().name());
        }

        refreshFavorites(visitor);
        refreshQueuePosition();
    }

    private void refreshFavorites(Visitor visitor) {
        if (favoritesListView == null) {
            return;
        }

        List<String> favoriteNames = new ArrayList<>();
        if (visitor.getFavoriteAttractions() != null) {
            for (Long attractionId : visitor.getFavoriteAttractions().toList()) {
                Attraction attraction = attractionService.getAttractionById(attractionId);
                favoriteNames.add(attraction != null ? attraction.getName() : "Atraccion #" + attractionId);
            }
        }

        favoritesListView.setItems(FXCollections.observableArrayList(favoriteNames));
    }

    private void refreshQueuePosition() {
        Visitor visitor = getCurrentVisitor();
        Attraction selectedAttraction = attractionSelector != null ? attractionSelector.getValue() : null;

        if (queuePositionLabel == null) {
            return;
        }

        if (visitor == null || selectedAttraction == null) {
            queuePositionLabel.setText("-");
            return;
        }

        int position = queueService.getQueuePosition(selectedAttraction.getId(), visitor.getId());
        visitor.setPositionInQueue(position);
        queuePositionLabel.setText(position > 0 ? String.valueOf(position) : "Sin fila");
    }

    private void startRealtimeRefresh() {
        queueRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> refreshQueuePosition()));
        queueRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        queueRefreshTimeline.play();
    }

    private Visitor getCurrentVisitor() {
        return authService.getVisitor(visitorId);
    }

    @PreDestroy
    public void stopRealtimeRefresh() {
        if (queueRefreshTimeline != null) {
            queueRefreshTimeline.stop();
        }
    }
}
