package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.CharacterType;
import com.OverZone.Scardaoni.shared.PowerUpType;
import com.OverZone.Scardaoni.shared.Direction;
import com.OverZone.Scardaoni.shared.GameConstants;
import com.badlogic.gdx.math.Rectangle;

/**
 * Jugador de la partida. Solo existe en el SERVIDOR.
 *
 * Guarda el estado real del jugador y aplica sus reglas: daño, muerte,
 * reaparición y la habilidad de su personaje. Las estadísticas (vida,
 * velocidad, daño, habilidad) salen de su CharacterType, así el mismo código
 * sirve para los cuatro personajes:
 *   - Volt:    Impulso Over (sale disparado hacia donde camina).
 *   - Brutus:  Escudo Cinético (frena el 70 % del daño que viene de frente).
 *   - Iris:    Campo de Regeneración (la zona la crea y la maneja MatchSimulation).
 *   - Phantom: Camuflaje Óptico (casi invisible; se corta si dispara).
 */
public class Player {

    public static final float SIZE = GameConstants.PLAYER_SIZE;

    public static final float DASH_SPEED_MULTIPLIER = 3.2f;
    public static final float HIT_INVULNERABILITY = 0.4f;
    public static final float SPAWN_INVULNERABILITY = 1.5f;
    public static final float RESPAWN_DELAY = 3.0f;

    private final int id;
    private final String name;
    private int team;
    private CharacterType character = CharacterType.VOLT;

    private float x, y;
    private float health;
    private boolean alive = true;
    private float respawnRemaining = 0f;

    private int facingDirection = Direction.DOWN;
    private boolean moving = false;

    /** Tiempo que le queda a la habilidad activa (impulso, escudo, campo o camuflaje). */
    private float abilityTimeRemaining = 0f;
    private float abilityCooldownRemaining = 0f;

    // Impulso Over (Volt)
    private float dashDirX, dashDirY;
    // Escudo Cinético (Brutus): hacia dónde apunta, con largo 1
    private float shieldDirX = 0f, shieldDirY = -1f;

    /** Segundos que le quedan a cada Power-Up temporal (en el orden de PowerUpType). */
    private final float[] boosts = new float[PowerUpType.values().length];

    private float invulnerableTimeRemaining = 0f;

    private int kills = 0;
    private int deaths = 0;

