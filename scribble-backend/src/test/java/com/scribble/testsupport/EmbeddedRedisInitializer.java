package com.scribble.testsupport;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import redis.embedded.RedisServer;

import java.io.IOException;

/**
 * Starts an in-process Redis server before the Spring context connects — no Docker required.
 */
public class EmbeddedRedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Matches {@code application-test.properties}; overridden below after bind. */
    private static final int REDIS_TEST_PORT = 6389;

    private static RedisServer redisServer;

    @Override
    public synchronized void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            if (redisServer == null) {
                redisServer = RedisServer.newRedisServer()
                        .bind("127.0.0.1")
                        .port(REDIS_TEST_PORT)
                        .build();
                redisServer.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        redisServer.stop();
                    } catch (IOException ignored) {
                        // best-effort shutdown for tests
                    }
                }));
            }
            int port = redisServer.ports().isEmpty() ? REDIS_TEST_PORT : redisServer.ports().getFirst();
            TestPropertyValues.of(
                    "spring.data.redis.host=127.0.0.1",
                    "spring.data.redis.port=" + port
            ).applyTo(applicationContext.getEnvironment());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start embedded Redis for tests", e);
        }
    }
}
