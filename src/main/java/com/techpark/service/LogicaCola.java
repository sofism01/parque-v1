package com.techpark.service;

import com.techpark.model.Usuario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logica de procesamiento de filas de visitantes.
 */
public class LogicaCola {
    private static final int SEGUNDOS_POR_PERSONA = 20;

    private int totalHistorico;

    public LogicaCola() {
        reiniciarEstado();
    }

    public int procesarFila(List<Usuario> lista) {
        if (lista == null || lista.isEmpty()) {
            return totalHistorico;
        }

        int removidos = 0;
        for (Usuario usuario : lista) {
            if (estaAtendido(usuario)) {
                removidos++;
            }
        }

        totalHistorico += removidos;
        lista.removeIf(this::estaAtendido);
        return totalHistorico;
    }

    public Map<String, Object> actualizarEstadoAtraccion(List<Usuario> filaActual) {
        int cantidadPersonas = filaActual == null ? 0 : filaActual.size();
        int tiempoTotalSegundos = cantidadPersonas <= 0 ? 0 : cantidadPersonas * SEGUNDOS_POR_PERSONA;

        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("tiempoFormateado", formatearTiempo(tiempoTotalSegundos));
        estado.put("tiempoTotalSegundos", tiempoTotalSegundos);
        return estado;
    }

    public void reiniciarEstado() {
        totalHistorico = 0;
    }

    public int getTotalHistorico() {
        return totalHistorico;
    }

    private String formatearTiempo(int tiempoTotalSegundos) {
        int minutos = tiempoTotalSegundos / 60;
        int segundos = tiempoTotalSegundos % 60;
        return minutos + " min " + segundos + " seg";
    }

    private boolean estaAtendido(Usuario usuario) {
        if (usuario == null || usuario.getEstado() == null) {
            return false;
        }

        return "ATENDIDO".equalsIgnoreCase(usuario.getEstado());
    }
}
