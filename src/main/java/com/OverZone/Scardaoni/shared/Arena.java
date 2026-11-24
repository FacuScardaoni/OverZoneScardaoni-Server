/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

public class Arena {

    public static final float WIDTH = 960f;
    public static final float HEIGHT = 540f;
    public static final float WALL_THICKNESS = 20f;

    private final List<Obstacle> obstacles = new ArrayList<>();

    public Arena() {
        buildLayout();
    }

    private void buildLayout() {

        obstacles.add(new Obstacle(0, 0, WIDTH, WALL_THICKNESS));                          // abajo
        obstacles.add(new Obstacle(0, HEIGHT - WALL_THICKNESS, WIDTH, WALL_THICKNESS));    // arriba
        obstacles.add(new Obstacle(0, 0, WALL_THICKNESS, HEIGHT));                         // izquierda
        obstacles.add(new Obstacle(WIDTH - WALL_THICKNESS, 0, WALL_THICKNESS, HEIGHT));    // derecha


        float bw = 48, bh = 48;
        obstacles.add(new Obstacle(200, 150, bw, bh));
        obstacles.add(new Obstacle(200, 340, bw, bh));
        obstacles.add(new Obstacle(710, 150, bw, bh));
        obstacles.add(new Obstacle(710, 340, bw, bh));
        obstacles.add(new Obstacle(456,  60, bw, bh));   // centro, abajo
        obstacles.add(new Obstacle(456, 430, bw, bh));   // centro, arriba
        obstacles.add(new Obstacle(330, 246, bw, bh));   // flanco izquierdo del centro
        obstacles.add(new Obstacle(582, 246, bw, bh));   // flanco derecho del centro
    }

    /** Busca un lugar libre cerca del centro del mapa. */
    public Vector2 findFreeSpawn(float entitySize) {
        return findFreeSpawnNear(WIDTH / 2f, HEIGHT / 2f, entitySize);
    }

    /**
     * Busca el lugar libre de obstáculos más cercano al punto indicado,
     * recorriendo círculos cada vez más grandes alrededor de él.
     */
    /**
     * Lugares donde pueden aparecer los Power-Ups (centros, sin obstáculos).
     * Están en zonas disputadas, entre las bases, para que haya que arriesgarse.
     */
    public static final float[][] POWERUP_SPAWNS = {
        {WIDTH / 2f, HEIGHT / 2f},   // centro
        {224f, HEIGHT / 2f},         // pasillo izquierdo
        {WIDTH - 224f, HEIGHT / 2f}, // pasillo derecho
        {340f, 120f},                // abajo a la izquierda del centro
        {WIDTH - 340f, HEIGHT - 120f} // arriba a la derecha del centro
    };

    public Vector2 findFreeSpawnNear(float centerX, float centerY, float entitySize) {
        if (isFreeFor(centerX, centerY, entitySize)) {
            return new Vector2(centerX, centerY);
        }
        for (float radius = 16; radius <= 400; radius += 16) {
            for (int angle = 0; angle < 360; angle += 30) {
                float rad = (float) Math.toRadians(angle);
                float x = centerX + radius * (float) Math.cos(rad);
                float y = centerY + radius * (float) Math.sin(rad);
                if (isFreeFor(x, y, entitySize)) {
                    return new Vector2(x, y);
                }
            }
        }
        return new Vector2(centerX, centerY);
    }

    public boolean isFreeFor(float x, float y, float size) {
        Rectangle bounds = new Rectangle(x - size / 2f, y - size / 2f, size, size);
        return !collidesWithAny(bounds);
    }

    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public Obstacle findCollision(Rectangle bounds) {
        for (Obstacle o : obstacles) {
            if (o.getBounds().overlaps(bounds)) {
                return o;
            }
        }
        return null;
    }

    public boolean collidesWithAny(Rectangle bounds) {
        return findCollision(bounds) != null;
    }
}
