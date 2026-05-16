package org.example.eventhub.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final CqlTemplate cqlTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final AppConfig appConfig;

    private String getCacheKey(String title) {
        String hash = DigestUtils.md5Hex(title);
        return "event:" + hash + ":reviews";
    }

    /**
     * Получение статистики отзывов (из кэша или с пересчетом)
     */
    public ReviewStatsResponse getReviewStats(String title, String eventId) {
        if (title == null) {
            return new ReviewStatsResponse(0, 0.0);
        }

        String cacheKey = getCacheKey(title);
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
            if (entries != null && !entries.isEmpty()) {
                String countStr = (String) entries.get("count");
                String ratingStr = (String) entries.get("rating");
                if (countStr != null && ratingStr != null) {
                    return new ReviewStatsResponse(Integer.parseInt(countStr), Double.parseDouble(ratingStr));
                }
            }
        } catch (Exception ignored) {
            return new ReviewStatsResponse(0, 0.0);
        }

        ReviewStatsResponse stats = recalculateAndCache(title);
        return stats != null ? stats : new ReviewStatsResponse(0, 0.0);
    }

    /**
     * Пересчет статистики отзывов по названию мероприятия и обновление Redis
     */
    private ReviewStatsResponse recalculateAndCache(String title) {
        if (title == null) {
            return new ReviewStatsResponse(0, 0.0);
        }

        String cacheKey = getCacheKey(title);

        List<String> eventIds = mongoTemplate.find(
                        Query.query(Criteria.where("title").is(title)), Event.class)
                .stream().map(Event::getId).toList();

        ReviewStatsResponse stats = new ReviewStatsResponse(0, 0.0);

        if (!eventIds.isEmpty()) {
            int totalCount = 0;
            double totalSum = 0.0;

            for (String id : eventIds) {
                String cql = "SELECT rating FROM event_reviews WHERE event_id = ? ALLOW FILTERING";
                try {
                    List<Byte> ratings = cqlTemplate.queryForList(cql, Byte.class, id);
                    if (ratings != null && !ratings.isEmpty()) {
                        totalCount += ratings.size();
                        totalSum += ratings.stream().mapToDouble(Byte::doubleValue).sum();
                    }
                } catch (Exception ignored) {
                }
            }

            if (totalCount > 0) {
                double avg = totalSum / totalCount;
                double roundedAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
                stats = new ReviewStatsResponse(totalCount, roundedAvg);
            }
        }

        saveToRedis(cacheKey, stats);

        return stats;
    }

    private void saveToRedis(String key, ReviewStatsResponse stats) {
        try {
            Map<String, String> hashModel = new HashMap<>();
            hashModel.put("count", String.valueOf(stats.getCount()));
            hashModel.put("rating", String.valueOf(stats.getRating()));

            redisTemplate.opsForHash().putAll(key, hashModel);
            redisTemplate.expire(key, Duration.ofSeconds(appConfig.getEventReviewsTtl()));
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
        String selectCql = "SELECT id, rating, comment FROM event_reviews WHERE event_id = ? AND created_by = ?";
        List<Map<String, Object>> results = cqlTemplate.queryForList(selectCql, eventId, userId);

        if (results.isEmpty()) return false;

        Map<String, Object> row = results.getFirst();
        if (!row.get("id").toString().equals(reviewId)) return false;

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
                "FROM event_reviews WHERE event_id = ? ALLOW FILTERING";

        List<Map<String, Object>> rows = cqlTemplate.queryForList(cql, eventId);

        if (rows.isEmpty()) {
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

                    String createdAtStr = "";
                    String updatedAtStr = "";

                    if (rawCreatedAt instanceof Instant) {
                        createdAtStr = ((Instant) rawCreatedAt)
                                .atOffset(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    }
                    if (rawUpdatedAt instanceof Instant) {
                        updatedAtStr = ((Instant) rawUpdatedAt)
                                .atOffset(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    }

                    String resEventId = row.get("event_id") != null ? row.get("event_id").toString() : eventId;

                    return ReviewResponse.builder()
                            .id(rawId != null ? rawId.toString() : "")
                            .event_id(resEventId)
                            .comment((String) row.get("comment"))
                            .created_by((String) row.get("created_by"))
                            .rating(row.get("rating") != null ? ((Number) row.get("rating")).intValue() : 0)
                            .created_at(createdAtStr)
                            .updated_at(updatedAtStr)
                            .build();
                })
                .sorted((r1, r2) -> {
                    if (r1.getCreated_at() == null || r2.getCreated_at() == null) return 0;
                    return r2.getCreated_at().compareTo(r1.getCreated_at());
                })
                .toList();

        int fromIndex = Math.min(offset != null ? offset : 0, allReviews.size());
        int toIndex = Math.min(fromIndex + (limit != null ? limit : allReviews.size()), allReviews.size());
        List<ReviewResponse> pagedReviews = allReviews.subList(fromIndex, toIndex);

        return ReviewListResponse.builder()
                .reviews(pagedReviews)
                .count(allReviews.size())
                .build();
    }
}