package com.OverZone.Scardaoni.servidor.juego;

import com.OverZone.Scardaoni.compartido.TipoPersonaje;
import com.OverZone.Scardaoni.compartido.TipoPotenciador;
import com.OverZone.Scardaoni.compartido.Direccion;
import com.OverZone.Scardaoni.compartido.ConstantesJuego;
import com.badlogic.gdx.math.Rectangle;

/**
 * Jugador de la partida. Solo existe en el SERVIDOR.
 *
 * Guarda el estado real del jugador y aplica sus reglas: daño, muerte,
 * reaparición y la habilidad de su personaje. Las estadísticas (vida,
 * velocidad, daño, habilidad) salen de su TipoPersonaje, así el mismo código
 * sirve para los cuatro personajes:
 *   - Volt:    Impulso Over (sale disparado hacia donde camina).
 *   - Brutus:  Escudo Cinético (frena el 70 % del daño que viene de frente).
 *   - Iris:    Campo de Regeneración (la zona la crea y la maneja SimulacionPartida).
 *   - Phantom: Camuflaje Óptico (casi invisible; se corta si dispara).
 */
public class Jugador {

    public static final float TAMANIO = ConstantesJuego.TAMANIO_JUGADOR;

    public static final float MULTIPLICADOR_IMPULSO = 3.2f;
    public static final float INVULNERABILIDAD_GOLPE = 0.4f;
    public static final float INVULNERABILIDAD_APARICION = 1.5f;
    public static final float DEMORA_REAPARICION = 3.0f;

    private final int id;
    private final String nombre;
    private int equipo;
    private TipoPersonaje personaje = TipoPersonaje.VOLT;

    private float x, y;
    private float vida;
    private boolean vivo = true;
    private float reaparicionRestante = 0f;

    private int direccionMirando = Direccion.ABAJO;
    private boolean moviendose = false;

    /** Tiempo que le queda a la habilidad activa (impulso, escudo, campo o camuflaje). */
    private float tiempoHabilidadRestante = 0f;
    private float recargaHabilidadRestante = 0f;

    // Impulso Over (Volt)
    private float impulsoDirX, impulsoDirY;
    // Escudo Cinético (Brutus): hacia dónde apunta, con largo 1
    private float escudoDirX = 0f, escudoDirY = -1f;

    /** Segundos que le quedan a cada Power-Up temporal (en el orden de TipoPotenciador). */
    private final float[] potenciadores = new float[TipoPotenciador.values().length];

    private float invulnerabilidadRestante = 0f;

    private int eliminaciones = 0;
    private int muertes = 0;

    public Jugador(int id, String nombre, int equipo, float x, float y) {
        this.id = id;
        this.nombre = nombre;
        this.equipo = equipo;
        this.x = x;
        this.y = y;
        this.vida = personaje.vidaMaxima;
    }

    public Rectangle getLimites() {
        return new Rectangle(x - TAMANIO / 2f, y - TAMANIO / 2f, TAMANIO, TAMANIO);
    }

    /** Descuenta los temporizadores. No mueve al jugador. */
    public void actualizar(float delta) {
        if (!vivo) {
            reaparicionRestante -= delta;
            return;
        }
        if (tiempoHabilidadRestante > 0) tiempoHabilidadRestante -= delta;
        if (recargaHabilidadRestante > 0) recargaHabilidadRestante -= delta;
        if (invulnerabilidadRestante > 0) invulnerabilidadRestante -= delta;
        for (int i = 0; i < potenciadores.length; i++) {
            if (potenciadores[i] > 0) potenciadores[i] = Math.max(0f, potenciadores[i] - delta);
        }
    }

    // ------------------------------------------------------------------
    // Power-Ups
    // ------------------------------------------------------------------

    /**
     * ¿Le sirve este Power-Up? El Botiquín solo se agarra si falta vida, así
     * no se desperdicia pasando por arriba con la vida llena.
     */
    public boolean puedeRecoger(TipoPotenciador tipo) {
        if (!vivo) return false;
        return tipo != TipoPotenciador.BOTIQUIN || vida < personaje.vidaMaxima;
    }

