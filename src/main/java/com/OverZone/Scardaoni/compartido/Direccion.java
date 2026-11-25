/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocolo.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.compartido;

/**
 * Direcciones hacia las que puede mirar un personaje.
 *
 * Los valores coinciden con las filas del spritesheet del jugador
 * (fila 0 = abajo, 1 = izquierda, 2 = derecha, 3 = arriba).
 * Están en el paquete de entidades, y no en el de gráficos, porque el
 * servidor también las usa y el servidor no dibuja nada.
 */
public final class Direccion {

    public static final int ABAJO = 0;
    public static final int IZQUIERDA = 1;
    public static final int DERECHA = 2;
    public static final int ARRIBA = 3;

    private Direccion() { }

    /** Elige una de las cuatro direcciones a partir de un vector (x, y). */
    public static int desdeVector(float x, float y) {
        if (Math.abs(x) > Math.abs(y)) {
            return x > 0 ? DERECHA : IZQUIERDA;
        }
        return y > 0 ? ARRIBA : ABAJO;
    }
}
