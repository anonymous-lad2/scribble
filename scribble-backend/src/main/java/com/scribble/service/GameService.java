package com.scribble.service;

import com.scribble.domain.game.GameRoom;
import com.scribble.domain.game.GameState;
import com.scribble.domain.player.Player;
import com.scribble.dto.GameEvent;
import com.scribble.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRoomRepository roomRepository;
    private final WordService wordService;
    private final ScoreService scoreService;
    private SimpMessagingTemplate messagingTemplate;

    // one scheduler for all rooms - handles turn timers + hint tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Active turn timers keyed by roomId - started so we can cancel early
    private final Map<String, ScheduledFuture<?>> turnTimers = new ConcurrentHashMap<>();

    // Active hint tasks keyed by roomd
    private final Map<String, List<ScheduledFuture<?>>> hintTasks = new ConcurrentHashMap<>();

    // ── Start game ────────────────────────────────────────────

    public void startGame(String roomId, String requestingPlayerId) {
        GameRoom room = roomRepository.findById(roomId);
        validateRoom(room, roomId);

        if(!room.getHostId().equals(requestingPlayerId)) {
            sendError(requestingPlayerId, "Only the host can start the game");
            return;
        }

        if(room.getPlayerCount() < 2) {
            sendError(requestingPlayerId, "Need at least 2 players to start");
            return;
        }

        if(room.getState() != GameState.LOBBY) {
            sendError(requestingPlayerId, "Game already started");
            return;
        }

        room.setState(GameState.WORD_PICK);
        room.setCurrentRound(1);
        room.setCurrentTurnIndex(-1);
        roomRepository.save(room);

        broadcast(roomId, GameEvent.builder()
                .type(GameEvent.Type.GAME_STARTED)
                .payload(Map.of("totalRounds", room.getTotalRounds()))
                .build());

        log.info("Game started in room {}", roomId);
        startTurn(roomId);
    }

    public void startTurn(String roomId) {
        GameRoom room = roomRepository.findById(roomId);
        validateRoom(room, roomId);

        room.resetForNewTurn();

        // Advance to next drawer via round-robin
        String drawerId = room.getNextDrawerId();
        room.setCurrentDrawerId(drawerId);
        room.setState(GameState.WORD_PICK);

        Player drawer = room.getPlayer(drawerId);
        drawer.setDrawing(true);

        // Get 3 word choices (easy, medium, hard)
        List<String> choices = wordService.getWordChoices(
                room.getLanguage(), room.getUsedWords()
        );

        roomRepository.save(room);

        // Tell everyone a new turn started and who is drawing
        broadcast(roomId, GameEvent.builder()
                .type(GameEvent.Type.TURN_STARTED)
                .payload(Map.of(
                        "drawerId", drawerId,
                        "drawerName", drawer.getUsername(),
                        "round", room.getCurrentRound(),
                        "totalRounds", room.getTotalRounds()
                ))
                .build());

        // send word choices privately to the drawer only
        sendToPlayer(drawer.getSessionId(), GameEvent.builder()
                .type(GameEvent.Type.WORD_CHOICES)
                .payload(Map.of("choices", choices))
                .build());

        log.info("Turn started in room {} - drawer: {}", roomId, drawer.getUsername());
    }

    // ── Word Chosen ───────────────────────────────────────────────

    public void onWordChosen(String roomId, String playerId, String chosenWord) {
        GameRoom room = roomRepository.findById(roomId);
        validateRoom(room, roomId);

        if(!playerId.equals(room.getCurrentDrawerId())) {
            sendError(playerId, "You are not the drawer");
            return;
        }

        if(room.getState() != GameState.WORD_PICK) {
            return; // Stale event, ignore
        }

        room.setCurrentWord(chosenWord);
        room.setMaskedWord(wordService.maskWord(chosenWord));
        room.getUsedWords().add(chosenWord);
        room.setState(GameState.DRAWING);
        roomRepository.save(room);

        // Tell everyone the masked word and who is drawing
        broadcast(roomId, GameEvent.builder()
                .type(GameEvent.Type.WORD_CHOSEN)
                .payload(Map.of(
                        "maskedWord", room.getMaskedWord(),
                        "drawerId", room.getCurrentDrawerId(),
                        "wordLength", chosenWord.length()
                ))
                .build());

        // Schedule the turn timer
        scheduleTurnTimer(room);

        // Schedule hint reveals at 50% and 75% of turn time
        scheduleHints(room);

        log.info("Word chosen in room {}: {} chars", roomId, chosenWord.length());
    }

    // ── End turn ───────────────────────────────────────────────

    public synchronized void endTurn(String roomId) {
        GameRoom room = roomRepository.findById(roomId);
        if(room == null || room.getState() == GameState.REVEAL) return;

        // Cancel the timer and hints if they haven't fired yer
        cancelTurnTimer(roomId);
        cancelHintTasks(roomId);

        room.setState(GameState.REVEAL);

        // calculate and update scores
        Map<String, Integer> turnScores = scoreService.calculateTurnScores(room);
        turnScores.forEach((pid, pts) -> {
            Player p = room.getPlayer(pid);
            if(p != null) p.setScore(p.getScore() + pts);
        });

        roomRepository.save(room);

        // Reveal the actual word to everyone
        broadcast(roomId, GameEvent.builder()
                .type(GameEvent.Type.TURN_ENDED)
                .payload(Map.of(
                        "actualWord", room.getCurrentWord(),
                        "scores", buildScorePayload(room)
                ))
                .build());

        log.info("Turn ended in room {} - word was: {}", roomId, room.getCurrentWord());

        // wait 4 seconds on REVEAL screen, then advance
        scheduler.schedule(() -> advanceAfterReveal(roomId), 4, TimeUnit.SECONDS);
    }

    // ── Advance after Reveal ───────────────────────────────────────────────

    private void advanceAfterReveal(String roomId) {
        GameRoom room = roomRepository.findById(roomId);
        if(room == null) return;

        boolean allPlayersHadTurn = room.getCurrentTurnIndex() >= room.getTurnOrder().size() - 1;

        if(allPlayersHadTurn) {
            // All players drew this round
            if(room.getCurrentRound() >= room.getTotalRounds()) {
                endGame(roomId);
            } else {
                // Next round
                room.setCurrentRound(room.getCurrentRound() + 1);
                room.setCurrentTurnIndex(-1); // reset turn index for next round
                roomRepository.save(room);

                broadcast(roomId, GameEvent.builder()
                        .type(GameEvent.Type.ROUND_ENDED)
                        .payload(Map.of(
                                "round", room.getCurrentRound() - 1,
                                "scores", buildScorePayload(room)
                        ))
                        .build());

                // small pause between rounds
                scheduler.schedule(() -> startTurn(roomId), 3, TimeUnit.SECONDS);
            }
        } else{
            startTurn(roomId);
        }
    }

    // ── End Game ───────────────────────────────────────────────

    private void endGame(String roomId) {
        GameRoom room = roomRepository.findById(roomId);
        if(room == null) return;

        room.setState(GameState.ENDED);
        roomRepository.save(room);

        // sort players by score descending for leaderboard
        List<Map<String, Object>> leaderboard = room.getPlayers().values().stream()
                .sorted((a, b) -> b.getScore() - a.getScore())
                .map(p -> Map.<String, Object>of(
                        "playerId", p.getPlayerId(),
                        "username", p.getUsername(),
                        "score", p.getScore(),
                        "avatarColor", p.getAvatarColor()
                ))
                .toList();

        broadcast(roomId, GameEvent.builder()
                .type(GameEvent.Type.GAME_ENDED)
                .payload(Map.of("leaderboard", leaderboard))
                .build());

        log.info("Game ended in room {}", roomId);
    }

    // ── Time Scheduling ───────────────────────────────────────────────

    private void scheduleTurnTimer(GameRoom room) {
        String roomId = room.getRoomId();
        int duration = room.getTurnDurationSeconds();

        // Broadcast a tick every second so clients can show countdown
        ScheduledFuture<?> tickTask = scheduler.scheduleAtFixedRate(() -> {
            // we track time client-side too, this is just a server sync
            // In a production build you'd track remaining seconds in Redis
        }, 1, 1, TimeUnit.SECONDS);

        // The main timer that ends the turn
        ScheduledFuture<?> turnEndTask = scheduler.schedule(
                () -> endTurn(roomId),
                duration,
                TimeUnit.SECONDS
        );

        // store the end task so we can cancel it if everyone guesses early
        turnTimers.put(roomId, turnEndTask);
    }

    private void scheduleHints(GameRoom room) {
        String roomId = room.getRoomId();
        int duration = room.getTurnDurationSeconds();

        // First hint at 50% of a turn time
        ScheduledFuture<?> hint1 = scheduler.schedule(
                () -> revealHint(roomId),
                duration / 2,
                TimeUnit.SECONDS
        );

        // Second Hint at 75% of turn time
        ScheduledFuture<?> hint2 = scheduler.schedule(
                () -> revealHint(roomId),
                (duration * 3) / 4,
                TimeUnit.SECONDS
        );

        hintTasks.put(roomId, List.of(hint1, hint2));
    }

    private void revealHint(String roomId) {
        GameRoom room = roomRepository.findById(roomId);
        if(room == null || room.getState() != GameState.DRAWING) return;

        String newMasked = wordService.revealHintLetter(
                room.getMaskedWord(), room.getCurrentWord()
        );
        room.setMaskedWord(newMasked);
        roomRepository.save(room);

        broadcast(roomId, GameEvent.builder()
                .type(GameEvent.Type.HINT)
                .payload(Map.of("maskedWord", newMasked))
                .build());

        log.debug("Hint revealed in room {}: {}", roomId, newMasked);
    }

    private void cancelTurnTimer(String roomId) {
        ScheduledFuture<?> timer = turnTimers.remove(roomId);
        if(timer != null) timer.cancel(false);
    }

    private void cancelHintTasks(String roomId) {
        List<ScheduledFuture<?>> hints = hintTasks.remove(roomId);
        if(hints != null) hints.forEach(h -> h.cancel(false));
    }

    // ── Guess Handling ───────────────────────────────────────────────

    // Called by GuessingService when a correct guess comes in
    public void onCorrectGuess(String roomId, String playerId) {
        GameRoom room = roomRepository.findById(roomId);
        if(room == null) return;

        room.getGuessedPlayerIds().add(playerId);
        Player guesser = room.getPlayer(playerId);
        roomRepository.save(room);

        broadcast(roomId, GameEvent.builder()
                .type(GameEvent.Type.PLAYER_GUESSED)
                .payload(Map.of(
                        "username", guesser.getUsername(),
                        "totalGuessed", room.getGuessedPlayerIds().size()
                ))
                .build());

        // End turn early if everyone has guessed
        if(room.allPlayersGuessed()) {
            log.info("All players guessed in room {} - ending turn early", roomId);
            endTurn(roomId);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private void broadcast(String roomId, GameEvent event) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/game", event);
    }

    private void sendToPlayer(String sessionId, GameEvent event) {
        // sends to /user/{sessionId}/queue/game - only that player receives it
        messagingTemplate.convertAndSendToUser(
                sessionId, "/queue/game", event
        );
    }

    private void sendError(String playerID, String message) {
        messagingTemplate.convertAndSendToUser(
                playerID, "/queue/errors",
                GameEvent.builder()
                        .type(GameEvent.Type.ERROR)
                        .payload(Map.of("message", message))
                        .build()
        );
    }

    private void validateRoom(GameRoom room, String roomId) {
        if(room == null) throw new IllegalArgumentException("Room not found: " + roomId);
    }

    private List<Map<String, Object>> buildScorePayload(GameRoom room) {
        return room.getPlayers().values().stream()
                .map(p -> Map.<String, Object>of(
                        "PlayerId", p.getPlayerId(),
                        "username", p.getUsername(),
                        "score", p.getScore()
                ))
                .toList();
    }
}
