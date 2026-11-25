/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocolo.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.compartido;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Foto" completa de la partida en un instante, armada por el servidor.
 *
 * El servidor envía una de estas unas 30 veces por segundo. Cada foto trae
 * TODO el estado (no solo lo que cambió): si una se pierde en la red, la
 * siguiente la reemplaza por completo y el cliente se pone al día solo.
 *
 * Formato del mensaje:
 *   STATE;seq;fase;tiempo;puntosAzul;puntosRojo;ganador;motivoFin;modo;anfitrion;JUGADORES;PROYECTILES;ZONAS;POWERUPS
 * donde JUGADORES, PROYECTILES, ZONAS y POWERUPS son listas separadas por "|" con sus
 * campos separados por ",". Una lista vacía se escribe "-".
 *
 * El campo "team" de cada jugador depende del modo: en Equipos es el equipo
 * (0 azul, 1 rojo); en Todos contra todos es un color propio (0 a 3).
 *
 * Los objetos de esta clase no cambian después de creados: así el hilo de red
 * puede armarlos y el hilo de dibujo leerlos sin pisarse.
 */
public class EstadoMundo {

    /** Datos de un jugador, tal como los ven los clientes. */
    public static class EstadoJugador {
        public final int id;
        public final String nombre;
        public final int equipo;
        /** Personaje elegido (TipoPersonaje.id()). */
        public final int personaje;
        public final float x, y;
        public final int mirando;
        public final boolean moviendose;
        public final boolean vivo;
        public final float vida;
        /** Habilidad en uso: Impulso (Volt) o Escudo (Brutus). */
        public final boolean habilidadActiva;
        /** Hacia dónde apunta el escudo, en grados (0 = derecha, 90 = arriba). */
        public final float anguloEscudo;
        public final boolean invulnerable;
        public final boolean listo;
        public final int eliminaciones, muertes;
        /** 0 = habilidad lista, 1 = recién usada. */
        public final float recargaHabilidad;
        public final float reapareceEn;
        /**
         * Segundos que le quedan a cada Power-Up temporal, en el orden de
         * TipoPotenciador (el Botiquín es instantáneo y siempre vale 0).
         * Viajan en un solo campo separados por ":" (ej.: "0:3.5:0:0").
         */
        public final float[] potenciadores;

        public EstadoJugador(int id, String nombre, int equipo, int personaje, float x, float y, int mirando,
                           boolean moviendose, boolean vivo, float vida, boolean habilidadActiva,
                           float anguloEscudo, boolean invulnerable, boolean listo, int eliminaciones, int muertes,
                           float recargaHabilidad, float reapareceEn, float[] potenciadores) {
            this.id = id;
            this.nombre = nombre;
            this.equipo = equipo;
            this.personaje = personaje;
            this.x = x;
            this.y = y;
            this.mirando = mirando;
            this.moviendose = moviendose;
            this.vivo = vivo;
            this.vida = vida;
            this.habilidadActiva = habilidadActiva;
            this.anguloEscudo = anguloEscudo;
            this.invulnerable = invulnerable;
            this.listo = listo;
            this.eliminaciones = eliminaciones;
            this.muertes = muertes;
            this.recargaHabilidad = recargaHabilidad;
            this.reapareceEn = reapareceEn;
            this.potenciadores = potenciadores;
        }

        /** Segundos que le quedan a ese Power-Up (0 si no está activo). */
        public float potenciador(TipoPotenciador tipo) {
            return tipo.id() < potenciadores.length ? potenciadores[tipo.id()] : 0f;
        }

        public boolean tieneAlgunPotenciador() {
            for (float b : potenciadores) if (b > 0f) return true;
            return false;
        }

        String codificar() {
            return id + "," + nombre + "," + equipo + "," + personaje + "," + Protocolo.f(x) + "," + Protocolo.f(y) + ","
                + mirando + "," + b(moviendose) + "," + b(vivo) + "," + Protocolo.f(vida) + ","
                + b(habilidadActiva) + "," + Math.round(anguloEscudo) + "," + b(invulnerable) + "," + b(listo) + ","
                + eliminaciones + "," + muertes + "," + Protocolo.f(recargaHabilidad) + "," + Protocolo.f(reapareceEn) + ","
                + codificarPotenciadores(potenciadores);
        }

        static EstadoJugador decodificar(String s) {
            String[] f = s.split(",");
            return new EstadoJugador(
                Integer.parseInt(f[0]), f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                Float.parseFloat(f[4]), Float.parseFloat(f[5]), Integer.parseInt(f[6]),
                booleano(f[7]), booleano(f[8]), Float.parseFloat(f[9]),
                booleano(f[10]), Float.parseFloat(f[11]), booleano(f[12]), booleano(f[13]),
                Integer.parseInt(f[14]), Integer.parseInt(f[15]),
                Float.parseFloat(f[16]), Float.parseFloat(f[17]), decodificarPotenciadores(f[18]));
        }

