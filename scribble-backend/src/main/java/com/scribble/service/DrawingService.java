package com.scribble.service;

import com.scribble.domain.game.DrawEvent;
import com.scribble.domain.game.GameRoom;
import com.scribble.domain.game.GameState;
import com.scribble.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DrawingService {

    private final GameRoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public DrawingService(
            GameRoomRepository roomRepository,
            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Cap draw event history - prevents memory bloat on long turns
    private static final int MAX_DRAW_EVENTS = 2000;

    // ── Handle incoming draw event ────────────────────────────

    public void handleDrawEvent(String roomId, String playerId, DrawEvent event) {
        GameRoom room = roomRepository.findById(roomId);
        if(room == null || room.getState() != GameState.DRAWING) return;

        // Only the current drawer can send draw events
        if(!playerId.equals(room.getCurrentDrawerId())) return;

        event.setTimestamp(System.currentTimeMillis());

        // Store in deque for late-joiner replay
        Deque<DrawEvent> events = room.getDrawEvents();
        events.addLast(event);

        // Enforce cap - drop oldest events if over limit
        while(events.size() > MAX_DRAW_EVENTS) {
            events.pollFirst();
        }

        roomRepository.save(room);

        // Broadcast to all players in the room immediately
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/draw",
                event
        );
    }

    // ── Undo last stroke ──────────────────────────────────────

    public void handleUndo(String roomId, String playerId) {
        GameRoom room = roomRepository.findById(roomId);
        if (room == null) return;
        if(!playerId.equals(room.getCurrentDrawerId())) return;

        // Remove all events back to the last PATH start
        // Strategy: find the last CLEAR or start of PATH sequence and truncate
        Deque<DrawEvent> events = room.getDrawEvents();
        List<DrawEvent> eventList = new ArrayList<>(events);

        // Walk backwards, remove until we hit a natural stroke boundary
        // A simple approach: remove the last 10 events (one drag gesture)
        int removeCount = Math.min(10, eventList.size());
        for(int i = 0; i < removeCount; i++) {
            if(!eventList.isEmpty()) {
                eventList.remove(eventList.size() - 1);
            }
        }

        events.clear();
        events.addAll(eventList);

        // Add an UNDO marker so clients can replay correctly
        DrawEvent undoEvent = DrawEvent.builder()
                .type(DrawEvent.Type.UNDO)
                .timestamp(System.currentTimeMillis())
                .build();
        events.addLast(undoEvent);

        roomRepository.save(room);

        // Broadcast undo — clients will re-render from scratch
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/draw",
                undoEvent
        );
    }

    // ── Clear canvas ──────────────────────────────────────────

    public void handleClear(String roomId, String playerId) {
        GameRoom room = roomRepository.findById(roomId);
        if (room == null) return;
        if (!playerId.equals(room.getCurrentDrawerId())) return;

        room.getDrawEvents().clear();

        DrawEvent clearEvent = DrawEvent.builder()
                .type(DrawEvent.Type.CLEAR)
                .timestamp(System.currentTimeMillis())
                .build();
        room.getDrawEvents().addLast(clearEvent);

        roomRepository.save(room);

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/draw",
                clearEvent
        );

        log.debug("Canvas cleared in room {} by drawer", roomId);
    }

    // ── Late joiner canvas replay ─────────────────────────────
    // When a player joins mid-turn, send them the full draw history
    // so their canvas catches up to the current state

    public void replayCanvasForPlayer(String roomId, String sessionId) {
        GameRoom room = roomRepository.findById(roomId);
        if (room == null || room.getDrawEvents().isEmpty()) return;

        List<DrawEvent> history = new ArrayList<>(room.getDrawEvents());

        messagingTemplate.convertAndSendToUser(
                sessionId, "/queue/draw",
                Map.of(
                        "type", "CANVAS_REPLAY",
                        "events", history
                )
        );

        log.info("Canvas replay sent to session {} in room {} ({} events)",
                sessionId, roomId, history.size());
    }
}
