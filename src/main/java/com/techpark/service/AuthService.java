package com.techpark.service;

import com.techpark.model.User;
import com.techpark.model.Visitor;
import com.techpark.model.Operator;
import com.techpark.model.Administrator;
import com.techpark.model.TicketType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio de Autenticación
 */
@Service
public class AuthService {
    private Map<String, User> users;
    private Map<String, String> activeTokens;

    public AuthService() {
        this.users = new HashMap<>();
        this.activeTokens = new HashMap<>();
        initializeSampleUsers();
    }

    private void initializeSampleUsers() {
        // Admin de prueba
        Administrator admin = new Administrator("admin", "admin123", "admin@techpark.com");
        admin.setId(1L);
        users.put("admin", admin);

        // Operador de prueba
        Operator operator = new Operator("operator", "operator123", "operator@techpark.com", 1L);
        operator.setId(2L);
        users.put("operator", operator);

        // Visitante de prueba
        Visitor visitor = new Visitor("visitor", "visitor123", "visitor@techpark.com");
        visitor.setId(3L);
        visitor.setDocument("123456789");
        visitor.setAge(25);
        visitor.setHeight(1.75);
        visitor.setVirtualBalance(100.0);
        visitor.setTicketType(TicketType.GENERAL);
        users.put("visitor", visitor);
    }

    public User authenticate(String username, String password) {
        User user = users.get(username);
        
        if (user != null && user.getPassword().equals(password) && user.isActive()) {
            return user;
        }
        
        return null;
    }

    public String generateToken(User user) {
        String token = UUID.randomUUID().toString();
        activeTokens.put(token, user.getUsername());
        return token;
    }

    public User validateToken(String token) {
        String username = activeTokens.get(token);
        if (username != null) {
            return users.get(username);
        }
        return null;
    }

    public void logout(String token) {
        activeTokens.remove(token);
    }

    public boolean registerVisitor(Visitor visitor) {
        if (users.containsKey(visitor.getUsername())) {
            return false;
        }
        
        visitor.setId((long) (users.size() + 1));
        visitor.setActive(true);
        users.put(visitor.getUsername(), visitor);
        return true;
    }

    public Visitor getVisitor(Long id) {
        for (User user : users.values()) {
            if (user instanceof Visitor && user.getId().equals(id)) {
                return (Visitor) user;
            }
        }
        return null;
    }

    public List<Operator> getAllOperators() {
        List<Operator> operators = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Operator) {
                operators.add((Operator) user);
            }
        }
        return operators;
    }

    public List<Visitor> getAllVisitors() {
        List<Visitor> visitors = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Visitor) {
                visitors.add((Visitor) user);
            }
        }
        return visitors;
    }

    public Operator getOperator(Long id) {
        for (User user : users.values()) {
            if (user instanceof Operator && user.getId().equals(id)) {
                return (Operator) user;
            }
        }
        return null;
    }
}
