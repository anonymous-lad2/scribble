package com.scribble.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")           // clients connect to ws://localhost:8080/ws
                .setAllowedOriginPatterns("*")       // allow all origins in dev — lock down in prod
                .withSockJS();                       // fallback for browsers that don't support raw WS
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix for messages FROM client TO a @MessageMapping method
        // e.g. client sends to "/app/draw" → hits @MessageMapping("/draw")
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for messages the broker pushes TO subscribed clients
        // /topic  → broadcast to everyone in a room
        // /queue  → private message to one player
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for user-specific private messages
        // Spring converts /user/{sessionId}/queue/word → /queue/word for that session
        registry.setUserDestinationPrefix("/user");
    }
}
