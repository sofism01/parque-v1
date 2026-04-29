package com.techpark.controller;

import com.techpark.model.Attraction;
import com.techpark.service.AttractionService;
import com.techpark.service.ReportService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class AdminDashboardController {
    private final AttractionService attractionService;
    private final ReportService reportService;
    private GraphViewController graphViewController;

    @FXML
    private TableView<Attraction> attractionsTable;

    @FXML
    private TableColumn<Attraction, String> nameColumn;

    @FXML
    private TableColumn<Attraction, String> statusColumn;

    @FXML
    private TableColumn<Attraction, Number> visitorsColumn;

    @FXML
    private TableColumn<Attraction, Number> waitTimeColumn;

    @FXML
    private Label totalRevenueLabel;

    @FXML
    private Label mostVisitedLabel;

    @FXML
    private Label quickStatusLabel;

    public AdminDashboardController(AttractionService attractionService,
                                    ReportService reportService,
                                    @Lazy GraphViewController graphViewController) {
        this.attractionService = attractionService;
        this.reportService = reportService;
        this.graphViewController = graphViewController;
    }

    @FXML
    public void initialize() {
        configureTable();
        refreshDashboard();
    }

    @FXML
    public void simulateStorm() {
        attractionService.closeAttractionsByWeather("Tormenta simulada");
        refreshDashboard();
        if (graphViewController != null) {
            graphViewController.refreshMap();
        }

        if (quickStatusLabel != null) {
            quickStatusLabel.setText("Cierre por clima aplicado");
        }
    }

    public void refreshDashboard() {
        if (attractionsTable != null) {
            attractionsTable.setItems(FXCollections.observableArrayList(attractionService.getAllAttractions()));
            attractionsTable.refresh();
        }

        Attraction mostVisited = reportService.getMostVisitedAttraction();
        if (totalRevenueLabel != null) {
            totalRevenueLabel.setText(String.format("$ %.2f", reportService.getCurrentRevenue()));
        }
        if (mostVisitedLabel != null) {
            mostVisitedLabel.setText(mostVisited != null
                    ? mostVisited.getName() + " (" + mostVisited.getAccumulatedVisitors() + ")"
                    : "Sin datos");
        }
    }

    private void configureTable() {
        if (nameColumn != null) {
            nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        }
        if (statusColumn != null) {
            statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatus().name()));
        }
        if (visitorsColumn != null) {
            visitorsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getAccumulatedVisitors()));
        }
        if (waitTimeColumn != null) {
            waitTimeColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getEstimatedWaitTime()));
        }
        if (attractionsTable != null) {
            attractionsTable.setRowFactory(table -> new TableRow<>() {
                @Override
                protected void updateItem(Attraction item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                    } else if (item.needsMaintenance()) {
                        setStyle("-fx-background-color: rgba(249, 115, 22, 0.25);");
                    } else {
                        setStyle("");
                    }
                }
            });
        }
    }

    public void setGraphViewController(GraphViewController graphViewController) {
        this.graphViewController = graphViewController;
    }
}
