/*
 * ARCHIVO COMPARTIDO entre los dos repositorios:
 *   - OverZoneScardaoni         (cliente)
 *   - OverZoneScardaoni-Server  (servidor)
 * Tiene que ser IDÉNTICO en los dos. Si lo cambiás en uno, copialo al otro.
 * Si cambia el formato de algún mensaje, subí también Protocolo.VERSION, así
 * un cliente y un servidor de versiones distintas se avisan en lugar de fallar.
 */
package com.OverZone.Scardaoni.compartido;

/**
 * Etapas por las que pasa la partida en el servidor.
 *
 * SALA → CUENTA_REGRESIVA → JUGANDO → RESULTADOS → SALA → ...
 *
 * Los clientes reciben la etapa actual dentro de cada estado y, según cuál
 * sea, muestran la pantalla que corresponde (sala, juego o resultados).
 */
public enum FasePartida {
    /** Sala de espera: los jugadores se conectan y marcan "listo". */
    SALA,
    /** Cuenta regresiva antes de empezar. */
    CUENTA_REGRESIVA,
    /** Partida en curso. */
    JUGANDO,
    /** Partida terminada: se muestran los resultados unos segundos. */
    RESULTADOS
}
