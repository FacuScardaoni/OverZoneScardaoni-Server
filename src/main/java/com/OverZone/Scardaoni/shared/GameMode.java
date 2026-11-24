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
 * Modos de juego.
 *
 *  - Todos contra todos: cada jugador juega solo; gana quien consigue más
 *    eliminaciones. Se puede jugar desde 2 jugadores.
 *  - Equipos 2v2: dos equipos de dos; gana el equipo con más eliminaciones.
 *    Solo se puede elegir con exactamente 4 jugadores en la sala.
 *
 * Lo elige el anfitrión de la sala (el primer jugador conectado). El servidor
 * valida el pedido: si no hay 4 jugadores, no acepta el modo por equipos.
 */
public enum GameMode {

    FREE_FOR_ALL("Todos contra todos", 2),
    TEAMS("Equipos 2v2", 4);

    public final String displayName;
    /** Jugadores necesarios para jugar este modo. */
    public final int requiredPlayers;

    GameMode(String displayName, int requiredPlayers) {
        this.displayName = displayName;
        this.requiredPlayers = requiredPlayers;
    }

    /** Número con el que viaja por la red. */
    public int id() {
        return ordinal();
    }

    public static GameMode fromId(int id) {
        GameMode[] all = values();
        return (id >= 0 && id < all.length) ? all[id] : FREE_FOR_ALL;
    }

    /** ¿Se puede jugar este modo con esta cantidad de jugadores? */
    public boolean allows(int players) {
        return this == TEAMS ? players == requiredPlayers : players >= requiredPlayers;
    }
}
