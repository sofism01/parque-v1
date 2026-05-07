package com.techpark.controller;

import com.techpark.dto.OperatorRestrictionCheckDto;
import com.techpark.model.Attraction;
import com.techpark.model.AttractionStatus;
import com.techpark.model.ClosureReason;
import com.techpark.model.OperationResult;
import com.techpark.model.Operator;
import com.techpark.model.Visitor;
import com.techpark.service.AttractionService;
import com.techpark.service.AuthService;
import com.techpark.service.GraphService;
import com.techpark.service.OperatorService;
import com.techpark.service.ParkDataBootstrapService;
import com.techpark.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/operator")
@CrossOrigin(origins = "*")
public class OperatorApiController {
    @Autowired
    private AuthService authService;

    @Autowired
    private OperatorService operatorService;

    @Autowired
    private AttractionService attractionService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ParkDataBootstrapService parkDataBootstrapService;

    @Autowired
    private GraphService graphService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@RequestHeader(value = "Authorization", required = false) String token) {
        Operator operator = resolveOperator(token);
        if (operator == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Acceso restringido al rol operador"));
        }

        parkDataBootstrapService.reloadDataFromDisk();
        Operator freshOperator = authService.getOperator(operator.getId());
        if (freshOperator == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Operador no encontrado"));
        }
        try {
            return ResponseEntity.ok(operatorService.getDashboardForOperator(freshOperator));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/zone-capacity")
    public ResponseEntity<?> getZoneCapacity(@RequestHeader(value = "Authorization", required = false) String token) {
        Operator operator = resolveOperator(token);
        if (operator == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Acceso restringido al rol operador"));
        }

        parkDataBootstrapService.reloadDataFromDisk();
        if (operator.getZoneId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El operador no tiene una zona asignada"));
        }
        Object capacity = operatorService.buildZoneCapacity(operator.getZoneId());
        parkDataBootstrapService.saveData();
        return ResponseEntity.ok(capacity);
    }

    @PostMapping("/attractions/{attractionId}/status")
    public ResponseEntity<?> updateAttractionStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long attractionId,
            @RequestBody Map<String, String> request
    ) {
        Operator operator = resolveOperator(token);
        if (operator == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Acceso restringido al rol operador"));
        }
        if (!operatorService.operatorCanManageAttraction(operator, attractionId)) {
            return ResponseEntity.status(403).body(Map.of("message", "No puedes gestionar atracciones de otra zona"));
        }

        Attraction attraction = attractionService.getAttractionById(attractionId);
        if (attraction == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Atraccion no encontrada"));
        }

        String estado = request.get("estado");
        if (estado == null || estado.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes indicar el estado de la atraccion"));
        }

        String normalizedState = estado.trim().toUpperCase();
        AttractionStatus status = switch (normalizedState) {
            case "ABIERTA" -> AttractionStatus.ACTIVA;
            case "MANTENIMIENTO" -> AttractionStatus.MANTENIMIENTO;
            case "CLIMA" -> AttractionStatus.CERRADA;
            default -> null;
        };

        if (status == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Estado no soportado para operador"));
        }

        ClosureReason reason = switch (normalizedState) {
            case "MANTENIMIENTO" -> ClosureReason.TECNICO;
            case "CLIMA" -> ClosureReason.CLIMA;
            default -> ClosureReason.NINGUNO;
        };

        attraction.changeStatus(status, reason);
        attraction.setEstado(normalizedState);
        if (!"ABIERTA".equals(normalizedState)) {
            queueService.cancelQueueWithAlert(
                    attraction.getId(),
                    "La atraccion " + attraction.getName() + " ha cerrado por " + normalizedState + ". Has sido removido de la fila."
            );
        }

        attractionService.updateAttraction(attraction);
        parkDataBootstrapService.saveData();
        parkDataBootstrapService.reloadDataFromDisk();
        graphService.recargarGrafo();

        return ResponseEntity.ok(Map.of(
                "message", "Estado actualizado",
                "attractionId", attractionId,
                "estado", normalizedState
        ));
    }

    @PostMapping("/restrictions/check")
    public ResponseEntity<?> validateRestrictions(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request
    ) {
        Operator operator = resolveOperator(token);
        if (operator == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Acceso restringido al rol operador"));
        }

        Long attractionId = request.get("attractionId") instanceof Number attractionNumber ? attractionNumber.longValue() : null;
        Long visitorId = request.get("visitorId") instanceof Number visitorNumber ? visitorNumber.longValue() : null;
        if (attractionId == null || visitorId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes indicar attractionId y visitorId"));
        }
        if (!operatorService.operatorCanManageAttraction(operator, attractionId)) {
            return ResponseEntity.status(403).body(Map.of("message", "No puedes validar atracciones de otra zona"));
        }

        parkDataBootstrapService.reloadDataFromDisk();
        Attraction attraction = attractionService.getAttractionById(attractionId);
        Visitor visitor = authService.getVisitor(visitorId);
        if (attraction == null || visitor == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Visitante o atraccion no encontrados"));
        }

        boolean meetsAge = visitor.getAge() >= attraction.getMinAge();
        boolean meetsHeight = visitor.getHeight() >= attraction.getMinHeight();
        boolean hasSufficientBalance = visitor.getVirtualBalance() >= attraction.getAdditionalCost();
        OperationResult accessResult = visitor.canAccessAttraction(attraction);
        boolean allowed = accessResult.isSuccess() && hasSufficientBalance;
        String message = allowed
                ? "Cumple edad, altura y saldo suficiente"
                : buildRestrictionFailureMessage(meetsAge, meetsHeight, hasSufficientBalance);

        return ResponseEntity.ok(new OperatorRestrictionCheckDto(
                allowed,
                allowed ? "CHECK" : "X",
                message,
                visitor.getId(),
                visitor.getUsername(),
                attraction.getId(),
                attraction.getName(),
                meetsAge,
                meetsHeight,
                hasSufficientBalance,
                visitor.getAge(),
                visitor.getHeight(),
                visitor.getVirtualBalance(),
                attraction.getMinAge(),
                attraction.getMinHeight(),
                attraction.getAdditionalCost()
        ));
    }

    private Operator resolveOperator(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return authService.resolveOperatorFromToken(token);
    }

    private String buildRestrictionFailureMessage(boolean meetsAge, boolean meetsHeight, boolean hasSufficientBalance) {
        StringBuilder builder = new StringBuilder("No cumple con: ");
        boolean first = true;
        if (!meetsAge) {
            builder.append("edad minima");
            first = false;
        }
        if (!meetsHeight) {
            if (!first) {
                builder.append(", ");
            }
            builder.append("altura minima");
            first = false;
        }
        if (!hasSufficientBalance) {
            if (!first) {
                builder.append(", ");
            }
            builder.append("saldo suficiente");
        }
        return builder.toString();
    }
}
