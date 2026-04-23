package com.scribble.domain.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(unique = true, length = 100)
    private String email;

    @Column
    private String password;

    @Column(nullable = false)
    private String avatarColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Stats
    private int totalGamesPlayed;
    private int totalWins;
    private int totalScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (role == null) role = Role.PLAYER;
    }

    public enum Role {
        GUEST, PLAYER, ADMIN
    }

}
