package org.example.eventhub.repository;

import org.example.eventhub.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends MongoRepository<Event, String> {
    /**
     * Проверяет существование события по точному заголовку.
     */
    boolean existsByTitle(String title);
}