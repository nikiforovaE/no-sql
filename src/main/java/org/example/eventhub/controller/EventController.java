package org.example.eventhub.controller;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.dto.EventCreateRequest;
import org.example.eventhub.dto.EventListResponse;
import org.example.eventhub.dto.EventPatchRequest;
import org.example.eventhub.model.Event;
import org.example.eventhub.service.EventService;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.util.CookieProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Контроллер для создания и просмотра событий.
 */
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("meetup", "concert", "exhibition", "party", "other");

    private final EventService eventService;
    private final SessionService sessionService;
    private final CookieProvider cookieProvider;

    /**
     * Создает новое событие. Доступно только для авторизованных пользователей.
     *
     * @param request данные события (название, адрес, даты)
     * @param sid     идентификатор сессии
     * @return 201 и ID события, либо ошибка (400, 401, 409)
     */
    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody EventCreateRequest request, @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, null, sid);
        }

        if (request.getTitle() == null || request.getTitle().isBlank())
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"title\" field", sid);
        if (request.getAddress() == null || request.getAddress().isBlank())
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"address\" field", sid);
        if (isInvalidDate(request.getStartedAt()))
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"started_at\" field", sid);
        if (isInvalidDate(request.getFinishedAt()))
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"finished_at\" field", sid);

        if (eventService.isTitleBusy(request.getTitle())) {
            return buildErrorResponse(HttpStatus.CONFLICT, "event already exists", sid);
        }

        Event event = Event.builder().title(request.getTitle()).description(request.getDescription()).location(Event.Location.builder().address(request.getAddress()).build()).startedAt(request.getStartedAt()).finishedAt(request.getFinishedAt()).createdBy(userId).build();

        Event savedEvent = eventService.saveEvent(event);

        return buildSuccessResponse(HttpStatus.CREATED, Map.of("id", savedEvent.getId()), sid, true);
    }

    /**
     * Редактирует данные о мероприятии. Доступ только у организатора.
     *
     * @param request данные события (категория, цена билета, город)
     * @param sid     идентификатор сессии
     * @return 201 и ID события, либо ошибка (400, 401, 409)
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable("id") String eventId, @RequestBody EventPatchRequest request, @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, null, sid);
        }

        Event event = eventService.findEvent(eventId);
        if (event == null || !event.getCreatedBy().equals(userId))
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Not found. Be sure that event exists and you are the organizer", sid);

        if (request.getCategory() != null) {
            if (!ALLOWED_CATEGORIES.contains(request.getCategory())) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"category\" field", sid);
            }
            event.setCategory(request.getCategory());
        }

        if (request.getPrice() != null) {
            if (request.getPrice() < 0) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"price\" field", sid);
            }
            event.setPrice(request.getPrice());
        }

        if (request.getCity() != null) {
            if (event.getLocation() == null) {
                event.setLocation(new Event.Location());
            }

            if (request.getCity().isEmpty()) {
                event.getLocation().setCity(null);
            } else {
                event.getLocation().setCity(request.getCity());
            }
        }
        eventService.updateEvent(event);

        return buildSuccessResponse(HttpStatus.NO_CONTENT, null, sid, true);
    }

    /**
     * Возвращает список мероприятий по фильтрам с пагинацией.
     *
     * @param id       точный поиск по ID
     * @param title    поиск по подстроке названия
     * @param category фильтр по категории
     * @param priceFrom мин. цена
     * @param priceTo   макс. цена
     * @param username  никнейм создателя
     * @param dateFrom  начало (YYYYMMDD)
     * @param dateTo    конец (YYYYMMDD)
     * @return 200 со списком событий и количеством
     */
    @GetMapping
    public ResponseEntity<?> listEvents(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category,
            @RequestParam(name = "price_from", required = false) Integer priceFrom,
            @RequestParam(name = "price_to", required = false) Integer priceTo,
            @RequestParam(required = false) String city,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(name = "user", required = false) String username,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        if (limit != null && limit < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"limit\" parameter", sid);
        if (offset != null && offset < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"offset\" parameter", sid);

        if (priceFrom != null && priceFrom < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"price_from\" field", sid);
        if (priceTo != null && priceTo < 0)
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"price_to\" field", sid);

        if (isInvalidSearchDate(dateFrom))
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"date_from\" field", sid);
        if (isInvalidSearchDate(dateTo))
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"date_to\" field", sid);

        List<Event> events = eventService.findEvents(
                id, title, category, priceFrom, priceTo, city, dateFrom, dateTo, username, limit, offset
        );

        EventListResponse responseBody = EventListResponse.builder()
                .events(events)
                .count(events.size())
                .build();

        return buildSuccessResponse(HttpStatus.OK, responseBody, sid, false);
    }

    /**
     * Валидация даты для ПОИСКА (формат YYYYMMDD).
     */
    private boolean isInvalidSearchDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return false;
        if (!dateStr.matches("\\d{8}")) return true;
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Валидация даты для СОЗДАНИЯ (формат RFC3339).
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
     * Формирует ответ с ошибкой и обновляет куку для продления сессии.
     */
    private ResponseEntity<?> buildErrorResponse(HttpStatus status, String message, String sid) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (sid != null && sessionService.exists(sid)) {
            sessionService.updateSession(sid);
            builder.header(HttpHeaders.SET_COOKIE, cookieProvider.createSessionCookie(sid).toString());
        }
        if (message == null) return builder.build();
        return builder.body(Map.of("message", message));
    }
}