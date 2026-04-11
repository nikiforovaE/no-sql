package org.example.eventhub.repository;

import org.example.eventhub.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с коллекцией пользователей в MongoDB.
 */
public interface UserRepository extends MongoRepository<User, String> {
    /**
     * Находит пользователя по его логину.
     *
     * @param username имя пользователя
     * @return Optional с найденным пользователем или пустой, если не найден
     */
    Optional<User> findByUsername(String username);
}