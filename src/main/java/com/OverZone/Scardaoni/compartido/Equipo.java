package com.OverZone.Scardaoni.compartido;

/** Equipos de la partida. */
public final class Equipo {
    public static final int NINGUNO = -1;
    public static final int AZUL = 0;
    public static final int ROJO = 1;

    private Equipo() { }

    public static String nombre(int equipo) {
        switch (equipo) {
            case AZUL: return "Azul";
            case ROJO: return "Rojo";
            default: return "-";
        }
    }
}
