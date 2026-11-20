/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;

/**
 * Direcciones hacia las que puede mirar un personaje.
 *
 * Los valores coinciden con las filas del spritesheet del jugador
 * (fila 0 = abajo, 1 = izquierda, 2 = derecha, 3 = arriba).
 * Están en el paquete de entidades, y no en el de gráficos, porque el
 * servidor también las usa y el servidor no dibuja nada.
 */
public final class Direction {

    public static final int DOWN = 0;
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int UP = 3;

    private Direction() { }

    /** Elige una de las cuatro direcciones a partir de un vector (x, y). */
    public static int fromVector(float x, float y) {
        if (Math.abs(x) > Math.abs(y)) {
            return x > 0 ? RIGHT : LEFT;
        }
        return y > 0 ? UP : DOWN;
    }
}
