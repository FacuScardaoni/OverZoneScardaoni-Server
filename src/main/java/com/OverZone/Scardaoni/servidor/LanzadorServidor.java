package com.OverZone.Scardaoni.servidor;

/**
 * Punto de entrada del SERVIDOR. Es un programa aparte del juego: no abre
 * ventana, solo administra la partida y se comunica con los clientes.
 *
 * Ejecutar desde el cmd:
 *   gradlew run
 * o, después de "gradlew jar":
 *   java -jar build/libs/OverZone-Server-1.0.0.jar
 */
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
