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
import org.example.eventhub.dto.LoginRequest;
import org.example.eventhub.service.AuthService;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.util.CookieProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Контроллер для управления аутентификацией (вход и выход).
 */
@Tag(name = "Authentication", description = "Управление аутентификацией пользователя")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final CookieProvider cookieProvider;

    /**
     * Выполняет вход пользователя в систему и привязывает его к сессии.
     *
     * @param request данные для входа (username, password)
     * @param sid     идентификатор текущей сессии из куки
     * @return 204 No Content и обновленная кука при успехе, иначе 401
     */
    @Operation(
            summary = "Аутентификация пользователя",
            description = "Проверяет credentials. При успехе привязывает сессию к пользователю и возвращает Cookie X-Session-Id."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Успешная аутентификация",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Устанавливает X-Session-Id куку", schema = @Schema(type = "string")),
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Неверный логин или пароль",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Обновляет TTL существующей куки", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"message\": \"invalid credentials\"}"))
            )
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Данные пользователя",
                    content = @Content(examples = @ExampleObject(value = "{\"username\": \"j0hnd0e42\", \"password\": \"svp4_dvp4_str0ng_passw0rd\"}"))
            )
            @RequestBody LoginRequest request,
            @Parameter(description = "ID сессии из Cookie", example = "3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d")
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        if (request.getUsername() == null || request.getUsername().isBlank() ||
                request.getPassword() == null || request.getPassword().isBlank()) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "invalid credentials", sid);
        }

        Optional<String> activeSid = authService.login(request.getUsername(), request.getPassword(), sid);

        if (activeSid.isEmpty()) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "invalid credentials", sid);
        }

        ResponseCookie cookie = cookieProvider.createSessionCookie(activeSid.get());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /**
     * Завершает сессию пользователя.
     * Если сессия не авторизована, возвращает 401.
     *
     * @param sid идентификатор сессии из куки
     * @return 204 No Content и команда на удаление куки
     */
    @Operation(
            summary = "Выход из аккаунта",
            description = "Удаляет текущую сессию и заставляет клиент удалить Cookie X-Session-Id (Max-Age=0)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Успешный выход",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Удаляет куку (Max-Age=0)", schema = @Schema(type = "string")),
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Сессия не найдена или не авторизована",
                    content = @Content
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(description = "ID сессии из Cookie", required = true)
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        String userId = sessionService.getUserId(sid);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.logout(sid);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieProvider.deleteSessionCookie().toString())
                .build();
    }

    /**
     * Формирует ответ с ошибкой, поддерживая жизнь текущей сессии.
     *
     * @param status  HTTP статус ответа
     * @param message сообщение об ошибке
     * @param sid     идентификатор текущей сессии
     * @return объект {@link ResponseEntity} с телом ошибки
     */
    private ResponseEntity<Map<String, String>> buildErrorResponse(HttpStatus status, String message, String sid) {
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);
        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }
        return responseBuilder.body(Map.of("message", message));
    }
}