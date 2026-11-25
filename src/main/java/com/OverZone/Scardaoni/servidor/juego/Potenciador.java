package com.OverZone.Scardaoni.servidor.juego;

import com.OverZone.Scardaoni.compartido.TipoPotenciador;

/**
 * Power-Up tirado en el mapa. Solo existe en el SERVIDOR: SimulacionPartida lo
 * crea en uno de los puntos de Arena.APARICIONES_POTENCIADORES, revisa en cada paso si
 * un jugador lo toca y, si es así, le aplica el efecto y lo borra.
 */
public class Potenciador {

    private final int id;
    private final TipoPotenciador tipo;
    /** Índice del punto de aparición que ocupa (para no poner dos en el mismo lugar). */
    private final int indiceAparicion;
    private final float x, y;

    public Potenciador(int id, TipoPotenciador tipo, int indiceAparicion, float x, float y) {
        this.id = id;
        this.tipo = tipo;
        this.indiceAparicion = indiceAparicion;
        this.x = x;
        this.y = y;
    }

    /** ¿El jugador está lo bastante cerca para agarrarlo? */
    public boolean toca(Jugador p) {
        float dx = p.getX() - x, dy = p.getY() - y;
        return dx * dx + dy * dy <= TipoPotenciador.RADIO_RECOGIDA * TipoPotenciador.RADIO_RECOGIDA;
    }

    public int getId() { return id; }
    public TipoPotenciador getTipo() { return tipo; }
    public int getIndiceAparicion() { return indiceAparicion; }
    public float getX() { return x; }
    public float getY() { return y; }
}
