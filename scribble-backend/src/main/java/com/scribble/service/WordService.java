package com.scribble.service;

import com.scribble.domain.word.Word;
import com.scribble.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordService {

    private final WordRepository wordRepository;

    // Return 3 word choices for the drawer - one for each difficulty
    public List<String> getWordChoices(String language, Set<String> usedWords) {
        List<String> choices = new ArrayList<>();
        List<String> usedList = usedWords.isEmpty() ? List.of("__none__") : new ArrayList<>(usedWords);

        for(Word.Difficulty difficulty : Word.Difficulty.values()) {
            List<Word> words = wordRepository.findRandomWords(language, difficulty.name(), usedList, 1);

            if(words.isEmpty()) {
                // All words of this difficulty exhausted - pull any random one
                words = wordRepository.findRandomWordsNoFilter(language, 1);
            }

            if(!words.isEmpty()) {
                choices.add(words.getFirst().getWord());
            }
        }

        if(choices.isEmpty()) {
            choices = List.of("apple", "bicycle", "elephant");
        }

        return choices;
    }

    // Build the masked word: "elephant" -> "_ _ _ _ _ _ _ _"
    public String maskWord(String word) {
        StringBuilder masked = new StringBuilder();

        for(int i = 0; i < word.length(); i++) {
            if(word.charAt(i) == ' ') {
                masked.append(' ');
            }
            else {
                masked.append('_');
            }

            if(i < word.length() - 1) masked.append(' ');
        }

        return masked.toString();
    }

    // Reveal one random hidden letter for hint
    // "_ _ _ _ _ _ _ _" → "_ l _ _ _ _ _ _"
    public String revealHintLetter(String currentMasked, String actualWord) {
        List<Integer> hiddenPosition = new ArrayList<>();
        String[] parts = currentMasked.split(" ");

        for(int i = 0; i < parts.length; i++) {
            if(parts[i].equals("_")) {
                hiddenPosition.add(i);
            }
        }

        if(hiddenPosition.isEmpty()) {
            return currentMasked;
        }

        int revealIndex = hiddenPosition.get(
                (int) (Math.random() * hiddenPosition.size())
        );

        parts[revealIndex] = String.valueOf(actualWord.charAt(revealIndex));

        return String.join(" ", parts);
    }
}
