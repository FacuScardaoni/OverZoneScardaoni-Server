/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocolo.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.compartido;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

public class Arena {

    public static final float ANCHO = 960f;
    public static final float ALTO = 540f;
    public static final float GROSOR_PARED = 20f;

    private final List<Obstaculo> obstaculos = new ArrayList<>();

    public Arena() {
        armarMapa();
    }

    private void armarMapa() {

        obstaculos.add(new Obstaculo(0, 0, ANCHO, GROSOR_PARED));                          // abajo
        obstaculos.add(new Obstaculo(0, ALTO - GROSOR_PARED, ANCHO, GROSOR_PARED));    // arriba
        obstaculos.add(new Obstaculo(0, 0, GROSOR_PARED, ALTO));                         // izquierda
        obstaculos.add(new Obstaculo(ANCHO - GROSOR_PARED, 0, GROSOR_PARED, ALTO));    // derecha


        float anchoBloque = 48, altoBloque = 48;
        obstaculos.add(new Obstaculo(200, 150, anchoBloque, altoBloque));
        obstaculos.add(new Obstaculo(200, 340, anchoBloque, altoBloque));
        obstaculos.add(new Obstaculo(710, 150, anchoBloque, altoBloque));
        obstaculos.add(new Obstaculo(710, 340, anchoBloque, altoBloque));
        obstaculos.add(new Obstaculo(456,  60, anchoBloque, altoBloque));   // centro, abajo
        obstaculos.add(new Obstaculo(456, 430, anchoBloque, altoBloque));   // centro, arriba
        obstaculos.add(new Obstaculo(330, 246, anchoBloque, altoBloque));   // flanco izquierdo del centro
        obstaculos.add(new Obstaculo(582, 246, anchoBloque, altoBloque));   // flanco derecho del centro
    }

    /** Busca un lugar libre cerca del centro del mapa. */
    public Vector2 buscarAparicionLibre(float tamanioEntidad) {
        return buscarAparicionLibreCerca(ANCHO / 2f, ALTO / 2f, tamanioEntidad);
    }

    /**
     * Busca el lugar libre de obstáculos más cercano al punto indicado,
     * recorriendo círculos cada vez más grandes alrededor de él.
     */
    /**
     * Lugares donde pueden aparecer los Power-Ups (centros, sin obstáculos).
     * Están en zonas disputadas, entre las bases, para que haya que arriesgarse.
     */
    public static final float[][] APARICIONES_POTENCIADORES = {
        {ANCHO / 2f, ALTO / 2f},   // centro
        {224f, ALTO / 2f},         // pasillo izquierdo
        {ANCHO - 224f, ALTO / 2f}, // pasillo derecho
        {340f, 120f},                // abajo a la izquierda del centro
        {ANCHO - 340f, ALTO - 120f} // arriba a la derecha del centro
    };

    public Vector2 buscarAparicionLibreCerca(float centroX, float centroY, float tamanioEntidad) {
        if (estaLibrePara(centroX, centroY, tamanioEntidad)) {
            return new Vector2(centroX, centroY);
        }
        for (float radio = 16; radio <= 400; radio += 16) {
            for (int angulo = 0; angulo < 360; angulo += 30) {
                float radianes = (float) Math.toRadians(angulo);
                float x = centroX + radio * (float) Math.cos(radianes);
                float y = centroY + radio * (float) Math.sin(radianes);
                if (estaLibrePara(x, y, tamanioEntidad)) {
                    return new Vector2(x, y);
                }
            }
        }
        return new Vector2(centroX, centroY);
    }

    public boolean estaLibrePara(float x, float y, float tamanio) {
        Rectangle limites = new Rectangle(x - tamanio / 2f, y - tamanio / 2f, tamanio, tamanio);
        return !chocaConAlguno(limites);
    }

    public List<Obstaculo> getObstaculos() {
        return obstaculos;
    }

    public Obstaculo buscarColision(Rectangle limites) {
        for (Obstaculo o : obstaculos) {
            if (o.getLimites().overlaps(limites)) {
                return o;
            }
        }
        return null;
    }

    public boolean chocaConAlguno(Rectangle limites) {
        return buscarColision(limites) != null;
    }
}
