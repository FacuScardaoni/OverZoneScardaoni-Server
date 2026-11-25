package com.OverZone.Scardaoni.servidor.juego;

import com.OverZone.Scardaoni.compartido.Direccion;
import com.OverZone.Scardaoni.compartido.TipoPersonaje;
import com.OverZone.Scardaoni.compartido.ConstantesJuego;
import com.OverZone.Scardaoni.compartido.ModoJuego;
import com.OverZone.Scardaoni.compartido.FasePartida;
import com.OverZone.Scardaoni.compartido.Equipo;
import com.OverZone.Scardaoni.compartido.EntradaJugador;
import com.OverZone.Scardaoni.compartido.TipoPotenciador;
import com.OverZone.Scardaoni.compartido.Protocolo;
import com.OverZone.Scardaoni.compartido.EstadoMundo;
import com.OverZone.Scardaoni.compartido.Arena;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * La partida real. Solo existe en el SERVIDOR.
 *
 * Contiene la lógica que antes estaba en PantallaJuego (movimiento con
 * colisiones, habilidades, disparos, daño), aplicada a hasta 4 jugadores,
 * con eliminaciones, reapariciones, etapas de partida y dos modos de juego:
 *
 *   - Todos contra todos: cada uno juega solo. El campo "team" de cada
 *     jugador es su color (0 a 3). Gana el que tenga más eliminaciones.
 *   - Equipos 2v2: azul contra rojo. Solo con exactamente 4 jugadores.
 *
 * El modo lo elige el anfitrión (el primero de la lista de jugadores). El
 * servidor decide si el pedido es válido: con menos de 4 jugadores no deja
 * elegir Equipos.
 *
 * No sabe nada de la red ni de la pantalla: recibe entradas (aplicarEntrada),
 * avanza en el tiempo (update) y entrega una foto del estado (crearEstado).
 * Por eso la usa un solo hilo, el del bucle del servidor.
 */
public class SimulacionPartida {

    public static final float DURACION_PARTIDA = 120f;
    public static final float DURACION_CUENTA_REGRESIVA = 3f;
    public static final float DURACION_RESULTADOS = 8f;
    public static final int MIN_JUGADORES = ConstantesJuego.MIN_JUGADORES;
    /** Si no llegan entradas de un jugador en este tiempo, se queda quieto y deja de disparar. */
    public static final float TIEMPO_ENTRADA_VIEJA = 0.25f;

    private static final float APARICION_AZUL_X = 90f;
    private static final float APARICION_ROJO_X = Arena.ANCHO - 90f;

    /** Puntos de aparición de Todos contra todos: uno cerca de cada esquina. */
    private static final float[][] APARICIONES_TODOS_CONTRA_TODOS = {
        {90f, 110f},
        {Arena.ANCHO - 90f, Arena.ALTO - 110f},
        {90f, Arena.ALTO - 110f},
        {Arena.ANCHO - 90f, 110f},
    };

    /** Lo que el servidor guarda de cada jugador además de su entidad. */
    private static class Lugar {
        final Jugador jugador;
        EntradaJugador entrada = EntradaJugador.NINGUNO;
        long ultimaSecuenciaEntrada = -1;
        float edadEntrada = 0f;
        int ultimoContadorHabilidad = 0;
        float recargaDisparo = 0f;
        boolean listo = false;
        /** Número del último pedido de modo atendido (cada pedido se atiende una sola vez). */
        int ultimoPedidoModo = 0;

        Lugar(Jugador jugador) {
            this.jugador = jugador;
        }
    }

    private final Arena arena = new Arena();
    /** Ordenados por orden de llegada. */
    private final List<Lugar> lugares = new ArrayList<>();
    private final List<Proyectil> proyectiles = new ArrayList<>();
    private final List<ZonaRegeneracion> zonas = new ArrayList<>();
    private final List<Potenciador> potenciadoresMapa = new ArrayList<>();
    /** Tiempo que falta para que aparezca el próximo Power-Up. */
    private float tiempoPotenciador = TipoPotenciador.DEMORA_PRIMERA_APARICION;
    private int proximoPotenciadorId = 1;
    private final Random azar = new Random();

