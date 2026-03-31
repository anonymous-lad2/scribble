package com.scribble.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @NotBlank
    private String username;

    private String avatarColor = "#4F46E5";  // default indigo

    @Min(1) @Max(8)
    private int totalRounds = 3;

    @Min(30) @Max(180)
    private int turnDurationSeconds = 80;

    @Min(2) @Max(12)
    private int maxPlayers = 8;

    private String language = "en";
}
