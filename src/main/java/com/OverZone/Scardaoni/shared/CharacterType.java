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
 * Personajes jugables y sus estadísticas, tomados de la propuesta del juego.
 *
 * Cada personaje es una constante del enum con sus propios valores. El
 * servidor los usa para aplicar las reglas (vida, velocidad, daño...) y el
 * cliente para mostrarlos en la sala de espera y dibujar el HUD.
 *
 *            Facción           Rol      Vida  Vel.  Daño  Alcance  Habilidad (recarga)
 *   Volt     CyberCore         Asalto    100   140    25    medio   Impulso Over (10 s)
 *   Brutus   Titan Force       Tanque    180   100    40    medio   Escudo Cinético (15 s)
 *   Iris     Aegis Protocol    Soporte   110   125    15    medio   Campo de Regeneración (20 s)
 *   Phantom  Shadow Syndicate  Asesino    90   170    38    corto   Camuflaje Óptico (18 s)
 */
public enum CharacterType {

    VOLT("Volt", "CyberCore", "Asalto",
        100f, 140f, 0.22f, 25f, 340f, 6f, 330f,
        "Impulso Over", "Aceleración instantánea", 10f, 0.18f),

    BRUTUS("Brutus", "Titan Force", "Tanque",
        180f, 100f, 0.50f, 40f, 260f, 10f, 330f,
        "Escudo Cinético", "Escudo frontal que absorbe el 70% del daño", 15f, 4f),

    IRIS("Iris", "Aegis Protocol", "Soporte",
        110f, 125f, 0.28f, 15f, 300f, 6f, 300f,
        "Campo de Regeneración", "Zona que cura a los aliados", 20f, 5f),

    PHANTOM("Phantom", "Shadow Syndicate", "Asesino",
        90f, 170f, 0.40f, 38f, 520f, 5f, 170f,
        "Camuflaje Óptico", "Casi invisible por unos segundos", 18f, 4f);

    /** Parte del daño frontal que absorbe el Escudo Cinético (0,7 = 70 %). */
    public static final float SHIELD_ABSORB = 0.7f;
    /** Brutus se mueve un poco más lento con el escudo activo (75 % de su velocidad). */
    public static final float SHIELD_SPEED_FACTOR = 0.75f;
    /** Radio del Campo de Regeneración de Iris. */
    public static final float REGEN_RADIUS = 56f;
    /** Vida por segundo que recuperan los aliados dentro del campo. */
    public static final float REGEN_PER_SECOND = 14f;

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
    /** Distancia máxima que recorre un disparo antes de desaparecer. */
    public final float projectileRange;
    public final String abilityName;
    public final String abilityDescription;
    /** Segundos hasta poder volver a usar la habilidad. */
    public final float abilityCooldown;
    /** Segundos que dura la habilidad activa. */
    public final float abilityDuration;

    CharacterType(String displayName, String faction, String role,
                  float maxHealth, float speed, float fireCooldown, float damage,
                  float projectileSpeed, float projectileSize, float projectileRange,
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
        this.projectileRange = projectileRange;
        this.abilityName = abilityName;
        this.abilityDescription = abilityDescription;
        this.abilityCooldown = abilityCooldown;
        this.abilityDuration = abilityDuration;
    }

    /** Número con el que viaja por la red (0 = Volt, 1 = Brutus, 2 = Iris, 3 = Phantom). */
    public int id() {
        return ordinal();
    }

    /** Convierte el número recibido por la red en un personaje. Si no es válido, Volt. */
    public static CharacterType fromId(int id) {
        CharacterType[] all = values();
        return (id >= 0 && id < all.length) ? all[id] : VOLT;
    }
}
