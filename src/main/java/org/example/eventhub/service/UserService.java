package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.model.User;
import org.example.eventhub.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

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
     * Проверяет соответствие сырого пароля и хеша из базы.
     *
     * @param rawPassword     пароль в чистом виде
     * @param encodedPassword хеш из базы
     * @return true, если пароли совпадают
     */
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}