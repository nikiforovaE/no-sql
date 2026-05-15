package org.example.eventhub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Health", description = "Проверка доступности сервиса")
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
    @Operation(
            summary = "Health check",
            description = "Проверяет работоспособность приложения. Если в запросе передана кука X-Session-Id, возвращает её обратно без обновления TTL в Redis."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Сервис доступен",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Возвращается только если X-Session-Id был передан в запросе. TTL НЕ обновляется.",
                            schema = @Schema(type = "string")
                    ),
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"status\": \"ok\"}")
                    )
            )
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health(
            @Parameter(description = "Идентификатор сессии", example = "a3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d")
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