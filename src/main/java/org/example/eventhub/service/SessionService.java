package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.config.AppConfig;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisHashCommands;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для управления жизненным циклом анонимных сессий в Redis.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final StringRedisTemplate redisTemplate;
    private final AppConfig appConfig;

    private String makeKey(String sid) {
        return "sid:" + sid;
    }

    /**
     * Проверяет существование сессии в Redis.
     *
     * @param sid Идентификатор сессии
     * @return true, если сессия существует, иначе false
     */
    public boolean exists(String sid) {
        if (sid == null)
            return false;
        return redisTemplate.hasKey(makeKey(sid));
    }

    /**
     * Создает новую запись сессии в Redis.
     *
     * @param sid Идентификатор сессии
     */
    public void createSession(String sid) {
        String key = makeKey(sid);
        String now = Instant.now().toString();

        Map<String, String> sessionData = Map.of(
                "created_at", now,
                "updated_at", now
        );
        redisTemplate.opsForHash().putAndExpire(
                key,
                sessionData,
                RedisHashCommands.HashFieldSetOption.upsert(),
                Expiration.seconds(appConfig.getUserSessionTtl())
        );
    }

    /**
     * Обновляет поле {@code updated_at} и продлевает время жизни сессии.
     *
     * @param sid Идентификатор сессии
     */
    public void updateSession(String sid) {
        String key = makeKey(sid);
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        redisTemplate.opsForHash().putAndExpire(
                key,
                Map.of(
                        "updated_at", now
                ),
                RedisHashCommands.HashFieldSetOption.upsert(),
                Expiration.seconds(appConfig.getUserSessionTtl())
        );
    }
}