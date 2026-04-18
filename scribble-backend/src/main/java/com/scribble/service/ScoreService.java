package com.scribble.service;

import com.scribble.domain.game.GameRoom;
import com.scribble.domain.player.Player;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ScoreService {

    private static final int MAX_GUESSER_SCORE = 300;
    private static final int MIN_GUESSER_SCORE = 50;
    private static final int MAX_DRAWER_BONUS = 100;

    // Called at the end of each turn
    // Returns a map of playerId → points earned THIS turn
    public Map<String, Integer> calculateTurnScores(GameRoom room) {
        Map<String, Integer> turnScores = new HashMap<>();
        int totalEligible = (int) room.getPlayers().values().stream()
                .filter(p -> !p.getPlayerId().equals(room.getCurrentDrawerId()))
                .filter(Player::isConnected)
                .count();

        if(totalEligible == 0) return turnScores;

        int correctGuesses = room.getGuessedPlayerIds().size();

        // Guesser score: we don't track exact guess time yet,
        // so everyone who guessed gets a flat score based on how
        // quickly the group finished (ratio of guessers)
        // In a later iteration you'd store the timestamp of each guess
        for(String guesserId : room.getGuessedPlayerIds()) {
            int points = calculateGuesserPoints(correctGuesses, totalEligible);
            turnScores.put(guesserId, points);
        }

        // Drawer bonus: proportional to how many players guessed correctly
        if(correctGuesses > 0) {
            int drawerBonus = (int) ((double) correctGuesses / totalEligible * MAX_DRAWER_BONUS);
            turnScores.put(room.getCurrentDrawerId(), drawerBonus);
        }

        return turnScores;
    }

    // Score scales with what fraction of players guessed
    // All guess fast → close to MAX; barely any guessed → close to MIN
    private int calculateGuesserPoints(int correctGuesses, int totalEligible) {
        double ratio = (double) correctGuesses / totalEligible;
        return (int) (MIN_GUESSER_SCORE + (MAX_GUESSER_SCORE - MIN_GUESSER_SCORE) * (1.0 - ratio + 0.3));
    }
}
