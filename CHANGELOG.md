# Changelog

Todos los cambios importantes realizados en el **servidor de OverZone** serán documentados en este archivo.

El formato utilizado está basado en el estándar **Keep a Changelog**, organizando las modificaciones desde las más recientes hasta las más antiguas.

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
