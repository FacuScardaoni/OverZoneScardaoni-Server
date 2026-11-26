package com.OverZone.Scardaoni.compartido;

import java.util.Locale;

public final class Protocolo {
    public static final int VERSION = 5;

    public static final int PUERTO_SERVIDOR = 5555;
    public static final int MAX_JUGADORES = 4;
    public static final int TAMANIO_MAX_PAQUETE = 8192;

    public static final String SEPARADOR = ";";

    // --- Cliente → Servidor ---
    public static final String DISCOVER = "OVERZONE_DISCOVER";
    public static final String CONNECT = "CONNECT";
    public static final String INPUT = "INPUT";
    public static final String PING = "PING";
    public static final String DISCONNECT = "DISCONNECT";

    // --- Servidor → Cliente ---
    public static final String SERVER_INFO = "OVERZONE_SERVER";
    public static final String WELCOME = "WELCOME";
    public static final String REJECT = "REJECT";
    public static final String STATE = "STATE";
    public static final String PONG = "PONG";
    public static final String SHUTDOWN = "SHUTDOWN";

    public static final String MOTIVO_LLENO = "FULL";
    public static final String MOTIVO_DESCONOCIDO = "UNKNOWN";
    public static final String MOTIVO_VERSION = "VERSION";

    // --- Tiempos (milisegundos) ---
    /** Cada cuánto el cliente avisa que sigue conectado. */
    public static final long INTERVALO_LATIDO_MS = 250;
    /** Si el servidor no recibe nada de un cliente en este tiempo, lo da por desconectado. */
    public static final long TIEMPO_ESPERA_CLIENTE_MS = 3000;
    /** Si el cliente no recibe nada del servidor en este tiempo, da la conexión por perdida. */
    public static final long TIEMPO_ESPERA_SERVIDOR_MS = 3000;

    // --- Frecuencias del servidor ---
    /** Veces por segundo que el servidor simula la partida. */
    public static final int PASOS_POR_SEGUNDO = 60;
    /** Veces por segundo que el servidor envía el estado a los clientes. */
    public static final int ESTADOS_POR_SEGUNDO = 30;

    private Protocolo() { }

    /** Une las partes de un mensaje con el separador. */
    public static String unir(Object... partes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) sb.append(SEPARADOR);
            sb.append(partes[i]);
        }
        return sb.toString();
    }

    public static String f(float valor) {
        return String.format(Locale.ROOT, "%.1f", valor);
    }

    /** Quita del nombre los caracteres que se usan como separadores. */
    public static String limpiarNombre(String nombre) {
        if (nombre == null) return "Jugador";
        String limpio = nombre.replaceAll("[;,|\\s]", "");
        if (limpio.isEmpty()) limpio = "Jugador";
        return limpio.length() > 12 ? limpio.substring(0, 12) : limpio;
    }
}
