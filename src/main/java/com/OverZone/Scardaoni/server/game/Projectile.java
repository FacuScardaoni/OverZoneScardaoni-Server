package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.GameConstants;
import com.badlogic.gdx.math.Rectangle;

/**
 * Proyectil. Lo crea y lo mueve el servidor.
 *
 * Recuerda quién lo disparó (ownerId) y de qué equipo es (team): solo puede
 * dañar a jugadores del equipo contrario.
 */
public class Projectile {

    public static final float SIZE = GameConstants.PROJECTILE_SIZE;
    public static final float SPEED = 320f;
    public static final float DAMAGE = 25f;

    private final int id;
    private final int ownerId;
    private final int team;
    private float x, y;
    private final float dirX, dirY;
    private boolean alive = true;

    public Projectile(int id, int ownerId, int team, float x, float y, float dirX, float dirY) {
        this.id = id;
        this.ownerId = ownerId;
        this.team = team;
        this.x = x;
        this.y = y;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        // Quien crea el proyectil debe garantizar una dirección válida;
        // por seguridad, una dirección nula se convierte en "hacia la derecha".
        if (len == 0) {
            this.dirX = 1;
            this.dirY = 0;
        } else {
            this.dirX = dirX / len;
            this.dirY = dirY / len;
        }
    }

    public void update(float delta) {
        x += dirX * SPEED * delta;
        y += dirY * SPEED * delta;
    }

    public Rectangle getBounds() {
        return new Rectangle(x - SIZE / 2f, y - SIZE / 2f, SIZE, SIZE);
    }

    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public int getTeam() { return team; }
    public boolean isAlive() { return alive; }
    public void kill() { alive = false; }
    public float getX() { return x; }
    public float getY() { return y; }
}
