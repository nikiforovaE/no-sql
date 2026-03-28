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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final CookieProvider cookieProvider;

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
     * Выполняет выход. Если пользователь не авторизован (нет user_id в сессии), возвращает 401.
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

    private ResponseEntity<Map<String, String>> buildErrorResponse(HttpStatus status, String message, String sid) {
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);
        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }
        return responseBuilder.body(Map.of("message", message));
    }
}