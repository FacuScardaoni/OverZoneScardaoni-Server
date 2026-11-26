package com.OverZone.Scardaoni.compartido;

public enum TipoPersonaje {
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
    public static final float ABSORCION_ESCUDO = 0.7f;
    /** Brutus se mueve un poco más lento con el escudo activo (75 % de su velocidad). */
    public static final float FACTOR_VELOCIDAD_ESCUDO = 0.75f;
    /** Radio del Campo de Regeneración de Iris. */
    public static final float RADIO_REGENERACION = 56f;
    /** Vida por segundo que recuperan los aliados dentro del campo. */
    public static final float REGENERACION_POR_SEGUNDO = 14f;

    public static final float COS_FRENTE_ESCUDO = 0.4f;

    public final String nombreVisible;
    public final String faccion;
    public final String rol;
    public final float vidaMaxima;
    /** Unidades por segundo. */
    public final float velocidad;
    /** Segundos entre disparos. */
    public final float recargaDisparo;
    public final float danio;
    public final float velocidadProyectil;
    public final float tamanioProyectil;
    /** Distancia máxima que recorre un disparo antes de desaparecer. */
    public final float alcanceProyectil;
    public final String nombreHabilidad;
    public final String descripcionHabilidad;
    /** Segundos hasta poder volver a usar la habilidad. */
    public final float recargaHabilidad;
    /** Segundos que dura la habilidad activa. */
    public final float duracionHabilidad;

    TipoPersonaje(String nombreVisible, String faccion, String rol,
                  float vidaMaxima, float velocidad, float recargaDisparo, float danio,
                  float velocidadProyectil, float tamanioProyectil, float alcanceProyectil,
                  String nombreHabilidad, String descripcionHabilidad,
                  float recargaHabilidad, float duracionHabilidad) {
        this.nombreVisible = nombreVisible;
        this.faccion = faccion;
        this.rol = rol;
        this.vidaMaxima = vidaMaxima;
        this.velocidad = velocidad;
        this.recargaDisparo = recargaDisparo;
        this.danio = danio;
        this.velocidadProyectil = velocidadProyectil;
        this.tamanioProyectil = tamanioProyectil;
        this.alcanceProyectil = alcanceProyectil;
        this.nombreHabilidad = nombreHabilidad;
        this.descripcionHabilidad = descripcionHabilidad;
        this.recargaHabilidad = recargaHabilidad;
        this.duracionHabilidad = duracionHabilidad;
    }

    /** Número con el que viaja por la red (0 = Volt, 1 = Brutus, 2 = Iris, 3 = Phantom). */
    public int id() {
        return ordinal();
    }

    /** Convierte el número recibido por la red en un personaje. Si no es válido, Volt. */
    public static TipoPersonaje desdeId(int id) {
        TipoPersonaje[] todos = values();
        return (id >= 0 && id < todos.length) ? todos[id] : VOLT;
    }
}
