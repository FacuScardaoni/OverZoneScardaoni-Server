/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;

/** Equipos de la partida. */
public final class Team {

    public static final int NONE = -1;
    public static final int BLUE = 0;
    public static final int RED = 1;

    private Team() { }

    public static String name(int team) {
        switch (team) {
            case BLUE: return "Azul";
            case RED: return "Rojo";
            default: return "-";
        }
    }
}
