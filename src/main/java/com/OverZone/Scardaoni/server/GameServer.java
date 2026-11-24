package com.OverZone.Scardaoni.server;

import com.OverZone.Scardaoni.server.game.Player;
import com.OverZone.Scardaoni.shared.MatchPhase;
import com.OverZone.Scardaoni.server.game.MatchSimulation;
import com.OverZone.Scardaoni.shared.CharacterType;
import com.OverZone.Scardaoni.shared.GameMode;
import com.OverZone.Scardaoni.shared.PlayerInput;
import com.OverZone.Scardaoni.shared.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Servidor autoritativo de OverZone.
 *
 * Usa dos hilos:
 *  - Hilo de RECEPCIÓN: espera paquetes UDP y los deja en una cola.
 *  - Hilo del BUCLE DE JUEGO: 60 veces por segundo saca los mensajes de la
 *    cola, los procesa, avanza la partida y, 30 veces por segundo, envía el
 *    estado a todos los clientes.
 *
 * Solo el hilo del bucle toca la partida (MatchSimulation), así nunca hay
 * dos hilos modificándola al mismo tiempo.
 */
public class GameServer {

    /** Un paquete recibido, guardado para procesarlo en el bucle de juego. */
    private static class Incoming {
        final String text;
        final InetAddress address;
        final int port;
        /** Cuándo se puede procesar (se atrasa si se simula retardo). */
        final long dueNanos;

        Incoming(String text, InetAddress address, int port, long dueNanos) {
            this.text = text;
            this.address = address;
            this.port = port;
            this.dueNanos = dueNanos;
        }
    }

    /** Un paquete a enviar más tarde (solo cuando se simula retardo). */
    private static class Outgoing {
        final byte[] data;
        final InetAddress address;
        final int port;
        final long dueNanos;

        Outgoing(byte[] data, InetAddress address, int port, long dueNanos) {
            this.data = data;
            this.address = address;
            this.port = port;
            this.dueNanos = dueNanos;
        }
    }

    private final String serverName;
    private final MatchSimulation match = new MatchSimulation();
    private final List<ConnectedClient> clients = new ArrayList<>();
    /** Cola segura para pasar mensajes del hilo de recepción al del bucle. */
    private final ConcurrentLinkedQueue<Incoming> inbox = new ConcurrentLinkedQueue<>();

    /** Paquetes demorados a propósito; solo los usa el hilo del bucle. */
    private final ArrayDeque<Outgoing> outbox = new ArrayDeque<>();

    // --- Simulación de una red mala (comandos "retardo" y "perdida") ---
    // Sirve para probar, por ejemplo en la defensa, que el juego sigue
    // funcionando con demoras y paquetes perdidos.
    private volatile int simulatedDelayMs = 0;
    private volatile int simulatedLossPercent = 0;
    private final Random random = new Random();

    private DatagramSocket socket;
    private Thread receiveThread;
    private Thread loopThread;
    private volatile boolean running = false;
    private long stateSeq = 0;

    public GameServer(String serverName) {
        this.serverName = Protocol.sanitizeName(serverName);
    }

    // ------------------------------------------------------------------
    // Inicio y cierre
    // ------------------------------------------------------------------

