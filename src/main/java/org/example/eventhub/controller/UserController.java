package org.example.eventhub.controller;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.dto.UserListResponse;
import org.example.eventhub.dto.UserRegistrationRequest;
import org.example.eventhub.model.Event;
import org.example.eventhub.model.User;
import org.example.eventhub.service.EventService;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.service.UserService;
import org.example.eventhub.util.CookieProvider;
import org.example.eventhub.util.SessionIdGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для управления пользователями (регистрация).
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SessionService sessionService;
    private final SessionIdGenerator sidGenerator;
    private final CookieProvider cookieProvider;
    private final EventService eventService;

    /**
     * Регистрирует нового пользователя в системе.
     * При успешной регистрации создается новая сессия, которая привязывается к пользователю.
     *
     * @param request данные для регистрации (full_name, username, password)
     * @param sid     текущий идентификатор сессии из куки (если есть)
     * @return 201 Created при успехе, 400 Bad Request при ошибке валидации, 409 Conflict если пользователь существует
     */
    @PostMapping("/users")
    public ResponseEntity<?> register(
            @RequestBody UserRegistrationRequest request,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"full_name\" field", sid);
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"username\" field", sid);
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"password\" field", sid);
        }

        if (userService.existsByUsername(request.getUsername())) {
            return buildErrorResponse(HttpStatus.CONFLICT, "user already exists", sid);
        }

        User user = userService.createUser(
                request.getFullName(),
                request.getUsername(),
                request.getPassword()
        );

        String newSid = sidGenerator.generateSid();
        sessionService.createSession(newSid);
        sessionService.linkUser(newSid, user.getId());

        ResponseCookie cookie = cookieProvider.createSessionCookie(newSid);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /**
     * Выполняет поиск пользователей (организаторов) по заданным фильтрам.
     *
     * @param id     поиск по точному ID
     * @param name   поиск по вхождению в полное имя
     * @param limit  ограничение выборки
     * @param offset количество пропускаемых записей
     * @param sid    идентификатор сессии для продления
     * @return 200 OK со списком UserShortInfo
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        if (limit != null && limit < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"limit\" field", sid);
        if (offset != null && offset < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"offset\" field", sid);

        List<User> foundUsers = userService.findUsers(id, name, limit, offset);

        List<UserListResponse.UserInfo> shortInfos = foundUsers.stream()
                .map(u -> UserListResponse.UserInfo.builder()
                        .id(u.getId())
                        .full_name(u.getFullName())
                        .username(u.getUsername())
                        .build())
                .toList();

        UserListResponse response = UserListResponse.builder()
                .users(shortInfos)
                .count(shortInfos.size())
                .build();

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            builder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }

        return builder.body(response);
    }

    /**
     * Получает подробные данные об организаторе.
     *
     * @param id  идентификатор пользователя из пути
     * @param sid идентификатор сессии из куки
     * @return 200 с данными пользователя или 404, если не найден
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(
            @PathVariable("id") String id,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        var userOpt = userService.findById(id);

        if (userOpt.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Not found", sid);
        }

        User user = userOpt.get();

        UserListResponse.UserInfo responseBody = UserListResponse.UserInfo.builder()
                .id(user.getId())
                .full_name(user.getFullName())
                .username(user.getUsername())
                .build();

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            builder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }

        return builder.body(responseBody);
    }

    /**
     * Возвращает список всех мероприятий конкретного организатора
     *
     * @param userId идентификатор пользователя из пути
     * @param sid    идентификатор сессии из куки
     * @return 200 со списком событий или 404, если пользователь не найден
     */
    @GetMapping("/users/{id}/events")
    public ResponseEntity<?> listUserEvents(
            @PathVariable("id") String userId,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        if (userService.findById(userId).isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "User not found", sid);
        }

        List<Event> events = eventService.findEventsByCreator(userId);

        org.example.eventhub.dto.EventListResponse response = org.example.eventhub.dto.EventListResponse.builder()
                .events(events)
                .count(events.size())
                .build();

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            builder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }

        return builder.body(response);
    }

    /**
     * Формирует ответ с ошибкой. Если сессия существует, обновляет её срок жизни.
     *
     * @param status  HTTP статус ошибки
     * @param message текст сообщения об ошибке
     * @param sid     текущий ID сессии для возможного обновления
     * @return ResponseEntity с JSON сообщением и (опционально) кукой обновления
     */
    private ResponseEntity<Map<String, String>> buildErrorResponse(HttpStatus status, String message, String sid) {
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);

        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            ResponseCookie cookie = cookieProvider.createSessionCookie(sid);
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return responseBuilder.body(Map.of("message", message));
    }
}