package org.example.eventhub.repository;

import org.example.eventhub.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с коллекцией событий в MongoDB.
 */
@Repository
public interface EventRepository extends MongoRepository<Event, String> {
    /**
     * Проверяет существование события по его названию.
     *
     * @param title название события
     * @return true, если событие существует, иначе false
     */
    boolean existsByTitle(String title);
}