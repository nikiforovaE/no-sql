package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class Neo4jSyncService {

    private final Neo4jClient neo4jClient;

    public void syncUser(String userId) {
        neo4jClient.query("MERGE (u:User {id: $id})")
                .bind(userId).to("id")
                .run();
    }

    public void syncEvent(String eventId, String title) {
        neo4jClient.query("MERGE (e:Event {id: $id}) SET e.title = $title")
                .bindAll(Map.of("id", eventId, "title", title))
                .run();
    }

    public void syncLike(String userId, String eventId) {
        neo4jClient.query("""
                        MERGE (u:User {id: $userId}) 
                        MERGE (e:Event {id: $eventId}) 
                        MERGE (u)-[:LIKED]->(e)
                        """)
                .bindAll(Map.of("userId", userId, "eventId", eventId))
                .run();
    }
}