    private ModoJuego modo = ModoJuego.TODOS_CONTRA_TODOS;
    private FasePartida fase = FasePartida.SALA;
    private float tiempoFase = 0f;
    private int puntosAzul = 0, puntosRojo = 0;
    /** Equipo ganador (Equipos) o id del jugador ganador (Todos contra todos); -1 si empate. */
    private int ganador = Equipo.NINGUNO;
    private String motivoFin = "-";

    private int proximoJugadorId = 1;
    private int proximoProyectilId = 1;
    private int proximaZonaId = 1;

    // ------------------------------------------------------------------
    // Jugadores que entran y salen
    // ------------------------------------------------------------------

    /** Agrega un jugador nuevo y le asigna equipo (o color) y lugar de aparición. */
    public Jugador agregarJugador(String nombre) {
        int equipo = modo == ModoJuego.EQUIPOS
            ? (contarEquipo(Equipo.AZUL) <= contarEquipo(Equipo.ROJO) ? Equipo.AZUL : Equipo.ROJO)
            : primerColorLibre();
        Vector2 aparicion = modo == ModoJuego.EQUIPOS ? puntoAparicionPara(equipo, contarEquipo(equipo)) : aparicionTodosContraTodos(equipo);
        Jugador jugador = new Jugador(proximoJugadorId++, nombre, equipo, aparicion.x, aparicion.y);
        lugares.add(new Lugar(jugador));
        if (fase == FasePartida.SALA || fase == FasePartida.CUENTA_REGRESIVA) {
            asignarEquipos();
        }
        return jugador;
    }

    public void quitarJugador(int jugadorId) {
        Lugar lugar = buscarLugar(jugadorId);
        if (lugar == null) return;
        lugares.remove(lugar);
        // Si se va Iris, su campo desaparece con ella.
        zonas.removeIf(z -> z.getDuenioId() == jugadorId);

        if (fase == FasePartida.SALA || fase == FasePartida.CUENTA_REGRESIVA) {
            // Si ya no hay 4 jugadores, el modo por equipos deja de estar permitido.
            if (!modo.permite(lugares.size())) modo = ModoJuego.TODOS_CONTRA_TODOS;
            asignarEquipos();
        } else if (fase == FasePartida.JUGANDO && !hayJugadoresParaSeguir()) {
            // Sin rivales no tiene sentido seguir.
            terminarPartida("PLAYERS");
        }
    }

    /** Durante la partida: ¿quedan rivales para seguir jugando? */
    private boolean hayJugadoresParaSeguir() {
        if (modo == ModoJuego.EQUIPOS) {
            return contarEquipo(Equipo.AZUL) > 0 && contarEquipo(Equipo.ROJO) > 0;
        }
        return lugares.size() >= 2;
    }

    public boolean tieneJugador(int jugadorId) {
        return buscarLugar(jugadorId) != null;
    }

    public int getCantidadJugadores() {
        return lugares.size();
    }

    /** El anfitrión es el primero de la lista (el que lleva más tiempo conectado). */
    public int getAnfitrionId() {
        return lugares.isEmpty() ? -1 : lugares.get(0).jugador.getId();
    }

    public ModoJuego getModo() {
        return modo;
    }

    // ------------------------------------------------------------------
    // Pedidos de los clientes
    // ------------------------------------------------------------------

    public void aplicarEntrada(int jugadorId, EntradaJugador entrada) {
        Lugar lugar = buscarLugar(jugadorId);
        if (lugar == null) return;
        // UDP no garantiza el orden: una entrada vieja que llega tarde se descarta.
        if (entrada.secuencia <= lugar.ultimaSecuenciaEntrada) return;
        lugar.ultimaSecuenciaEntrada = entrada.secuencia;
        lugar.entrada = entrada;
        lugar.edadEntrada = 0f;
    }

    public void setListo(int jugadorId, boolean listo) {
        Lugar lugar = buscarLugar(jugadorId);
        if (lugar != null) lugar.listo = listo;
    }

