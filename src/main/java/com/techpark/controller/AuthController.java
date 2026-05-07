package com.techpark.controller;

import com.techpark.dto.LoginResponse;
import com.techpark.model.Operator;
import com.techpark.model.User;
import com.techpark.model.Zone;
import com.techpark.service.AttractionService;
import com.techpark.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para Autenticacion
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private AttractionService attractionService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        User user = authService.authenticate(username, password);
        if (user == null) {
            Operator operator = authService.getOperatorByUsernameOrEmail(username);
            if (operator != null && operator.getPassword().equals(password) && operator.isActive()) {
                user = operator;
            }
        }

        if (user != null) {
            String token = authService.generateToken(user);
            Long zoneId = null;
            String zoneName = null;
            if (user instanceof Operator operator) {
                zoneId = operator.getZoneId();
                Zone zone = zoneId != null ? attractionService.getZoneById(zoneId) : null;
                zoneName = zone != null ? zone.getName() : null;
            }

            LoginResponse response = new LoginResponse(
                    true,
                    "Login exitoso",
                    token,
                    user.getRole(),
                    user.getId(),
                    user.getUsername(),
                    zoneId,
                    zoneName
            );
            return ResponseEntity.ok(response);
        }

        LoginResponse response = new LoginResponse(
                false,
                "Usuario o contrasena incorrectos",
                null,
                null,
                null,
                null,
                null,
                null
        );
        return ResponseEntity.status(401).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logout exitoso");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestHeader("Authorization") String token) {
        User user = authService.validateToken(token);

        Map<String, Object> response = new HashMap<>();
        if (user != null) {
            Long zoneId = null;
            String zoneName = null;
            if (user instanceof Operator operator) {
                zoneId = operator.getZoneId();
                Zone zone = zoneId != null ? attractionService.getZoneById(zoneId) : null;
                zoneName = zone != null ? zone.getName() : null;
            }
            response.put("valid", true);
            response.put("role", user.getRole());
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("zoneId", zoneId);
            response.put("zoneName", zoneName);
            return ResponseEntity.ok(response);
        }

        response.put("valid", false);
        return ResponseEntity.status(401).body(response);
    }
}
