# Changelog

Todos los cambios importantes realizados en el **servidor de OverZone** serán documentados en este archivo.

El formato utilizado está basado en el estándar **Keep a Changelog**, organizando las modificaciones desde las más recientes hasta las más antiguas.

## [0.8.0] - 2026-11-25

Código en español. **Cambia el protocolo (versión 5): hay que actualizar los dos repositorios.**

### Cambiado

* Todos los nombres propios del código pasaron a español: paquetes (`shared` → `compartido`, `screens` → `pantallas`, `network` → `red`, `ui` → `interfaz`, `input` → `entrada`, `graphics` → `graficos`, `server` → `servidor`, `server.game` → `servidor.juego`), clases (`Main` → `JuegoOverZone`, `MatchSimulation` → `SimulacionPartida`, `ClientNetwork` → `RedCliente`, `WorldSnapshot` → `EstadoMundo`...), métodos, campos, variables y constantes. La tabla completa está en `docs/nombres.md` del repositorio del cliente.
* Quedan en inglés solo los nombres que define LibGDX o Java (`render`, `dispose`, `touchDown`, `SpriteBatch`...), las palabras del protocolo (`PING`, `STATE`, `INPUT`...), los nombres de los personajes y las variables de una o dos letras (`x`, `dx`, `i`).
* Los nombres de las etapas de la partida que viajan en el `STATE` ahora son `SALA`, `CUENTA_REGRESIVA`, `JUGANDO` y `RESULTADOS`, por eso sube la versión del protocolo.
* Lanzadores: `Lwjgl3Launcher` → `LanzadorEscritorio` (cliente) y `ServerLauncher` → `LanzadorServidor` (servidor); `build.gradle` ya apunta a los nombres nuevos.
* Los hilos tienen nombres en español: `RedCliente`, `ServidorRecepcion`, `ServidorBucleJuego` y `ServidorCierre`.

## [0.7.1] - 2026-11-24

**Protocolo versión 4: hay que actualizar también el cliente.**

### Corregido

* El cambio de modo a veces no reaccionaba. Ahora el `PING` trae un **pedido de modo numerado** (`PING;listo;personaje;nPedidoModo;modo;marca`) y `MatchSimulation.requestMode` atiende cada número una sola vez: no se pierde con la pérdida de paquetes y cada clic del anfitrión es un pedido nuevo.

### Agregado

* **Power-Ups temporales**: `PowerUp` (en el mapa) y `PowerUpType` (paquete `shared`). `MatchSimulation.updatePowerUps` hace aparecer uno al azar en un punto libre de `Arena.POWERUP_SPAWNS` (a los 5 s y después cada 8 s, hasta 3), detecta quién lo agarra y aplica el efecto: Botiquín (+50 de vida, solo si falta vida), Turbo (+35 % de velocidad), Sobrecarga (+50 % de daño) y Blindaje (mitad de daño recibido), los tres últimos por 6 s. Se pierden al morir.
* `Projectile` guarda su propio daño (para la Sobrecarga).
* La consola muestra los pedidos de modo rechazados y el motivo: "no es el anfitrion", "hacen falta 4 jugadores" o "la partida ya empezo".

## [0.7.0] - 2026-11-22

**Protocolo versión 3: hay que actualizar también el cliente.**

### Agregado

* `GameMode`: **Todos contra todos** (2 a 4 jugadores, gana el jugador con más eliminaciones; empate si hay dos arriba) y **Equipos 2v2** (exactamente 4 jugadores).
* **Anfitrión:** el primer jugador de la lista elige el modo. `MatchSimulation.requestMode` solo acepta el pedido del anfitrión, antes de empezar, y si el modo es válido con los jugadores que hay. El pedido se procesa solo cuando cambia.
* Si estando en Equipos alguien se va de la sala, vuelve a Todos contra todos.
* Aparición en las cuatro esquinas en Todos contra todos; al reaparecer, en la esquina más lejana de los rivales vivos.
* **Iris:** `RegenZone`, el Campo de Regeneración, que cura 14 de vida por segundo a los aliados dentro (en Todos contra todos, solo a Iris).
* **Phantom:** Camuflaje Óptico, que se corta al disparar.
* Alcance máximo de los proyectiles según el personaje.
* El comando `estado` y los mensajes de la consola muestran el modo de juego.

### Cambiado

* Recargas según la propuesta: Volt 10 s, Brutus 15 s. Brutus camina al 75 % de su velocidad con el escudo activo.
* Protocolo versión 3: el `PING` incluye el modo pedido; el `STATE` incluye el modo, el anfitrión y los campos de regeneración.

## [0.5.0] - 2026-11-19

### Agregado

* Segundo personaje, **Brutus**: más vida, más daño, menos velocidad y menor cadencia.
* **Escudo Cinético**: mientras está activo, los disparos que llegan de frente hacen solo el 30 % del daño. La dirección del escudo sigue al mouse del jugador.
* `CharacterType` (paquete `shared`) con las estadísticas de cada personaje. `Player` y `Projectile` toman de ahí la vida, la velocidad, el daño, la cadencia y la habilidad.
* `MatchSimulation.setCharacter`: el personaje se elige en la sala de espera; durante la partida el cambio se ignora.

### Cambiado

* Protocolo versión 2: el `PING` incluye el personaje elegido y el estado incluye el personaje, la habilidad activa y la dirección del escudo.

## [0.4.0] - 2026-11-19

Creación del repositorio del servidor, separado del cliente. Hasta la versión 0.3.0, el servidor formaba parte del repositorio `OverZoneScardaoni` como módulo `server`; su historial anterior está en ese repositorio.

### Agregado

* Proyecto Gradle independiente que genera `OverZone-Server-1.0.0.jar`, ejecutable desde el cmd.
* `GameServer`, `ConnectedClient` y `ServerLauncher`, traídos del módulo `server` del repositorio del cliente.
* Paquete `server.game` con la lógica de la partida (`MatchSimulation`, `Player`, `Projectile`), que ya no está en el cliente.
* Paquete `shared`, copia idéntica del que usa el cliente.
* Control de versión de protocolo: las conexiones de clientes con una versión de `Protocol.VERSION` distinta se rechazan con `REJECT;VERSION`.
