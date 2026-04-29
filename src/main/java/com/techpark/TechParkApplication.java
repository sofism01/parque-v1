package com.techpark;

import com.google.gson.Gson;
import com.techpark.config.SpringFxmlLoader;
import com.techpark.controller.DataImportController;
import com.techpark.datastructures.Graph;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class TechParkApplication extends Application {
    private static ConfigurableApplicationContext applicationContext;
    private static String[] launchArgs;

    public static void main(String[] args) {
        launchArgs = args;
        runDiagnostics();
        launch(args);
    }

    @Override
    public void init() throws Exception {
        applicationContext = new SpringApplicationBuilder(TechParkApplication.class)
                .headless(false)
                .run(launchArgs);

        applicationContext.getBean(DataImportController.class).importDataResource("/data/data.json");
    }

    @Override
    public void start(Stage stage) throws Exception {
        SpringFxmlLoader springFxmlLoader = applicationContext.getBean(SpringFxmlLoader.class);
        FXMLLoader loader = springFxmlLoader.load("/fxml/main-view.fxml");
        Parent root = loader.getRoot();

        Scene scene = new Scene(root, 1024, 768);
        stage.setTitle("Tech-Park UQ");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(768);
        stage.show();
    }

    @Override
    public void stop() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        Platform.exit();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("*")
                        .allowedHeaders("*");
            }
        };
    }

    private static void runDiagnostics() {
        try (InputStream inputStream = TechParkApplication.class.getResourceAsStream("/data/data.json")) {
            if (inputStream == null) {
                throw new IOException("No se encontro /data/data.json");
            }

            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            DiagnosticData data = new Gson().fromJson(json, DiagnosticData.class);
            Graph<Attraction> graph = new Graph<>();
            Map<Long, Attraction> attractionsById = new HashMap<>();

            for (DiagnosticAttraction attractionData : data.attractions) {
                Attraction attraction = new Attraction();
                attraction.setId(attractionData.id);
                attraction.setName(attractionData.name);
                attraction.setType(AttractionType.valueOf(attractionData.type));
                attraction.setStatus(AttractionStatus.valueOf(attractionData.status));
                attraction.setClosureReason(ClosureReason.valueOf(attractionData.closureReason));
                attraction.setZoneId(attractionData.zoneId);
                attraction.setPosX(attractionData.posX);
                attraction.setPosY(attractionData.posY);
                attractionsById.put(attraction.getId(), attraction);
                graph.addNode(attraction);
            }

            for (DiagnosticConnection connection : data.connections) {
                Attraction source = attractionsById.get(connection.sourceId);
                Attraction destination = attractionsById.get(connection.destinationId);
                if (source != null && destination != null) {
                    graph.addEdge(source, destination, connection.weight);
                }
            }

            Attraction[] farthestPair = resolveFarthestPair(attractionsById);
            List<Attraction> path = graph.dijkstra(farthestPair[0], farthestPair[1], attraction ->
                    attraction != null && AttractionStatus.ACTIVA.equals(attraction.getStatus()));

            if (!path.isEmpty()
                    && path.get(0).equals(farthestPair[0])
                    && path.get(path.size() - 1).equals(farthestPair[1])) {
                System.out.println("--- DIAGNÓSTICO DE ESTRUCTURAS: OK ---");
                return;
            }
        } catch (Exception exception) {
            System.err.println("--- DIAGNÓSTICO DE ESTRUCTURAS: FAIL ---");
            exception.printStackTrace(System.err);
            return;
        }

        System.err.println("--- DIAGNÓSTICO DE ESTRUCTURAS: FAIL ---");
    }

    private static Attraction[] resolveFarthestPair(Map<Long, Attraction> attractionsById) {
        Attraction first = null;
        Attraction second = null;
        double maxDistance = -1;

        for (Attraction source : attractionsById.values()) {
            for (Attraction destination : attractionsById.values()) {
                if (source.equals(destination)) {
                    continue;
                }

                double dx = source.getPosX() - destination.getPosX();
                double dy = source.getPosY() - destination.getPosY();
                double distance = Math.sqrt((dx * dx) + (dy * dy));

                if (distance > maxDistance) {
                    maxDistance = distance;
                    first = source;
                    second = destination;
                }
            }
        }

        if (first == null || second == null) {
            throw new IllegalStateException("No fue posible determinar los puntos mas lejanos");
        }

        return new Attraction[]{first, second};
    }

    private static class DiagnosticData {
        private List<DiagnosticAttraction> attractions;
        private List<DiagnosticConnection> connections;
    }

    private static class DiagnosticAttraction {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String closureReason;
        private Long zoneId;
        private double posX;
        private double posY;
    }

    private static class DiagnosticConnection {
        private Long sourceId;
        private Long destinationId;
        private int weight;
    }
}
