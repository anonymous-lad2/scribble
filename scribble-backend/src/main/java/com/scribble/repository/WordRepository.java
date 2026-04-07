package com.scribble.repository;

import com.scribble.domain.word.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, Long> {

    // Fetch N random words by difficulty and language, excluding already used ones
    @Query(value = """
        SELECT * FROM words WHERE language = :language AND difficulty = :difficulty
        AND word NOT IN (:usedWords) ORDER BY RANDOM() LIMIT :limit
        """, nativeQuery = true)
    List<Word> findRandomWords(
            @Param("language") String language,
            @Param("difficulty") String difficulty,
            @Param("usedWords") List<String> usedWords,
            @Param("limit") int limit
    );

    @Query(value = "SELECT * FROM words WHERE language = :language ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Word> findRandomWordsNoFilter(
            @Param("language") String language,
            @Param("limit") int limit
    );
}
