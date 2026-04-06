package com.scribble.domain.game;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.scribble.domain.player.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameRoom implements Serializable {

    // ── Identity ──────────────────────────────────────────────
    private String roomId;          // 6-char code e.g. "XK92AB"
    private String hostId;          // playerId of the host

    // ── Players ───────────────────────────────────────────────
    // ConcurrentHashMap: multiple players can join/leave simultaneously
    // Key = playerId, Value = Player object
    @Builder.Default
    private Map<String, Player> players = new ConcurrentHashMap<>();

    // Ordered list of playerIds for round-robin drawer selection
    @Builder.Default
    private List<String> turnOrder = new ArrayList<>();

    // ── Game state ────────────────────────────────────────────
    @Builder.Default
    private GameState state = GameState.LOBBY;

    private int currentRound;        // 1-based
    private int totalRounds;         // configured by host (default 3)
    private int currentTurnIndex;    // index into turnOrder

    private int turnDurationSeconds; // configured by host (default 80)

    // ── Current turn ──────────────────────────────────────────
    private String currentDrawerId;
    private String currentWord;      // the actual word (only sent to drawer privately)
    private String maskedWord;       // e.g. "_ p p _ _" (sent to everyone else)

    // playerIds who have correctly guessed this turn
    @Builder.Default
    private Set<String> guessedPlayerIds = new HashSet<>();

    // ── Drawing events (for late-joiner canvas replay) ────────
    // Deque cap enforced in DrawingService — max 2000 events
    @Builder.Default
    private Deque<DrawEvent> drawEvents = new ArrayDeque<>();

    // ── Word bank tracking (no repeats in a session) ──────────
    @Builder.Default
    private Set<String> usedWords = new HashSet<>();

    // ── Settings ──────────────────────────────────────────────
    private String language;         // "en" by default
    private int maxPlayers;          // default 8

    // ── Convenience methods ───────────────────────────────────

    public void addPlayer(Player player) {
        players.put(player.getPlayerId(), player);
        turnOrder.add(player.getPlayerId());
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
        turnOrder.remove(playerId);
    }

    public Player getPlayer(String playerId) {
        return players.get(playerId);
    }

    public int getPlayerCount() {
        return players.size();
    }

    @JsonIgnore
    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    // Who draws next — advances the index and wraps around
    public String getNextDrawerId() {
        if (turnOrder.isEmpty()) return null;
        currentTurnIndex = (currentTurnIndex + 1) % turnOrder.size();
        return turnOrder.get(currentTurnIndex);
    }

    // True when everyone except the drawer has guessed correctly
    @JsonIgnore
    public boolean allPlayersGuessed() {
        long eligibleCount = players.values().stream()
                .filter(p -> !p.getPlayerId().equals(currentDrawerId))
                .filter(Player::isConnected)
                .count();
        return guessedPlayerIds.size() >= eligibleCount;
    }

    public void resetForNewTurn() {
        guessedPlayerIds.clear();
        drawEvents.clear();
        currentWord = null;
        maskedWord = null;
        players.values().forEach(Player::resetForNewTurn);
    }
}