package com.faircourt.playmatch.game;

/** State of a game; constants equal the DB check values (blueprint section 11). CLOSED = read-only history. */
public enum GameState {
    OPEN,
    CLOSED
}
