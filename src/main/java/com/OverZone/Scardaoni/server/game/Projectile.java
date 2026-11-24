package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.CharacterType;
import com.badlogic.gdx.math.Rectangle;

/**
 * Proyectil. Lo crea y lo mueve el servidor.
 *
 * Recuerda quién lo disparó, de qué equipo es (solo daña a los rivales) y con
 * qué personaje: de eso dependen su velocidad, su tamaño, su daño y su
 * alcance. Cuando recorre su alcance máximo, desaparece.
 */
public class Projectile {

    private final int id;
    private final int ownerId;
    private final int team;
    private final CharacterType character;
    private float x, y;
    private final float dirX, dirY;
    private boolean alive = true;
    /** Distancia recorrida desde que se disparó. */
    private float traveled = 0f;

    public Projectile(int id, int ownerId, int team, CharacterType character,
                      float x, float y, float dirX, float dirY) {
        this.id = id;
        this.ownerId = ownerId;
        this.team = team;
        this.character = character;
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
        float step = character.projectileSpeed * delta;
        x += dirX * step;
        y += dirY * step;
        traveled += step;
        if (traveled >= character.projectileRange) alive = false;
    }

    public Rectangle getBounds() {
        float size = character.projectileSize;
        return new Rectangle(x - size / 2f, y - size / 2f, size, size);
    }

    public float getDamage() { return character.damage; }
    public CharacterType getCharacter() { return character; }
    public float getDirX() { return dirX; }
    public float getDirY() { return dirY; }
    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public int getTeam() { return team; }
    public boolean isAlive() { return alive; }
    public void kill() { alive = false; }
    public float getX() { return x; }
    public float getY() { return y; }
}
