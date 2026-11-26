package com.OverZone.Scardaoni.compartido;

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
