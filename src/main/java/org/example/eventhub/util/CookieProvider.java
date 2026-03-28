package org.example.eventhub.util;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.config.AppConfig;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Вспомогательный компонент для создания и управления куками сессий.
 */
@Component
@RequiredArgsConstructor
public class CookieProvider {

    /**
     * Имя HTTP-куки, используемой для идентификации сессии.
     */
    public static final String SESSION_COOKIE_NAME = "X-Session-Id";

    private final AppConfig appConfig;

    /**
     * Создает куку с идентификатором сессии.
     *
     * @param sid идентификатор сессии (Session ID)
     * @return настроенный объект ResponseCookie
     */
    public ResponseCookie createSessionCookie(String sid) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, sid)
                .httpOnly(true)
                .path("/")
                .maxAge(appConfig.getUserSessionTtl())
                .build();
    }
}