        private static String codificarPotenciadores(float[] potenciadores) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < potenciadores.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(Protocolo.f(potenciadores[i]));
            }
            return sb.toString();
        }

        private static float[] decodificarPotenciadores(String s) {
            String[] partes = s.split(":");
            float[] potenciadores = new float[TipoPotenciador.values().length];
            for (int i = 0; i < partes.length && i < potenciadores.length; i++) potenciadores[i] = Float.parseFloat(partes[i]);
            return potenciadores;
        }
    }

    /** Datos de un proyectil, tal como los ven los clientes. */
    public static class EstadoProyectil {
        public final int id;
        public final int equipo;
        /** Personaje que lo disparó (define su tamaño y su aspecto). */
        public final int personaje;
        public final float x, y;

        public EstadoProyectil(int id, int equipo, int personaje, float x, float y) {
            this.id = id;
            this.equipo = equipo;
            this.personaje = personaje;
            this.x = x;
            this.y = y;
        }

        String codificar() {
            return id + "," + equipo + "," + personaje + "," + Protocolo.f(x) + "," + Protocolo.f(y);
        }

        static EstadoProyectil decodificar(String s) {
            String[] f = s.split(",");
            return new EstadoProyectil(Integer.parseInt(f[0]), Integer.parseInt(f[1]),
                Integer.parseInt(f[2]), Float.parseFloat(f[3]), Float.parseFloat(f[4]));
        }
    }

    /** Campo de Regeneración de Iris: un círculo que cura a los aliados que están adentro. */
    public static class EstadoZona {
        public final int id;
        public final int duenioId;
        public final int equipo;
        public final float x, y, radio;
        /** Segundos que le quedan. */
        public final float restante;

        public EstadoZona(int id, int duenioId, int equipo, float x, float y, float radio, float restante) {
            this.id = id;
            this.duenioId = duenioId;
            this.equipo = equipo;
            this.x = x;
            this.y = y;
            this.radio = radio;
            this.restante = restante;
        }

        String codificar() {
            return id + "," + duenioId + "," + equipo + "," + Protocolo.f(x) + "," + Protocolo.f(y) + ","
                + Protocolo.f(radio) + "," + Protocolo.f(restante);
        }

        static EstadoZona decodificar(String s) {
            String[] f = s.split(",");
            return new EstadoZona(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                Float.parseFloat(f[3]), Float.parseFloat(f[4]), Float.parseFloat(f[5]), Float.parseFloat(f[6]));
        }
    }

    /** Power-Up tirado en el mapa, esperando que alguien lo agarre. */
    public static class EstadoPotenciador {
        public final int id;
        public final int tipo;
        public final float x, y;

        public EstadoPotenciador(int id, int tipo, float x, float y) {
            this.id = id;
            this.tipo = tipo;
            this.x = x;
            this.y = y;
        }

        public TipoPotenciador getTipo() {
            return TipoPotenciador.desdeId(tipo);
        }

        String codificar() {
            return id + "," + tipo + "," + Protocolo.f(x) + "," + Protocolo.f(y);
        }

        static EstadoPotenciador decodificar(String s) {
            String[] f = s.split(",");
            return new EstadoPotenciador(Integer.parseInt(f[0]), Integer.parseInt(f[1]),
                Float.parseFloat(f[2]), Float.parseFloat(f[3]));
        }
    }

    public final long secuencia;
    public final FasePartida fase;
    /** Según la fase: segundos de cuenta regresiva, de partida o de resultados. */
    public final float tiempoFase;
    public final int puntosAzul, puntosRojo;
    /**
     * Ganador. En Equipos: Equipo.AZUL o Equipo.ROJO. En Todos contra todos: el id
     * del jugador ganador. En los dos: -1 si hubo empate o no terminó.
     */
    public final int ganador;
    /** Por qué terminó la partida: TIME (tiempo agotado) o PLAYERS (se fueron jugadores). */
    public final String motivoFin;
    /** Modo de juego (ModoJuego.id()). */
    public final int modo;
    /** Id del anfitrión de la sala (el que elige el modo). */
    public final int anfitrionId;
    public final List<EstadoJugador> jugadores;
    public final List<EstadoProyectil> proyectiles;
    public final List<EstadoZona> zonas;
    public final List<EstadoPotenciador> potenciadoresMapa;
    /** Momento en que el cliente recibió esta foto (System.nanoTime). Lo completa el cliente. */
    public final long recibidoNanos;

    public EstadoMundo(long secuencia, FasePartida fase, float tiempoFase, int puntosAzul, int puntosRojo,
                         int ganador, String motivoFin, int modo, int anfitrionId, List<EstadoJugador> jugadores,
                         List<EstadoProyectil> proyectiles, List<EstadoZona> zonas,
                         List<EstadoPotenciador> potenciadoresMapa, long recibidoNanos) {
        this.secuencia = secuencia;
        this.fase = fase;
        this.tiempoFase = tiempoFase;
        this.puntosAzul = puntosAzul;
        this.puntosRojo = puntosRojo;
        this.ganador = ganador;
        this.motivoFin = motivoFin;
        this.modo = modo;
        this.anfitrionId = anfitrionId;
        this.jugadores = Collections.unmodifiableList(jugadores);
        this.proyectiles = Collections.unmodifiableList(proyectiles);
        this.zonas = Collections.unmodifiableList(zonas);
        this.potenciadoresMapa = Collections.unmodifiableList(potenciadoresMapa);
        this.recibidoNanos = recibidoNanos;
    }

    public EstadoJugador buscarJugador(int id) {
        for (EstadoJugador p : jugadores) {
            if (p.id == id) return p;
        }
        return null;
    }

    public EstadoProyectil buscarProyectil(int id) {
        for (EstadoProyectil p : proyectiles) {
            if (p.id == id) return p;
        }
        return null;
    }

    public EstadoPotenciador buscarPotenciador(int id) {
        for (EstadoPotenciador p : potenciadoresMapa) {
            if (p.id == id) return p;
        }
        return null;
    }

    public ModoJuego getModo() {
        return ModoJuego.desdeId(modo);
    }

    /** ¿Son rivales estos dos jugadores? En Todos contra todos, todos lo son. */
    public boolean sonRivales(EstadoJugador a, EstadoJugador b) {
        if (a.id == b.id) return false;
        return getModo() == ModoJuego.TODOS_CONTRA_TODOS || a.equipo != b.equipo;
    }

    public int contarEquipo(int equipo) {
        int n = 0;
        for (EstadoJugador p : jugadores) {
            if (p.equipo == equipo) n++;
        }
        return n;
    }

    public int getPuntos(int equipo) {
        if (equipo == Equipo.AZUL) return puntosAzul;
        if (equipo == Equipo.ROJO) return puntosRojo;
        return 0;
    }

    public String aMensaje() {
        StringBuilder sb = new StringBuilder(512);
        sb.append(Protocolo.STATE).append(';').append(secuencia).append(';').append(fase.name()).append(';')
            .append(Protocolo.f(tiempoFase)).append(';').append(puntosAzul).append(';').append(puntosRojo).append(';')
            .append(ganador).append(';').append(motivoFin).append(';')
            .append(modo).append(';').append(anfitrionId).append(';');
        agregarLista(sb, jugadores);
        sb.append(';');
        agregarLista(sb, proyectiles);
        sb.append(';');
        agregarLista(sb, zonas);
        sb.append(';');
        agregarLista(sb, potenciadoresMapa);
        return sb.toString();
    }

    private static void agregarLista(StringBuilder sb, List<?> elementos) {
        if (elementos.isEmpty()) {
            sb.append('-');
            return;
        }
        for (int i = 0; i < elementos.size(); i++) {
            if (i > 0) sb.append('|');
            Object elemento = elementos.get(i);
            if (elemento instanceof EstadoJugador) sb.append(((EstadoJugador) elemento).codificar());
            else if (elemento instanceof EstadoProyectil) sb.append(((EstadoProyectil) elemento).codificar());
            else if (elemento instanceof EstadoZona) sb.append(((EstadoZona) elemento).codificar());
            else sb.append(((EstadoPotenciador) elemento).codificar());
        }
    }

    /** Lee un mensaje STATE. Lanza una excepción si el mensaje está mal formado. */
    public static EstadoMundo interpretar(String mensaje, long recibidoNanos) {
        // El -1 conserva los campos vacíos del final en lugar de descartarlos.
        String[] partes = mensaje.split(";", -1);
        List<EstadoJugador> jugadores = new ArrayList<>();
        if (!partes[10].equals("-")) {
            for (String s : partes[10].split("\\|")) jugadores.add(EstadoJugador.decodificar(s));
        }
        List<EstadoProyectil> proyectiles = new ArrayList<>();
        if (!partes[11].equals("-")) {
            for (String s : partes[11].split("\\|")) proyectiles.add(EstadoProyectil.decodificar(s));
        }
        List<EstadoZona> zonas = new ArrayList<>();
        if (!partes[12].equals("-")) {
            for (String s : partes[12].split("\\|")) zonas.add(EstadoZona.decodificar(s));
        }
        List<EstadoPotenciador> potenciadoresMapa = new ArrayList<>();
        if (!partes[13].equals("-")) {
            for (String s : partes[13].split("\\|")) potenciadoresMapa.add(EstadoPotenciador.decodificar(s));
        }
        return new EstadoMundo(
            Long.parseLong(partes[1]),
            FasePartida.valueOf(partes[2]),
            Float.parseFloat(partes[3]),
            Integer.parseInt(partes[4]),
            Integer.parseInt(partes[5]),
            Integer.parseInt(partes[6]),
            partes[7],
            Integer.parseInt(partes[8]),
            Integer.parseInt(partes[9]),
            jugadores, proyectiles, zonas, potenciadoresMapa, recibidoNanos);
    }

    private static String b(boolean v) { return v ? "1" : "0"; }
    private static boolean booleano(String s) { return "1".equals(s); }
}