    /**
     * Cambia el personaje de un jugador. Solo se permite en la sala de
     * espera: durante la partida, el pedido se ignora.
     */
    public void setPersonaje(int jugadorId, TipoPersonaje personaje) {
        if (fase != FasePartida.SALA && fase != FasePartida.CUENTA_REGRESIVA) return;
        Lugar lugar = buscarLugar(jugadorId);
        if (lugar != null && lugar.jugador.getPersonaje() != personaje) {
            lugar.jugador.setPersonaje(personaje);
        }
    }

    /** Resultado de un pedido de cambio de modo (para mostrarlo en la consola). */
    public enum ResultadoPedidoModo {
        /** No es un pedido nuevo (es la repetición de uno ya atendido). */
        REPETIDO,
        ACEPTADO,
        YA_ELEGIDO,
        NO_ES_ANFITRION,
        FALTAN_JUGADORES,
        PARTIDA_EMPEZADA
    }

    /**
     * Pedido de cambio de modo. Cada pedido trae un número que el cliente
     * sube cada vez que el jugador elige un modo (como el contador de la
     * habilidad). El pedido viaja repetido en todos los PING: el servidor
     * atiende cada número una sola vez, así una pérdida de paquetes no lo
     * hace perder y una repetición no lo aplica dos veces.
     *
     * Se acepta solo si:
     *   - viene del anfitrión,
     *   - la partida no empezó (sala de espera o cuenta regresiva), y
     *   - el modo se puede jugar con los jugadores que hay (Equipos: 4 exactos).
     */
    public ResultadoPedidoModo pedirModo(int jugadorId, int numeroPedido, ModoJuego pedido) {
        Lugar lugar = buscarLugar(jugadorId);
        if (lugar == null || numeroPedido <= lugar.ultimoPedidoModo) return ResultadoPedidoModo.REPETIDO;
        lugar.ultimoPedidoModo = numeroPedido;
        if (jugadorId != getAnfitrionId()) return ResultadoPedidoModo.NO_ES_ANFITRION;
        if (fase != FasePartida.SALA && fase != FasePartida.CUENTA_REGRESIVA) return ResultadoPedidoModo.PARTIDA_EMPEZADA;
        if (pedido == modo) return ResultadoPedidoModo.YA_ELEGIDO;
        if (!pedido.permite(lugares.size())) return ResultadoPedidoModo.FALTAN_JUGADORES;
        modo = pedido;
        // Al cambiar el modo se vuelve a la sala, así nadie arranca sin enterarse.
        fase = FasePartida.SALA;
        asignarEquipos();
        return ResultadoPedidoModo.ACEPTADO;
    }

    // ------------------------------------------------------------------
    // Avance del tiempo
    // ------------------------------------------------------------------

    public void actualizar(float delta) {
        switch (fase) {
            case SALA:
                if (puedeEmpezar()) {
                    fase = FasePartida.CUENTA_REGRESIVA;
                    tiempoFase = DURACION_CUENTA_REGRESIVA;
                }
                break;
            case CUENTA_REGRESIVA:
                if (!puedeEmpezar()) {
                    fase = FasePartida.SALA;
                    break;
                }
                tiempoFase -= delta;
                if (tiempoFase <= 0) empezarPartida();
                break;
            case JUGANDO:
                actualizarJugando(delta);
                break;
            case RESULTADOS:
                tiempoFase -= delta;
                if (tiempoFase <= 0) volverASala();
                break;
        }
    }

    private boolean puedeEmpezar() {
        if (!modo.permite(lugares.size())) return false;
        if (modo == ModoJuego.EQUIPOS && (contarEquipo(Equipo.AZUL) != 2 || contarEquipo(Equipo.ROJO) != 2)) return false;
        for (Lugar s : lugares) {
            if (!s.listo) return false;
        }
        return true;
    }

