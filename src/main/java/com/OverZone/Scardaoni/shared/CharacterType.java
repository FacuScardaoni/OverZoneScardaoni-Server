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
 * Personajes jugables y sus estadísticas.
 *
 * Cada personaje es una constante del enum con sus propios valores. El
 * servidor los usa para aplicar las reglas (vida, velocidad, daño...) y el
 * cliente para mostrarlos en la sala de espera y dibujar el HUD.
 *
 *            Vida  Velocidad  Cadencia  Daño  Habilidad
 *   Volt      100     140      0,22 s    25   Impulso Over (dash)
 *   Brutus    180     100      0,45 s    40   Escudo Cinético
 */
public enum CharacterType {

    VOLT("Volt", "CyberCore", "Asalto",
        100f, 140f, 0.22f, 25f, 320f, 6f,
        "Impulso Over", "Aceleracion instantanea", 3.0f, 0.18f),

    BRUTUS("Brutus", "Titan Force", "Tanque",
        180f, 100f, 0.45f, 40f, 260f, 10f,
        "Escudo Cinetico", "Escudo frontal que absorbe el 70% del dano", 8.0f, 3.0f);

    /** Parte del daño frontal que absorbe el Escudo Cinético (0,7 = 70 %). */
    public static final float SHIELD_ABSORB = 0.7f;
    /**
     * Qué tan "de frente" tiene que venir un disparo para que el escudo lo
     * frene. Se compara con el coseno del ángulo: 0,4 equivale a un cono de
     * unos 66 grados hacia cada lado de donde apunta Brutus.
     */
    public static final float SHIELD_FRONT_COS = 0.4f;

    public final String displayName;
    public final String faction;
    public final String role;
    public final float maxHealth;
    /** Unidades por segundo. */
    public final float speed;
    /** Segundos entre disparos. */
    public final float fireCooldown;
    public final float damage;
    public final float projectileSpeed;
    public final float projectileSize;
    public final String abilityName;
    public final String abilityDescription;
    /** Segundos hasta poder volver a usar la habilidad. */
    public final float abilityCooldown;
    /** Segundos que dura la habilidad activa. */
    public final float abilityDuration;

    CharacterType(String displayName, String faction, String role,
                  float maxHealth, float speed, float fireCooldown, float damage,
                  float projectileSpeed, float projectileSize,
                  String abilityName, String abilityDescription,
                  float abilityCooldown, float abilityDuration) {
        this.displayName = displayName;
        this.faction = faction;
        this.role = role;
        this.maxHealth = maxHealth;
        this.speed = speed;
        this.fireCooldown = fireCooldown;
        this.damage = damage;
        this.projectileSpeed = projectileSpeed;
        this.projectileSize = projectileSize;
        this.abilityName = abilityName;
        this.abilityDescription = abilityDescription;
        this.abilityCooldown = abilityCooldown;
        this.abilityDuration = abilityDuration;
    }

    /** Número con el que viaja por la red (0 = Volt, 1 = Brutus). */
    public int id() {
        return ordinal();
    }

    /** Convierte el número recibido por la red en un personaje. Si no es válido, Volt. */
    public static CharacterType fromId(int id) {
        CharacterType[] all = values();
        return (id >= 0 && id < all.length) ? all[id] : VOLT;
    }
}
