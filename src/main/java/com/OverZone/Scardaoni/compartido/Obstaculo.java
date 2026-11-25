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


public class Obstaculo {

    private final Rectangle limites;

    public Obstaculo(float x, float y, float ancho, float alto) {
        this.limites = new Rectangle(x, y, ancho, alto);
    }

    public Rectangle getLimites() {
        return limites;
    }
}
