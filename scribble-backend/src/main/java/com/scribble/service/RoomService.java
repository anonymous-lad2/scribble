package com.scribble.service;

import com.scribble.domain.game.GameRoom;
import com.scribble.domain.game.GameState;
import com.scribble.domain.player.Player;
import com.scribble.dto.CreateRoomRequest;
import com.scribble.dto.JoinRoomRequest;
import com.scribble.dto.RoomResponse;
import com.scribble.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final GameRoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Characters used for room code — no 0/O/I/1 to avoid confusion
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    // ── Create ────────────────────────────────────────────────

    public RoomResponse createRoom(CreateRoomRequest request) {
        String roomId = generateUniqueRoomCode();
        String playerId = UUID.randomUUID().toString();

        Player host = Player.builder()
                .playerId(playerId)
                .username(request.getUsername())
                .avatarColor(request.getAvatarColor())
                .isHost(true)
                .isConnected(true)
                .score(0)
                .build();

        GameRoom room = GameRoom.builder()
                .roomId(roomId)
                .hostId(playerId)
                .totalRounds(request.getTotalRounds())
                .turnDurationSeconds(request.getTurnDurationSeconds())
                .maxPlayers(request.getMaxPlayers())
                .language(request.getLanguage())
                .currentRound(0)
                .currentTurnIndex(-1)
                .build();

        room.addPlayer(host);
        roomRepository.save(room);

        log.info("Room created: {} by player: {}", roomId, request.getUsername());
        return RoomResponse.from(room, playerId);
    }

    // ── Join ──────────────────────────────────────────────────

    public RoomResponse joinRoom(JoinRoomRequest request) {
        GameRoom room = getExistingRoom(request.getRoomId());

        if (room.isFull()) {
            throw new IllegalStateException("Room is full");
        }
        if (room.getState() != GameState.LOBBY) {
            throw new IllegalStateException("Game already in progress");
        }

        String playerId = UUID.randomUUID().toString();

        Player player = Player.builder()
                .playerId(playerId)
                .username(request.getUsername())
                .avatarColor(request.getAvatarColor())
                .isHost(false)
                .isConnected(true)
                .score(0)
                .build();

        room.addPlayer(player);
        roomRepository.save(room);

        // Broadcast updated player list to everyone already in the room
        broadcastRoomUpdate(room, "PLAYER_JOINED", Map.of(
                "username", player.getUsername(),
                "playerId", playerId
        ));

        log.info("Player {} joined room {}", request.getUsername(), request.getRoomId());
        return RoomResponse.from(room, playerId);
    }

    // ── Leave ─────────────────────────────────────────────────

    public void leaveRoom(String roomId, String playerId) {
        GameRoom room = getExistingRoom(roomId);
        Player leaving = room.getPlayer(playerId);

        if (leaving == null) return;

        room.removePlayer(playerId);

        // If room is now empty, delete it entirely
        if (room.getPlayerCount() == 0) {
            roomRepository.delete(roomId);
            log.info("Room {} deleted — no players remaining", roomId);
            return;
        }

        // If the host left, transfer host to the next player
        if (leaving.isHost()) {
            transferHost(room);
        }

        roomRepository.save(room);

        broadcastRoomUpdate(room, "PLAYER_LEFT", Map.of(
                "username", leaving.getUsername(),
                "playerId", playerId,
                "newHostId", room.getHostId()
        ));

        log.info("Player {} left room {}", leaving.getUsername(), roomId);
    }

    public void updatePlayerSession(String roomId, String playerId, String sessionId) {
        GameRoom room = roomRepository.findById(roomId);
        if (room == null) return;

        Player player = room.getPlayer(playerId);
        if (player != null) {
            player.setSessionId(sessionId);
            player.setConnected(true);
            roomRepository.save(room);
        }
    }

    // ── Disconnect (WebSocket dropped) ────────────────────────
    // Different from leaveRoom — marks player as disconnected
    // rather than removing them, giving them a chance to reconnect

    public void handleDisconnect(String roomId, String playerId) {
        if (roomId == null || playerId == null) return;

        GameRoom room = roomRepository.findById(roomId);
        if (room == null) return;

        Player player = room.getPlayer(playerId);
        if (player == null) return;

        player.setConnected(false);
        roomRepository.save(room);

        broadcastRoomUpdate(room, "PLAYER_DISCONNECTED", Map.of(
                "username", player.getUsername(),
                "playerId", playerId
        ));

        log.info("Player {} disconnected from room {}", player.getUsername(), roomId);
    }

    // ── Reconnect ─────────────────────────────────────────────

    public RoomResponse reconnect(String roomId, String playerId, String newSessionId) {
        GameRoom room = getExistingRoom(roomId);
        Player player = room.getPlayer(playerId);

        if (player == null) {
            throw new IllegalArgumentException("Player not found in room");
        }

        player.setConnected(true);
        player.setSessionId(newSessionId);
        roomRepository.save(room);

        broadcastRoomUpdate(room, "PLAYER_RECONNECTED", Map.of(
                "username", player.getUsername(),
                "playerId", playerId
        ));

        log.info("Player {} reconnected to room {}", player.getUsername(), roomId);
        return RoomResponse.from(room, playerId);
    }

    // ── Helpers ───────────────────────────────────────────────

    public GameRoom getExistingRoom(String roomId) {
        GameRoom room = roomRepository.findById(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found: " + roomId);
        }
        return room;
    }

    private void transferHost(GameRoom room) {
        // Pick the first connected player as the new host
        room.getPlayers().values().stream()
                .filter(Player::isConnected)
                .findFirst()
                .ifPresent(newHost -> {
                    newHost.setHost(true);
                    room.setHostId(newHost.getPlayerId());
                    log.info("Host transferred to {} in room {}",
                            newHost.getUsername(), room.getRoomId());
                });
    }

    private void broadcastRoomUpdate(GameRoom room, String eventType, Map<String, Object> extra) {
        // Sends to /topic/room/{roomId}/lobby
        // All players in the lobby are subscribed to this topic
        String destination = "/topic/room/" + room.getRoomId() + "/lobby";
        messagingTemplate.convertAndSend(destination, (Object) Map.of(
                "event", eventType,
                "players", room.getPlayers().values(),
                "hostId", room.getHostId(),
                "extra", extra
        ));
    }

    // Fisher-Yates inspired room code generation
    private String generateUniqueRoomCode() {
        String code;
        int attempts = 0;
        do {
            code = generateRoomCode();
            attempts++;
            if (attempts > 10) {
                throw new RuntimeException("Could not generate unique room code");
            }
        } while (roomRepository.exists(code));
        return code;
    }

    private String generateRoomCode() {
        StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}