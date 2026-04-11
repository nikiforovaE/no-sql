package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.model.User;
import org.example.eventhub.repository.UserRepository;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления пользователями и их данными.
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    /**
     * Проверяет, зарегистрирован ли пользователь с таким логином.
     *
     * @param username имя пользователя
     * @return true, если пользователь найден
     */
    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    /**
     * Создает нового пользователя.
     *
     * @param fullName полное имя
     * @param username логин
     * @param password пароль в открытом виде
     * @return сохраненный объект пользователя
     */
    public User createUser(String fullName, String username, String password) {
        User user = User.builder()
                .fullName(fullName)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        return userRepository.save(user);
    }

    /**
     * Находит пользователя по его уникальному идентификатору.
     *
     * @param id идентификатор пользователя
     * @return Optional с объектом пользователя
     */
    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    /**
     * Ищет пользователя по имени.
     *
     * @param username имя пользователя
     * @return Optional с пользователем или пустой
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Выполняет поиск пользователей по фильтрам.
     */
    public List<User> findUsers(String id, String name, Integer limit, Integer offset) {
        Query query = new Query();

        if (id != null && !id.isBlank()) {
            query.addCriteria(Criteria.where("_id").is(id));
        }

        if (name != null && !name.isBlank()) {
            query.addCriteria(Criteria.where("full_name").regex(name, "i"));
        }

        if (offset != null) query.skip(offset);
        if (limit != null) query.limit(limit);

        return mongoTemplate.find(query, User.class);
    }

    /**
     * Сверяет введенный пароль с хешем из базы данных.
     *
     * @param rawPassword     пароль в открытом виде
     * @param encodedPassword хеш пароля из БД
     * @return true, если пароли совпадают
     */
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}