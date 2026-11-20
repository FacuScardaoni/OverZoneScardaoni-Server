/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;

/**
 * Valores del juego que necesitan conocer tanto el servidor como el cliente.
 *
 * El servidor los usa para las reglas (tamaño para los choques, vida máxima)
 * y el cliente para dibujar (tamaño de los sprites, barra de vida, mínimo de
 * jugadores que muestra la sala de espera).
 */
public final class GameConstants {

    /** Lado del cuadrado que ocupa un jugador, en unidades del mapa. */
    public static final float PLAYER_SIZE = 24f;
    public static final float PLAYER_MAX_HEALTH = 100f;
    /** Lado del cuadrado que ocupa un proyectil. */
    public static final float PROJECTILE_SIZE = 8f;
    /** Jugadores necesarios para empezar una partida. */
    public static final int MIN_PLAYERS = 2;

    private GameConstants() { }
}