    /** Aplica el efecto de un Power-Up. Si ya estaba activo, se reinicia su tiempo. */
    public void aplicarPotenciador(TipoPotenciador tipo) {
        if (tipo == TipoPotenciador.BOTIQUIN) curar(TipoPotenciador.CURACION_BOTIQUIN);
        else potenciadores[tipo.id()] = tipo.duracion;
    }

    public boolean tienePotenciador(TipoPotenciador tipo) {
        return potenciadores[tipo.id()] > 0f;
    }

    /** Copia de los tiempos que le quedan a cada Power-Up (para el estado). */
    public float[] getPotenciadores() {
        return potenciadores.clone();
    }

    /** Multiplicador de daño de los disparos (Sobrecarga). */
    public float getMultiplicadorDanio() {
        return tienePotenciador(TipoPotenciador.SOBRECARGA) ? TipoPotenciador.FACTOR_SOBRECARGA : 1f;
    }

    private void borrarPotenciadores() {
        java.util.Arrays.fill(potenciadores, 0f);
    }

    // ------------------------------------------------------------------
    // Habilidades
    // ------------------------------------------------------------------

    /**
     * Intenta usar la habilidad del personaje en la dirección indicada.
     * Devuelve true si se activó (no estaba recargando).
     */
    public boolean intentarUsarHabilidad(float dirX, float dirY) {
        if (!vivo || recargaHabilidadRestante > 0f || habilidadEstaActiva()) return false;
        float largo = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        boolean necesitaDireccion = personaje == TipoPersonaje.VOLT || personaje == TipoPersonaje.BRUTUS;
        if (necesitaDireccion && largo == 0) return false;

        tiempoHabilidadRestante = personaje.duracionHabilidad;
        recargaHabilidadRestante = personaje.recargaHabilidad;
        if (personaje == TipoPersonaje.VOLT) {
            impulsoDirX = dirX / largo;
            impulsoDirY = dirY / largo;
        } else if (personaje == TipoPersonaje.BRUTUS) {
            setDireccionEscudo(dirX, dirY);
        }
        // Iris y Phantom no necesitan dirección: el campo se crea donde está
        // Iris (lo hace SimulacionPartida) y el camuflaje solo dura un tiempo.
        return true;
    }

    public boolean habilidadEstaActiva() {
        return tiempoHabilidadRestante > 0f;
    }

    public boolean estaImpulsando() {
        return personaje == TipoPersonaje.VOLT && habilidadEstaActiva();
    }

    public boolean escudoEstaActivo() {
        return personaje == TipoPersonaje.BRUTUS && habilidadEstaActiva();
    }

    public boolean estaCamuflado() {
        return personaje == TipoPersonaje.PHANTOM && habilidadEstaActiva();
    }

    /** Phantom se hace visible de nuevo (por ejemplo, al disparar). */
    public void cortarCamuflaje() {
        if (estaCamuflado()) tiempoHabilidadRestante = 0f;
    }

    /** Recupera vida sin pasarse del máximo del personaje. */
    public void curar(float cantidadVida) {
        if (!vivo) return;
        vida = Math.min(personaje.vidaMaxima, vida + cantidadVida);
    }

    /** El escudo sigue hacia donde apunta Brutus mientras está activo. */
    public void setDireccionEscudo(float dirX, float dirY) {
        float largo = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (largo == 0) return;
        escudoDirX = dirX / largo;
        escudoDirY = dirY / largo;
    }

    /** Ángulo del escudo en grados (0 = derecha, 90 = arriba). */
    public float getAnguloEscudo() {
        return (float) Math.toDegrees(Math.atan2(escudoDirY, escudoDirX));
    }

    public float getVelocidadActual() {
        float velocidad = personaje.velocidad;
        if (estaImpulsando()) velocidad *= MULTIPLICADOR_IMPULSO;
        // Con el escudo levantado, Brutus camina un poco más lento.
        else if (escudoEstaActivo()) velocidad *= TipoPersonaje.FACTOR_VELOCIDAD_ESCUDO;
        // Power-Up Turbo
        if (tienePotenciador(TipoPotenciador.TURBO)) velocidad *= TipoPotenciador.FACTOR_TURBO;
        return velocidad;
    }