    /** Abre el puerto y arranca los hilos. Devuelve false si no se pudo. */
    public boolean start() {
        try {
            socket = new DatagramSocket(Protocol.SERVER_PORT);
            socket.setBroadcast(true);
        } catch (BindException e) {
            System.out.println("ERROR: el puerto " + Protocol.SERVER_PORT + " ya esta en uso.");
            System.out.println("Probablemente ya hay un servidor de OverZone abierto en esta computadora.");
            System.out.println("Cerralo antes de abrir otro.");
            return false;
        } catch (SocketException e) {
            System.out.println("ERROR: no se pudo abrir el puerto " + Protocol.SERVER_PORT + ": " + e.getMessage());
            return false;
        }

        running = true;
        receiveThread = new Thread(this::receiveLoop, "ServerReceive");
        loopThread = new Thread(this::gameLoop, "ServerGameLoop");
        receiveThread.start();
        loopThread.start();

        // Si se cierra la consola con Ctrl+C, igual se avisa a los clientes
        // y se libera el puerto.
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "ServerShutdown"));

        System.out.println("=== Servidor OverZone \"" + serverName + "\" ===");
        System.out.println("Escuchando en el puerto UDP " + Protocol.SERVER_PORT);
        for (String ip : localAddresses()) System.out.println("  IP de esta computadora: " + ip);
        printHelp();
        return true;
    }

    /** Detiene todo de forma ordenada. Se puede llamar más de una vez. */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        System.out.println("Cerrando servidor...");

        // Avisar a los clientes (dos veces, por si se pierde un paquete).
        List<ConnectedClient> copy;
        synchronized (clients) {
            copy = new ArrayList<>(clients);
        }
        byte[] shutdown = Protocol.SHUTDOWN.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 2; i++) {
            for (ConnectedClient c : copy) sendNow(shutdown, c.address, c.port);
        }

        // Cerrar el socket desbloquea al hilo de recepción, que termina.
        socket.close();
        joinQuietly(loopThread);
        joinQuietly(receiveThread);
        System.out.println("Servidor cerrado.");
    }

    /**
     * Lee comandos escritos en la consola. Termina cuando se escribe "salir".
     * Si no hay consola (la entrada se cerró), el servidor sigue funcionando
     * hasta que se cierre con Ctrl+C.
     */
    public void runConsole() {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        boolean exitRequested = false;
        try {
            String line;
            while (running && (line = console.readLine()) != null) {
                if (handleCommand(line.trim().toLowerCase())) {
                    exitRequested = true;
                    break;
                }
            }
        } catch (IOException e) {
            // Consola no disponible.
        }
        if (!exitRequested) {
            joinQuietly(loopThread, 0); // espera hasta que el servidor se detenga (Ctrl+C)
        }
        stop();
    }

    /** Ejecuta un comando de consola. Devuelve true si se pidió salir. */
    private boolean handleCommand(String line) {
        try {
            if (line.equals("salir")) {
                return true;
            } else if (line.equals("estado")) {
                printStatus();
            } else if (line.startsWith("retardo ")) {
                simulatedDelayMs = Math.max(0, Integer.parseInt(line.substring(8).trim()));
                System.out.println("Retardo simulado: " + simulatedDelayMs + " ms en cada sentido");
            } else if (line.startsWith("perdida ")) {
                simulatedLossPercent = Math.max(0, Math.min(100, Integer.parseInt(line.substring(8).trim())));
                System.out.println("Perdida simulada: " + simulatedLossPercent + "% de los paquetes");
            } else if (line.equals("normal")) {
                simulatedDelayMs = 0;
                simulatedLossPercent = 0;
                System.out.println("Red normal (sin retardo ni perdida simulados)");
            } else if (!line.isEmpty()) {
                printHelp();
            }
        } catch (NumberFormatException e) {
            System.out.println("Numero invalido.");
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Hilo de recepción
    // ------------------------------------------------------------------

    private void receiveLoop() {
        byte[] buffer = new byte[Protocol.MAX_PACKET_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // se bloquea hasta que llega algo
                if (shouldDropForSimulation()) continue;
                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                long due = System.nanoTime() + simulatedDelayMs * 1_000_000L;
                inbox.add(new Incoming(text, packet.getAddress(), packet.getPort(), due));
            } catch (IOException e) {
                // Al cerrar el socket, receive() lanza una excepción: es la
                // forma normal de terminar este hilo.
                if (running) System.out.println("Error recibiendo: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Hilo del bucle de juego
    // ------------------------------------------------------------------

    private void gameLoop() {
        final long tickNanos = 1_000_000_000L / Protocol.TICK_RATE;
        final int ticksPerState = Protocol.TICK_RATE / Protocol.STATE_RATE;
        final float delta = 1f / Protocol.TICK_RATE;
        long nextTick = System.nanoTime();
        long tick = 0;

        while (running) {
            MatchPhase before = match.getPhase();
            GameMode modeBefore = match.getMode();
            processInbox();
            dropSilentClients();
            match.update(delta);
            if (match.getPhase() != before) {
                System.out.println("Fase: " + before + " -> " + match.getPhase());
            }
            if (match.getMode() != modeBefore) {
                System.out.println("Modo: " + modeBefore.displayName + " -> " + match.getMode().displayName);
            }

            if (tick % ticksPerState == 0) broadcastState();
            flushOutbox();
            tick++;

            // Paso de tiempo fijo: la partida avanza exactamente 1/60 de
            // segundo por vuelta, y se espera lo que falte hasta la siguiente.
            nextTick += tickNanos;
            long sleepNanos = nextTick - System.nanoTime();
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    break;
                }
            } else if (sleepNanos < -tickNanos * 10) {
                // Si el servidor se atrasó mucho (por ejemplo la PC se trabó),
                // no intenta recuperar todas las vueltas perdidas de golpe.
                nextTick = System.nanoTime();
            }
        }
    }

    private void processInbox() {
        Incoming in;
        long now = System.nanoTime();
        // Solo se procesan los mensajes cuyo momento ya llegó (retardo simulado).
        while ((in = inbox.peek()) != null && in.dueNanos <= now) {
            inbox.poll();
            try {
                handleMessage(in);
            } catch (RuntimeException e) {
                // Un mensaje mal formado no debe tirar abajo el servidor.
                System.out.println("Mensaje invalido de " + in.address.getHostAddress() + ": " + in.text);
            }
        }
    }

    private void handleMessage(Incoming in) {
        String[] parts = in.text.split(";", -1);
        String type = parts[0];

        if (type.equals(Protocol.DISCOVER)) {
            send(Protocol.join(Protocol.SERVER_INFO, serverName, match.getPlayerCount(), Protocol.MAX_PLAYERS,
                Protocol.VERSION), in.address, in.port);
            return;
        }

        ConnectedClient client = findClient(in.address, in.port);

        if (type.equals(Protocol.CONNECT)) {
            if (client != null) {
                // El cliente reintentó porque no le llegó la bienvenida.
                send(Protocol.join(Protocol.WELCOME, client.playerId), in.address, in.port);
                return;
            }
            int clientVersion = parts.length > 2 ? Integer.parseInt(parts[2]) : -1;
            if (clientVersion != Protocol.VERSION) {
                send(Protocol.join(Protocol.REJECT, Protocol.REASON_VERSION, Protocol.VERSION), in.address, in.port);
                System.out.println("Rechazado (version " + clientVersion + " distinta de "
                    + Protocol.VERSION + "): " + in.address.getHostAddress());
                return;
            }
            if (match.getPlayerCount() >= Protocol.MAX_PLAYERS) {
                send(Protocol.join(Protocol.REJECT, Protocol.REASON_FULL), in.address, in.port);
                System.out.println("Rechazado (partida llena): " + in.address.getHostAddress());
                return;
            }
            String name = Protocol.sanitizeName(parts.length > 1 ? parts[1] : "");
            Player player = match.addPlayer(name);
            ConnectedClient c = new ConnectedClient(in.address, in.port, player.getId(), name);
            synchronized (clients) {
                clients.add(c);
            }
            send(Protocol.join(Protocol.WELCOME, player.getId()), in.address, in.port);
            System.out.println("Conectado: " + c + " -> " + match.getPlayerCount() + " jugador(es)");
            return;
        }

        if (client == null) {
            // Llega algo de alguien que no está en la lista (por ejemplo, el
            // servidor se reinició y el cliente sigue mandando). Se le avisa.
            if (type.equals(Protocol.INPUT) || type.equals(Protocol.PING)) {
                send(Protocol.join(Protocol.REJECT, Protocol.REASON_UNKNOWN), in.address, in.port);
            }
            return;
        }

        client.lastPacketNanos = System.nanoTime();

        switch (type) {
            case Protocol.INPUT:
                match.applyInput(client.playerId, PlayerInput.parse(parts));
                break;
            case Protocol.PING:
                match.setReady(client.playerId, "1".equals(parts[1]));
                match.setCharacter(client.playerId, CharacterType.fromId(Integer.parseInt(parts[2])));
                // Solo el anfitrión puede cambiar el modo; la simulación lo valida.
                match.requestMode(client.playerId, GameMode.fromId(Integer.parseInt(parts[3])));
                // Se devuelve la misma marca de tiempo: el cliente calcula el retardo.
                send(Protocol.join(Protocol.PONG, parts[4]), in.address, in.port);
                break;
            case Protocol.DISCONNECT:
                removeClient(client, "se desconecto");
                break;
            default:
                break;
        }
    }

    /** Desconecta a los clientes que no mandan nada hace demasiado tiempo. */
    private void dropSilentClients() {
        long now = System.nanoTime();
        List<ConnectedClient> silent = new ArrayList<>();
        synchronized (clients) {
            for (ConnectedClient c : clients) {
                if ((now - c.lastPacketNanos) / 1_000_000L > Protocol.CLIENT_TIMEOUT_MS) silent.add(c);
            }
        }
        for (ConnectedClient c : silent) removeClient(c, "sin respuesta (tiempo de espera agotado)");
    }

    private void removeClient(ConnectedClient client, String reason) {
        synchronized (clients) {
            clients.remove(client);
        }
        match.removePlayer(client.playerId);
        System.out.println("Desconectado: " + client + " - " + reason + " -> "
            + match.getPlayerCount() + " jugador(es)");
    }

    private void broadcastState() {
        String message = match.createSnapshot(++stateSeq).toMessage();
        synchronized (clients) {
            for (ConnectedClient c : clients) send(message, c.address, c.port);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Envía un mensaje (desde el hilo del bucle), aplicando la red simulada si está activa. */
    private void send(String message, InetAddress address, int port) {
        if (shouldDropForSimulation()) return;
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        if (simulatedDelayMs > 0) {
            outbox.add(new Outgoing(data, address, port, System.nanoTime() + simulatedDelayMs * 1_000_000L));
        } else {
            sendNow(data, address, port);
        }
    }

    private void sendNow(byte[] data, InetAddress address, int port) {
        try {
            socket.send(new DatagramPacket(data, data.length, address, port));
        } catch (IOException e) {
            // Si un envío falla, el siguiente estado lo reemplaza.
        }
    }

    /** Envía los paquetes demorados cuyo momento ya llegó. */
    private void flushOutbox() {
        long now = System.nanoTime();
        while (!outbox.isEmpty() && outbox.peek().dueNanos <= now) {
            Outgoing o = outbox.poll();
            sendNow(o.data, o.address, o.port);
        }
    }

    private boolean shouldDropForSimulation() {
        int loss = simulatedLossPercent;
        return loss > 0 && random.nextInt(100) < loss;
    }

    private static void printHelp() {
        System.out.println("Comandos:");
        System.out.println("  estado        muestra la fase y los jugadores conectados");
        System.out.println("  retardo <ms>  simula una red lenta (ej: retardo 150)");
        System.out.println("  perdida <%>   simula paquetes perdidos (ej: perdida 20)");
        System.out.println("  normal        quita el retardo y la perdida simulados");
        System.out.println("  salir         cierra el servidor avisando a los clientes");
    }

    private ConnectedClient findClient(InetAddress address, int port) {
        synchronized (clients) {
            for (ConnectedClient c : clients) {
                if (c.isFrom(address, port)) return c;
            }
        }
        return null;
    }

    private void printStatus() {
        System.out.println("Fase: " + match.getPhase() + " | Modo: " + match.getMode().displayName
            + " | Jugadores: " + match.getPlayerCount() + "/" + Protocol.MAX_PLAYERS);
        synchronized (clients) {
            for (ConnectedClient c : clients) {
                long ms = (System.nanoTime() - c.lastPacketNanos) / 1_000_000L;
                System.out.println("  " + c + " - ultimo mensaje hace " + ms + " ms");
            }
        }
    }

    private static List<String> localAddresses() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                Iterator<InetAddress> it = Collections.list(ni.getInetAddresses()).iterator();
                while (it.hasNext()) {
                    InetAddress a = it.next();
                    if (a.getAddress().length == 4) result.add(a.getHostAddress() + "  (" + ni.getDisplayName() + ")");
                }
            }
        } catch (SocketException e) {
            // Sin datos de la red: no se muestran las IP.
        }
        return result;
    }

    private static void joinQuietly(Thread t) {
        joinQuietly(t, 2000);
    }

    /** Espera a que el hilo termine; con 0 espera sin límite. */
    private static void joinQuietly(Thread t, long millis) {
        if (t == null || t == Thread.currentThread()) return;
        try {
            t.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
