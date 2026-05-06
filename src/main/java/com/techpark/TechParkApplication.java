package com.techpark;

import com.google.gson.Gson;
import com.techpark.datastructures.Graph;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.AttractionType;
import com.techpark.model.ClosureReason;
import com.techpark.service.ParkDataBootstrapService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication(scanBasePackages = "com.techpark")
@EnableScheduling
public class TechParkApplication {
    private static final Path DATA_FILE_PATH = Path.of("data.json");
    private static final String DEFAULT_DATA_JSON = "{\"attractions\":[], \"zones\":[], \"operators\":[], \"connections\":[]}";
    private static ConfigurableApplicationContext applicationContext;

    public static void main(String[] args) { 
        runDiagnostics();

        applicationContext = new SpringApplicationBuilder(TechParkApplication.class)
                .headless(false)
                .run(args);

        applicationContext.getBean(ParkDataBootstrapService.class).importDataFile();
        launchDesktopUiSafely(args);
    }

    public static ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
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

    private static void launchDesktopUiSafely(String[] args) {
        try {
            Class<?> desktopAppClass = Class.forName("com.techpark.TechParkDesktopApplication");
            Method launchMethod = desktopAppClass.getMethod("launchDesktop", String[].class);
            launchMethod.invoke(null, (Object) args);
        } catch (Throwable throwable) {
            System.err.println("JavaFX no disponible. El servidor Spring Boot continua en el puerto 8080.");
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            System.err.println(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    private static void runDiagnostics() {
        try {
            String json = readOrInitializeDataFile();
            DiagnosticData data = new Gson().fromJson(json, DiagnosticData.class);
            if (data == null || data.attractions == null || data.connections == null) {
                System.out.println("--- DIAGNOSTICO DE ESTRUCTURAS: OK ---");
                return;
            }
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
                System.out.println("--- DIAGNOSTICO DE ESTRUCTURAS: OK ---");
                return;
            }
        } catch (Exception exception) {
            System.err.println("--- DIAGNOSTICO DE ESTRUCTURAS: FAIL ---");
            exception.printStackTrace(System.err);
            return;
        }

        System.err.println("--- DIAGNOSTICO DE ESTRUCTURAS: FAIL ---");
    }

    private static String readOrInitializeDataFile() throws IOException {
        if (!Files.exists(DATA_FILE_PATH) || Files.size(DATA_FILE_PATH) == 0) {
            Files.writeString(DATA_FILE_PATH, DEFAULT_DATA_JSON, StandardCharsets.UTF_8);
            return DEFAULT_DATA_JSON;
        }

        String content = Files.readString(DATA_FILE_PATH, StandardCharsets.UTF_8);
        if (content == null || content.isBlank()) {
            Files.writeString(DATA_FILE_PATH, DEFAULT_DATA_JSON, StandardCharsets.UTF_8);
            return DEFAULT_DATA_JSON;
        }

        return content;
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
