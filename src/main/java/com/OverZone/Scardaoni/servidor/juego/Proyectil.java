package com.OverZone.Scardaoni.servidor.juego;

import com.OverZone.Scardaoni.compartido.TipoPersonaje;
import com.badlogic.gdx.math.Rectangle;

public class Proyectil {
    private final int id;
    private final int duenioId;
    private final int equipo;
    private final TipoPersonaje personaje;
    private float x, y;
    private final float dirX, dirY;
    private boolean vivo = true;
    /** Daño que hace al pegar (puede estar aumentado por el Power-Up Sobrecarga). */
    private final float danio;
    /** Distancia recorrida desde que se disparó. */
    private float recorrido = 0f;

    public Proyectil(int id, int duenioId, int equipo, TipoPersonaje personaje,
                      float x, float y, float dirX, float dirY) {
        this(id, duenioId, equipo, personaje, x, y, dirX, dirY, personaje.danio);
    }

    public Proyectil(int id, int duenioId, int equipo, TipoPersonaje personaje,
                      float x, float y, float dirX, float dirY, float danio) {
        this.danio = danio;
        this.id = id;
        this.duenioId = duenioId;
        this.equipo = equipo;
        this.personaje = personaje;
        this.x = x;
        this.y = y;
        float largo = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        // Quien crea el proyectil debe garantizar una dirección válida;
        // por seguridad, una dirección nula se convierte en "hacia la derecha".
        if (largo == 0) {
            this.dirX = 1;
            this.dirY = 0;
        } else {
            this.dirX = dirX / largo;
            this.dirY = dirY / largo;
        }
    }

    public void actualizar(float delta) {
        float paso = personaje.velocidadProyectil * delta;
        x += dirX * paso;
        y += dirY * paso;
        recorrido += paso;
        if (recorrido >= personaje.alcanceProyectil) vivo = false;
    }

    public Rectangle getLimites() {
        float tamanio = personaje.tamanioProyectil;
        return new Rectangle(x - tamanio / 2f, y - tamanio / 2f, tamanio, tamanio);
    }

    public float getDanio() { return danio; }
    public TipoPersonaje getPersonaje() { return personaje; }
    public float getDirX() { return dirX; }
    public float getDirY() { return dirY; }
    public int getId() { return id; }
    public int getDuenioId() { return duenioId; }
    public int getEquipo() { return equipo; }
    public boolean estaVivo() { return vivo; }
    public void destruir() { vivo = false; }
    public float getX() { return x; }
    public float getY() { return y; }
}
