package com.OverZone.Scardaoni.servidor;

import com.OverZone.Scardaoni.servidor.juego.Jugador;
import com.OverZone.Scardaoni.compartido.FasePartida;
import com.OverZone.Scardaoni.servidor.juego.SimulacionPartida;
import com.OverZone.Scardaoni.compartido.TipoPersonaje;
import com.OverZone.Scardaoni.compartido.ModoJuego;
import com.OverZone.Scardaoni.compartido.EntradaJugador;
import com.OverZone.Scardaoni.compartido.Protocolo;

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

public class ServidorJuego {
    /** Un paquete recibido, guardado para procesarlo en el bucle de juego. */
    private static class MensajeEntrante {
        final String texto;
        final InetAddress direccionIp;
        final int puerto;
        /** Cuándo se puede procesar (se atrasa si se simula retardo). */
        final long vencimientoNanos;

        MensajeEntrante(String texto, InetAddress direccionIp, int puerto, long vencimientoNanos) {
            this.texto = texto;
            this.direccionIp = direccionIp;
            this.puerto = puerto;
            this.vencimientoNanos = vencimientoNanos;
        }
    }

    /** Un paquete a enviar más tarde (solo cuando se simula retardo). */
    private static class MensajeSaliente {
        final byte[] datos;
        final InetAddress direccionIp;
        final int puerto;
        final long vencimientoNanos;

        MensajeSaliente(byte[] datos, InetAddress direccionIp, int puerto, long vencimientoNanos) {
            this.datos = datos;
            this.direccionIp = direccionIp;
            this.puerto = puerto;
            this.vencimientoNanos = vencimientoNanos;
        }
    }

    private final String nombreServidor;
    private final SimulacionPartida partida = new SimulacionPartida();
    private final List<ClienteConectado> clientes = new ArrayList<>();
    /** Cola segura para pasar mensajes del hilo de recepción al del bucle. */
    private final ConcurrentLinkedQueue<MensajeEntrante> bandejaEntrada = new ConcurrentLinkedQueue<>();

    /** Paquetes demorados a propósito; solo los usa el hilo del bucle. */
    private final ArrayDeque<MensajeSaliente> bandejaSalida = new ArrayDeque<>();

    // --- Simulación de una red mala (comandos "retardo" y "perdida") ---
    // Sirve para probar, por ejemplo en la defensa, que el juego sigue
    // funcionando con demoras y paquetes perdidos.
    private volatile int retardoSimuladoMs = 0;
    private volatile int perdidaSimuladaPorcentaje = 0;
    private final Random azar = new Random();

    private DatagramSocket socket;
    private Thread hiloRecepcion;
    private Thread hiloBucle;
    private volatile boolean corriendo = false;
    private long secuenciaEstado = 0;

    public ServidorJuego(String nombreServidor) {
        this.nombreServidor = Protocolo.limpiarNombre(nombreServidor);
    }

    // ------------------------------------------------------------------
    // Inicio y cierre
    // ------------------------------------------------------------------

    /** Abre el puerto y arranca los hilos. Devuelve false si no se pudo. */
    public boolean iniciar() {
        try {
            socket = new DatagramSocket(Protocolo.PUERTO_SERVIDOR);
            socket.setBroadcast(true);
        } catch (BindException e) {
            System.out.println("ERROR: el puerto " + Protocolo.PUERTO_SERVIDOR + " ya esta en uso.");
            System.out.println("Probablemente ya hay un servidor de OverZone abierto en esta computadora.");
            System.out.println("Cerralo antes de abrir otro.");
            return false;
        } catch (SocketException e) {
            System.out.println("ERROR: no se pudo abrir el puerto " + Protocolo.PUERTO_SERVIDOR + ": " + e.getMessage());
            return false;
        }

        corriendo = true;
        hiloRecepcion = new Thread(this::bucleRecepcion, "ServidorRecepcion");
        hiloBucle = new Thread(this::bucleJuego, "ServidorBucleJuego");
        hiloRecepcion.start();
        hiloBucle.start();

        // Si se cierra la consola con Ctrl+C, igual se avisa a los clientes
        // y se libera el puerto.
        Runtime.getRuntime().addShutdownHook(new Thread(this::detener, "ServidorCierre"));

        System.out.println("=== Servidor OverZone \"" + nombreServidor + "\" ===");
        System.out.println("Escuchando en el puerto UDP " + Protocolo.PUERTO_SERVIDOR);
        for (String ip : direccionesLocales()) System.out.println("  IP de esta computadora: " + ip);
        mostrarAyuda();
        return true;
    }

