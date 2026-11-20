package com.OverZone.Scardaoni.server;

import java.net.InetAddress;

/** Un cliente conectado: su dirección en la red y el jugador que maneja. */
public class ConnectedClient {

    final InetAddress address;
    final int port;
    final int playerId;
    final String name;
    /** Última vez que llegó algo de este cliente (System.nanoTime). */
    long lastPacketNanos;

    ConnectedClient(InetAddress address, int port, int playerId, String name) {
        this.address = address;
        this.port = port;
        this.playerId = playerId;
        this.name = name;
        this.lastPacketNanos = System.nanoTime();
    }

    boolean isFrom(InetAddress otherAddress, int otherPort) {
        return address.equals(otherAddress) && port == otherPort;
    }

    @Override
    public String toString() {
        return name + " (#" + playerId + ", " + address.getHostAddress() + ":" + port + ")";
    }
}
