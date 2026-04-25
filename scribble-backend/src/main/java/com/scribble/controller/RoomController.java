package com.scribble.controller;

import com.scribble.dto.CreateRoomRequest;
import com.scribble.dto.JoinRoomRequest;
import com.scribble.dto.RoomResponse;
import com.scribble.security.AuthenticatedUser;
import com.scribble.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/create")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(roomService.createRoom(request, user.getUserId(), user.getUsername()));
    }

    @PostMapping("/join")
    public ResponseEntity<RoomResponse> joinRoom(
            @Valid @RequestBody JoinRoomRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(roomService.joinRoom(request, user.getUserId(), user.getUsername()));
    }

    @DeleteMapping("/{roomId}/leave/{playerId}")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable String roomId,
            @PathVariable String playerId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        roomService.leaveRoom(roomId, playerId, user.getUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(
            @PathVariable String roomId,
            @RequestParam String playerId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        roomService.assertPlayerBelongsToAccount(roomId, playerId, user.getUserId());
        var room = roomService.getExistingRoom(roomId);
        return ResponseEntity.ok(RoomResponse.from(room, playerId));
    }
}
