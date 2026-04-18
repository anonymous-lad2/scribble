package com.scribble.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GameEvent {
    public enum Type {
        GAME_STARTED,
        TURN_STARTED,       // new turn beginning, who is drawing
        WORD_CHOICES,       // sent privately to drawer: 3 word options
        WORD_CHOSEN,        // broadcast: masked word + drawer info
        HINT,               // broadcast: updated masked word with one letter revealed
        PLAYER_GUESSED,     // broadcast: someone guessed correctly (name only, not word)
        TURN_ENDED,         // broadcast: turn over, actual word revealed
        SCORES_UPDATED,     // broadcast: updated score for all players
        ROUND_ENDED,        // broadcast: all turns in this round done
        GAME_ENDED,         // broadcast: final leaderboard
        TIMER_TICK,         // broadcast: seconds remaining
        ERROR               // private: something went wrong
    }

    private Type type;
    private Object payload;  // flexible — each event type has its own shape
}
