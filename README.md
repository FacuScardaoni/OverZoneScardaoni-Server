# OverZone — Servidor

Servidor del videojuego multijugador **OverZone**, desarrollado para el Trabajo Práctico Final de **Programación sobre Redes**.

El juego (cliente) está en otro repositorio: [**OverZoneScardaoni**](https://github.com/FacuScardaoni/OverZoneScardaoni).

## Qué hace

Es un servidor **autoritativo** sobre **UDP**: es el único programa que ejecuta la lógica de la partida. Los clientes solo le envían sus entradas (teclas, mouse, disparo, habilidad) y dibujan el estado que el servidor les manda.

* Responde al **broadcast** de los clientes, así lo encuentran en la red local sin escribir ninguna IP.
* Administra la sala de espera, los equipos (Azul y Rojo, de 2 a 4 jugadores), la cuenta regresiva, la partida y los resultados. Al terminar, vuelve solo a la sala para jugar otra partida.
* Simula la partida 60 veces por segundo con paso de tiempo fijo y envía el estado completo a los clientes 30 veces por segundo.
* Detecta desconexiones (avisadas o por tiempo de espera), avisa a los clientes al cerrarse y cierra sus hilos y su socket de forma ordenada.

No abre ninguna ventana. Solo usa el núcleo de LibGDX por sus clases de matemática (`Rectangle`, `Vector2`).

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

También se puede abrir sin generar el `.jar`, con `gradlew.bat run`, o desde IntelliJ ejecutando `ServerLauncher`.

Opcionalmente se le puede dar un nombre al servidor, que los clientes ven al conectarse:

```bash
java -jar build\libs\OverZone-Server-1.0.0.jar MiServidor
```

### Comandos de la consola

| Comando | Qué hace |
| --- | --- |
| `estado` | Muestra la etapa de la partida y los jugadores conectados. |
| `retardo <ms>` | Simula una red lenta, agregando ese retardo en cada sentido (ej.: `retardo 150`). |
| `perdida <%>` | Simula paquetes perdidos (ej.: `perdida 20`). |
| `normal` | Quita el retardo y la pérdida simulados. |
| `salir` | Cierra el servidor avisando a los clientes. |

El servidor usa el puerto UDP **5555**. Si ya hay otro servidor abierto en la misma computadora, avisa que el puerto está ocupado y se cierra.

## Organización del código

```
src/main/java/com/OverZone/Scardaoni/
├── server/            Programa del servidor
│   ├── ServerLauncher     punto de entrada (main)
│   ├── GameServer         socket UDP, hilos, mensajes, clientes conectados
│   ├── ConnectedClient    dirección y jugador de cada cliente
│   └── game/              La partida real (solo existe en el servidor)
│       ├── MatchSimulation    reglas, etapas, equipos, colisiones, puntaje
│       ├── Player             jugador: vida, habilidad (impulso o escudo), muerte y reaparición
│       └── Projectile         proyectil
└── shared/            COMPARTIDO con el cliente (copia idéntica)
    ├── Protocol           mensajes, puerto, tiempos y versión del protocolo
    ├── PlayerInput        entradas que envía el cliente
    ├── WorldSnapshot      estado de la partida que envía el servidor
    ├── CharacterType      estadísticas de Volt y Brutus
    ├── MatchPhase, Team, Direction, GameConstants
    └── Arena, Obstacle    el mapa
```

### El paquete `shared`

Cliente y servidor tienen que "hablar el mismo idioma", por eso el paquete `shared` está copiado en los dos repositorios y **tiene que ser idéntico en ambos**. Si se cambia algo en uno, hay que copiarlo al otro.

Para que una diferencia no pase desapercibida, el cliente envía `Protocol.VERSION` al conectarse. Si no coincide con la del servidor, la conexión se rechaza y el cliente muestra la versión de cada uno. Cuando se cambia el formato de un mensaje, hay que subir ese número en los dos repositorios.

El protocolo, los hilos, el manejo de errores y el cumplimiento de los requisitos técnicos se explican en `docs/arquitectura-red.md`, en el repositorio del cliente.

---

**Autor:** Facundo Scardaoni
