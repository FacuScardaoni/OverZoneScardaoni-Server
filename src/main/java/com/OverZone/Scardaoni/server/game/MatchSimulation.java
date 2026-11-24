package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.Direction;
import com.OverZone.Scardaoni.shared.CharacterType;
import com.OverZone.Scardaoni.shared.GameConstants;
import com.OverZone.Scardaoni.shared.GameMode;
import com.OverZone.Scardaoni.shared.MatchPhase;
import com.OverZone.Scardaoni.shared.Team;
import com.OverZone.Scardaoni.shared.PlayerInput;
import com.OverZone.Scardaoni.shared.PowerUpType;
import com.OverZone.Scardaoni.shared.Protocol;
import com.OverZone.Scardaoni.shared.WorldSnapshot;
import com.OverZone.Scardaoni.shared.Arena;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * La partida real. Solo existe en el SERVIDOR.
 *
 * Contiene la lógica que antes estaba en GameScreen (movimiento con
 * colisiones, habilidades, disparos, daño), aplicada a hasta 4 jugadores,
 * con eliminaciones, reapariciones, etapas de partida y dos modos de juego:
 *
 *   - Todos contra todos: cada uno juega solo. El campo "team" de cada
 *     jugador es su color (0 a 3). Gana el que tenga más eliminaciones.
 *   - Equipos 2v2: azul contra rojo. Solo con exactamente 4 jugadores.
 *
 * El modo lo elige el anfitrión (el primero de la lista de jugadores). El
 * servidor decide si el pedido es válido: con menos de 4 jugadores no deja
 * elegir Equipos.
 *
 * No sabe nada de la red ni de la pantalla: recibe entradas (applyInput),
 * avanza en el tiempo (update) y entrega una foto del estado (createSnapshot).
 * Por eso la usa un solo hilo, el del bucle del servidor.
 */
public class MatchSimulation {

    public static final float MATCH_DURATION = 120f;
    public static final float COUNTDOWN_DURATION = 3f;
    public static final float RESULTS_DURATION = 8f;
    public static final int MIN_PLAYERS = GameConstants.MIN_PLAYERS;
    /** Si no llegan entradas de un jugador en este tiempo, se queda quieto y deja de disparar. */
    public static final float INPUT_STALE_TIME = 0.25f;

    private static final float SPAWN_BLUE_X = 90f;
    private static final float SPAWN_RED_X = Arena.WIDTH - 90f;

    /** Puntos de aparición de Todos contra todos: uno cerca de cada esquina. */
    private static final float[][] FFA_SPAWNS = {
        {90f, 110f},
        {Arena.WIDTH - 90f, Arena.HEIGHT - 110f},
        {90f, Arena.HEIGHT - 110f},
        {Arena.WIDTH - 90f, 110f},
    };

    /** Lo que el servidor guarda de cada jugador además de su entidad. */
    private static class Slot {
        final Player player;
        PlayerInput input = PlayerInput.NONE;
        long lastInputSeq = -1;
        float inputAge = 0f;
        int lastAbilityCount = 0;
        float fireCooldown = 0f;
        boolean ready = false;
        /** Número del último pedido de modo atendido (cada pedido se atiende una sola vez). */
        int lastModeRequest = 0;

        Slot(Player player) {
            this.player = player;
        }
    }

    private final Arena arena = new Arena();
    /** Ordenados por orden de llegada. */
    private final List<Slot> slots = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<RegenZone> zones = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    /** Tiempo que falta para que aparezca el próximo Power-Up. */
    private float powerUpTimer = PowerUpType.FIRST_SPAWN_DELAY;
    private int nextPowerUpId = 1;
    private final Random random = new Random();

    private GameMode mode = GameMode.FREE_FOR_ALL;
    private MatchPhase phase = MatchPhase.LOBBY;
    private float phaseTimer = 0f;
    private int scoreBlue = 0, scoreRed = 0;
    /** Equipo ganador (Equipos) o id del jugador ganador (Todos contra todos); -1 si empate. */
    private int winner = Team.NONE;
    private String endReason = "-";

