package com.OverZone.Scardaoni.server.game;

import com.OverZone.Scardaoni.shared.Direction;
import com.OverZone.Scardaoni.shared.GameConstants;
import com.OverZone.Scardaoni.shared.MatchPhase;
import com.OverZone.Scardaoni.shared.Team;
import com.OverZone.Scardaoni.shared.PlayerInput;
import com.OverZone.Scardaoni.shared.WorldSnapshot;
import com.OverZone.Scardaoni.shared.Arena;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * La partida real. Solo existe en el SERVIDOR.
 *
 * Contiene la lógica que antes estaba en GameScreen (movimiento con
 * colisiones, impulso, disparos, daño), ahora aplicada a varios jugadores
 * y con equipos, eliminaciones, reapariciones y etapas de partida.
 *
 * No sabe nada de la red ni de la pantalla: recibe entradas (applyInput),
 * avanza en el tiempo (update) y entrega una foto del estado (createSnapshot).
 * Por eso la usa un solo hilo, el del bucle del servidor.
 */
public class MatchSimulation {

    public static final float MATCH_DURATION = 120f;
    public static final float COUNTDOWN_DURATION = 3f;
    public static final float RESULTS_DURATION = 8f;
    public static final float FIRE_COOLDOWN = 0.22f;
    public static final int MIN_PLAYERS = GameConstants.MIN_PLAYERS;
    /** Si no llegan entradas de un jugador en este tiempo, se queda quieto y deja de disparar. */
    public static final float INPUT_STALE_TIME = 0.25f;

    private static final float SPAWN_BLUE_X = 90f;
    private static final float SPAWN_RED_X = Arena.WIDTH - 90f;

    /** Lo que el servidor guarda de cada jugador además de su entidad. */
    private static class Slot {
        final Player player;
        PlayerInput input = PlayerInput.NONE;
        long lastInputSeq = -1;
        float inputAge = 0f;
        int lastAbilityCount = 0;
        float fireCooldown = 0f;
        boolean ready = false;

        Slot(Player player) {
            this.player = player;
        }
    }

    private final Arena arena = new Arena();
    /** Ordenados por orden de llegada. */
    private final List<Slot> slots = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();

    private MatchPhase phase = MatchPhase.LOBBY;
    private float phaseTimer = 0f;
    private int scoreBlue = 0, scoreRed = 0;
    private int winner = Team.NONE;
    private String endReason = "-";

    private int nextPlayerId = 1;
    private int nextProjectileId = 1;

    // ------------------------------------------------------------------
    // Jugadores que entran y salen
    // ------------------------------------------------------------------

