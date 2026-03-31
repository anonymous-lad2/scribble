package com.scribble.domain.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player implements Serializable {

    private String playerId;        // UUID, assigned on join
    private String username;
    private String avatarColor;     // hex color e.g -> "#FF5773
    private String sessionId;       // WebSocket session ID - used to route private message

    private int score;
    private boolean hasGuessedCorrect;
    private boolean isDrawing;
    private boolean isConnected;
    private boolean isHost;

    // Called at the start of every new turn to reset per-turn state
    public void resetForNewTurn() {
        this.hasGuessedCorrect = false;
        this.isDrawing = false;
    }
}