    /** Detiene todo de forma ordenada. Se puede llamar más de una vez. */
    public synchronized void detener() {
        if (!corriendo) return;
        corriendo = false;
        System.out.println("Cerrando servidor...");

        // Avisar a los clientes (dos veces, por si se pierde un paquete).
        List<ClienteConectado> copia;
        synchronized (clientes) {
            copia = new ArrayList<>(clientes);
        }
        byte[] cierre = Protocolo.SHUTDOWN.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 2; i++) {
            for (ClienteConectado c : copia) enviarAhora(cierre, c.direccionIp, c.puerto);
        }

        // Cerrar el socket desbloquea al hilo de recepción, que termina.
        socket.close();
        esperarHilo(hiloBucle);
        esperarHilo(hiloRecepcion);
        System.out.println("Servidor cerrado.");
    }

    public void correrConsola() {
        BufferedReader consola = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        boolean salidaPedida = false;
        try {
            String linea;
            while (corriendo && (linea = consola.readLine()) != null) {
                if (procesarComando(linea.trim().toLowerCase())) {
                    salidaPedida = true;
                    break;
                }
            }
        } catch (IOException e) {
            // Consola no disponible.
        }
        if (!salidaPedida) {
            esperarHilo(hiloBucle, 0); // espera hasta que el servidor se detenga (Ctrl+C)
        }
        detener();
    }

    /** Ejecuta un comando de consola. Devuelve true si se pidió salir. */
    private boolean procesarComando(String linea) {
        try {
            if (linea.equals("salir")) {
                return true;
            } else if (linea.equals("estado")) {
                mostrarEstado();
            } else if (linea.startsWith("retardo ")) {
                retardoSimuladoMs = Math.max(0, Integer.parseInt(linea.substring(8).trim()));
                System.out.println("Retardo simulado: " + retardoSimuladoMs + " ms en cada sentido");
            } else if (linea.startsWith("perdida ")) {
                perdidaSimuladaPorcentaje = Math.max(0, Math.min(100, Integer.parseInt(linea.substring(8).trim())));
                System.out.println("Perdida simulada: " + perdidaSimuladaPorcentaje + "% de los paquetes");
            } else if (linea.equals("normal")) {
                retardoSimuladoMs = 0;
                perdidaSimuladaPorcentaje = 0;
                System.out.println("Red normal (sin retardo ni perdida simulados)");
            } else if (!linea.isEmpty()) {
                mostrarAyuda();
            }
        } catch (NumberFormatException e) {
            System.out.println("Numero invalido.");
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Hilo de recepción
    // ------------------------------------------------------------------

    private void bucleRecepcion() {
        byte[] bufer = new byte[Protocolo.TAMANIO_MAX_PAQUETE];
        while (corriendo) {
            try {
                DatagramPacket paquete = new DatagramPacket(bufer, bufer.length);
                socket.receive(paquete); // se bloquea hasta que llega algo
                if (debeDescartarPorSimulacion()) continue;
                String texto = new String(paquete.getData(), 0, paquete.getLength(), StandardCharsets.UTF_8);
                long vencimiento = System.nanoTime() + retardoSimuladoMs * 1_000_000L;
                bandejaEntrada.add(new MensajeEntrante(texto, paquete.getAddress(), paquete.getPort(), vencimiento));
            } catch (IOException e) {
                // Al cerrar el socket, receive() lanza una excepción: es la
                // forma normal de terminar este hilo.
                if (corriendo) System.out.println("Error recibiendo: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Hilo del bucle de juego
    // ------------------------------------------------------------------

    private void bucleJuego() {
        final long pasoNanos = 1_000_000_000L / Protocolo.PASOS_POR_SEGUNDO;
        final int pasosPorEstado = Protocolo.PASOS_POR_SEGUNDO / Protocolo.ESTADOS_POR_SEGUNDO;
        final float delta = 1f / Protocolo.PASOS_POR_SEGUNDO;
        long proximoPaso = System.nanoTime();
        long pasoActual = 0;

        while (corriendo) {
            FasePartida antes = partida.getFase();
            ModoJuego modoAntes = partida.getModo();
            procesarBandejaEntrada();
            sacarClientesSilenciosos();
            partida.actualizar(delta);
            if (partida.getFase() != antes) {
                System.out.println("Fase: " + antes + " -> " + partida.getFase());
            }
            if (partida.getModo() != modoAntes) {
                System.out.println("Modo: " + modoAntes.nombreVisible + " -> " + partida.getModo().nombreVisible);
            }

            if (pasoActual % pasosPorEstado == 0) difundirEstado();
            vaciarBandejaSalida();
            pasoActual++;

            // Paso de tiempo fijo: la partida avanza exactamente 1/60 de
            // segundo por vuelta, y se espera lo que falte hasta la siguiente.
            proximoPaso += pasoNanos;
            long esperaNanos = proximoPaso - System.nanoTime();
            if (esperaNanos > 0) {
                try {
                    Thread.sleep(esperaNanos / 1_000_000L, (int) (esperaNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    break;
                }
            } else if (esperaNanos < -pasoNanos * 10) {
                // Si el servidor se atrasó mucho (por ejemplo la PC se trabó),
                // no intenta recuperar todas las vueltas perdidas de golpe.
                proximoPaso = System.nanoTime();
            }
        }
    }

    private void procesarBandejaEntrada() {
        MensajeEntrante in;
        long ahora = System.nanoTime();
        // Solo se procesan los mensajes cuyo momento ya llegó (retardo simulado).
        while ((in = bandejaEntrada.peek()) != null && in.vencimientoNanos <= ahora) {
            bandejaEntrada.poll();
            try {
                procesarMensaje(in);
            } catch (RuntimeException e) {
                // Un mensaje mal formado no debe tirar abajo el servidor.
                System.out.println("Mensaje invalido de " + in.direccionIp.getHostAddress() + ": " + in.texto);
            }
        }
    }

    /** Muestra en la consola los pedidos de modo rechazados, para entender qué pasó. */
    private void registrarPedidoModo(ClienteConectado cliente, ModoJuego pedido, SimulacionPartida.ResultadoPedidoModo resultado) {
        String porque;
        switch (resultado) {
            case NO_ES_ANFITRION: porque = "no es el anfitrion"; break;
            case FALTAN_JUGADORES: porque = "hacen falta " + pedido.jugadoresNecesarios + " jugadores"; break;
            case PARTIDA_EMPEZADA: porque = "la partida ya empezo"; break;
            default: return; // aceptado (el cambio ya se muestra), repetido o ya elegido
        }
        System.out.println("Pedido de modo " + pedido.nombreVisible + " de " + cliente.nombre
            + " rechazado: " + porque);
    }

    private void procesarMensaje(MensajeEntrante in) {
        String[] partes = in.texto.split(";", -1);
        String tipo = partes[0];

        if (tipo.equals(Protocolo.DISCOVER)) {
            enviar(Protocolo.unir(Protocolo.SERVER_INFO, nombreServidor, partida.getCantidadJugadores(), Protocolo.MAX_JUGADORES,
                Protocolo.VERSION), in.direccionIp, in.puerto);
            return;
        }

        ClienteConectado cliente = buscarCliente(in.direccionIp, in.puerto);

        if (tipo.equals(Protocolo.CONNECT)) {
            if (cliente != null) {
                // El cliente reintentó porque no le llegó la bienvenida.
                enviar(Protocolo.unir(Protocolo.WELCOME, cliente.jugadorId), in.direccionIp, in.puerto);
                return;
            }
            int versionCliente = partes.length > 2 ? Integer.parseInt(partes[2]) : -1;
            if (versionCliente != Protocolo.VERSION) {
                enviar(Protocolo.unir(Protocolo.REJECT, Protocolo.MOTIVO_VERSION, Protocolo.VERSION), in.direccionIp, in.puerto);
                System.out.println("Rechazado (version " + versionCliente + " distinta de "
                    + Protocolo.VERSION + "): " + in.direccionIp.getHostAddress());
                return;
            }
            if (partida.getCantidadJugadores() >= Protocolo.MAX_JUGADORES) {
                enviar(Protocolo.unir(Protocolo.REJECT, Protocolo.MOTIVO_LLENO), in.direccionIp, in.puerto);
                System.out.println("Rechazado (partida llena): " + in.direccionIp.getHostAddress());
                return;
            }
            String nombre = Protocolo.limpiarNombre(partes.length > 1 ? partes[1] : "");
            Jugador jugador = partida.agregarJugador(nombre);
            ClienteConectado c = new ClienteConectado(in.direccionIp, in.puerto, jugador.getId(), nombre);
            synchronized (clientes) {
                clientes.add(c);
            }
            enviar(Protocolo.unir(Protocolo.WELCOME, jugador.getId()), in.direccionIp, in.puerto);
            System.out.println("Conectado: " + c + " -> " + partida.getCantidadJugadores() + " jugador(es)");
            return;
        }

        if (cliente == null) {
            // Llega algo de alguien que no está en la lista (por ejemplo, el
            // servidor se reinició y el cliente sigue mandando). Se le avisa.
            if (tipo.equals(Protocolo.INPUT) || tipo.equals(Protocolo.PING)) {
                enviar(Protocolo.unir(Protocolo.REJECT, Protocolo.MOTIVO_DESCONOCIDO), in.direccionIp, in.puerto);
            }
            return;
        }

        cliente.ultimoPaqueteNanos = System.nanoTime();

        switch (tipo) {
            case Protocolo.INPUT:
                partida.aplicarEntrada(cliente.jugadorId, EntradaJugador.interpretar(partes));
                break;
            case Protocolo.PING:
                partida.setListo(cliente.jugadorId, "1".equals(partes[1]));
                partida.setPersonaje(cliente.jugadorId, TipoPersonaje.desdeId(Integer.parseInt(partes[2])));
                // Pedido de modo numerado: la simulación lo atiende una sola vez y lo valida.
                ModoJuego pedido = ModoJuego.desdeId(Integer.parseInt(partes[4]));
                SimulacionPartida.ResultadoPedidoModo resultado =
                    partida.pedirModo(cliente.jugadorId, Integer.parseInt(partes[3]), pedido);
                registrarPedidoModo(cliente, pedido, resultado);
                // Se devuelve la misma marca de tiempo: el cliente calcula el retardo.
                enviar(Protocolo.unir(Protocolo.PONG, partes[5]), in.direccionIp, in.puerto);
                break;
            case Protocolo.DISCONNECT:
                quitarCliente(cliente, "se desconecto");
                break;
            default:
                break;
        }
    }

    /** Desconecta a los clientes que no mandan nada hace demasiado tiempo. */
    private void sacarClientesSilenciosos() {
        long ahora = System.nanoTime();
        List<ClienteConectado> silencioso = new ArrayList<>();
        synchronized (clientes) {
            for (ClienteConectado c : clientes) {
                if ((ahora - c.ultimoPaqueteNanos) / 1_000_000L > Protocolo.TIEMPO_ESPERA_CLIENTE_MS) silencioso.add(c);
            }
        }
        for (ClienteConectado c : silencioso) quitarCliente(c, "sin respuesta (tiempo de espera agotado)");
    }

    private void quitarCliente(ClienteConectado cliente, String motivo) {
        synchronized (clientes) {
            clientes.remove(cliente);
        }
        partida.quitarJugador(cliente.jugadorId);
        System.out.println("Desconectado: " + cliente + " - " + motivo + " -> "
            + partida.getCantidadJugadores() + " jugador(es)");
    }

    private void difundirEstado() {
        String mensaje = partida.crearEstado(++secuenciaEstado).aMensaje();
        synchronized (clientes) {
            for (ClienteConectado c : clientes) enviar(mensaje, c.direccionIp, c.puerto);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Envía un mensaje (desde el hilo del bucle), aplicando la red simulada si está activa. */
    private void enviar(String mensaje, InetAddress direccionIp, int puerto) {
        if (debeDescartarPorSimulacion()) return;
        byte[] datos = mensaje.getBytes(StandardCharsets.UTF_8);
        if (retardoSimuladoMs > 0) {
            bandejaSalida.add(new MensajeSaliente(datos, direccionIp, puerto, System.nanoTime() + retardoSimuladoMs * 1_000_000L));
        } else {
            enviarAhora(datos, direccionIp, puerto);
        }
    }

    private void enviarAhora(byte[] datos, InetAddress direccionIp, int puerto) {
        try {
            socket.send(new DatagramPacket(datos, datos.length, direccionIp, puerto));
        } catch (IOException e) {
            // Si un envío falla, el siguiente estado lo reemplaza.
        }
    }

    /** Envía los paquetes demorados cuyo momento ya llegó. */
    private void vaciarBandejaSalida() {
        long ahora = System.nanoTime();
        while (!bandejaSalida.isEmpty() && bandejaSalida.peek().vencimientoNanos <= ahora) {
            MensajeSaliente o = bandejaSalida.poll();
            enviarAhora(o.datos, o.direccionIp, o.puerto);
        }
    }

    private boolean debeDescartarPorSimulacion() {
        int perdida = perdidaSimuladaPorcentaje;
        return perdida > 0 && azar.nextInt(100) < perdida;
    }

    private static void mostrarAyuda() {
        System.out.println("Comandos:");
        System.out.println("  estado        muestra la fase y los jugadores conectados");
        System.out.println("  retardo <ms>  simula una red lenta (ej: retardo 150)");
        System.out.println("  perdida <%>   simula paquetes perdidos (ej: perdida 20)");
        System.out.println("  normal        quita el retardo y la perdida simulados");
        System.out.println("  salir         cierra el servidor avisando a los clientes");
    }

    private ClienteConectado buscarCliente(InetAddress direccionIp, int puerto) {
        synchronized (clientes) {
            for (ClienteConectado c : clientes) {
                if (c.esDe(direccionIp, puerto)) return c;
            }
        }
        return null;
    }

    private void mostrarEstado() {
        System.out.println("Fase: " + partida.getFase() + " | Modo: " + partida.getModo().nombreVisible
            + " | Jugadores: " + partida.getCantidadJugadores() + "/" + Protocolo.MAX_JUGADORES);
        synchronized (clientes) {
            for (ClienteConectado c : clientes) {
                long ms = (System.nanoTime() - c.ultimoPaqueteNanos) / 1_000_000L;
                System.out.println("  " + c + " - ultimo mensaje hace " + ms + " ms");
            }
        }
    }

    private static List<String> direccionesLocales() {
        List<String> resultado = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfacesRed = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfacesRed)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                Iterator<InetAddress> it = Collections.list(ni.getInetAddresses()).iterator();
                while (it.hasNext()) {
                    InetAddress a = it.next();
                    if (a.getAddress().length == 4) resultado.add(a.getHostAddress() + "  (" + ni.getDisplayName() + ")");
                }
            }
        } catch (SocketException e) {
            // Sin datos de la red: no se muestran las IP.
        }
        return resultado;
    }

    private static void esperarHilo(Thread t) {
        esperarHilo(t, 2000);
    }

    /** Espera a que el hilo termine; con 0 espera sin límite. */
    private static void esperarHilo(Thread t, long milis) {
        if (t == null || t == Thread.currentThread()) return;
        try {
            t.join(milis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
