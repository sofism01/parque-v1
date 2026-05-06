package com.techpark.model;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Variante de usuario con estado operativo para procesamiento de filas.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Usuario extends User {
    private String estado;

    public Usuario(Long id, String username, String password, String email, String role, boolean active, java.time.LocalDate createdAt, String estado) {
        super(id, username, password, email, role, active, createdAt);
        setEstado(estado);
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado != null ? estado.trim().toUpperCase() : null;
    }
}
