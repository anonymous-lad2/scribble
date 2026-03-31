package com.scribble.repository;

import com.scribble.domain.game.GameRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class GameRoomRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis Key pattern: "room:XK92AB"
    private static final String KEY_PREFIX = "room";

    // Rooms expire after 2 hours of inactivity
    private static final long ROOM_TTL_HOURS = 2;

    public void save(GameRoom room) {
        String key = KEY_PREFIX + room.getRoomId();
        redisTemplate.opsForValue().set(key, room, ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public GameRoom findById(String roomId) {
        String key = KEY_PREFIX + roomId;
        return (GameRoom) redisTemplate.opsForValue().get(key);
    }

    public void delete(String roomId) {
        redisTemplate.delete(KEY_PREFIX + roomId);
    }

    public boolean exists(String roomId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + roomId));
    }

    // Refresh TTL on every activity so active room don't expire
    public void refreshTtl(String roomId) {
        redisTemplate.expire(KEY_PREFIX + roomId, ROOM_TTL_HOURS, TimeUnit.HOURS);
    }
}
