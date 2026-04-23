package com.scribble.event;

import com.scribble.service.DrawingService;
import com.scribble.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RoomService roomService;
    private final DrawingService drawingService;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        // Client sends playerId and roomId as STOMP connect headers
        String playerId = accessor.getFirstNativeHeader("playerId");
        String roomId = accessor.getFirstNativeHeader("roomId");

        if (playerId != null && roomId != null) {
            // Store in session so other handlers can access without
            // the client repeating these on every message
            accessor.getSessionAttributes().put("playerId", playerId);
            accessor.getSessionAttributes().put("roomId", roomId);

            // Update player's sessionId in room so private messages route correctly
            // This is critical for sendToUser() to work
            roomService.updatePlayerSession(roomId, playerId, accessor.getSessionId());

            // Send canvas replay if joining mid-game
            drawingService.replayCanvasForPlayer(roomId, playerId);

            log.info("WS connect — player: {} room: {} session: {}",
                    playerId, roomId, accessor.getSessionId());
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String playerId = (String) accessor.getSessionAttributes().get("playerId");
        String roomId = (String) accessor.getSessionAttributes().get("roomId");

        if (playerId != null && roomId != null) {
            roomService.handleDisconnect(roomId, playerId);
            log.info("WS disconnect — player: {} room: {}", playerId, roomId);
        }
    }
}
