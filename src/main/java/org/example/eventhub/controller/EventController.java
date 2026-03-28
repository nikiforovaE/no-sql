package org.example.eventhub.controller;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.dto.EventCreateRequest;
import org.example.eventhub.dto.EventListResponse;
import org.example.eventhub.model.Event;
import org.example.eventhub.service.EventService;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.util.CookieProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для создания и просмотра событий.
 */
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final SessionService sessionService;
    private final CookieProvider cookieProvider;

    /**
     * Создает новое событие (POST /events).
     * Доступно только авторизованным пользователям.
     */
    @PostMapping
    public ResponseEntity<?> createEvent(
            @RequestBody EventCreateRequest request,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, null, sid, "field");
        }

        if (request.getTitle() == null || request.getTitle().isBlank())
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"title\" field", sid, "field");
        if (request.getAddress() == null || request.getAddress().isBlank())
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"address\" field", sid, "field");
        if (isInvalidDate(request.getStartedAt()))
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"started_at\" field", sid, "field");
        if (isInvalidDate(request.getFinishedAt()))
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"finished_at\" field", sid, "field");

        if (eventService.isTitleBusy(request.getTitle())) {
            return buildErrorResponse(HttpStatus.CONFLICT, "event already exists", sid, "field");
        }

        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .location(Event.Location.builder().address(request.getAddress()).build())
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .createdBy(userId)
                .build();

        Event savedEvent = eventService.saveEvent(event);

        return buildSuccessResponse(HttpStatus.CREATED, Map.of("id", savedEvent.getId()), sid, true);
    }

    /**
     * Возвращает список событий (GET /events) с фильтрацией и пагинацией.
     */
    @GetMapping
    public ResponseEntity<?> listEvents(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        if (limit != null && limit < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"limit\" parameter", sid, "parameter");
        if (offset != null && offset < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"offset\" parameter", sid, "parameter");

        List<Event> events = eventService.findEvents(title, limit, offset);

        EventListResponse responseBody = EventListResponse.builder()
                .events(events)
                .count(events.size())
                .build();

        return buildSuccessResponse(HttpStatus.OK, responseBody, sid, false);
    }

    /**
     * Проверяет строку на соответствие формату даты RFC3339.
     */
    private boolean isInvalidDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return true;
        try {
            OffsetDateTime.parse(dateStr);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Формирует успешный ответ и (опционально) обновляет TTL сессии.
     */
    private ResponseEntity<?> buildSuccessResponse(HttpStatus status, Object body, String sid, boolean updateTtl) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (sid != null && sessionService.exists(sid)) {
            if (updateTtl) {
                sessionService.updateSession(sid);
            }
            builder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }
        return builder.body(body);
    }

    /**
     * Формирует ответ с ошибкой.
     */
    private ResponseEntity<?> buildErrorResponse(HttpStatus status, String message, String sid, String errorType) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            builder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }
        if (message == null) return builder.build();
        return builder.body(Map.of("message", message));
    }
}