    private int nextPlayerId = 1;
    private int nextProjectileId = 1;
    private int nextZoneId = 1;

    // ------------------------------------------------------------------
    // Jugadores que entran y salen
    // ------------------------------------------------------------------

    /** Agrega un jugador nuevo y le asigna equipo (o color) y lugar de aparición. */
    public Player addPlayer(String name) {
        int team = mode == GameMode.TEAMS
            ? (countTeam(Team.BLUE) <= countTeam(Team.RED) ? Team.BLUE : Team.RED)
            : firstFreeColor();
        Vector2 spawn = mode == GameMode.TEAMS ? spawnPointFor(team, countTeam(team)) : ffaSpawn(team);
        Player player = new Player(nextPlayerId++, name, team, spawn.x, spawn.y);
        slots.add(new Slot(player));
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.COUNTDOWN) {
            assignTeams();
        }
        return player;
    }

    public void removePlayer(int playerId) {
        Slot slot = findSlot(playerId);
        if (slot == null) return;
        slots.remove(slot);
        // Si se va Iris, su campo desaparece con ella.
        zones.removeIf(z -> z.getOwnerId() == playerId);

        if (phase == MatchPhase.LOBBY || phase == MatchPhase.COUNTDOWN) {
            // Si ya no hay 4 jugadores, el modo por equipos deja de estar permitido.
            if (!mode.allows(slots.size())) mode = GameMode.FREE_FOR_ALL;
            assignTeams();
        } else if (phase == MatchPhase.PLAYING && !enoughPlayersToContinue()) {
            // Sin rivales no tiene sentido seguir.
            endMatch("PLAYERS");
        }
    }

    /** Durante la partida: ¿quedan rivales para seguir jugando? */
    private boolean enoughPlayersToContinue() {
        if (mode == GameMode.TEAMS) {
            return countTeam(Team.BLUE) > 0 && countTeam(Team.RED) > 0;
        }
        return slots.size() >= 2;
    }

    public boolean hasPlayer(int playerId) {
        return findSlot(playerId) != null;
    }

    public int getPlayerCount() {
        return slots.size();
    }

    /** El anfitrión es el primero de la lista (el que lleva más tiempo conectado). */
    public int getHostId() {
        return slots.isEmpty() ? -1 : slots.get(0).player.getId();
    }

    public GameMode getMode() {
        return mode;
    }

    // ------------------------------------------------------------------
    // Pedidos de los clientes
    // ------------------------------------------------------------------

    public void applyInput(int playerId, PlayerInput input) {
        Slot slot = findSlot(playerId);
        if (slot == null) return;
        // UDP no garantiza el orden: una entrada vieja que llega tarde se descarta.
        if (input.seq <= slot.lastInputSeq) return;
        slot.lastInputSeq = input.seq;
        slot.input = input;
        slot.inputAge = 0f;
    }

    public void setReady(int playerId, boolean ready) {
        Slot slot = findSlot(playerId);
        if (slot != null) slot.ready = ready;
    }

    /**
     * Cambia el personaje de un jugador. Solo se permite en la sala de
     * espera: durante la partida, el pedido se ignora.
     */
    public void setCharacter(int playerId, CharacterType character) {
        if (phase != MatchPhase.LOBBY && phase != MatchPhase.COUNTDOWN) return;
        Slot slot = findSlot(playerId);
        if (slot != null && slot.player.getCharacter() != character) {
            slot.player.setCharacter(character);
        }
    }

    /** Resultado de un pedido de cambio de modo (para mostrarlo en la consola). */
    public enum ModeRequestResult {
        /** No es un pedido nuevo (es la repetición de uno ya atendido). */
        REPEATED,
        ACCEPTED,
        ALREADY_SELECTED,
        NOT_HOST,
        NOT_ENOUGH_PLAYERS,
        MATCH_STARTED
    }

    /**
     * Pedido de cambio de modo. Cada pedido trae un número que el cliente
     * sube cada vez que el jugador elige un modo (como el contador de la
     * habilidad). El pedido viaja repetido en todos los PING: el servidor
     * atiende cada número una sola vez, así una pérdida de paquetes no lo
     * hace perder y una repetición no lo aplica dos veces.
     *
     * Se acepta solo si:
     *   - viene del anfitrión,
     *   - la partida no empezó (sala de espera o cuenta regresiva), y
     *   - el modo se puede jugar con los jugadores que hay (Equipos: 4 exactos).
     */
    public ModeRequestResult requestMode(int playerId, int requestNumber, GameMode requested) {
        Slot slot = findSlot(playerId);
        if (slot == null || requestNumber <= slot.lastModeRequest) return ModeRequestResult.REPEATED;
        slot.lastModeRequest = requestNumber;
        if (playerId != getHostId()) return ModeRequestResult.NOT_HOST;
        if (phase != MatchPhase.LOBBY && phase != MatchPhase.COUNTDOWN) return ModeRequestResult.MATCH_STARTED;
        if (requested == mode) return ModeRequestResult.ALREADY_SELECTED;
        if (!requested.allows(slots.size())) return ModeRequestResult.NOT_ENOUGH_PLAYERS;
        mode = requested;
        // Al cambiar el modo se vuelve a la sala, así nadie arranca sin enterarse.
        phase = MatchPhase.LOBBY;
        assignTeams();
        return ModeRequestResult.ACCEPTED;
    }

    // ------------------------------------------------------------------
    // Avance del tiempo
    // ------------------------------------------------------------------

    public void update(float delta) {
        switch (phase) {
            case LOBBY:
                if (canStart()) {
                    phase = MatchPhase.COUNTDOWN;
                    phaseTimer = COUNTDOWN_DURATION;
                }
                break;
            case COUNTDOWN:
                if (!canStart()) {
                    phase = MatchPhase.LOBBY;
                    break;
                }
                phaseTimer -= delta;
                if (phaseTimer <= 0) startMatch();
                break;
            case PLAYING:
                updatePlaying(delta);
                break;
            case RESULTS:
                phaseTimer -= delta;
                if (phaseTimer <= 0) backToLobby();
                break;
        }
    }

    private boolean canStart() {
        if (!mode.allows(slots.size())) return false;
        if (mode == GameMode.TEAMS && (countTeam(Team.BLUE) != 2 || countTeam(Team.RED) != 2)) return false;
        for (Slot s : slots) {
            if (!s.ready) return false;
        }
        return true;
    }

    private void startMatch() {
        phase = MatchPhase.PLAYING;
        phaseTimer = MATCH_DURATION;
        scoreBlue = 0;
        scoreRed = 0;
        winner = Team.NONE;
        endReason = "-";
        projectiles.clear();
        zones.clear();
        powerUps.clear();
        powerUpTimer = PowerUpType.FIRST_SPAWN_DELAY;
        int blueIndex = 0, redIndex = 0;
        for (Slot s : slots) {
            int team = s.player.getTeam();
            Vector2 spawn;
            if (mode == GameMode.TEAMS) {
                spawn = spawnPointFor(team, team == Team.BLUE ? blueIndex++ : redIndex++);
            } else {
                spawn = ffaSpawn(team);
            }
            s.player.resetForNewMatch(spawn.x, spawn.y);
            s.fireCooldown = 0f;
            // Lo que se haya pedido antes de empezar no cuenta como habilidad.
            s.lastAbilityCount = s.input.abilityCount;
        }
    }

    private void endMatch(String reason) {
        phase = MatchPhase.RESULTS;
        phaseTimer = RESULTS_DURATION;
        endReason = reason;
        if (mode == GameMode.TEAMS) {
            if (scoreBlue > scoreRed) winner = Team.BLUE;
            else if (scoreRed > scoreBlue) winner = Team.RED;
            else winner = Team.NONE;
        } else {
            // Gana el jugador con más eliminaciones; si hay dos empatados arriba, es empate.
            winner = Team.NONE;
            int best = -1;
            boolean tie = false;
            for (Slot s : slots) {
                int k = s.player.getKills();
                if (k > best) {
                    best = k;
                    winner = s.player.getId();
                    tie = false;
                } else if (k == best) {
                    tie = true;
                }
            }
            if (tie) winner = Team.NONE;
        }
        projectiles.clear();
        zones.clear();
        powerUps.clear();
    }

    private void backToLobby() {
        phase = MatchPhase.LOBBY;
        phaseTimer = 0f;
        for (Slot s : slots) s.ready = false;
        if (!mode.allows(slots.size())) mode = GameMode.FREE_FOR_ALL;
        assignTeams();
    }

    private void updatePlaying(float delta) {
        phaseTimer -= delta;

        for (Slot s : slots) {
            Player p = s.player;
            s.inputAge += delta;
            // Si el cliente dejó de mandar entradas (pausa, paquetes perdidos),
            // el personaje se detiene en lugar de seguir con la última orden.
            PlayerInput in = s.inputAge > INPUT_STALE_TIME ? s.input.withoutActions() : s.input;

            p.update(delta);

            if (p.isReadyToRespawn()) {
                Vector2 spawn = respawnPointFor(p);
                p.respawn(spawn.x, spawn.y);
            }

            if (p.isAlive()) {
                handleMovement(s, in, delta);
                handleShooting(s, in, delta);
            } else {
                s.lastAbilityCount = in.abilityCount;
            }
        }

        updateZones(delta);
        updatePowerUps(delta);
        updateProjectiles(delta);
        resolveCollisions();

        if (phaseTimer <= 0) {
            phaseTimer = 0;
            endMatch("TIME");
        }
    }

    private void handleMovement(Slot s, PlayerInput in, float delta) {
        Player player = s.player;
        boolean pressingMovementKeys = in.moveX != 0 || in.moveY != 0;
        float aimDx = in.aimX - player.getX();
        float aimDy = in.aimY - player.getY();

        // Habilidad: hubo un pedido nuevo si el contador creció.
        if (in.abilityCount != s.lastAbilityCount) {
            s.lastAbilityCount = in.abilityCount;
            switch (player.getCharacter()) {
                case VOLT: {
                    // Impulso: hacia donde camina o, si está quieto, hacia el mouse.
                    float dashX = pressingMovementKeys ? in.moveX : aimDx;
                    float dashY = pressingMovementKeys ? in.moveY : aimDy;
                    player.tryUseAbility(dashX, dashY);
                    break;
                }
                case BRUTUS:
                    // Escudo: hacia donde apunta el mouse.
                    player.tryUseAbility(aimDx, aimDy);
                    break;
                case IRIS:
                    // Campo de Regeneración: se crea donde está parada Iris.
                    if (player.tryUseAbility(0, 0)) {
                        zones.add(new RegenZone(nextZoneId++, player.getId(), player.getTeam(),
                            player.getX(), player.getY(), player.getCharacter().abilityDuration));
                    }
                    break;
                case PHANTOM:
                    // Camuflaje Óptico: el servidor marca a Phantom como camuflado
                    // y lo manda en el estado; cada cliente lo dibuja casi invisible.
                    player.tryUseAbility(0, 0);
                    break;
            }
        }
        // Mientras el escudo está activo, sigue la dirección del mouse.
        if (player.isShieldActive()) {
            player.setShieldDirection(aimDx, aimDy);
        }

        float speed = player.getCurrentSpeed();
        float dx = 0, dy = 0;
        if (player.isDashing()) {
            dx = player.getDashDirX() * speed * delta;
            dy = player.getDashDirY() * speed * delta;
        } else if (pressingMovementKeys) {
            // Normalización: en diagonal no se va más rápido.
            float length = (float) Math.sqrt(in.moveX * in.moveX + in.moveY * in.moveY);
            dx = (in.moveX / length) * speed * delta;
            dy = (in.moveY / length) * speed * delta;
        }

        moveWithCollision(player, dx, dy);
        player.setMoving(pressingMovementKeys || player.isDashing());

        if (pressingMovementKeys) {
            player.setFacingDirection(Direction.fromVector(in.moveX, in.moveY));
        } else if (aimDx != 0 || aimDy != 0) {
            player.setFacingDirection(Direction.fromVector(aimDx, aimDy));
        }
    }

    /** Mueve eje por eje para poder deslizarse contra las paredes. */
    private void moveWithCollision(Player player, float dx, float dy) {
        float half = Player.SIZE / 2f;
        if (dx != 0) {
            float newX = player.getX() + dx;
            Rectangle testX = new Rectangle(newX - half, player.getY() - half, Player.SIZE, Player.SIZE);
            if (!arena.collidesWithAny(testX)) player.setX(newX);
        }
        if (dy != 0) {
            float newY = player.getY() + dy;
            Rectangle testY = new Rectangle(player.getX() - half, newY - half, Player.SIZE, Player.SIZE);
            if (!arena.collidesWithAny(testY)) player.setY(newY);
        }
    }

    private void handleShooting(Slot s, PlayerInput in, float delta) {
        if (s.fireCooldown > 0) s.fireCooldown -= delta;
        if (!in.fire || s.fireCooldown > 0) return;

        Player player = s.player;
        float dirX = in.aimX - player.getX();
        float dirY = in.aimY - player.getY();
        if (dirX * dirX + dirY * dirY < 1f) {
            // Mouse encima del jugador: se dispara hacia donde mira, para
            // que nunca se cree un proyectil sin dirección.
            Vector2 facing = facingVector(player.getFacingDirection());
            dirX = facing.x;
            dirY = facing.y;
        }
        // El daño se fija al disparar: si tiene Sobrecarga, el proyectil pega más fuerte.
        projectiles.add(new Projectile(nextProjectileId++, player.getId(), player.getTeam(),
            player.getCharacter(), player.getX(), player.getY(), dirX, dirY,
            player.getCharacter().damage * player.getDamageMultiplier()));
        s.fireCooldown = player.getCharacter().fireCooldown;
        // Disparar delata a Phantom: el camuflaje se corta.
        player.breakCamouflage();
    }

    /** Cura a los aliados dentro de cada Campo de Regeneración y borra los que vencieron. */
    private void updateZones(float delta) {
        Iterator<RegenZone> it = zones.iterator();
        while (it.hasNext()) {
            RegenZone z = it.next();
            z.update(delta);
            if (!z.isAlive()) {
                it.remove();
                continue;
            }
            for (Slot s : slots) {
                Player p = s.player;
                if (!p.isAlive() || !isAllyOfZone(p, z)) continue;
                if (z.contains(p.getX(), p.getY())) {
                    p.heal(CharacterType.REGEN_PER_SECOND * delta);
                }
            }
        }
    }

    /**
     * Power-Ups: cada tanto aparece uno nuevo, de un tipo al azar, en un punto
     * libre del mapa; y el primer jugador vivo que lo toque se lo lleva.
     */
    private void updatePowerUps(float delta) {
        powerUpTimer -= delta;
        if (powerUpTimer <= 0f) {
            powerUpTimer = PowerUpType.SPAWN_INTERVAL;
            if (powerUps.size() < PowerUpType.MAX_ON_MAP) spawnPowerUp();
        }

        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) {
            PowerUp pu = it.next();
            for (Slot s : slots) {
                Player p = s.player;
                if (pu.touches(p) && p.canPickUp(pu.getType())) {
                    p.applyPowerUp(pu.getType());
                    it.remove();
                    break;
                }
            }
        }
    }

    private void spawnPowerUp() {
        // Puntos de aparición que no tienen ya un Power-Up encima
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < Arena.POWERUP_SPAWNS.length; i++) {
            boolean taken = false;
            for (PowerUp pu : powerUps) {
                if (pu.getSpawnIndex() == i) taken = true;
            }
            if (!taken) free.add(i);
        }
        if (free.isEmpty()) return;
        int index = free.get(random.nextInt(free.size()));
        PowerUpType[] types = PowerUpType.values();
        PowerUpType type = types[random.nextInt(types.length)];
        float[] pos = Arena.POWERUP_SPAWNS[index];
        powerUps.add(new PowerUp(nextPowerUpId++, type, index, pos[0], pos[1]));
    }

    /** En Equipos el campo cura a todo el equipo de Iris; en Todos contra todos, solo a Iris. */
    private boolean isAllyOfZone(Player p, RegenZone z) {
        if (mode == GameMode.TEAMS) return p.getTeam() == z.getTeam();
        return p.getId() == z.getOwnerId();
    }

    private void updateProjectiles(float delta) {
        for (Projectile p : projectiles) {
            p.update(delta);
            if (p.getX() < 0 || p.getX() > Arena.WIDTH || p.getY() < 0 || p.getY() > Arena.HEIGHT) {
                p.kill();
            }
        }
    }

    private void resolveCollisions() {
        for (Projectile p : projectiles) {
            if (!p.isAlive()) continue;
            Rectangle bounds = p.getBounds();

            if (arena.collidesWithAny(bounds)) {
                p.kill();
                continue;
            }

            for (Slot s : slots) {
                Player target = s.player;
                // Sin fuego amigo: solo se daña a los rivales.
                if (!target.isAlive() || !isEnemyOf(target, p)) continue;
                if (!target.getBounds().overlaps(bounds)) continue;

                boolean eliminated = target.takeDamage(p.getDamage(), p.getDirX(), p.getDirY());
                if (eliminated) {
                    Slot shooter = findSlot(p.getOwnerId());
                    if (shooter != null) shooter.player.addKill();
                    if (mode == GameMode.TEAMS) {
                        if (p.getTeam() == Team.BLUE) scoreBlue++;
                        else scoreRed++;
                    }
                }
                p.kill();
                break;
            }
        }

        // Se recorre con un Iterator para poder borrar mientras se recorre.
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            if (!it.next().isAlive()) it.remove();
        }
    }

    // ------------------------------------------------------------------
    // Equipos y puntos de aparición
    // ------------------------------------------------------------------

    /** ¿El proyectil puede dañar a este jugador? */
    private boolean isEnemyOf(Player target, Projectile p) {
        if (target.getId() == p.getOwnerId()) return false;
        if (mode == GameMode.TEAMS) return target.getTeam() != p.getTeam();
        return true; // Todos contra todos: cualquiera que no sea uno mismo
    }

    /**
     * Reparte equipos según el modo y ubica a cada uno en su lugar de aparición.
     *   - Equipos: alternado por orden de llegada (azul, rojo, azul, rojo).
     *   - Todos contra todos: cada uno recibe su propio color (0, 1, 2, 3).
     */
    private void assignTeams() {
        int blueIndex = 0, redIndex = 0;
        for (int i = 0; i < slots.size(); i++) {
            Player p = slots.get(i).player;
            Vector2 spawn;
            if (mode == GameMode.TEAMS) {
                int team = (i % 2 == 0) ? Team.BLUE : Team.RED;
                p.setTeam(team);
                spawn = spawnPointFor(team, team == Team.BLUE ? blueIndex++ : redIndex++);
            } else {
                p.setTeam(i);
                spawn = ffaSpawn(i);
            }
            p.respawn(spawn.x, spawn.y);
        }
    }

    /** Primer color libre (para alguien que entra con la partida en juego). */
    private int firstFreeColor() {
        for (int c = 0; c < Protocol.MAX_PLAYERS; c++) {
            if (countTeam(c) == 0) return c;
        }
        return 0;
    }

    private Vector2 spawnPointFor(int team, int indexInTeam) {
        float x = team == Team.BLUE ? SPAWN_BLUE_X : SPAWN_RED_X;
        float y = Arena.HEIGHT / 2f + (indexInTeam - 0.5f) * 60f;
        return arena.findFreeSpawnNear(x, y, Player.SIZE);
    }

    private Vector2 ffaSpawn(int color) {
        float[] pos = FFA_SPAWNS[Math.floorMod(color, FFA_SPAWNS.length)];
        return arena.findFreeSpawnNear(pos[0], pos[1], Player.SIZE);
    }

    /**
     * Dónde reaparece alguien eliminado.
     *   - Equipos: en la base de su equipo.
     *   - Todos contra todos: en la esquina más alejada de los rivales vivos,
     *     para no reaparecer al lado de quien lo acaba de eliminar.
     */
    private Vector2 respawnPointFor(Player p) {
        if (mode == GameMode.TEAMS) return spawnPointFor(p.getTeam(), 0);
        float bestDistance = -1f;
        float[] best = FFA_SPAWNS[0];
        for (float[] pos : FFA_SPAWNS) {
            float nearest = Float.MAX_VALUE;
            for (Slot s : slots) {
                Player other = s.player;
                if (other == p || !other.isAlive()) continue;
                float dx = other.getX() - pos[0], dy = other.getY() - pos[1];
                nearest = Math.min(nearest, dx * dx + dy * dy);
            }
            if (nearest > bestDistance) {
                bestDistance = nearest;
                best = pos;
            }
        }
        return arena.findFreeSpawnNear(best[0], best[1], Player.SIZE);
    }

    private static Vector2 facingVector(int facing) {
        switch (facing) {
            case Direction.LEFT: return new Vector2(-1, 0);
            case Direction.RIGHT: return new Vector2(1, 0);
            case Direction.UP: return new Vector2(0, 1);
            default: return new Vector2(0, -1);
        }
    }

    private int countTeam(int team) {
        int n = 0;
        for (Slot s : slots) {
            if (s.player.getTeam() == team) n++;
        }
        return n;
    }

    private Slot findSlot(int playerId) {
        for (Slot s : slots) {
            if (s.player.getId() == playerId) return s;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Foto del estado para enviar a los clientes
    // ------------------------------------------------------------------

    public WorldSnapshot createSnapshot(long seq) {
        List<WorldSnapshot.PlayerState> ps = new ArrayList<>();
        for (Slot s : slots) {
            Player p = s.player;
            ps.add(new WorldSnapshot.PlayerState(
                p.getId(), p.getName(), p.getTeam(), p.getCharacter().id(), p.getX(), p.getY(),
                p.getFacingDirection(), p.isMoving(), p.isAlive(), p.getHealth(),
                p.isAbilityActive(), p.getShieldAngle(), p.isInvulnerable(), s.ready,
                p.getKills(), p.getDeaths(), p.getAbilityCooldownProgress(), p.getRespawnRemaining(),
                p.getBoosts()));
        }
        List<WorldSnapshot.ProjectileState> prs = new ArrayList<>();
        for (Projectile p : projectiles) {
            prs.add(new WorldSnapshot.ProjectileState(p.getId(), p.getTeam(), p.getCharacter().id(),
                p.getX(), p.getY()));
        }
        List<WorldSnapshot.ZoneState> zs = new ArrayList<>();
        for (RegenZone z : zones) {
            zs.add(new WorldSnapshot.ZoneState(z.getId(), z.getOwnerId(), z.getTeam(),
                z.getX(), z.getY(), z.getRadius(), z.getRemaining()));
        }
        List<WorldSnapshot.PowerUpState> pus = new ArrayList<>();
        for (PowerUp pu : powerUps) {
            pus.add(new WorldSnapshot.PowerUpState(pu.getId(), pu.getType().id(), pu.getX(), pu.getY()));
        }
        return new WorldSnapshot(seq, phase, Math.max(0f, phaseTimer), scoreBlue, scoreRed,
            winner, endReason, mode.id(), getHostId(), ps, prs, zs, pus, 0L);
    }

    public MatchPhase getPhase() {
        return phase;
    }

    public Arena getArena() {
        return arena;
    }
}
