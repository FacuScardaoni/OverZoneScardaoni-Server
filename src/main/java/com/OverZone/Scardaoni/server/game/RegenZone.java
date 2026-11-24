package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.CharacterType;

/**
 * Campo de Regeneración de Iris. Solo existe en el SERVIDOR.
 *
 * Es un círculo fijo en el mapa que dura unos segundos. En cada paso de la
 * simulación, MatchSimulation cura a los aliados de Iris que estén adentro.
 * Los clientes solo reciben dónde está y cuánto le queda, para dibujarlo.
 */
public class RegenZone {

    private final int id;
    private final int ownerId;
    private final int team;
    private final float x, y;
    private final float radius = CharacterType.REGEN_RADIUS;
    private float remaining;

    public RegenZone(int id, int ownerId, int team, float x, float y, float duration) {
        this.id = id;
        this.ownerId = ownerId;
        this.team = team;
        this.x = x;
        this.y = y;
        this.remaining = duration;
    }

    public void update(float delta) {
        remaining -= delta;
    }

    public boolean isAlive() {
        return remaining > 0f;
    }

    /** ¿El punto (px, py) está dentro del círculo? Se comparan distancias al cuadrado. */
    public boolean contains(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= radius * radius;
    }

    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public int getTeam() { return team; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRadius() { return radius; }
    public float getRemaining() { return Math.max(0f, remaining); }
}