    private void empezarPartida() {
        fase = FasePartida.JUGANDO;
        tiempoFase = DURACION_PARTIDA;
        puntosAzul = 0;
        puntosRojo = 0;
        ganador = Equipo.NINGUNO;
        motivoFin = "-";
        proyectiles.clear();
        zonas.clear();
        potenciadoresMapa.clear();
        tiempoPotenciador = TipoPotenciador.DEMORA_PRIMERA_APARICION;
        int indiceAzul = 0, indiceRojo = 0;
        for (Lugar s : lugares) {
            int equipo = s.jugador.getEquipo();
            Vector2 aparicion;
            if (modo == ModoJuego.EQUIPOS) {
                aparicion = puntoAparicionPara(equipo, equipo == Equipo.AZUL ? indiceAzul++ : indiceRojo++);
            } else {
                aparicion = aparicionTodosContraTodos(equipo);
            }
            s.jugador.reiniciarParaPartida(aparicion.x, aparicion.y);
            s.recargaDisparo = 0f;
            // Lo que se haya pedido antes de empezar no cuenta como habilidad.
            s.ultimoContadorHabilidad = s.entrada.contadorHabilidad;
        }
    }

    private void terminarPartida(String motivo) {
        fase = FasePartida.RESULTADOS;
        tiempoFase = DURACION_RESULTADOS;
        motivoFin = motivo;
        if (modo == ModoJuego.EQUIPOS) {
            if (puntosAzul > puntosRojo) ganador = Equipo.AZUL;
            else if (puntosRojo > puntosAzul) ganador = Equipo.ROJO;
            else ganador = Equipo.NINGUNO;
        } else {
            // Gana el jugador con más eliminaciones; si hay dos empatados arriba, es empate.
            ganador = Equipo.NINGUNO;
            int mejor = -1;
            boolean empate = false;
            for (Lugar s : lugares) {
                int k = s.jugador.getEliminaciones();
                if (k > mejor) {
                    mejor = k;
                    ganador = s.jugador.getId();
                    empate = false;
                } else if (k == mejor) {
                    empate = true;
                }
            }
            if (empate) ganador = Equipo.NINGUNO;
        }
        proyectiles.clear();
        zonas.clear();
        potenciadoresMapa.clear();
    }

    private void volverASala() {
        fase = FasePartida.SALA;
        tiempoFase = 0f;
        for (Lugar s : lugares) s.listo = false;
        if (!modo.permite(lugares.size())) modo = ModoJuego.TODOS_CONTRA_TODOS;
        asignarEquipos();
    }

    private void actualizarJugando(float delta) {
        tiempoFase -= delta;

        for (Lugar s : lugares) {
            Jugador p = s.jugador;
            s.edadEntrada += delta;
            // Si el cliente dejó de mandar entradas (pausa, paquetes perdidos),
            // el personaje se detiene en lugar de seguir con la última orden.
            EntradaJugador in = s.edadEntrada > TIEMPO_ENTRADA_VIEJA ? s.entrada.sinAcciones() : s.entrada;

            p.actualizar(delta);

            if (p.puedeReaparecer()) {
                Vector2 aparicion = puntoReaparicionPara(p);
                p.reaparecer(aparicion.x, aparicion.y);
            }

            if (p.estaVivo()) {
                procesarMovimiento(s, in, delta);
                procesarDisparo(s, in, delta);
            } else {
                s.ultimoContadorHabilidad = in.contadorHabilidad;
            }
        }

        actualizarZonas(delta);
        actualizarPotenciadores(delta);
        actualizarProyectiles(delta);
        resolverColisiones();

        if (tiempoFase <= 0) {
            tiempoFase = 0;
            terminarPartida("TIME");
        }
    }

