package com.OverZone.Scardaoni.compartido;

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
