package org.example.eventhub.service;

import com.datastax.oss.driver.api.core.cql.Row;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        if (title == null || title.isBlank())
            return new ReviewStatsResponse(0, 0.0);

        String cacheKey = "event:" + DigestUtils.md5Hex(title.toLowerCase().trim()) + ":reviews";

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ReviewStatsResponse.class);
            } catch (Exception ignored) {
            }
        }

        List<String> eventIds = mongoTemplate.find(
                        Query.query(Criteria.where("title").is(title)), Event.class)
                .stream().map(Event::getId).toList();

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
                    redisTemplate.opsForValue().set(cacheKey,
                            objectMapper.writeValueAsString(stats),
                            Duration.ofSeconds(appConfig.getEventReviewsTtl()));
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

    public ReviewListResponse getReviews(String eventId, Integer limit, Integer offset) {
        String cql = "SELECT id, event_id, comment, created_by, rating, created_at, updated_at " +
                "FROM event_reviews WHERE event_id = ?";

        var rows = cqlTemplate.queryForList(cql, eventId);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        List<ReviewResponse> allReviews = rows.stream()
                .map(row -> ReviewResponse.builder()
                        .id(row.get("id").toString())
                        .event_id((String) row.get("event_id"))
                        .comment((String) row.get("comment"))
                        .created_by((String) row.get("created_by"))
                        .rating(((Byte) row.get("rating")).intValue())
                        .created_at(((java.time.Instant) row.get("created_at"))
                                .atZone(ZoneId.of("UTC")).format(formatter))
                        .updated_at(((java.time.Instant) row.get("updated_at"))
                                .atZone(ZoneId.of("UTC")).format(formatter))
                        .build())
                .toList();

        int fromIndex = Math.min(offset, allReviews.size());
        int toIndex = Math.min(fromIndex + limit, allReviews.size());

        List<ReviewResponse> pagedReviews = allReviews.subList(fromIndex, toIndex);

        return ReviewListResponse.builder()
                .reviews(pagedReviews)
                .count(pagedReviews.size())
                .build();
    }

    public boolean updateReview(String eventId, String reviewId, String userId, Integer rating, String comment, String title) {
        String selectCql = "SELECT event_id, created_by, rating, comment FROM event_reviews WHERE id = ? ALLOW FILTERING";
        Row row = cqlTemplate.queryForObject(selectCql, (r, rowNum) -> r, UUID.fromString(reviewId));

        if (row == null) return false;

        if (!row.getString("event_id").equals(eventId) || !row.getString("created_by").equals(userId)) {
            return false;
        }

        int finalRating = (rating != null) ? rating : row.getByte("rating");
        String finalComment = (comment != null) ? comment : row.getString("comment");
        Instant now = Instant.now();

        String updateCql = "UPDATE event_reviews SET rating = ?, comment = ?, updated_at = ? WHERE event_id = ? AND created_by = ?";
        cqlTemplate.execute(updateCql, (byte) finalRating, finalComment, now, eventId, userId);

        redisTemplate.delete(getCacheKey(title));

        return true;
    }
}