    private void procesarMovimiento(Lugar s, EntradaJugador in, float delta) {
        Jugador jugador = s.jugador;
        boolean apretandoTeclasMovimiento = in.moverX != 0 || in.moverY != 0;
        float apuntarDx = in.apuntarX - jugador.getX();
        float apuntarDy = in.apuntarY - jugador.getY();

        // Habilidad: hubo un pedido nuevo si el contador creció.
        if (in.contadorHabilidad != s.ultimoContadorHabilidad) {
            s.ultimoContadorHabilidad = in.contadorHabilidad;
            switch (jugador.getPersonaje()) {
                case VOLT: {
                    // Impulso: hacia donde camina o, si está quieto, hacia el mouse.
                    float impulsoX = apretandoTeclasMovimiento ? in.moverX : apuntarDx;
                    float impulsoY = apretandoTeclasMovimiento ? in.moverY : apuntarDy;
                    jugador.intentarUsarHabilidad(impulsoX, impulsoY);
                    break;
                }
                case BRUTUS:
                    // Escudo: hacia donde apunta el mouse.
                    jugador.intentarUsarHabilidad(apuntarDx, apuntarDy);
                    break;
                case IRIS:
                    // Campo de Regeneración: se crea donde está parada Iris.
                    if (jugador.intentarUsarHabilidad(0, 0)) {
                        zonas.add(new ZonaRegeneracion(proximaZonaId++, jugador.getId(), jugador.getEquipo(),
                            jugador.getX(), jugador.getY(), jugador.getPersonaje().duracionHabilidad));
                    }
                    break;
                case PHANTOM:
                    // Camuflaje Óptico: el servidor marca a Phantom como camuflado
                    // y lo manda en el estado; cada cliente lo dibuja casi invisible.
                    jugador.intentarUsarHabilidad(0, 0);
                    break;
            }
        }
        // Mientras el escudo está activo, sigue la dirección del mouse.
        if (jugador.escudoEstaActivo()) {
            jugador.setDireccionEscudo(apuntarDx, apuntarDy);
        }

        float velocidad = jugador.getVelocidadActual();
        float dx = 0, dy = 0;
        if (jugador.estaImpulsando()) {
            dx = jugador.getImpulsoDirX() * velocidad * delta;
            dy = jugador.getImpulsoDirY() * velocidad * delta;
        } else if (apretandoTeclasMovimiento) {
            // Normalización: en diagonal no se va más rápido.
            float largoVector = (float) Math.sqrt(in.moverX * in.moverX + in.moverY * in.moverY);
            dx = (in.moverX / largoVector) * velocidad * delta;
            dy = (in.moverY / largoVector) * velocidad * delta;
        }

        moverConColision(jugador, dx, dy);
        jugador.setMoviendose(apretandoTeclasMovimiento || jugador.estaImpulsando());

        if (apretandoTeclasMovimiento) {
            jugador.setDireccionMirando(Direccion.desdeVector(in.moverX, in.moverY));
        } else if (apuntarDx != 0 || apuntarDy != 0) {
            jugador.setDireccionMirando(Direccion.desdeVector(apuntarDx, apuntarDy));
        }
    }

    /** Mueve eje por eje para poder deslizarse contra las paredes. */
    private void moverConColision(Jugador jugador, float dx, float dy) {
        float mitad = Jugador.TAMANIO / 2f;
        if (dx != 0) {
            float nuevoX = jugador.getX() + dx;
            Rectangle pruebaX = new Rectangle(nuevoX - mitad, jugador.getY() - mitad, Jugador.TAMANIO, Jugador.TAMANIO);
            if (!arena.chocaConAlguno(pruebaX)) jugador.setX(nuevoX);
        }
        if (dy != 0) {
            float nuevoY = jugador.getY() + dy;
            Rectangle pruebaY = new Rectangle(jugador.getX() - mitad, nuevoY - mitad, Jugador.TAMANIO, Jugador.TAMANIO);
            if (!arena.chocaConAlguno(pruebaY)) jugador.setY(nuevoY);
        }
    }

