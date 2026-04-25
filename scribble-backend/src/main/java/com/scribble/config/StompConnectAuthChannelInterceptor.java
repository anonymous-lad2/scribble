package com.scribble.config;

import com.scribble.security.AuthenticatedUser;
import com.scribble.security.JwtService;
import com.scribble.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Validates JWT on STOMP CONNECT and checks {@code roomId}/{@code playerId} against
 * the account id stored on the player when they created/joined the room.
 */
@Component
@RequiredArgsConstructor
public class StompConnectAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final RoomService roomService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("STOMP CONNECT requires Authorization: Bearer <token>");
        }
        String token = authHeader.substring(7);
        if (!jwtService.isValid(token)) {
            throw new IllegalStateException("Invalid or expired token");
        }

        String userId = jwtService.extractUserId(token);
        String username = jwtService.extractUsername(token);
        String role = jwtService.extractRole(token);
        if (!StringUtils.hasText(role)) {
            role = "GUEST";
        }

        String roomId = accessor.getFirstNativeHeader("roomId");
        String playerId = accessor.getFirstNativeHeader("playerId");
        if (!StringUtils.hasText(roomId) || !StringUtils.hasText(playerId)) {
            throw new IllegalStateException("STOMP CONNECT requires roomId and playerId headers");
        }

        try {
            roomService.assertPlayerBelongsToAccount(roomId, playerId, userId);
        } catch (ResponseStatusException ex) {
            String reason = ex.getReason() != null ? ex.getReason() : "Forbidden";
            throw new IllegalStateException(reason, ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }

        var auth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, username, role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        accessor.setUser(auth);
        accessor.getSessionAttributes().put("playerId", playerId);
        accessor.getSessionAttributes().put("roomId", roomId);
        return message;
    }
}
