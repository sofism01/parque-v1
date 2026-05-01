package com.techpark.controller;

import com.techpark.dto.LoginResponse;
import com.techpark.model.User;
import com.techpark.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para Autenticación
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        User user = authService.authenticate(username, password);
        
        if (user != null) {
            String token = authService.generateToken(user);
            LoginResponse response = new LoginResponse(
                true,
                "Login exitoso",
                token,
                user.getRole(),
                user.getId(),
                user.getUsername()
            );
            return ResponseEntity.ok(response);
        } else {
            LoginResponse response = new LoginResponse(
                false,
                "Usuario o contraseña incorrectos",
                null,
                null,
                null,
                null
            );
            return ResponseEntity.status(401).body(response);
        }
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
            response.put("valid", true);
            response.put("role", user.getRole());
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            return ResponseEntity.ok(response);
        } else {
            response.put("valid", false);
            return ResponseEntity.status(401).body(response);
        }
    }
}
