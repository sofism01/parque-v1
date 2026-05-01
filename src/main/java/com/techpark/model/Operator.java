package com.techpark.model;

import com.techpark.datastructures.LinkedList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad: Operador de Atracción
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Operator extends User {
    private Long zoneId; // Zona asignada
    private LinkedList<Long> assignedAttractionsIds; // IDs de atracciones asignadas

    public Operator(String username, String password, String email, Long zoneId) {
        super(null, username, password, email, "OPERATOR", true, null);
        this.zoneId = zoneId;
        this.assignedAttractionsIds = new LinkedList<>();
    }

    // Asignar atracción
    public void addAttraction(Long attractionId) {
        if (!assignedAttractionsIds.toList().contains(attractionId)) {
            assignedAttractionsIds.add(attractionId);
        }
    }

    // Remover atracción
    public void removeAttraction(Long attractionId) {
        for (int i = 0; i < assignedAttractionsIds.size(); i++) {
            if (assignedAttractionsIds.get(i).equals(attractionId)) {
                assignedAttractionsIds.remove(i);
                break;
            }
        }
    }

    // Verificar si gestiona una atracción
    public boolean managesAttraction(Long attractionId) {
        return assignedAttractionsIds.toList().contains(attractionId);
    }

    // Obtener cantidad de atracciones asignadas
    public int getAssignedAttractionsCount() {
        return assignedAttractionsIds.size();
    }
}
