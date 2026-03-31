package com.scribble.domain.game;

public enum GameState {
    LOBBY,       // players joining, waiting for host to start
    WORD_PICK,   // drawer is choosing from 3 word options
    DRAWING,     // active drawing + guessing phase
    REVEAL,      // turn ended, showing the word + scores
    ENDED        // all rounds done, final leaderboard
}
