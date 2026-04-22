package com.scribble.service;

import com.scribble.domain.game.GameRoom;
import com.scribble.domain.game.GameState;
import com.scribble.domain.player.Player;
import com.scribble.dto.GameEvent;
import com.scribble.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuessService {

    private final GameRoomRepository roomRepository;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    // Max edit distance to count as "close" - 1 typo is forgiven
    private static final int CLOSE_GUESS_THRESHOLD = 1;
    private static final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    public void handleGuess(String roomId, String playerId, String rawGuess) {
        GameRoom room = roomRepository.findById(roomId);
        if(room == null || room.getState() != GameState.DRAWING) return;

        Player guesser = room.getPlayer(playerId);
        if(guesser == null) return;

        // Drawer can't guess their own word
        if(playerId.equals(room.getCurrentDrawerId())) return;

        // Already guessed correctly this turn - ignore
        if(room.getGuessedPlayerIds().contains(playerId)) return;

        String guess = normalize(rawGuess);
        String actual = normalize(room.getCurrentWord());

        if(guess.equals(actual)) {
            handleCorrectGuess(room, guesser, roomId, playerId);
        } else{
            handleWrongGuess(room, guesser, roomId, guess, actual, rawGuess);
        }
    }

    private void handleCorrectGuess(GameRoom room, Player guesser, String roomId, String playerId) {
        guesser.setHasGuessedCorrect(true);
        roomRepository.save(room);

        // send private confirmation to the guesser
        messagingTemplate.convertAndSendToUser(
                guesser.getSessionId(), "/queue/game",
                GameEvent.builder()
                        .type(GameEvent.Type.PLAYER_GUESSED)
                        .payload(Map.of(
                                "correct", true,
                                "message", "You guessed it!"
                        ))
                        .build()
        );

        gameService.onCorrectGuess(roomId, playerId);

        log.info("Correct guess in room {} by {}", roomId, guesser.getUsername());
    }

    private void handleWrongGuess(GameRoom room, Player guesser, String roomId, String guess, String actual, String rawGuess) {
        int distance = levenshtein.apply(guess, actual);
        boolean isClose = distance <= CLOSE_GUESS_THRESHOLD && guess.length() > 2; // avoid "a" matching "at" as close

        // Broadcast the guess to chat so everyone sees it
        // BUT hide it from the drawer (they'd see the answer)
        // In this simple version we broadcast to all —
        // frontend hides it from drawer based on their role
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/chat",
                (Object) Map.of(
                        "type", isClose ? "CLOSE_GUESS" : "CHAT",
                        "username", guesser.getUsername(),
                        "message", rawGuess,
                        "isClose", isClose,
                        "playerId", guesser.getPlayerId()
                )
        );

        if(isClose) {
            // Send privately "you're close!" nudge to the guesser
            messagingTemplate.convertAndSendToUser(
                    guesser.getSessionId(), "/queue/game",
                    GameEvent.builder()
                            .type(GameEvent.Type.ERROR)
                            .payload(Map.of(
                                    "message", "So close! Keep trying...",
                                    "type", "CLOSE_HINT"
                            ))
                            .build()
            );
        }

        log.debug("Wrong guess in room {} by {}: '{}' (distance: {})",
                roomId, guesser.getUsername(), guess, distance);
    }

    // Normalize: lowercase, trim, collapse multiple spaces
    private String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ");
    }
}
