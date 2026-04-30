package com.techpark.service;

import com.techpark.model.User;
import com.techpark.model.Visitor;
import com.techpark.model.Operator;
import com.techpark.model.Administrator;
import com.techpark.model.TicketType;
import com.techpark.datastructures.CustomSet;
import com.techpark.datastructures.LinkedList;
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
        if (visitor == null || visitor.getUsername() == null || users.containsKey(visitor.getUsername())) {
            return false;
        }

        visitor.setId(nextUserId());
        visitor.setActive(true);
        normalizeVisitor(visitor);
        users.put(visitor.getUsername(), visitor);
        return true;
    }

    public void replaceVisitors(List<Visitor> visitors) {
        List<String> visitorUsernames = new ArrayList<>();
        for (Map.Entry<String, User> entry : users.entrySet()) {
            if (entry.getValue() instanceof Visitor) {
                visitorUsernames.add(entry.getKey());
            }
        }

        for (String username : visitorUsernames) {
            users.remove(username);
        }

        if (visitors == null) {
            return;
        }

        for (Visitor visitor : visitors) {
            if (visitor == null || visitor.getUsername() == null) {
                continue;
            }
            normalizeVisitor(visitor);
            users.put(visitor.getUsername(), visitor);
        }
    }

    public boolean registerOperator(Operator operator) {
        if (operator == null || operator.getUsername() == null || users.containsKey(operator.getUsername())) {
            return false;
        }

        if (operator.getId() == null) {
            operator.setId(nextUserId());
        }
        if (operator.getRole() == null || operator.getRole().isBlank()) {
            operator.setRole("OPERATOR");
        }
        if (operator.getAssignedAttractionsIds() == null) {
            operator.setAssignedAttractionsIds(new LinkedList<>());
        }
        users.put(operator.getUsername(), operator);
        return true;
    }

    public void replaceOperators(List<Operator> operators) {
        List<String> operatorUsernames = new ArrayList<>();
        for (Map.Entry<String, User> entry : users.entrySet()) {
            if (entry.getValue() instanceof Operator) {
                operatorUsernames.add(entry.getKey());
            }
        }

        for (String username : operatorUsernames) {
            users.remove(username);
        }

        if (operators == null) {
            return;
        }

        for (Operator operator : operators) {
            registerOperator(operator);
        }
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

    private Long nextUserId() {
        long maxId = 0L;
        for (User user : users.values()) {
            if (user.getId() != null && user.getId() > maxId) {
                maxId = user.getId();
            }
        }
        return maxId + 1;
    }

    private void normalizeVisitor(Visitor visitor) {
        if (visitor.getRole() == null || visitor.getRole().isBlank()) {
            visitor.setRole("VISITOR");
        }
        if (visitor.getFavoriteAttractions() == null) {
            visitor.setFavoriteAttractions(new CustomSet<>());
        }
        if (visitor.getVisitHistory() == null) {
            visitor.setVisitHistory(new LinkedList<>());
        }
        if (visitor.getNotifications() == null) {
            visitor.setNotifications(new ArrayList<>());
        }
        if (visitor.getTicketType() == null) {
            visitor.setTicketType(TicketType.GENERAL);
        }
        if (visitor.getPositionInQueue() == 0) {
            visitor.setPositionInQueue(-1);
        }
    }
}
