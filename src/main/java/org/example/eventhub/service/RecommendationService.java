package org.example.eventhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.eventhub.config.AppConfig;
import org.example.eventhub.dto.event.RecommendationResponse;
import org.example.eventhub.model.Event;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final Neo4jClient neo4jClient;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    public RecommendationResponse getRecommendations(String userId) {
        String cacheKey = "user:" + userId + ":recomms";
        String hashField = "events";

        try {
            Object cached = redisTemplate.opsForHash().get(cacheKey, hashField);
            if (cached != null) {
                List<Event> cachedEvents = objectMapper.readValue(cached.toString(), new TypeReference<List<Event>>() {
                });
                return new RecommendationResponse(cachedEvents);
            }
        } catch (Exception ignored) {
        }

        String cypher = """
                MATCH (u:User {id: $userId})-[:LIKED]->(liked:Event)<-[:LIKED]-(other:User)
                MATCH (other)-[:LIKED]->(rec:Event)
                WHERE NOT (u)-[:LIKED]->(rec) AND u <> other
                RETURN rec.id AS eventId, COUNT(other) AS score
                ORDER BY score DESC
                """;

        Collection<Map<String, Object>> records = neo4jClient.query(cypher)
                .bind(userId).to("userId")
                .fetch().all();

        if (records.isEmpty()) {
            return saveAndReturn(cacheKey, hashField, new RecommendationResponse(List.of()));
        }

        Map<String, Long> scores = new HashMap<>();
        for (Map<String, Object> rec : records) {
            scores.put((String) rec.get("eventId"), (Long) rec.get("score"));
        }

        List<Event> events = mongoTemplate.find(
                Query.query(Criteria.where("id").in(scores.keySet())), Event.class
        );

        Instant now = Instant.now();
        Map<String, Event> deduplicated = new HashMap<>();

        for (Event event : events) {
            String title = event.getTitle();
            if (!deduplicated.containsKey(title)) {
                deduplicated.put(title, event);
            } else {
                Event existing = deduplicated.get(title);
                long existingDiff = Math.abs(OffsetDateTime.parse(existing.getStartedAt()).toInstant().toEpochMilli() - now.toEpochMilli());
                long currentDiff = Math.abs(OffsetDateTime.parse(event.getStartedAt()).toInstant().toEpochMilli() - now.toEpochMilli());
                if (currentDiff < existingDiff) {
                    deduplicated.put(title, event);
                }
            }
        }

        List<Event> finalEvents = new ArrayList<>(deduplicated.values());
        finalEvents.sort((e1, e2) -> {
            Long score1 = scores.getOrDefault(e1.getId(), 0L);
            Long score2 = scores.getOrDefault(e2.getId(), 0L);
            return Long.compare(score2, score1);
        });

        RecommendationResponse response = new RecommendationResponse(finalEvents);
        return saveAndReturn(cacheKey, hashField, response);
    }

    private RecommendationResponse saveAndReturn(String cacheKey, String hashField, RecommendationResponse response) {
        try {
            long ttl = (appConfig.getRecommendationsTtl() != null) ? appConfig.getRecommendationsTtl() : 60L;

            String json = objectMapper.writeValueAsString(response.getEvents());

            redisTemplate.opsForHash().put(cacheKey, hashField, json);
            redisTemplate.expire(cacheKey, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            System.err.println("Ошибка сохранения рекомендаций в Redis: " + e.getMessage());
        }
        return response;
    }
}