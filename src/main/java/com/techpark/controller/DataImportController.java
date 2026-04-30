package com.techpark.controller;

import com.techpark.service.ParkDataBootstrapService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Scope("prototype")
public class DataImportController {
    private final ParkDataBootstrapService parkDataBootstrapService;

    @FXML
    private Label importStatusLabel;

    public DataImportController(ParkDataBootstrapService parkDataBootstrapService) {
        this.parkDataBootstrapService = parkDataBootstrapService;
    }

    public void importData(File file) throws java.io.IOException {
        parkDataBootstrapService.importData(file);
    }

    public void importDataResource(String resourcePath) {
        parkDataBootstrapService.importDataResource(resourcePath);
    }

    public void importDataAsync(File file, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                importData(file);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            if (importStatusLabel != null) {
                importStatusLabel.setText("Datos importados correctamente");
            }
            if (onSuccess != null) {
                onSuccess.run();
            }
        });

        task.setOnFailed(event -> {
            if (importStatusLabel != null) {
                importStatusLabel.setText("Error importando datos");
            }
            if (onError != null) {
                onError.accept(task.getException());
            }
        });

        Thread worker = new Thread(task, "park-data-import");
        worker.setDaemon(true);
        worker.start();
    }
}
