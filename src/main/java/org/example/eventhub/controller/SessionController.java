package org.example.eventhub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Sessions", description = "Управление анонимными сессиями")
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
    @Operation(
            summary = "Создание или обновление сессии",
            description = "Если кука отсутствует или сессия истекла — создает новую (201). Если кука есть и сессия активна — обновляет её TTL в Redis (200)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Сессия создана (первый визит)",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "X-Session-Id={sid}; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}",
                            schema = @Schema(type = "string")
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Сессия обновлена (повторный визит)",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "X-Session-Id={sid}; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}",
                            schema = @Schema(type = "string")
                    )
            )
    })
    @PostMapping("/session")
    public ResponseEntity<Void> session(
            @Parameter(description = "ID сессии из Cookie", example = "3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d")
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