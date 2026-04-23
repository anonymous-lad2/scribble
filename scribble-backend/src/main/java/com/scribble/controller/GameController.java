package com.scribble.controller;

import com.scribble.domain.game.DrawEvent;
import com.scribble.service.DrawingService;
import com.scribble.service.GameService;
import com.scribble.service.GuessService;
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
    private final DrawingService drawingService;
    private final GuessService guessService;

    // ── Game lifecycle ────────────────────────────────────────

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

    // ── Drawing ───────────────────────────────────────────────

    // Client sends every mouse/touch move to /app/draw/{roomId}
    @MessageMapping("/draw/{roomId}")
    public void draw(
            @DestinationVariable String roomId,
            @Payload DrawEvent event,
            SimpMessageHeaderAccessor headerAccessor) {

        // playerId passed as a header to avoid bloating every draw payload
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        drawingService.handleDrawEvent(roomId, playerId, event);
    }

    @MessageMapping("/draw/{roomId}/undo")
    public void undo(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload) {
        drawingService.handleUndo(roomId, payload.get("playerId"));
    }

    @MessageMapping("/draw/{roomId}/clear")
    public void clear(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload) {
        drawingService.handleClear(roomId, payload.get("playerId"));
    }

    // ── Chat / Guessing ───────────────────────────────────────

    // All chat messages go through here — GuessService decides
    // if it's a correct guess or just a chat message
    @MessageMapping("/chat/{roomId}")
    public void chat(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload) {

        String playerId = payload.get("playerId");
        String message = payload.get("message");

        guessService.handleGuess(roomId, playerId, message);
    }
}
