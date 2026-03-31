package com.scribble.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinRoomRequest {

    @NotBlank
    private String roomId;

    @NotBlank
    private String username;

    private String avatarColor = "#10B981";  // default green
}
