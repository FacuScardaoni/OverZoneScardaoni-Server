package com.OverZone.Scardaoni.compartido;

import com.badlogic.gdx.math.Rectangle;

public class Obstaculo {
    private final Rectangle limites;

    public Obstaculo(float x, float y, float ancho, float alto) {
        this.limites = new Rectangle(x, y, ancho, alto);
    }

    public Rectangle getLimites() {
        return limites;
    }
}
