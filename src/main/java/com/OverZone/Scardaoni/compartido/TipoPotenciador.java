package com.OverZone.Scardaoni.compartido;

public enum TipoPotenciador {
    BOTIQUIN("Botiquín", "+50 de vida", 0f),
    TURBO("Turbo", "+35% velocidad", 6f),
    SOBRECARGA("Sobrecarga", "+50% daño", 6f),
    BLINDAJE("Blindaje", "-50% daño recibido", 6f);

    /** Vida que recupera el Botiquín. */
    public static final float CURACION_BOTIQUIN = 50f;
    /** Multiplicador de velocidad del Turbo. */
    public static final float FACTOR_TURBO = 1.35f;
    /** Multiplicador de daño de la Sobrecarga. */
    public static final float FACTOR_SOBRECARGA = 1.5f;
    /** Parte del daño que se recibe con Blindaje. */
    public static final float FACTOR_BLINDAJE = 0.5f;

    /** Distancia (desde el centro del jugador) a la que se agarra un Power-Up. */
    public static final float RADIO_RECOGIDA = 22f;
    /** Primer Power-Up: a los 5 segundos de empezar la partida. */
    public static final float DEMORA_PRIMERA_APARICION = 5f;
    /** Después, uno nuevo cada 8 segundos... */
    public static final float INTERVALO_APARICION = 8f;
    /** ...mientras no haya ya 3 en el mapa. */
    public static final int MAXIMO_EN_MAPA = 3;

    public final String nombreVisible;
    public final String descripcion;
    /** Segundos que dura el efecto (0 = efecto instantáneo). */
    public final float duracion;

    TipoPotenciador(String nombreVisible, String descripcion, float duracion) {
        this.nombreVisible = nombreVisible;
        this.descripcion = descripcion;
        this.duracion = duracion;
    }

    public boolean esTemporal() {
        return duracion > 0f;
    }

    public int id() {
        return ordinal();
    }

    public static TipoPotenciador desdeId(int id) {
        TipoPotenciador[] todos = values();
        return (id >= 0 && id < todos.length) ? todos[id] : BOTIQUIN;
    }
}