    private void procesarDisparo(Lugar s, EntradaJugador in, float delta) {
        if (s.recargaDisparo > 0) s.recargaDisparo -= delta;
        if (!in.disparar || s.recargaDisparo > 0) return;

        Jugador jugador = s.jugador;
        float dirX = in.apuntarX - jugador.getX();
        float dirY = in.apuntarY - jugador.getY();
        if (dirX * dirX + dirY * dirY < 1f) {
            // Mouse encima del jugador: se dispara hacia donde mira, para
            // que nunca se cree un proyectil sin dirección.
            Vector2 mirando = vectorMirada(jugador.getDireccionMirando());
            dirX = mirando.x;
            dirY = mirando.y;
        }
        // El daño se fija al disparar: si tiene Sobrecarga, el proyectil pega más fuerte.
        proyectiles.add(new Proyectil(proximoProyectilId++, jugador.getId(), jugador.getEquipo(),
            jugador.getPersonaje(), jugador.getX(), jugador.getY(), dirX, dirY,
            jugador.getPersonaje().danio * jugador.getMultiplicadorDanio()));
        s.recargaDisparo = jugador.getPersonaje().recargaDisparo;
        // Disparar delata a Phantom: el camuflaje se corta.
        jugador.cortarCamuflaje();
    }

    /** Cura a los aliados dentro de cada Campo de Regeneración y borra los que vencieron. */
    private void actualizarZonas(float delta) {
        Iterator<ZonaRegeneracion> it = zonas.iterator();
        while (it.hasNext()) {
            ZonaRegeneracion z = it.next();
            z.actualizar(delta);
            if (!z.estaVivo()) {
                it.remove();
                continue;
            }
            for (Lugar s : lugares) {
                Jugador p = s.jugador;
                if (!p.estaVivo() || !esAliadoDeZona(p, z)) continue;
                if (z.contiene(p.getX(), p.getY())) {
                    p.curar(TipoPersonaje.REGENERACION_POR_SEGUNDO * delta);
                }
            }
        }
    }

    /**
     * Power-Ups: cada tanto aparece uno nuevo, de un tipo al azar, en un punto
     * libre del mapa; y el primer jugador vivo que lo toque se lo lleva.
     */
    private void actualizarPotenciadores(float delta) {
        tiempoPotenciador -= delta;
        if (tiempoPotenciador <= 0f) {
            tiempoPotenciador = TipoPotenciador.INTERVALO_APARICION;
            if (potenciadoresMapa.size() < TipoPotenciador.MAXIMO_EN_MAPA) aparecerPotenciador();
        }

        Iterator<Potenciador> it = potenciadoresMapa.iterator();
        while (it.hasNext()) {
            Potenciador pot = it.next();
            for (Lugar s : lugares) {
                Jugador p = s.jugador;
                if (pot.toca(p) && p.puedeRecoger(pot.getTipo())) {
                    p.aplicarPotenciador(pot.getTipo());
                    it.remove();
                    break;
                }
            }
        }
    }

    private void aparecerPotenciador() {
        // Puntos de aparición que no tienen ya un Power-Up encima
        List<Integer> libres = new ArrayList<>();
        for (int i = 0; i < Arena.APARICIONES_POTENCIADORES.length; i++) {
            boolean ocupado = false;
            for (Potenciador pot : potenciadoresMapa) {
                if (pot.getIndiceAparicion() == i) ocupado = true;
            }
            if (!ocupado) libres.add(i);
        }
        if (libres.isEmpty()) return;
        int indice = libres.get(azar.nextInt(libres.size()));
        TipoPotenciador[] tipos = TipoPotenciador.values();
        TipoPotenciador tipo = tipos[azar.nextInt(tipos.length)];
        float[] posicion = Arena.APARICIONES_POTENCIADORES[indice];
        potenciadoresMapa.add(new Potenciador(proximoPotenciadorId++, tipo, indice, posicion[0], posicion[1]));
    }

    /** En Equipos el campo cura a todo el equipo de Iris; en Todos contra todos, solo a Iris. */
    private boolean esAliadoDeZona(Jugador p, ZonaRegeneracion z) {
        if (modo == ModoJuego.EQUIPOS) return p.getEquipo() == z.getEquipo();
        return p.getId() == z.getDuenioId();
    }

    private void actualizarProyectiles(float delta) {
        for (Proyectil p : proyectiles) {
            p.actualizar(delta);
            if (p.getX() < 0 || p.getX() > Arena.ANCHO || p.getY() < 0 || p.getY() > Arena.ALTO) {
                p.destruir();
            }
        }
    }

