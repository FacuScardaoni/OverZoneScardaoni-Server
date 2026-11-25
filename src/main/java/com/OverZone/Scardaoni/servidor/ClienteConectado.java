package com.OverZone.Scardaoni.servidor;

import java.net.InetAddress;

/** Un cliente conectado: su dirección en la red y el jugador que maneja. */
public class ClienteConectado {

    final InetAddress direccionIp;
    final int puerto;
    final int jugadorId;
    final String nombre;
    /** Última vez que llegó algo de este cliente (System.nanoTime). */
    long ultimoPaqueteNanos;

    ClienteConectado(InetAddress direccionIp, int puerto, int jugadorId, String nombre) {
        this.direccionIp = direccionIp;
        this.puerto = puerto;
        this.jugadorId = jugadorId;
        this.nombre = nombre;
        this.ultimoPaqueteNanos = System.nanoTime();
    }

    boolean esDe(InetAddress otraDireccion, int otroPuerto) {
        return direccionIp.equals(otraDireccion) && puerto == otroPuerto;
    }

    @Override
    public String toString() {
        return nombre + " (#" + jugadorId + ", " + direccionIp.getHostAddress() + ":" + puerto + ")";
    }
}
