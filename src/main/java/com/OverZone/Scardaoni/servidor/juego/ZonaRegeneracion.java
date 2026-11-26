package com.OverZone.Scardaoni.servidor.juego;

import com.OverZone.Scardaoni.compartido.TipoPersonaje;

public class ZonaRegeneracion {
    private final int id;
    private final int duenioId;
    private final int equipo;
    private final float x, y;
    private final float radio = TipoPersonaje.RADIO_REGENERACION;
    private float restante;

    public ZonaRegeneracion(int id, int duenioId, int equipo, float x, float y, float duracion) {
        this.id = id;
        this.duenioId = duenioId;
        this.equipo = equipo;
        this.x = x;
        this.y = y;
        this.restante = duracion;
    }

    public void actualizar(float delta) {
        restante -= delta;
    }

    public boolean estaVivo() {
        return restante > 0f;
    }

    /** ¿El punto (px, py) está dentro del círculo? Se comparan distancias al cuadrado. */
    public boolean contiene(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= radio * radio;
    }

    public int getId() { return id; }
    public int getDuenioId() { return duenioId; }
    public int getEquipo() { return equipo; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRadio() { return radio; }
    public float getRestante() { return Math.max(0f, restante); }
}
