package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.config.AppConfig;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
        if (sid == null) return false;
        return redisTemplate.hasKey(makeKey(sid));
    }

    /**
     * Создает пустую анонимную сессию
     *
     * @param sid Идентификатор сессии
     */
    public void createSession(String sid) {
        String key = makeKey(sid);
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, String> sessionData = Map.of("created_at", now, "updated_at", now);

        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().putAll(key, sessionData);
                operations.expire(key, appConfig.getUserSessionTtl(), TimeUnit.SECONDS);
                return operations.exec();
            }
        });
    }

    /**
     * Обновляет поле {@code updated_at} и продлевает время жизни сессии.
     *
     * @param sid Идентификатор сессии
     */
    public void updateSession(String sid) {
        if (sid == null) return;

        String key = makeKey(sid);
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().put(key, "updated_at", now);
                operations.expire(key, appConfig.getUserSessionTtl(), TimeUnit.SECONDS);
                return operations.exec();
            }
        });
    }

    /**
     * Удаляет сессию из Redis (нужно для Logout).
     *
     * @param sid Идентификатор сессии
     */
    public void deleteSession(String sid) {
        if (sid != null) {
            redisTemplate.delete(makeKey(sid));
        }
    }

    /**
     * Привязывает ID пользователя из MongoDB к сессии в Redis.
     *
     * @param sid    Идентификатор сессии
     * @param userId Идентификатор пользователя
     */
    public void linkUser(String sid, String userId) {
        if (sid == null || userId == null) return;

        String key = makeKey(sid);

        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().put(key, "user_id", userId);
                operations.opsForHash().put(key, "updated_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                operations.expire(key, appConfig.getUserSessionTtl(), TimeUnit.SECONDS);
                return operations.exec();
            }
        });
    }

    /**
     * Получает ID пользователя, привязанного к сессии.
     *
     * @param sid Идентификатор сессии
     * @return ID пользователя, null - если сессия анонимная
     */
    public String getUserId(String sid) {
        if (sid == null) return null;
        return (String) redisTemplate.opsForHash().get(makeKey(sid), "user_id");
    }
}