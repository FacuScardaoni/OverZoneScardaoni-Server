/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;


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
 *   STATE;seq;fase;tiempo;puntosAzul;puntosRojo;ganador;motivoFin;modo;anfitrion;JUGADORES;PROYECTILES;ZONAS
 * donde JUGADORES, PROYECTILES y ZONAS son listas separadas por "|" con sus
 * campos separados por ",". Una lista vacía se escribe "-".
 *
 * El campo "team" de cada jugador depende del modo: en Equipos es el equipo
 * (0 azul, 1 rojo); en Todos contra todos es un color propio (0 a 3).
 *
 * Los objetos de esta clase no cambian después de creados: así el hilo de red
 * puede armarlos y el hilo de dibujo leerlos sin pisarse.
 */
public class WorldSnapshot {

    /** Datos de un jugador, tal como los ven los clientes. */
    public static class PlayerState {
        public final int id;
        public final String name;
        public final int team;
        /** Personaje elegido (CharacterType.id()). */
        public final int character;
        public final float x, y;
        public final int facing;
        public final boolean moving;
        public final boolean alive;
        public final float health;
        /** Habilidad en uso: Impulso (Volt) o Escudo (Brutus). */
        public final boolean abilityActive;
        /** Hacia dónde apunta el escudo, en grados (0 = derecha, 90 = arriba). */
        public final float shieldAngle;
        public final boolean invulnerable;
        public final boolean ready;
        public final int kills, deaths;
        /** 0 = habilidad lista, 1 = recién usada. */
        public final float abilityCooldown;
        public final float respawnIn;

        public PlayerState(int id, String name, int team, int character, float x, float y, int facing,
                           boolean moving, boolean alive, float health, boolean abilityActive,
                           float shieldAngle, boolean invulnerable, boolean ready, int kills, int deaths,
                           float abilityCooldown, float respawnIn) {
            this.id = id;
            this.name = name;
            this.team = team;
            this.character = character;
            this.x = x;
            this.y = y;
            this.facing = facing;
            this.moving = moving;
            this.alive = alive;
            this.health = health;
            this.abilityActive = abilityActive;
            this.shieldAngle = shieldAngle;
            this.invulnerable = invulnerable;
            this.ready = ready;
            this.kills = kills;
            this.deaths = deaths;
            this.abilityCooldown = abilityCooldown;
            this.respawnIn = respawnIn;
        }

        String encode() {
            return id + "," + name + "," + team + "," + character + "," + Protocol.f(x) + "," + Protocol.f(y) + ","
                + facing + "," + b(moving) + "," + b(alive) + "," + Protocol.f(health) + ","
                + b(abilityActive) + "," + Math.round(shieldAngle) + "," + b(invulnerable) + "," + b(ready) + ","
                + kills + "," + deaths + "," + Protocol.f(abilityCooldown) + "," + Protocol.f(respawnIn);
        }

        static PlayerState decode(String s) {
            String[] f = s.split(",");
            return new PlayerState(
                Integer.parseInt(f[0]), f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                Float.parseFloat(f[4]), Float.parseFloat(f[5]), Integer.parseInt(f[6]),
                bool(f[7]), bool(f[8]), Float.parseFloat(f[9]),
                bool(f[10]), Float.parseFloat(f[11]), bool(f[12]), bool(f[13]),
                Integer.parseInt(f[14]), Integer.parseInt(f[15]),
                Float.parseFloat(f[16]), Float.parseFloat(f[17]));
        }
    }

    /** Datos de un proyectil, tal como los ven los clientes. */
    public static class ProjectileState {
        public final int id;
        public final int team;
        /** Personaje que lo disparó (define su tamaño y su aspecto). */
        public final int character;
        public final float x, y;

        public ProjectileState(int id, int team, int character, float x, float y) {
            this.id = id;
            this.team = team;
            this.character = character;
            this.x = x;
            this.y = y;
        }

        String encode() {
            return id + "," + team + "," + character + "," + Protocol.f(x) + "," + Protocol.f(y);
        }

        static ProjectileState decode(String s) {
            String[] f = s.split(",");
            return new ProjectileState(Integer.parseInt(f[0]), Integer.parseInt(f[1]),
                Integer.parseInt(f[2]), Float.parseFloat(f[3]), Float.parseFloat(f[4]));
        }
    }

    /** Campo de Regeneración de Iris: un círculo que cura a los aliados que están adentro. */
    public static class ZoneState {
        public final int id;
        public final int ownerId;
        public final int team;
        public final float x, y, radius;
        /** Segundos que le quedan. */
        public final float remaining;

        public ZoneState(int id, int ownerId, int team, float x, float y, float radius, float remaining) {
            this.id = id;
            this.ownerId = ownerId;
            this.team = team;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.remaining = remaining;
        }

        String encode() {
            return id + "," + ownerId + "," + team + "," + Protocol.f(x) + "," + Protocol.f(y) + ","
                + Protocol.f(radius) + "," + Protocol.f(remaining);
        }

        static ZoneState decode(String s) {
            String[] f = s.split(",");
            return new ZoneState(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                Float.parseFloat(f[3]), Float.parseFloat(f[4]), Float.parseFloat(f[5]), Float.parseFloat(f[6]));
        }
    }

