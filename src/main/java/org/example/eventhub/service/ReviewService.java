package org.example.eventhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.eventhub.config.AppConfig;
import org.example.eventhub.dto.review.ReviewListResponse;
import org.example.eventhub.dto.review.ReviewResponse;
import org.example.eventhub.dto.review.ReviewStatsResponse;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final CqlTemplate cqlTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneOffset.UTC);

    private String getCacheKey(String title) {
        String hash = DigestUtils.md5Hex(title);
        return "event:" + hash + ":reviews";
    }

    /**
     * Получение статистики отзывов (из кэша или с пересчетом)
     */
    public ReviewStatsResponse getReviewStats(String title) {
        if (title == null) return new ReviewStatsResponse(0, 0.0);

        String cacheKey = getCacheKey(title);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, ReviewStatsResponse.class);
            }
        } catch (Exception ignored) {
        }

        return recalculateAndCache(title);
    }

    /**
     * Пересчет статистики отзывов по названию мероприятия и обновление Redis
     */
    private ReviewStatsResponse recalculateAndCache(String title) {
        String cacheKey = getCacheKey(title);
        List<String> eventIds = mongoTemplate.find(
                        Query.query(Criteria.where("title").is(title)), Event.class)
                .stream().map(Event::getId).toList();

        ReviewStatsResponse stats = new ReviewStatsResponse(0, 0.0);

        if (!eventIds.isEmpty()) {
            String ids = String.join("','", eventIds);
            String cql = "SELECT rating FROM event_reviews WHERE event_id IN ('" + ids + "')";

            List<Byte> ratings = cqlTemplate.queryForList(cql, Byte.class);

            if (!ratings.isEmpty()) {
                int count = ratings.size();
                double sum = ratings.stream().mapToDouble(Byte::doubleValue).sum();
                double avg = sum / count;

                double roundedAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
                stats = new ReviewStatsResponse(count, roundedAvg);
            }
        }

        saveToRedis(cacheKey, stats);

        return stats;
    }

    private void saveToRedis(String key, ReviewStatsResponse stats) {
        try {
            String json = objectMapper.writeValueAsString(stats);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(appConfig.getEventReviewsTtl()));
        } catch (Exception ignored) {
        }
    }

    /**
     * Сохранение нового отзыва
     */
    public String saveReview(String eventId, String userId, String comment, int rating, String title) {
        String checkCql = "SELECT count(*) FROM event_reviews WHERE event_id = ? AND created_by = ?";
        Long count = cqlTemplate.queryForObject(checkCql, Long.class, eventId, userId);
        if (count != null && count > 0) {
            return null;
        }

        UUID reviewId = UUID.randomUUID();
        Instant now = Instant.now();

        String insertCql = "INSERT INTO event_reviews (id, event_id, created_by, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        cqlTemplate.execute(insertCql, reviewId, eventId, userId, (byte) rating, comment, now, now);

        recalculateAndCache(title);

        return reviewId.toString();
    }

    /**
     * Обновление существующего отзыва
     */
    public boolean updateReview(String eventId, String reviewId, String userId, Integer rating, String comment, String title) {
        String selectCql = "SELECT event_id, created_by, rating, comment FROM event_reviews WHERE id = ? ALLOW FILTERING";
        List<Map<String, Object>> results = cqlTemplate.queryForList(selectCql, UUID.fromString(reviewId));

        if (results.isEmpty()) return false;

        Map<String, Object> row = results.get(0);

        if (!row.get("event_id").equals(eventId) || !row.get("created_by").equals(userId)) {
            return false;
        }

        byte finalRating = (rating != null) ? rating.byteValue() : ((Number) row.get("rating")).byteValue();
        String finalComment = (comment != null) ? comment : (String) row.get("comment");
        Instant now = Instant.now();

        String updateCql = "UPDATE event_reviews SET rating = ?, comment = ?, updated_at = ? WHERE event_id = ? AND created_by = ?";
        cqlTemplate.execute(updateCql, finalRating, finalComment, now, eventId, userId);

        recalculateAndCache(title);

        return true;
    }

    /**
     * Получение списка отзывов с пагинацией
     */
    public ReviewListResponse getReviews(String eventId, Integer limit, Integer offset) {
        String cql = "SELECT id, event_id, comment, created_by, rating, created_at, updated_at " +
                "FROM event_reviews WHERE event_id = ?";

        List<Map<String, Object>> rows = cqlTemplate.queryForList(cql, eventId);

        if (rows == null || rows.isEmpty()) {
            return ReviewListResponse.builder()
                    .reviews(List.of())
                    .count(0)
                    .build();
        }

        List<ReviewResponse> allReviews = rows.stream()
                .map(row -> {
                    Object rawId = row.get("id");
                    Object rawCreatedAt = row.get("created_at");
                    Object rawUpdatedAt = row.get("updated_at");

                    return ReviewResponse.builder()
                            .id(rawId != null ? rawId.toString() : "")
                            .event_id((String) row.get("event_id"))
                            .comment((String) row.get("comment"))
                            .created_by((String) row.get("created_by"))
                            .rating(row.get("rating") != null ? ((Number) row.get("rating")).intValue() : 0)
                            .created_at(rawCreatedAt instanceof Instant ? ISO_FORMATTER.format((Instant) rawCreatedAt) : "")
                            .updated_at(rawUpdatedAt instanceof Instant ? ISO_FORMATTER.format((Instant) rawUpdatedAt) : "")
                            .build();
                })
                .toList();

        int fromIndex = Math.min(offset != null ? offset : 0, allReviews.size());
        int toIndex = Math.min(fromIndex + (limit != null ? limit : allReviews.size()), allReviews.size());

        List<ReviewResponse> pagedReviews = allReviews.subList(fromIndex, toIndex);

        return ReviewListResponse.builder()
                .reviews(pagedReviews)
                .count(pagedReviews.size())
                .build();
    }
}