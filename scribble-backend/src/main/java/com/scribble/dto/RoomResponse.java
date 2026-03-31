package com.scribble.dto;

import com.scribble.domain.game.GameRoom;
import com.scribble.domain.game.GameState;
import com.scribble.domain.player.Player;
import lombok.Builder;
import lombok.Data;

import java.util.Collection;

@Data
@Builder
public class RoomResponse {

    private String roomId;
    private String hostId;
    private GameState state;
    private int currentRound;
    private int totalRounds;
    private int turnDurationSeconds;
    private int maxPlayers;
    private String language;
    private Collection<Player> players;

    // The player who just joined/created — their own ID
    private String yourPlayerId;

    public static RoomResponse from(GameRoom room, String yourPlayerId) {
        return RoomResponse.builder()
                .roomId(room.getRoomId())
                .hostId(room.getHostId())
                .state(room.getState())
                .currentRound(room.getCurrentRound())
                .totalRounds(room.getTotalRounds())
                .turnDurationSeconds(room.getTurnDurationSeconds())
                .maxPlayers(room.getMaxPlayers())
                .language(room.getLanguage())
                .players(room.getPlayers().values())
                .yourPlayerId(yourPlayerId)
                .build();
    }
}
