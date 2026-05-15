package org.example.eventhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.eventhub.config.AppConfig;
import org.example.eventhub.dto.ReviewStatsResponse;
import org.example.eventhub.model.Event;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final CqlTemplate cqlTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    private String getCacheKey(String title) {
        String hash = DigestUtils.md5Hex(title.toLowerCase().trim());
        return "event:" + hash + ":reviews";
    }

    /**
     * Получение статистики отзывов
     */
    public ReviewStatsResponse getReviewStats(String title) {
        if (title == null) return new ReviewStatsResponse(0, 0.0);

        String cacheKey = getCacheKey(title);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ReviewStatsResponse.class);
            } catch (Exception ignored) {
            }
        }

        List<String> eventIds = mongoTemplate.find(Query.query(Criteria.where("title").is(title)), Event.class).stream().map(Event::getId).toList();

        ReviewStatsResponse stats = new ReviewStatsResponse(0, 0.0);

        if (!eventIds.isEmpty()) {
            String ids = String.join("','", eventIds);
            String cql = "SELECT rating FROM event_reviews WHERE event_id IN ('" + ids + "')";
            List<Integer> ratings = cqlTemplate.queryForList(cql, Integer.class);

            if (!ratings.isEmpty()) {
                int count = ratings.size();
                double sum = ratings.stream().mapToDouble(Integer::doubleValue).sum();
                double avg = sum / count;

                double roundedAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();

                stats = new ReviewStatsResponse(count, roundedAvg);

                try {
                    redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(stats), Duration.ofSeconds(appConfig.getLikeTtl()));
                } catch (Exception ignored) {
                }
            }
        }
        return stats;
    }

    /**
     * Сохранение нового отзыва
     */
    public String saveReview(String eventId, String userId, String comment, int rating, String title) {
        String checkCql = "SELECT id FROM event_reviews WHERE event_id = ? AND created_by = ?";
        List<UUID> existing = cqlTemplate.queryForList(checkCql, UUID.class, eventId, userId);
        if (!existing.isEmpty()) {
            return null;
        }

        UUID reviewId = UUID.randomUUID();
        Instant now = Instant.now();

        String insertCql = "INSERT INTO event_reviews (id, event_id, created_by, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        cqlTemplate.execute(insertCql, reviewId, eventId, userId, (byte) rating, comment, now, now);

        redisTemplate.delete(getCacheKey(title));

        return reviewId.toString();
    }
}