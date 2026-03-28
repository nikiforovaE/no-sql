package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.model.User;
import org.example.eventhub.util.SessionIdGenerator;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервис для управления процессами аутентификации и авторизации.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final SessionService sessionService;
    private final SessionIdGenerator sidGenerator;

    /**
     * Выполняет логику входа пользователя.
     *
     * @param username   имя пользователя
     * @param password   пароль
     * @param currentSid текущий ID сессии из куки
     * @return Optional с актуальным ID сессии при успехе, либо пустой при неудаче
     */
    public Optional<String> login(String username, String password, String currentSid) {
        Optional<User> userOpt = userService.findByUsername(username);

        if (userOpt.isEmpty() || !userService.checkPassword(password, userOpt.get().getPasswordHash())) {
            return Optional.empty();
        }

        User user = userOpt.get();
        String activeSid = currentSid;

        if (activeSid == null || !sessionService.exists(activeSid)) {
            activeSid = sidGenerator.generateSid();
            sessionService.createSession(activeSid);
        }

        sessionService.linkUser(activeSid, user.getId());

        return Optional.of(activeSid);
    }

    /**
     * Выполняет выход из системы.
     *
     * @param sid ID сессии для удаления
     */
    public void logout(String sid) {
        if (sid != null) {
            sessionService.deleteSession(sid);
        }
    }
}