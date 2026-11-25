# OverZone — Servidor

Servidor del videojuego multijugador **OverZone**, desarrollado para el Trabajo Práctico Final de **Programación sobre Redes**.

El juego (cliente) está en otro repositorio: [**OverZoneScardaoni**](https://github.com/FacuScardaoni/OverZoneScardaoni).

## Qué hace

Es un servidor **autoritativo** sobre **UDP**: es el único programa que ejecuta la lógica de la partida. Los clientes solo le envían sus entradas (teclas, mouse, disparo, habilidad) y dibujan el estado que el servidor les manda.

* Responde al **broadcast** de los clientes, así lo encuentran en la red local sin escribir ninguna IP.
* Administra la sala de espera (hasta 4 jugadores), la cuenta regresiva, la partida y los resultados. Al terminar, vuelve solo a la sala para jugar otra partida.
* Dos modos de juego: **Todos contra todos** (2 a 4 jugadores) y **Equipos 2v2** (exactamente 4). Lo elige el anfitrión (el primer jugador conectado) y el servidor valida que se pueda jugar con los jugadores que hay.
* Power-Ups temporales (Botiquín, Turbo, Sobrecarga y Blindaje): el servidor decide cuándo y dónde aparecen, quién los agarra y aplica el efecto.
* Cuatro personajes: Volt, Brutus, Iris y Phantom. El servidor aplica sus habilidades, incluido el Campo de Regeneración de Iris (cura a los aliados dentro de la zona) y el Camuflaje Óptico de Phantom.
* Simula la partida 60 veces por segundo con paso de tiempo fijo y envía el estado completo a los clientes 30 veces por segundo.
* Detecta desconexiones (avisadas o por tiempo de espera), avisa a los clientes al cerrarse y cierra sus hilos y su socket de forma ordenada.

No abre ninguna ventana. Solo usa el núcleo de LibGDX por sus clases de matemática (`Rectangle`, `Vector2`).


> **Nombres en español:** todo el código usa nombres en español (paquetes, clases, métodos y variables). La tabla de equivalencias con los nombres anteriores en inglés está en [docs/nombres.md](https://github.com/FacuScardaoni/OverZoneScardaoni/blob/main/docs/nombres.md).

## Requisitos

* **Java 21.** Para comprobarlo: `java -version`.
* Estar en la **misma subred** que los clientes (por ejemplo, todas las computadoras en `192.168.0.x`).
* Permitir Java en el firewall de Windows para **redes privadas** (lo pregunta la primera vez que se abre).

## Cómo ejecutarlo

Desde la carpeta del repositorio, en el cmd:

```bash
gradlew.bat jar
java -jar build\libs\OverZone-Server-1.0.0.jar
```

El primer comando genera el ejecutable (la primera vez tarda unos minutos, porque descarga Gradle y las dependencias). El segundo abre el servidor.

También se puede abrir sin generar el `.jar`, con `gradlew.bat run`, o desde IntelliJ ejecutando `LanzadorServidor`.

Opcionalmente se le puede dar un nombre al servidor, que los clientes ven al conectarse:

```bash
java -jar build\libs\OverZone-Server-1.0.0.jar MiServidor
```

### Comandos de la consola

| Comando | Qué hace |
| --- | --- |
| `estado` | Muestra la etapa de la partida, el modo de juego y los jugadores conectados. |
| `retardo <ms>` | Simula una red lenta, agregando ese retardo en cada sentido (ej.: `retardo 150`). |
| `perdida <%>` | Simula paquetes perdidos (ej.: `perdida 20`). |
| `normal` | Quita el retardo y la pérdida simulados. |
| `salir` | Cierra el servidor avisando a los clientes. |

El servidor usa el puerto UDP **5555**. Si ya hay otro servidor abierto en la misma computadora, avisa que el puerto está ocupado y se cierra.

## Organización del código

```
src/main/java/com/OverZone/Scardaoni/
├── servidor/            Programa del servidor
│   ├── LanzadorServidor     punto de entrada (main)
│   ├── ServidorJuego         socket UDP, hilos, mensajes, clientes conectados
│   ├── ClienteConectado    dirección y jugador de cada cliente
│   └── juego/              La partida real (solo existe en el servidor)
│       ├── SimulacionPartida    reglas, etapas, modos, anfitrión, equipos, colisiones, puntaje
│       ├── Jugador             jugador: vida, habilidad, curación, muerte y reaparición
│       ├── Proyectil         proyectil (con alcance máximo y su propio daño)
│       ├── ZonaRegeneracion          Campo de Regeneración de Iris
│       └── Potenciador            Power-Up tirado en el mapa
└── compartido/            COMPARTIDO con el cliente (copia idéntica)
    ├── Protocolo           mensajes, puerto, tiempos y versión del protocolo
    ├── EntradaJugador        entradas que envía el cliente
    ├── EstadoMundo      estado de la partida que envía el servidor
    ├── TipoPersonaje      estadísticas de Volt, Brutus, Iris y Phantom
    ├── ModoJuego           Todos contra todos / Equipos 2v2
    ├── TipoPotenciador        tipos de Power-Up, efectos y tiempos
    ├── FasePartida, Equipo, Direccion, ConstantesJuego
    └── Arena, Obstaculo    el mapa
```

### El paquete `compartido`

Cliente y servidor tienen que "hablar el mismo idioma", por eso el paquete `compartido` está copiado en los dos repositorios y **tiene que ser idéntico en ambos**. Si se cambia algo en uno, hay que copiarlo al otro.

Para que una diferencia no pase desapercibida, el cliente envía `Protocolo.VERSION` al conectarse. Si no coincide con la del servidor, la conexión se rechaza y el cliente muestra la versión de cada uno. Cuando se cambia el formato de un mensaje, hay que subir ese número en los dos repositorios.

El protocolo, los hilos, el manejo de errores y el cumplimiento de los requisitos técnicos se explican en `docs/arquitectura-red.md`, en el repositorio del cliente.

---

**Autor:** Facundo Scardaoni
