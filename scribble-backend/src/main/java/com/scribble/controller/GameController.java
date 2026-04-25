package com.scribble.controller;

import com.scribble.domain.game.DrawEvent;
import com.scribble.security.AuthenticatedUser;
import com.scribble.service.DrawingService;
import com.scribble.service.GameService;
import com.scribble.service.GuessService;
import com.scribble.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final DrawingService drawingService;
    private final GuessService guessService;
    private final RoomService roomService;

    // ── Game lifecycle ────────────────────────────────────────

    // Client sends: /app/game/{roomId}/start
    @MessageMapping("/game/{roomId}/start")
    public void startGame(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal) {
        String playerId = payload.get("playerId");
        requireMatchingPlayer(principal, roomId, playerId);
        gameService.startGame(roomId, playerId);
    }

    // Client sends: /app/game/{roomId}/word-chosen
    @MessageMapping("/game/{roomId}/word-chosen")
    public void wordChosen(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal) {
        String playerId = payload.get("playerId");
        String word = payload.get("word");
        requireMatchingPlayer(principal, roomId, playerId);
        gameService.onWordChosen(roomId, playerId, word);
    }

    // ── Drawing ───────────────────────────────────────────────

    // Client sends every mouse/touch move to /app/draw/{roomId}
    @MessageMapping("/draw/{roomId}")
    public void draw(
            @DestinationVariable String roomId,
            @Payload DrawEvent event,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {

        // playerId passed as a header to avoid bloating every draw payload
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        requireMatchingPlayer(principal, roomId, playerId);
        drawingService.handleDrawEvent(roomId, playerId, event);
    }

    @MessageMapping("/draw/{roomId}/undo")
    public void undo(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal) {
        String playerId = payload.get("playerId");
        requireMatchingPlayer(principal, roomId, playerId);
        drawingService.handleUndo(roomId, playerId);
    }

    @MessageMapping("/draw/{roomId}/clear")
    public void clear(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal) {
        String playerId = payload.get("playerId");
        requireMatchingPlayer(principal, roomId, playerId);
        drawingService.handleClear(roomId, playerId);
    }

    // ── Chat / Guessing ───────────────────────────────────────

    // All chat messages go through here — GuessService decides
    // if it's a correct guess or just a chat message
    @MessageMapping("/chat/{roomId}")
    public void chat(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal) {

        String playerId = payload.get("playerId");
        String message = payload.get("message");
        requireMatchingPlayer(principal, roomId, playerId);

        guessService.handleGuess(roomId, playerId, message);
    }

    private void requireMatchingPlayer(Principal principal, String roomId, String playerId) {
        String userId = requireUserId(principal);
        if (playerId == null) {
            throw new IllegalStateException("playerId is required");
        }
        roomService.assertPlayerBelongsToAccount(roomId, playerId, userId);
    }

    private static String requireUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken t
                && t.getPrincipal() instanceof AuthenticatedUser u) {
            return u.getUserId();
        }
        throw new IllegalStateException("Unauthenticated STOMP connection");
    }
}
