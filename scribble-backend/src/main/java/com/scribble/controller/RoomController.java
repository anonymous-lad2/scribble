package com.scribble.controller;

import com.scribble.dto.CreateRoomRequest;
import com.scribble.dto.JoinRoomRequest;
import com.scribble.dto.RoomResponse;
import com.scribble.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/create")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.ok(roomService.createRoom(request));
    }

    @PostMapping("/join")
    public ResponseEntity<RoomResponse> joinRoom(@Valid @RequestBody JoinRoomRequest request) {
        return ResponseEntity.ok(roomService.joinRoom(request));
    }

    @DeleteMapping("/{roomId}/leave/{playerId}")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId, @PathVariable String playerId) {
        roomService.leaveRoom(roomId, playerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomId, @RequestParam String playerId) {
        var room = roomService.getExistingRoom(roomId);
        return ResponseEntity.ok(RoomResponse.from(room, playerId));
    }
}
