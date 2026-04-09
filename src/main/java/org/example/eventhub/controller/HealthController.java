package org.example.eventhub.controller;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.util.CookieProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Контроллер для проверки состояния сервиса.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final CookieProvider cookieProvider;

    /**
     * Возвращает статус сервиса. Если сессия существует, возвращает её в Cookie.
     *
     * @param sessionId Идентификатор сессии из Cookie
     * @return ResponseEntity с JSON статусом и заголовком Set-Cookie (если сессия передана)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health(
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sessionId
    ) {
        Map<String, String> body = Map.of("status", "ok");

        if (sessionId == null || !sessionId.matches("^[a-f0-9]{32}$")) {
            return ResponseEntity.ok(body);
        }

        ResponseCookie cookie = cookieProvider.createSessionCookie(sessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }
}