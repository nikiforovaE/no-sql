package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.config.AppConfig;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final StringRedisTemplate redisTemplate;
    private final AppConfig appConfig;

    private String makeKey(String sid) {
        return "sid:" + sid;
    }

    public boolean exists(String sid) {
        if (sid == null)
            return false;
        return redisTemplate.hasKey(makeKey(sid));
    }

    public void createSession(String sid) {
        String key = makeKey(sid);
        String now = Instant.now().toString();

        Map<String, String> sessionData = Map.of(
                "created_at", now,
                "updated_at", now
        );

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

    public void updateSession(String sid) {
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
}