    private void resolverColisiones() {
        for (Proyectil p : proyectiles) {
            if (!p.estaVivo()) continue;
            Rectangle limites = p.getLimites();

            if (arena.chocaConAlguno(limites)) {
                p.destruir();
                continue;
            }

            for (Lugar s : lugares) {
                Jugador objetivo = s.jugador;
                // Sin fuego amigo: solo se daña a los rivales.
                if (!objetivo.estaVivo() || !esRivalDe(objetivo, p)) continue;
                if (!objetivo.getLimites().overlaps(limites)) continue;

                boolean eliminado = objetivo.recibirDanio(p.getDanio(), p.getDirX(), p.getDirY());
                if (eliminado) {
                    Lugar tirador = buscarLugar(p.getDuenioId());
                    if (tirador != null) tirador.jugador.sumarEliminacion();
                    if (modo == ModoJuego.EQUIPOS) {
                        if (p.getEquipo() == Equipo.AZUL) puntosAzul++;
                        else puntosRojo++;
                    }
                }
                p.destruir();
                break;
            }
        }

        // Se recorre con un Iterator para poder borrar mientras se recorre.
        Iterator<Proyectil> it = proyectiles.iterator();
        while (it.hasNext()) {
            if (!it.next().estaVivo()) it.remove();
        }
    }

    // ------------------------------------------------------------------
    // Equipos y puntos de aparición
    // ------------------------------------------------------------------

    /** ¿El proyectil puede dañar a este jugador? */
    private boolean esRivalDe(Jugador objetivo, Proyectil p) {
        if (objetivo.getId() == p.getDuenioId()) return false;
        if (modo == ModoJuego.EQUIPOS) return objetivo.getEquipo() != p.getEquipo();
        return true; // Todos contra todos: cualquiera que no sea uno mismo
    }

    /**
     * Reparte equipos según el modo y ubica a cada uno en su lugar de aparición.
     *   - Equipos: alternado por orden de llegada (azul, rojo, azul, rojo).
     *   - Todos contra todos: cada uno recibe su propio color (0, 1, 2, 3).
     */
    private void asignarEquipos() {
        int indiceAzul = 0, indiceRojo = 0;
        for (int i = 0; i < lugares.size(); i++) {
            Jugador p = lugares.get(i).jugador;
            Vector2 aparicion;
            if (modo == ModoJuego.EQUIPOS) {
                int equipo = (i % 2 == 0) ? Equipo.AZUL : Equipo.ROJO;
                p.setEquipo(equipo);
                aparicion = puntoAparicionPara(equipo, equipo == Equipo.AZUL ? indiceAzul++ : indiceRojo++);
            } else {
                p.setEquipo(i);
                aparicion = aparicionTodosContraTodos(i);
            }
            p.reaparecer(aparicion.x, aparicion.y);
        }
    }

    /** Primer color libre (para alguien que entra con la partida en juego). */
    private int primerColorLibre() {
        for (int c = 0; c < Protocolo.MAX_JUGADORES; c++) {
            if (contarEquipo(c) == 0) return c;
        }
        return 0;
    }

    private Vector2 puntoAparicionPara(int equipo, int indiceEnEquipo) {
        float x = equipo == Equipo.AZUL ? APARICION_AZUL_X : APARICION_ROJO_X;
        float y = Arena.ALTO / 2f + (indiceEnEquipo - 0.5f) * 60f;
        return arena.buscarAparicionLibreCerca(x, y, Jugador.TAMANIO);
    }

    private Vector2 aparicionTodosContraTodos(int color) {
        float[] posicion = APARICIONES_TODOS_CONTRA_TODOS[Math.floorMod(color, APARICIONES_TODOS_CONTRA_TODOS.length)];
        return arena.buscarAparicionLibreCerca(posicion[0], posicion[1], Jugador.TAMANIO);
    }

