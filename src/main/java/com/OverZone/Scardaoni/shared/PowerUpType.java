/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;

/**
 * Power-Ups temporales que aparecen en el mapa durante la partida.
 *
 * El SERVIDOR decide cuándo y dónde aparecen, detecta quién los agarra y
 * aplica el efecto. Los clientes solo los dibujan (y muestran en el HUD
 * cuánto le queda a cada efecto).
 *
 *   Botiquín    recupera 50 de vida al instante (solo si te falta vida)
 *   Turbo       35 % más de velocidad durante 6 s
 *   Sobrecarga  50 % más de daño por disparo durante 6 s
 *   Blindaje    recibís la mitad del daño durante 6 s
 *
 * Si agarrás uno que ya tenés activo, se reinicia su tiempo (no se suma).
 */
public enum PowerUpType {

    HEALTH("Botiquín", "+50 de vida", 0f),
    TURBO("Turbo", "+35% velocidad", 6f),
    OVERLOAD("Sobrecarga", "+50% daño", 6f),
    ARMOR("Blindaje", "-50% daño recibido", 6f);

    /** Vida que recupera el Botiquín. */
    public static final float HEAL_AMOUNT = 50f;
    /** Multiplicador de velocidad del Turbo. */
    public static final float TURBO_FACTOR = 1.35f;
    /** Multiplicador de daño de la Sobrecarga. */
    public static final float OVERLOAD_FACTOR = 1.5f;
    /** Parte del daño que se recibe con Blindaje. */
    public static final float ARMOR_FACTOR = 0.5f;

    /** Distancia (desde el centro del jugador) a la que se agarra un Power-Up. */
    public static final float PICKUP_RADIUS = 22f;
    /** Primer Power-Up: a los 5 segundos de empezar la partida. */
    public static final float FIRST_SPAWN_DELAY = 5f;
    /** Después, uno nuevo cada 8 segundos... */
    public static final float SPAWN_INTERVAL = 8f;
    /** ...mientras no haya ya 3 en el mapa. */
    public static final int MAX_ON_MAP = 3;

    public final String displayName;
    public final String description;
    /** Segundos que dura el efecto (0 = efecto instantáneo). */
    public final float duration;

    PowerUpType(String displayName, String description, float duration) {
        this.displayName = displayName;
        this.description = description;
        this.duration = duration;
    }

    public boolean isTimed() {
        return duration > 0f;
    }

    public int id() {
        return ordinal();
    }

    public static PowerUpType fromId(int id) {
        PowerUpType[] all = values();
        return (id >= 0 && id < all.length) ? all[id] : HEALTH;
    }
}
