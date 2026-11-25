/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocolo.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
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
