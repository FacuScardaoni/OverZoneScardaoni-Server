package com.OverZone.Scardaoni.server;

/**
 * Punto de entrada del SERVIDOR. Es un programa aparte del juego: no abre
 * ventana, solo administra la partida y se comunica con los clientes.
 *
 * Ejecutar desde el cmd:
 *   gradlew run
 * o, después de "gradlew jar":
 *   java -jar build/libs/OverZone-Server-1.0.0.jar
 */
public class ServerLauncher {

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "OverZone";
        GameServer server = new GameServer(name);
        if (!server.start()) {
            // No se pudo abrir el puerto: el motivo ya se mostró en consola.
            System.exit(1);
        }
        server.runConsole();
    }
}
