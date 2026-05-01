package com.techpark.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad: Administrador del Parque
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Administrator extends User {
    private String department; // Recursos Humanos, Operaciones, Mantenimiento, etc

    public Administrator(String username, String password, String email, String department) {
        super(null, username, password, email, "ADMIN", true, null);
        this.department = department;
    }

    public Administrator(String username, String password, String email) {
        super(null, username, password, email, "ADMIN", true, null);
        this.department = "Operaciones";
    }
}
