package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.Direction;
import com.OverZone.Scardaoni.shared.GameConstants;
import com.badlogic.gdx.math.Rectangle;

/**
 * Jugador de la partida (personaje Volt).
 *
 * Esta clase la usa el SERVIDOR: guarda el estado real del jugador y aplica
 * sus reglas (daño, muerte, reaparición, impulso). Los clientes nunca la
 * modifican: solo reciben una copia de sus datos dentro del estado de la
 * partida que envía el servidor.
 */
public class Player {

    public static final float SIZE = GameConstants.PLAYER_SIZE;
    public static final float SPEED = 140f; // unidades/seg
    public static final float MAX_HEALTH = GameConstants.PLAYER_MAX_HEALTH;

    public static final float DASH_SPEED_MULTIPLIER = 3.2f;
    public static final float DASH_DURATION = 0.18f;
    public static final float DASH_COOLDOWN = 3.0f;

    public static final float HIT_INVULNERABILITY = 0.4f;
    public static final float SPAWN_INVULNERABILITY = 1.5f;
    public static final float RESPAWN_DELAY = 3.0f;

    private final int id;
    private final String name;
    private int team;

    private float x, y;
    private float health = MAX_HEALTH;
    private boolean alive = true;
    private float respawnRemaining = 0f;

    private int facingDirection = Direction.DOWN;
    private boolean moving = false;

    private float dashTimeRemaining = 0f;
    private float dashCooldownRemaining = 0f;
    private float dashDirX, dashDirY;

    private float invulnerableTimeRemaining = 0f;

    private int kills = 0;
    private int deaths = 0;

    public Player(int id, String name, int team, float x, float y) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.x = x;
        this.y = y;
    }

    public Rectangle getBounds() {
        return new Rectangle(x - SIZE / 2f, y - SIZE / 2f, SIZE, SIZE);
    }

    /** Descuenta los temporizadores. No mueve al jugador. */
    public void update(float delta) {
        if (!alive) {
            respawnRemaining -= delta;
            return;
        }
        if (dashTimeRemaining > 0) dashTimeRemaining -= delta;
        if (dashCooldownRemaining > 0) dashCooldownRemaining -= delta;
        if (invulnerableTimeRemaining > 0) invulnerableTimeRemaining -= delta;
    }

    public void tryStartDash(float dirX, float dirY) {
        if (!alive) return;
        if (dashCooldownRemaining <= 0f && (dirX != 0 || dirY != 0)) {
            dashTimeRemaining = DASH_DURATION;
            dashCooldownRemaining = DASH_COOLDOWN;
            float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            dashDirX = dirX / len;
            dashDirY = dirY / len;
        }
    }

    public boolean isDashing() {
        return dashTimeRemaining > 0f;
    }

    public float getCurrentSpeed() {
        return isDashing() ? SPEED * DASH_SPEED_MULTIPLIER : SPEED;
    }

    public float getDashDirX() { return dashDirX; }
    public float getDashDirY() { return dashDirY; }

    /**
     * Aplica daño. Devuelve true si este golpe eliminó al jugador, para que
     * quien lo llama pueda sumar el punto al equipo contrario.
     */
    public boolean takeDamage(float amount) {
        if (!alive || invulnerableTimeRemaining > 0) return false;
        health -= amount;
        invulnerableTimeRemaining = HIT_INVULNERABILITY;
        if (health <= 0) {
            health = 0;
            alive = false;
            moving = false;
            dashTimeRemaining = 0;
            respawnRemaining = RESPAWN_DELAY;
            deaths++;
            return true;
        }
        return false;
    }

    public boolean isReadyToRespawn() {
        return !alive && respawnRemaining <= 0f;
    }

    /** Vuelve a la partida en la posición indicada, con vida completa. */
    public void respawn(float newX, float newY) {
        x = newX;
        y = newY;
        health = MAX_HEALTH;
        alive = true;
        respawnRemaining = 0f;
        dashTimeRemaining = 0f;
        dashCooldownRemaining = 0f;
        invulnerableTimeRemaining = SPAWN_INVULNERABILITY;
    }

    /** Deja al jugador como al principio de una partida nueva. */
    public void resetForNewMatch(float newX, float newY) {
        respawn(newX, newY);
        kills = 0;
        deaths = 0;
    }

    public void addKill() { kills++; }

    public boolean isAlive() { return alive; }
    public boolean isInvulnerable() { return invulnerableTimeRemaining > 0f; }

    public float getDashCooldownProgress() {
        // 0 = listo para usar, 1 = recién usado
        return Math.max(0f, dashCooldownRemaining / DASH_COOLDOWN);
    }

    public float getRespawnRemaining() { return Math.max(0f, respawnRemaining); }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getTeam() { return team; }
    /** Solo se usa en la sala de espera, al reacomodar los equipos. */
    public void setTeam(int team) { this.team = team; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public float getX() { return x; }
    public float getY() { return y; }
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public float getHealth() { return health; }
    public int getFacingDirection() { return facingDirection; }
    public void setFacingDirection(int facingDirection) { this.facingDirection = facingDirection; }
    public boolean isMoving() { return moving; }
    public void setMoving(boolean moving) { this.moving = moving; }
}