    /** Agrega un jugador nuevo y lo ubica en el equipo con menos integrantes. */
    public Player addPlayer(String name) {
        int team = countTeam(Team.BLUE) <= countTeam(Team.RED) ? Team.BLUE : Team.RED;
        Vector2 spawn = spawnPointFor(team, countTeam(team));
        Player player = new Player(nextPlayerId++, name, team, spawn.x, spawn.y);
        slots.add(new Slot(player));
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.COUNTDOWN) {
            rebalanceTeams();
        }
        return player;
    }

    public void removePlayer(int playerId) {
        Slot slot = findSlot(playerId);
        if (slot == null) return;
        slots.remove(slot);

        if (phase == MatchPhase.LOBBY || phase == MatchPhase.COUNTDOWN) {
            rebalanceTeams();
        } else if (phase == MatchPhase.PLAYING
            && (countTeam(Team.BLUE) == 0 || countTeam(Team.RED) == 0)) {
            // Un equipo se quedó sin jugadores: no tiene sentido seguir.
            endMatch("PLAYERS");
        }
    }

    public boolean hasPlayer(int playerId) {
        return findSlot(playerId) != null;
    }

    public int getPlayerCount() {
        return slots.size();
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
        if (slots.size() < MIN_PLAYERS) return false;
        if (countTeam(Team.BLUE) == 0 || countTeam(Team.RED) == 0) return false;
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
        int blueIndex = 0, redIndex = 0;
        for (Slot s : slots) {
            int team = s.player.getTeam();
            Vector2 spawn = spawnPointFor(team, team == Team.BLUE ? blueIndex++ : redIndex++);
            s.player.resetForNewMatch(spawn.x, spawn.y);
            s.fireCooldown = 0f;
            // Lo que se haya pedido antes de empezar no cuenta como impulso.
            s.lastAbilityCount = s.input.abilityCount;
        }
    }

    private void endMatch(String reason) {
        phase = MatchPhase.RESULTS;
        phaseTimer = RESULTS_DURATION;
        endReason = reason;
        if (scoreBlue > scoreRed) winner = Team.BLUE;
        else if (scoreRed > scoreBlue) winner = Team.RED;
        else winner = Team.NONE;
        projectiles.clear();
    }

    private void backToLobby() {
        phase = MatchPhase.LOBBY;
        phaseTimer = 0f;
        for (Slot s : slots) s.ready = false;
        rebalanceTeams();
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
                Vector2 spawn = spawnPointFor(p.getTeam(), 0);
                p.respawn(spawn.x, spawn.y);
            }

            if (p.isAlive()) {
                handleMovement(s, in, delta);
                handleShooting(s, in, delta);
            } else {
                s.lastAbilityCount = in.abilityCount;
            }
        }

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
            if (!player.isDashing()) {
                float dashX = pressingMovementKeys ? in.moveX : aimDx;
                float dashY = pressingMovementKeys ? in.moveY : aimDy;
                player.tryStartDash(dashX, dashY);
            }
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
        projectiles.add(new Projectile(nextProjectileId++, player.getId(), player.getTeam(),
            player.getX(), player.getY(), dirX, dirY));
        s.fireCooldown = FIRE_COOLDOWN;
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
                // Sin fuego amigo: solo se daña al equipo contrario.
                if (!target.isAlive() || target.getTeam() == p.getTeam()) continue;
                if (!target.getBounds().overlaps(bounds)) continue;

                boolean eliminated = target.takeDamage(Projectile.DAMAGE);
                if (eliminated) {
                    Slot shooter = findSlot(p.getOwnerId());
                    if (shooter != null) shooter.player.addKill();
                    if (p.getTeam() == Team.BLUE) scoreBlue++;
                    else scoreRed++;
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

    /** Reparte los equipos de forma alternada según el orden de llegada. */
    private void rebalanceTeams() {
        int blueIndex = 0, redIndex = 0;
        for (int i = 0; i < slots.size(); i++) {
            Player p = slots.get(i).player;
            int team = (i % 2 == 0) ? Team.BLUE : Team.RED;
            p.setTeam(team);
            Vector2 spawn = spawnPointFor(team, team == Team.BLUE ? blueIndex++ : redIndex++);
            p.respawn(spawn.x, spawn.y);
        }
    }

    private Vector2 spawnPointFor(int team, int indexInTeam) {
        float x = team == Team.BLUE ? SPAWN_BLUE_X : SPAWN_RED_X;
        float y = Arena.HEIGHT / 2f + (indexInTeam - 0.5f) * 60f;
        return arena.findFreeSpawnNear(x, y, Player.SIZE);
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
                p.getId(), p.getName(), p.getTeam(), p.getX(), p.getY(),
                p.getFacingDirection(), p.isMoving(), p.isAlive(), p.getHealth(),
                p.isDashing(), p.isInvulnerable(), s.ready, p.getKills(), p.getDeaths(),
                p.getDashCooldownProgress(), p.getRespawnRemaining()));
        }
        List<WorldSnapshot.ProjectileState> prs = new ArrayList<>();
        for (Projectile p : projectiles) {
            prs.add(new WorldSnapshot.ProjectileState(p.getId(), p.getTeam(), p.getX(), p.getY()));
        }
        return new WorldSnapshot(seq, phase, Math.max(0f, phaseTimer), scoreBlue, scoreRed,
            winner, endReason, ps, prs, 0L);
    }

    public MatchPhase getPhase() {
        return phase;
    }

    public Arena getArena() {
        return arena;
    }
}
