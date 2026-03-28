package org.example.eventhub.controller;

import org.example.eventhub.config.AppConfig;
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
public class HealthController {

    private final AppConfig appConfig;

    public HealthController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Возвращает статус сервиса. Если сессия существует, возвращает её в Cookie.
     *
     * @param sessionId Идентификатор сессии из Cookie
     * @return ResponseEntity с JSON статусом и заголовком Set-Cookie (если сессия передана)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health(
            @CookieValue(name = "X-Session-Id", required = false) String sessionId
    ) {
        Map<String, String> body = Map.of("status", "ok");

        if (sessionId == null || !sessionId.matches("^[a-f0-9]{32}$")) {
            return ResponseEntity.ok(body);
        }

        ResponseCookie cookie = ResponseCookie.from("X-Session-Id", sessionId)
                .httpOnly(true)
                .path("/")
                .maxAge(appConfig.getUserSessionTtl())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }
}