package org.example.eventhub.controller;

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
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
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
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
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