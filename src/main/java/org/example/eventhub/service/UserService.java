package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.model.User;
import org.example.eventhub.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервис для управления пользователями и их данными.
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
     * Ищет пользователя по имени.
     *
     * @param username имя пользователя
     * @return Optional с пользователем или пустой
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
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