    public final long seq;
    public final MatchPhase phase;
    /** Según la fase: segundos de cuenta regresiva, de partida o de resultados. */
    public final float phaseTimer;
    public final int scoreBlue, scoreRed;
    /**
     * Ganador. En Equipos: Team.BLUE o Team.RED. En Todos contra todos: el id
     * del jugador ganador. En los dos: -1 si hubo empate o no terminó.
     */
    public final int winner;
    /** Por qué terminó la partida: TIME (tiempo agotado) o PLAYERS (se fueron jugadores). */
    public final String endReason;
    /** Modo de juego (GameMode.id()). */
    public final int mode;
    /** Id del anfitrión de la sala (el que elige el modo). */
    public final int hostId;
    public final List<PlayerState> players;
    public final List<ProjectileState> projectiles;
    public final List<ZoneState> zones;
    /** Momento en que el cliente recibió esta foto (System.nanoTime). Lo completa el cliente. */
    public final long receivedNanos;

    public WorldSnapshot(long seq, MatchPhase phase, float phaseTimer, int scoreBlue, int scoreRed,
                         int winner, String endReason, int mode, int hostId, List<PlayerState> players,
                         List<ProjectileState> projectiles, List<ZoneState> zones, long receivedNanos) {
        this.seq = seq;
        this.phase = phase;
        this.phaseTimer = phaseTimer;
        this.scoreBlue = scoreBlue;
        this.scoreRed = scoreRed;
        this.winner = winner;
        this.endReason = endReason;
        this.mode = mode;
        this.hostId = hostId;
        this.players = Collections.unmodifiableList(players);
        this.projectiles = Collections.unmodifiableList(projectiles);
        this.zones = Collections.unmodifiableList(zones);
        this.receivedNanos = receivedNanos;
    }

    public PlayerState findPlayer(int id) {
        for (PlayerState p : players) {
            if (p.id == id) return p;
        }
        return null;
    }

    public ProjectileState findProjectile(int id) {
        for (ProjectileState p : projectiles) {
            if (p.id == id) return p;
        }
        return null;
    }

    public GameMode getMode() {
        return GameMode.fromId(mode);
    }

    /** ¿Son rivales estos dos jugadores? En Todos contra todos, todos lo son. */
    public boolean areEnemies(PlayerState a, PlayerState b) {
        if (a.id == b.id) return false;
        return getMode() == GameMode.FREE_FOR_ALL || a.team != b.team;
    }

    public int countTeam(int team) {
        int n = 0;
        for (PlayerState p : players) {
            if (p.team == team) n++;
        }
        return n;
    }

    public int getScore(int team) {
        if (team == Team.BLUE) return scoreBlue;
        if (team == Team.RED) return scoreRed;
        return 0;
    }

    public String toMessage() {
        StringBuilder sb = new StringBuilder(512);
        sb.append(Protocol.STATE).append(';').append(seq).append(';').append(phase.name()).append(';')
            .append(Protocol.f(phaseTimer)).append(';').append(scoreBlue).append(';').append(scoreRed).append(';')
            .append(winner).append(';').append(endReason).append(';')
            .append(mode).append(';').append(hostId).append(';');
        appendList(sb, players);
        sb.append(';');
        appendList(sb, projectiles);
        sb.append(';');
        appendList(sb, zones);
        return sb.toString();
    }

    private static void appendList(StringBuilder sb, List<?> items) {
        if (items.isEmpty()) {
            sb.append('-');
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append('|');
            Object item = items.get(i);
            if (item instanceof PlayerState) sb.append(((PlayerState) item).encode());
            else if (item instanceof ProjectileState) sb.append(((ProjectileState) item).encode());
            else sb.append(((ZoneState) item).encode());
        }
    }

    /** Lee un mensaje STATE. Lanza una excepción si el mensaje está mal formado. */
    public static WorldSnapshot parse(String message, long receivedNanos) {
        // El -1 conserva los campos vacíos del final en lugar de descartarlos.
        String[] parts = message.split(";", -1);
        List<PlayerState> players = new ArrayList<>();
        if (!parts[10].equals("-")) {
            for (String s : parts[10].split("\\|")) players.add(PlayerState.decode(s));
        }
        List<ProjectileState> projectiles = new ArrayList<>();
        if (!parts[11].equals("-")) {
            for (String s : parts[11].split("\\|")) projectiles.add(ProjectileState.decode(s));
        }
        List<ZoneState> zones = new ArrayList<>();
        if (!parts[12].equals("-")) {
            for (String s : parts[12].split("\\|")) zones.add(ZoneState.decode(s));
        }
        return new WorldSnapshot(
            Long.parseLong(parts[1]),
            MatchPhase.valueOf(parts[2]),
            Float.parseFloat(parts[3]),
            Integer.parseInt(parts[4]),
            Integer.parseInt(parts[5]),
            Integer.parseInt(parts[6]),
            parts[7],
            Integer.parseInt(parts[8]),
            Integer.parseInt(parts[9]),
            players, projectiles, zones, receivedNanos);
    }

    private static String b(boolean v) { return v ? "1" : "0"; }
    private static boolean bool(String s) { return "1".equals(s); }
}
