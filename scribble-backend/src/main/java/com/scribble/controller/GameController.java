package com.scribble.controller;

import com.scribble.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    // Client sends: /app/game/{roomId}/start
    @MessageMapping("/game/{roomId}/start")
    public void startGame(@DestinationVariable String roomId, @Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String playerId = payload.get("playerId");
        gameService.startGame(roomId, playerId);
    }

    // Client sends: /app/game/{roomId}/word-chosen
    @MessageMapping("/game/{roomId}/word-chosen")
    public void wordChosen(@DestinationVariable String roomId, @Payload Map<String, String> payload) {
        String playerId = payload.get("playerId");
        String word = payload.get("word");
        gameService.onWordChosen(roomId, playerId, word);
    }
}