    public float getImpulsoDirX() { return impulsoDirX; }
    public float getImpulsoDirY() { return impulsoDirY; }

    // ------------------------------------------------------------------
    // Daño
    // ------------------------------------------------------------------

    /**
     * Aplica daño de un proyectil que viaja en la dirección (proyectilDirX, proyectilDirY).
     * Si Brutus tiene el escudo activo y el disparo viene de frente, el escudo
     * absorbe parte del daño. Devuelve true si este golpe eliminó al jugador.
     */
    public boolean recibirDanio(float cantidadVida, float proyectilDirX, float proyectilDirY) {
        if (!vivo || invulnerabilidadRestante > 0) return false;

        if (escudoEstaActivo()) {
            // El proyectil viene "de frente" si viaja en sentido contrario a
            // donde apunta el escudo. El producto escalar entre las dos
            // direcciones es -1 si son opuestas y +1 si van para el mismo lado.
            float producto = proyectilDirX * escudoDirX + proyectilDirY * escudoDirY;
            if (producto <= -TipoPersonaje.COS_FRENTE_ESCUDO) {
                cantidadVida *= (1f - TipoPersonaje.ABSORCION_ESCUDO);
            }
        }

        // Power-Up Blindaje: se recibe la mitad del daño.
        if (tienePotenciador(TipoPotenciador.BLINDAJE)) cantidadVida *= TipoPotenciador.FACTOR_BLINDAJE;

        vida -= cantidadVida;
        invulnerabilidadRestante = INVULNERABILIDAD_GOLPE;
        if (vida <= 0) {
            vida = 0;
            vivo = false;
            moviendose = false;
            tiempoHabilidadRestante = 0;
            reaparicionRestante = DEMORA_REAPARICION;
            borrarPotenciadores(); // al morir se pierden los Power-Ups
            muertes++;
            return true;
        }
        return false;
    }

    public boolean puedeReaparecer() {
        return !vivo && reaparicionRestante <= 0f;
    }

    /** Vuelve a la partida en la posición indicada, con vida completa. */
    public void reaparecer(float nuevoX, float nuevoY) {
        x = nuevoX;
        y = nuevoY;
        vida = personaje.vidaMaxima;
        vivo = true;
        reaparicionRestante = 0f;
        tiempoHabilidadRestante = 0f;
        recargaHabilidadRestante = 0f;
        invulnerabilidadRestante = INVULNERABILIDAD_APARICION;
        borrarPotenciadores();
    }

    /** Deja al jugador como al principio de una partida nueva. */
    public void reiniciarParaPartida(float nuevoX, float nuevoY) {
        reaparecer(nuevoX, nuevoY);
        eliminaciones = 0;
        muertes = 0;
    }

    public void sumarEliminacion() { eliminaciones++; }

    public boolean estaVivo() { return vivo; }
    public boolean esInvulnerable() { return invulnerabilidadRestante > 0f; }

    public float getProgresoRecargaHabilidad() {
        // 0 = lista para usar, 1 = recién usada
        return Math.max(0f, recargaHabilidadRestante / personaje.recargaHabilidad);
    }

    public float getReaparicionRestante() { return Math.max(0f, reaparicionRestante); }

    public TipoPersonaje getPersonaje() { return personaje; }

    /** Solo se usa en la sala de espera. Cambia el personaje y repone la vida. */
    public void setPersonaje(TipoPersonaje personaje) {
        this.personaje = personaje;
        this.vida = personaje.vidaMaxima;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public int getEquipo() { return equipo; }
    /** Solo se usa en la sala de espera, al reacomodar los equipos. */
    public void setEquipo(int equipo) { this.equipo = equipo; }
    public int getEliminaciones() { return eliminaciones; }
    public int getMuertes() { return muertes; }
    public float getX() { return x; }
    public float getY() { return y; }
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public float getVida() { return vida; }
    public int getDireccionMirando() { return direccionMirando; }
    public void setDireccionMirando(int direccionMirando) { this.direccionMirando = direccionMirando; }
    public boolean seEstaMoviendo() { return moviendose; }
    public void setMoviendose(boolean moviendose) { this.moviendose = moviendose; }
}
