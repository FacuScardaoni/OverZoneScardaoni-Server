/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;

import java.util.Locale;

/**
 * Reglas de comunicación entre clientes y servidor.
 *
 * Todos los mensajes son texto, con sus partes separadas por ";".
 * El primer campo siempre indica el tipo de mensaje.
 *
 * Cliente → Servidor
 *   OVERZONE_DISCOVER                      (broadcast) ¿hay algún servidor?
 *   CONNECT;nombre;version                 quiero unirme
 *   INPUT;seq;moveX;moveY;aimX;aimY;fire;abilityCount
 *   PING;listo;personaje;marcaDeTiempo     sigo conectado (cada 250 ms)
 *   DISCONNECT                             me voy
 *
 * Servidor → Cliente
 *   OVERZONE_SERVER;nombre;jugadores;max;version   respuesta al broadcast
 *   WELCOME;idJugador                      conexión aceptada
 *   REJECT;motivo                          conexión rechazada (FULL / UNKNOWN / VERSION)
 *   STATE;...                              estado completo de la partida
 *   PONG;marcaDeTiempo                     respuesta al PING (mide el retardo)
 *   SHUTDOWN                               el servidor se está cerrando
 */
public final class Protocol {

    /**
     * Versión del protocolo. Cliente y servidor están en repositorios
     * distintos: si uno se actualiza y el otro no, esta versión no coincide
     * y la conexión se rechaza con un aviso claro.
     */
    public static final int VERSION = 2;

    public static final int SERVER_PORT = 5555;
    public static final int MAX_PLAYERS = 4;
    public static final int MAX_PACKET_SIZE = 8192;

    public static final String SEP = ";";

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

    public static final String REASON_FULL = "FULL";
    public static final String REASON_UNKNOWN = "UNKNOWN";
    public static final String REASON_VERSION = "VERSION";

    // --- Tiempos (milisegundos) ---
    /** Cada cuánto el cliente avisa que sigue conectado. */
    public static final long HEARTBEAT_INTERVAL_MS = 250;
    /** Si el servidor no recibe nada de un cliente en este tiempo, lo da por desconectado. */
    public static final long CLIENT_TIMEOUT_MS = 3000;
    /** Si el cliente no recibe nada del servidor en este tiempo, da la conexión por perdida. */
    public static final long SERVER_TIMEOUT_MS = 3000;

    // --- Frecuencias del servidor ---
    /** Veces por segundo que el servidor simula la partida. */
    public static final int TICK_RATE = 60;
    /** Veces por segundo que el servidor envía el estado a los clientes. */
    public static final int STATE_RATE = 30;

    private Protocol() { }

    /** Une las partes de un mensaje con el separador. */
    public static String join(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(SEP);
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Convierte un número con decimales en texto, SIEMPRE con punto decimal.
     * Con la configuración regional de Argentina, String.format escribiría
     * "12,5" y la coma rompería el mensaje, que usa comas como separador.
     */
    public static String f(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Quita del nombre los caracteres que se usan como separadores. */
    public static String sanitizeName(String name) {
        if (name == null) return "Jugador";
        String clean = name.replaceAll("[;,|\\s]", "");
        if (clean.isEmpty()) clean = "Jugador";
        return clean.length() > 12 ? clean.substring(0, 12) : clean;
    }
}