    /**
     * Dónde reaparece alguien eliminado.
     *   - Equipos: en la base de su equipo.
     *   - Todos contra todos: en la esquina más alejada de los rivales vivos,
     *     para no reaparecer al lado de quien lo acaba de eliminar.
     */
    private Vector2 puntoReaparicionPara(Jugador p) {
        if (modo == ModoJuego.EQUIPOS) return puntoAparicionPara(p.getEquipo(), 0);
        float mejorDistanciaAparicion = -1f;
        float[] mejor = APARICIONES_TODOS_CONTRA_TODOS[0];
        for (float[] posicion : APARICIONES_TODOS_CONTRA_TODOS) {
            float masCercano = Float.MAX_VALUE;
            for (Lugar s : lugares) {
                Jugador otro = s.jugador;
                if (otro == p || !otro.estaVivo()) continue;
                float dx = otro.getX() - posicion[0], dy = otro.getY() - posicion[1];
                masCercano = Math.min(masCercano, dx * dx + dy * dy);
            }
            if (masCercano > mejorDistanciaAparicion) {
                mejorDistanciaAparicion = masCercano;
                mejor = posicion;
            }
        }
        return arena.buscarAparicionLibreCerca(mejor[0], mejor[1], Jugador.TAMANIO);
    }

    private static Vector2 vectorMirada(int mirando) {
        switch (mirando) {
            case Direccion.IZQUIERDA: return new Vector2(-1, 0);
            case Direccion.DERECHA: return new Vector2(1, 0);
            case Direccion.ARRIBA: return new Vector2(0, 1);
            default: return new Vector2(0, -1);
        }
    }

    private int contarEquipo(int equipo) {
        int n = 0;
        for (Lugar s : lugares) {
            if (s.jugador.getEquipo() == equipo) n++;
        }
        return n;
    }

    private Lugar buscarLugar(int jugadorId) {
        for (Lugar s : lugares) {
            if (s.jugador.getId() == jugadorId) return s;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Foto del estado para enviar a los clientes
    // ------------------------------------------------------------------

    public EstadoMundo crearEstado(long secuencia) {
        List<EstadoMundo.EstadoJugador> ps = new ArrayList<>();
        for (Lugar s : lugares) {
            Jugador p = s.jugador;
            ps.add(new EstadoMundo.EstadoJugador(
                p.getId(), p.getNombre(), p.getEquipo(), p.getPersonaje().id(), p.getX(), p.getY(),
                p.getDireccionMirando(), p.seEstaMoviendo(), p.estaVivo(), p.getVida(),
                p.habilidadEstaActiva(), p.getAnguloEscudo(), p.esInvulnerable(), s.listo,
                p.getEliminaciones(), p.getMuertes(), p.getProgresoRecargaHabilidad(), p.getReaparicionRestante(),
                p.getPotenciadores()));
        }
        List<EstadoMundo.EstadoProyectil> prs = new ArrayList<>();
        for (Proyectil p : proyectiles) {
            prs.add(new EstadoMundo.EstadoProyectil(p.getId(), p.getEquipo(), p.getPersonaje().id(),
                p.getX(), p.getY()));
        }
        List<EstadoMundo.EstadoZona> zs = new ArrayList<>();
        for (ZonaRegeneracion z : zonas) {
            zs.add(new EstadoMundo.EstadoZona(z.getId(), z.getDuenioId(), z.getEquipo(),
                z.getX(), z.getY(), z.getRadio(), z.getRestante()));
        }
        List<EstadoMundo.EstadoPotenciador> pus = new ArrayList<>();
        for (Potenciador pot : potenciadoresMapa) {
            pus.add(new EstadoMundo.EstadoPotenciador(pot.getId(), pot.getTipo().id(), pot.getX(), pot.getY()));
        }
        return new EstadoMundo(secuencia, fase, Math.max(0f, tiempoFase), puntosAzul, puntosRojo,
            ganador, motivoFin, modo.id(), getAnfitrionId(), ps, prs, zs, pus, 0L);
    }

    public FasePartida getFase() {
        return fase;
    }

    public Arena getArena() {
        return arena;
    }
}
