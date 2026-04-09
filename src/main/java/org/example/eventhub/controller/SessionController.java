package org.example.eventhub.controller;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.util.CookieProvider;
import org.example.eventhub.util.SessionIdGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.example.eventhub.config.AppConfig;

/**
 * Контроллер для обработки запросов управления сессиями.
 */
@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SessionIdGenerator sidGenerator;
    private final CookieProvider cookieProvider;
    private final AppConfig appConfig;

    /**
     * Создает или обновляет сессию пользователя.
     *
     * @param sid Текущий идентификатор сессии из Cookie (может быть null)
     * @return ResponseEntity с HTTP 201 при создании или 200 при обновлении
     */
    @PostMapping("/session")
    public ResponseEntity<Void> session(
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {

        String currentSid = sid;
        HttpStatus status = HttpStatus.OK;

        if (currentSid != null && !currentSid.matches("^[a-f0-9]{32}$")) {
            currentSid = null;
        }

        if (currentSid == null || !sessionService.exists(currentSid)) {
            currentSid = sidGenerator.generateSid();
            sessionService.createSession(currentSid);
            status = HttpStatus.CREATED;
        } else {
            sessionService.updateSession(currentSid);
        }

        ResponseCookie cookie = cookieProvider.createSessionCookie(currentSid);

        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

}