    public Player(int id, String name, int team, float x, float y) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.x = x;
        this.y = y;
        this.health = character.maxHealth;
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
        if (abilityTimeRemaining > 0) abilityTimeRemaining -= delta;
        if (abilityCooldownRemaining > 0) abilityCooldownRemaining -= delta;
        if (invulnerableTimeRemaining > 0) invulnerableTimeRemaining -= delta;
        for (int i = 0; i < boosts.length; i++) {
            if (boosts[i] > 0) boosts[i] = Math.max(0f, boosts[i] - delta);
        }
    }

    // ------------------------------------------------------------------
    // Power-Ups
    // ------------------------------------------------------------------

    /**
     * ¿Le sirve este Power-Up? El Botiquín solo se agarra si falta vida, así
     * no se desperdicia pasando por arriba con la vida llena.
     */
    public boolean canPickUp(PowerUpType type) {
        if (!alive) return false;
        return type != PowerUpType.HEALTH || health < character.maxHealth;
    }

    /** Aplica el efecto de un Power-Up. Si ya estaba activo, se reinicia su tiempo. */
    public void applyPowerUp(PowerUpType type) {
        if (type == PowerUpType.HEALTH) heal(PowerUpType.HEAL_AMOUNT);
        else boosts[type.id()] = type.duration;
    }

    public boolean hasBoost(PowerUpType type) {
        return boosts[type.id()] > 0f;
    }

    /** Copia de los tiempos que le quedan a cada Power-Up (para el estado). */
    public float[] getBoosts() {
        return boosts.clone();
    }

    /** Multiplicador de daño de los disparos (Sobrecarga). */
    public float getDamageMultiplier() {
        return hasBoost(PowerUpType.OVERLOAD) ? PowerUpType.OVERLOAD_FACTOR : 1f;
    }

    private void clearBoosts() {
        java.util.Arrays.fill(boosts, 0f);
    }

    // ------------------------------------------------------------------
    // Habilidades
    // ------------------------------------------------------------------

    /**
     * Intenta usar la habilidad del personaje en la dirección indicada.
     * Devuelve true si se activó (no estaba recargando).
     */
    public boolean tryUseAbility(float dirX, float dirY) {
        if (!alive || abilityCooldownRemaining > 0f || isAbilityActive()) return false;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        boolean needsDirection = character == CharacterType.VOLT || character == CharacterType.BRUTUS;
        if (needsDirection && len == 0) return false;

        abilityTimeRemaining = character.abilityDuration;
        abilityCooldownRemaining = character.abilityCooldown;
        if (character == CharacterType.VOLT) {
            dashDirX = dirX / len;
            dashDirY = dirY / len;
        } else if (character == CharacterType.BRUTUS) {
            setShieldDirection(dirX, dirY);
        }
        // Iris y Phantom no necesitan dirección: el campo se crea donde está
        // Iris (lo hace MatchSimulation) y el camuflaje solo dura un tiempo.
        return true;
    }

    public boolean isAbilityActive() {
        return abilityTimeRemaining > 0f;
    }

    public boolean isDashing() {
        return character == CharacterType.VOLT && isAbilityActive();
    }

    public boolean isShieldActive() {
        return character == CharacterType.BRUTUS && isAbilityActive();
    }

    public boolean isCamouflaged() {
        return character == CharacterType.PHANTOM && isAbilityActive();
    }

    /** Phantom se hace visible de nuevo (por ejemplo, al disparar). */
    public void breakCamouflage() {
        if (isCamouflaged()) abilityTimeRemaining = 0f;
    }

    /** Recupera vida sin pasarse del máximo del personaje. */
    public void heal(float amount) {
        if (!alive) return;
        health = Math.min(character.maxHealth, health + amount);
    }

    /** El escudo sigue hacia donde apunta Brutus mientras está activo. */
    public void setShieldDirection(float dirX, float dirY) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len == 0) return;
        shieldDirX = dirX / len;
        shieldDirY = dirY / len;
    }

    /** Ángulo del escudo en grados (0 = derecha, 90 = arriba). */
    public float getShieldAngle() {
        return (float) Math.toDegrees(Math.atan2(shieldDirY, shieldDirX));
    }

    public float getCurrentSpeed() {
        float speed = character.speed;
        if (isDashing()) speed *= DASH_SPEED_MULTIPLIER;
        // Con el escudo levantado, Brutus camina un poco más lento.
        else if (isShieldActive()) speed *= CharacterType.SHIELD_SPEED_FACTOR;
        // Power-Up Turbo
        if (hasBoost(PowerUpType.TURBO)) speed *= PowerUpType.TURBO_FACTOR;
        return speed;
    }

    public float getDashDirX() { return dashDirX; }
    public float getDashDirY() { return dashDirY; }

    // ------------------------------------------------------------------
    // Daño
    // ------------------------------------------------------------------

    /**
     * Aplica daño de un proyectil que viaja en la dirección (projDirX, projDirY).
     * Si Brutus tiene el escudo activo y el disparo viene de frente, el escudo
     * absorbe parte del daño. Devuelve true si este golpe eliminó al jugador.
     */
    public boolean takeDamage(float amount, float projDirX, float projDirY) {
        if (!alive || invulnerableTimeRemaining > 0) return false;

        if (isShieldActive()) {
            // El proyectil viene "de frente" si viaja en sentido contrario a
            // donde apunta el escudo. El producto escalar entre las dos
            // direcciones es -1 si son opuestas y +1 si van para el mismo lado.
            float dot = projDirX * shieldDirX + projDirY * shieldDirY;
            if (dot <= -CharacterType.SHIELD_FRONT_COS) {
                amount *= (1f - CharacterType.SHIELD_ABSORB);
            }
        }

        // Power-Up Blindaje: se recibe la mitad del daño.
        if (hasBoost(PowerUpType.ARMOR)) amount *= PowerUpType.ARMOR_FACTOR;

        health -= amount;
        invulnerableTimeRemaining = HIT_INVULNERABILITY;
        if (health <= 0) {
            health = 0;
            alive = false;
            moving = false;
            abilityTimeRemaining = 0;
            respawnRemaining = RESPAWN_DELAY;
            clearBoosts(); // al morir se pierden los Power-Ups
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
        health = character.maxHealth;
        alive = true;
        respawnRemaining = 0f;
        abilityTimeRemaining = 0f;
        abilityCooldownRemaining = 0f;
        invulnerableTimeRemaining = SPAWN_INVULNERABILITY;
        clearBoosts();
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

    public float getAbilityCooldownProgress() {
        // 0 = lista para usar, 1 = recién usada
        return Math.max(0f, abilityCooldownRemaining / character.abilityCooldown);
    }

    public float getRespawnRemaining() { return Math.max(0f, respawnRemaining); }

    public CharacterType getCharacter() { return character; }

    /** Solo se usa en la sala de espera. Cambia el personaje y repone la vida. */
    public void setCharacter(CharacterType character) {
        this.character = character;
        this.health = character.maxHealth;
    }

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
