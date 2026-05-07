package com.techpark;

import com.techpark.config.SpringFxmlLoader;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TechParkDesktopApplication extends Application {
    public static void launchDesktop(String[] args) {
        Application.launch(TechParkDesktopApplication.class, args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        SpringFxmlLoader springFxmlLoader = new SpringFxmlLoader(TechParkApplication.getApplicationContext());
        FXMLLoader loader = springFxmlLoader.load("/fxml/main-view.fxml");
        Parent root = loader.getRoot();

        Scene scene = new Scene(root, 1024, 768);
        stage.setTitle("Tech-Park UQ");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(768);
        stage.show();
    }
}
