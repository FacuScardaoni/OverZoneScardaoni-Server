package com.OverZone.Scardaoni.compartido;

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
