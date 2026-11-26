package com.OverZone.Scardaoni.servidor;

public class LanzadorServidor {
    public static void main(String[] argumentos) {
        String nombre = argumentos.length > 0 ? argumentos[0] : "OverZone";
        ServidorJuego servidor = new ServidorJuego(nombre);
        if (!servidor.iniciar()) {
            // No se pudo abrir el puerto: el motivo ya se mostró en consola.
            System.exit(1);
        }
        servidor.correrConsola();
    }
}
