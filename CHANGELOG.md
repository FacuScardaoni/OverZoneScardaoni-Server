# Changelog

Todos los cambios importantes realizados en el **servidor de OverZone** serán documentados en este archivo.

El formato utilizado está basado en el estándar **Keep a Changelog**, organizando las modificaciones desde las más recientes hasta las más antiguas.

## [0.4.0] - 2026-11-19

Creación del repositorio del servidor, separado del cliente. Hasta la versión 0.3.0, el servidor formaba parte del repositorio `OverZoneScardaoni` como módulo `server`; su historial anterior está en ese repositorio.

### Agregado

* Proyecto Gradle independiente que genera `OverZone-Server-1.0.0.jar`, ejecutable desde el cmd.
* `GameServer`, `ConnectedClient` y `ServerLauncher`, traídos del módulo `server` del repositorio del cliente.
* Paquete `server.game` con la lógica de la partida (`MatchSimulation`, `Player`, `Projectile`), que ya no está en el cliente.
* Paquete `shared`, copia idéntica del que usa el cliente.
* Control de versión de protocolo: las conexiones de clientes con una versión de `Protocol.VERSION` distinta se rechazan con `REJECT;VERSION`.
