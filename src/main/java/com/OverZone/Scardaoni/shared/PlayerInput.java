/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocol.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.shared;

import java.util.Locale;

/**
 * Lo que un cliente le pide al servidor en un instante: hacia dónde moverse,
 * hacia dónde apunta, si dispara y cuántas veces usó la habilidad.
 *
 * El cliente NO envía su posición: el servidor es quien la calcula.
 */
public class PlayerInput {

    public static final PlayerInput NONE = new PlayerInput(0, 0, 0, 0, 0, false, 0);

    /** Número de orden: el servidor descarta entradas viejas que lleguen tarde. */
    public final long seq;
    /** -1, 0 o 1 en cada eje. */
    public final float moveX, moveY;
    /** Posición del mouse en el mapa. */
    public final float aimX, aimY;
    /** Botón de disparo mantenido. */
    public final boolean fire;
    /**
     * Cantidad total de veces que se pidió la habilidad desde que se conectó.
     * Se manda un contador y no un "se apretó ahora" porque, si ese único
     * paquete se perdiera, el impulso se perdería. Con el contador, cualquier
     * paquete posterior le avisa al servidor que hubo un pedido nuevo.
     */
    public final int abilityCount;

    public PlayerInput(long seq, float moveX, float moveY, float aimX, float aimY,
                       boolean fire, int abilityCount) {
        this.seq = seq;
        this.moveX = clampAxis(moveX);
        this.moveY = clampAxis(moveY);
        this.aimX = aimX;
        this.aimY = aimY;
        this.fire = fire;
        this.abilityCount = abilityCount;
    }

    /** Misma entrada pero sin moverse ni disparar (se usa si dejan de llegar entradas). */
    public PlayerInput withoutActions() {
        return new PlayerInput(seq, 0, 0, aimX, aimY, false, abilityCount);
    }

    public String toMessage() {
        return Protocol.join(Protocol.INPUT, seq,
            (int) moveX, (int) moveY,
            Protocol.f(aimX), Protocol.f(aimY),
            fire ? 1 : 0, abilityCount);
    }

    /** Lee un mensaje INPUT. Lanza una excepción si el mensaje está mal formado. */
    public static PlayerInput parse(String[] parts) {
        return new PlayerInput(
            Long.parseLong(parts[1]),
            Float.parseFloat(parts[2]),
            Float.parseFloat(parts[3]),
            Float.parseFloat(parts[4]),
            Float.parseFloat(parts[5]),
            "1".equals(parts[6]),
            Integer.parseInt(parts[7]));
    }

    private static float clampAxis(float v) {
        // Un cliente no puede pedir moverse más rápido mandando un número grande.
        if (v > 0) return 1;
        if (v < 0) return -1;
        return 0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Input#%d(%.0f,%.0f fire=%b ab=%d)", seq, moveX, moveY, fire, abilityCount);
    }
}
