/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocolo.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.compartido;

import java.util.Locale;

/**
 * Lo que un cliente le pide al servidor en un instante: hacia dónde moverse,
 * hacia dónde apunta, si dispara y cuántas veces usó la habilidad.
 *
 * El cliente NO envía su posición: el servidor es quien la calcula.
 */
public class EntradaJugador {

    public static final EntradaJugador NINGUNO = new EntradaJugador(0, 0, 0, 0, 0, false, 0);

    /** Número de orden: el servidor descarta entradas viejas que lleguen tarde. */
    public final long secuencia;
    /** -1, 0 o 1 en cada eje. */
    public final float moverX, moverY;
    /** Posición del mouse en el mapa. */
    public final float apuntarX, apuntarY;
    /** Botón de disparo mantenido. */
    public final boolean disparar;
    /**
     * Cantidad total de veces que se pidió la habilidad desde que se conectó.
     * Se manda un contador y no un "se apretó ahora" porque, si ese único
     * paquete se perdiera, el impulso se perdería. Con el contador, cualquier
     * paquete posterior le avisa al servidor que hubo un pedido nuevo.
     */
    public final int contadorHabilidad;

    public EntradaJugador(long secuencia, float moverX, float moverY, float apuntarX, float apuntarY,
                       boolean disparar, int contadorHabilidad) {
        this.secuencia = secuencia;
        this.moverX = limitarEje(moverX);
        this.moverY = limitarEje(moverY);
        this.apuntarX = apuntarX;
        this.apuntarY = apuntarY;
        this.disparar = disparar;
        this.contadorHabilidad = contadorHabilidad;
    }

    /** Misma entrada pero sin moverse ni disparar (se usa si dejan de llegar entradas). */
    public EntradaJugador sinAcciones() {
        return new EntradaJugador(secuencia, 0, 0, apuntarX, apuntarY, false, contadorHabilidad);
    }

    public String aMensaje() {
        return Protocolo.unir(Protocolo.INPUT, secuencia,
            (int) moverX, (int) moverY,
            Protocolo.f(apuntarX), Protocolo.f(apuntarY),
            disparar ? 1 : 0, contadorHabilidad);
    }

    /** Lee un mensaje INPUT. Lanza una excepción si el mensaje está mal formado. */
    public static EntradaJugador interpretar(String[] partes) {
        return new EntradaJugador(
            Long.parseLong(partes[1]),
            Float.parseFloat(partes[2]),
            Float.parseFloat(partes[3]),
            Float.parseFloat(partes[4]),
            Float.parseFloat(partes[5]),
            "1".equals(partes[6]),
            Integer.parseInt(partes[7]));
    }

    private static float limitarEje(float v) {
        // Un cliente no puede pedir moverse más rápido mandando un número grande.
        if (v > 0) return 1;
        if (v < 0) return -1;
        return 0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Input#%d(%.0f,%.0f fire=%b ab=%d)", secuencia, moverX, moverY, disparar, contadorHabilidad);
    }
}
