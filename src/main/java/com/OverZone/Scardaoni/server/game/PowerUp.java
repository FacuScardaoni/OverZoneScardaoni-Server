package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.PowerUpType;

/**
 * Power-Up tirado en el mapa. Solo existe en el SERVIDOR: MatchSimulation lo
 * crea en uno de los puntos de Arena.POWERUP_SPAWNS, revisa en cada paso si
 * un jugador lo toca y, si es así, le aplica el efecto y lo borra.
 */
public class PowerUp {

    private final int id;
    private final PowerUpType type;
    /** Índice del punto de aparición que ocupa (para no poner dos en el mismo lugar). */
    private final int spawnIndex;
    private final float x, y;

    public PowerUp(int id, PowerUpType type, int spawnIndex, float x, float y) {
        this.id = id;
        this.type = type;
        this.spawnIndex = spawnIndex;
        this.x = x;
        this.y = y;
    }

    /** ¿El jugador está lo bastante cerca para agarrarlo? */
    public boolean touches(Player p) {
        float dx = p.getX() - x, dy = p.getY() - y;
        return dx * dx + dy * dy <= PowerUpType.PICKUP_RADIUS * PowerUpType.PICKUP_RADIUS;
    }

    public int getId() { return id; }
    public PowerUpType getType() { return type; }
    public int getSpawnIndex() { return spawnIndex; }
    public float getX() { return x; }
    public float getY() { return y; }
}
