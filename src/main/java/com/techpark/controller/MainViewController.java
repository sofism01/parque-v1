package com.techpark.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class MainViewController {
    @FXML
    private BorderPane root;

    @FXML
    private GraphViewController graphViewController;

    @FXML
    private VisitorPanelController visitorPanelController;

    @FXML
    private AdminDashboardController adminDashboardController;

    @FXML
    private DataImportController dataImportController;

    @FXML
    public void initialize() {
        if (adminDashboardController != null && graphViewController != null) {
            adminDashboardController.setGraphViewController(graphViewController);
        }
    }
}
