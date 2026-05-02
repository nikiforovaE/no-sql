package org.example.eventhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.eventhub.config.AppConfig;
import org.example.eventhub.dto.ReactionResponse;
import org.example.eventhub.model.Event;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final CqlTemplate cqlTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    private final AppConfig appConfig;

    /**
     * Получение реакций (Cache-Aside).
     * Агрегирует лайки по всем мероприятиям с одинаковым названием.
     */
    public ReactionResponse getReactions(String title) {
        if (title == null) return new ReactionResponse(0, 0);

        String cacheKey = "event:" + DigestUtils.md5Hex(title.toLowerCase().trim()) + ":reactions";

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ReactionResponse.class);
            } catch (Exception ignored) {
            }
        }

        Query query = new Query(Criteria.where("title").is(title));
        List<String> eventIds = mongoTemplate.find(query, Event.class).stream().map(Event::getId).toList();

        ReactionResponse result = new ReactionResponse(0, 0);
        if (!eventIds.isEmpty()) {
            String idsClause = String.join("','", eventIds);
            String cql = "SELECT like_value FROM event_reactions WHERE event_id IN ('" + idsClause + "')";

            List<Byte> values = cqlTemplate.queryForList(cql, Byte.class);
            long likes = values.stream().filter(v -> v == 1).count();
            long dislikes = values.stream().filter(v -> v == -1).count();
            result = new ReactionResponse(likes, dislikes);

            try {
                redisTemplate.opsForValue().set(cacheKey,

                        objectMapper.writeValueAsString(result), Duration.ofSeconds(appConfig.getLikeTtl()));
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    /**
     * Сохранение реакции в Cassandra и удаление кэша из Redis.
     */
    public void saveReaction(String eventId, String userId, int value, String title) {
        String cql = "INSERT INTO event_reactions (event_id, created_by, like_value, created_at) VALUES (?, ?, ?, toTimestamp(now()))";
        cqlTemplate.execute(cql, eventId, userId, (byte) value);

        String cacheKey = "event:" + DigestUtils.md5Hex(title.toLowerCase().trim()) + ":reactions";
        redisTemplate.delete(cacheKey);
    }
}