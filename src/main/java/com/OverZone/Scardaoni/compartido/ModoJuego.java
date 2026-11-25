/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocolo.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.compartido;

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
public enum ModoJuego {

    TODOS_CONTRA_TODOS("Todos contra todos", 2),
    EQUIPOS("Equipos 2v2", 4);

    public final String nombreVisible;
    /** Jugadores necesarios para jugar este modo. */
    public final int jugadoresNecesarios;

    ModoJuego(String nombreVisible, int jugadoresNecesarios) {
        this.nombreVisible = nombreVisible;
        this.jugadoresNecesarios = jugadoresNecesarios;
    }

    /** Número con el que viaja por la red. */
    public int id() {
        return ordinal();
    }

    public static ModoJuego desdeId(int id) {
        ModoJuego[] todos = values();
        return (id >= 0 && id < todos.length) ? todos[id] : TODOS_CONTRA_TODOS;
    }

    /** ¿Se puede jugar este modo con esta cantidad de jugadores? */
    public boolean permite(int jugadores) {
        return this == EQUIPOS ? jugadores == jugadoresNecesarios : jugadores >= jugadoresNecesarios;
    }
}
