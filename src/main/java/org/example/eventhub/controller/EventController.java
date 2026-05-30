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
import org.example.eventhub.dto.event.EventCreateRequest;
import org.example.eventhub.dto.event.EventListResponse;
import org.example.eventhub.dto.event.EventPatchRequest;
import org.example.eventhub.dto.review.ReviewCreateRequest;
import org.example.eventhub.dto.review.ReviewListResponse;
import org.example.eventhub.dto.review.ReviewPatchRequest;
import org.example.eventhub.model.Event;
import org.example.eventhub.service.EventService;
import org.example.eventhub.service.ReviewService;
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
@Tag(name = "Events", description = "Управление событиями и мероприятиями")
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("meetup", "concert", "exhibition", "party", "other");

    private final EventService eventService;
    private final SessionService sessionService;
    private final ReviewService reviewService;
    private final CookieProvider cookieProvider;

    /**
     * Создает новое событие. Доступно только для авторизованных пользователей.
     *
     * @param request данные события (название, адрес, даты)
     * @param sid     идентификатор сессии
     * @return 201 и ID события, либо ошибка (400, 401, 409)
     */
    @Operation(summary = "Создание события", description = "Доступно только авторизованным пользователям. Создает новое мероприятие и привязывает его к текущему пользователю.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Событие успешно создано",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Обновляет TTL сессии",
                            schema = @Schema(type = "string")
                    ),
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"id\": \"12e9c0b1a2b3c3d5e6f7a8b7\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидные входные данные",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"invalid \\\"title\\\" field\"}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Событие с таким названием уже существует",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"event already exists\"}")
                    )
            )
    })
    @PostMapping
    public ResponseEntity<?> createEvent(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные нового события", content = @Content(examples = @ExampleObject(value = "{\n  \"title\": \"Мой день рождения\",\n  \"address\": \"г. Санкт-Петербург, ул. Пушкина, дом Колотушкина\",\n  \"started_at\": \"2026-04-01T12:00:00+03:00\",\n  \"finished_at\": \"2026-04-01T23:00:00+03:00\",\n  \"description\": \"Приглашаю вас отпраздновать мое 30-с-чем-то-летие\"\n}"))) @RequestBody EventCreateRequest request, @Parameter(description = "ID сессии из Cookie", example = "3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d") @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
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
    @Operation(summary = "Редактирование мероприятия", description = "Доступно только организатору мероприятия. Позволяет изменить категорию, цену и город.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Успешное обновление",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Обновляет TTL сессии",
                            schema = @Schema(type = "string"))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидные параметры",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"invalid \\\"category\\\" field\"}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Не авторизован"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Мероприятие не найдено или вы не организатор",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Not found. Be sure that event exists and you are the organizer\"}")
                    )
            )
    })
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateEvent(@Parameter(description = "ID мероприятия", example = "12e9c0b1a2b3c3d5e6f7a8b7") @PathVariable("id") String eventId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Поля для обновления", content = @Content(examples = @ExampleObject(value = "{\n  \"category\": \"concert\",\n  \"price\": 1000,\n  \"city\": \"Москва\"\n}"))) @RequestBody EventPatchRequest request, @Parameter(description = "ID сессии из Cookie") @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
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
     * Оставляет отзыв на мероприятие. Доступно только авторизованным пользователям. Одно мероприятие - один пользователь — один отзыв.
     *
     * @param request данные отзыва (комментарий, оценка)
     * @param sid     идентификатор сессии
     * @return 201 и ID события, либо ошибка (400, 401, 409)
     */
    @Operation(summary = "Отзыв на мероприятие", description = "Доступно только авторизованным пользователям. Одно мероприятие - один пользователь — один отзыв.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Отзыв успешно создан",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Обновляет TTL сессии"),
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"id\": \"56e2c0b3a2b4c1a5e6f7f8b3\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидные параметры",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"invalid \\\"category\\\" field\"}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Не авторизован"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Мероприятие не найдено или вы не организатор",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Not found. Be sure that event exists and you are the organizer\"}")
                    )
            )
    })
    @PostMapping("/{id}/reviews")
    public ResponseEntity<?> createReview(
            @PathVariable("id") String eventId,
            @RequestBody ReviewCreateRequest request,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.getComment() == null || request.getComment().length() > 300) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"comment\" field", sid);
        }
        if (request.getRating() < 1 || request.getRating() > 5) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"rating\" field", sid);
        }

        Event event = eventService.findEvent(eventId);
        if (event == null) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Event not found", sid);
        }

        String reviewId = reviewService.saveReview(
                eventId,
                userId,
                request.getComment(),
                request.getRating(),
                event.getTitle()
        );
        if (reviewId == null) {
            return buildErrorResponse(HttpStatus.CONFLICT, "Already exists", sid);
        }

        return buildSuccessResponse(HttpStatus.CREATED, Map.of("id", reviewId), sid, true);
    }

    @Operation(summary = "Список отзывов", description = "Возвращает отзывы для конкретного мероприятия с пагинацией.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Список получен",
                    content = @Content(schema = @Schema(implementation = ReviewListResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Невалидные лимит или офсет")
    })
    @GetMapping("/{id}/reviews")
    public ResponseEntity<?> listReviews(
            @PathVariable("id") String eventId,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        if (limit < 0) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"limit\" field", sid);
        }
        if (offset < 0) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"offset\" field", sid);
        }

        Event event = eventService.findEvent(eventId);
        if (event == null) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Event not found", sid);
        }

        ReviewListResponse response = reviewService.getReviews(eventId, limit, offset);

        return buildSuccessResponse(HttpStatus.OK, response, sid, false);
    }

    @Operation(summary = "Изменить отзыв",
            description = "Позволяет редактировать рейтинг и комментарий. Доступно только владельцу отзыва.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Успешно обновлено"),
            @ApiResponse(responseCode = "404", description = "Отзыв или мероприятие не найдено")
    })
    @PatchMapping("/{id}/reviews/{review_id}")
    public ResponseEntity<?> updateReview(
            @PathVariable("id") String eventId,
            @PathVariable("review_id") String reviewId,
            @RequestBody ReviewPatchRequest request,
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid
    ) {
        String userId = sessionService.getUserId(sid);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (request.getComment() != null && request.getComment().length() > 300) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"comment\" field", sid);
        }
        if (request.getRating() != null && (request.getRating() < 1 || request.getRating() > 5)) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "invalid \"rating\" field", sid);
        }

        Event event = eventService.findEvent(eventId);
        if (event == null)
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Event not found", sid);


        boolean updated = reviewService.updateReview(
                eventId,
                reviewId,
                userId,
                request.getRating(),
                request.getComment(),
                event.getTitle()
        );

        if (!updated) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Event not found", sid);
        }

        return buildSuccessResponse(HttpStatus.NO_CONTENT, null, sid, true);
    }

    /**
     * Получает подробные данные о конкретном мероприятии.
     *
     * @param eventId идентификатор мероприятия из пути
     * @param sid     идентификатор сессии из куки
     * @return 200 и данные события, либо 404 если не найдено
     */
    @Operation(summary = "Получение мероприятия по ID")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Мероприятие найдено",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Возвращает куку БЕЗ обновления TTL",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Event.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Мероприятие не найдено",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Not found\"}")
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getEvent(@Parameter(description = "ID мероприятия", example = "12e9c0b1a2b3c3d5e6f7a8b7") @PathVariable("id") String eventId, @RequestParam(required = false) String include, @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
        Event event = eventService.findEvent(eventId);
        if (event == null)
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Event not found", sid);

        enrichEvent(event, include);

        return buildSuccessResponse(HttpStatus.OK, event, sid, false);
    }

    /**
     * Возвращает список мероприятий по фильтрам с пагинацией.
     *
     * @param id        точный поиск по ID
     * @param title     поиск по подстроке названия
     * @param category  фильтр по категории
     * @param priceFrom мин. цена
     * @param priceTo   макс. цена
     * @param username  никнейм создателя
     * @param dateFrom  начало (YYYYMMDD)
     * @param dateTo    конец (YYYYMMDD)
     * @return 200 со списком событий и количеством
     */
    @Operation(summary = "Поиск мероприятий",
            description = "Возвращает список мероприятий с фильтрацией. В параметр include можно передать 'reactions,reviews' через запятую.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Результаты поиска",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Возвращает ту же куку, что была в запросе (без обновления TTL)",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = EventListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Ошибка в параметрах запроса",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"invalid \\\"limit\\\" parameter\"}")
                    )
            )
    })

    @GetMapping
    public ResponseEntity<?> listEvents(@RequestParam(required = false) String include,
                                        @Parameter(description = "Поиск по точному ID") @RequestParam(required = false) String id, @Parameter(description = "Поиск по подстроке названия") @RequestParam(required = false) String title, @Parameter(description = "Фильтр по категории (meetup, concert, exhibition, party, other)") @RequestParam(required = false) String category, @Parameter(description = "Минимальная цена") @RequestParam(name = "price_from", required = false) Integer priceFrom, @Parameter(description = "Максимальная цена (price_to=0 для бесплатных)") @RequestParam(name = "price_to", required = false) Integer priceTo, @Parameter(description = "Город проведения") @RequestParam(required = false) String city, @Parameter(description = "Дата начала не раньше (YYYYMMDD)", example = "20260314") @RequestParam(name = "date_from", required = false) String dateFrom, @Parameter(description = "Дата начала не позже (YYYYMMDD)", example = "20260314") @RequestParam(name = "date_to", required = false) String dateTo, @Parameter(description = "Никнейм организатора") @RequestParam(name = "user", required = false) String username, @Parameter(description = "Лимит пагинации", example = "10") @RequestParam(required = false) Integer limit, @Parameter(description = "Смещение пагинации", example = "0") @RequestParam(required = false) Integer offset, @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
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

        List<Event> events = eventService.findEvents(id, title, category, priceFrom, priceTo, city, dateFrom, dateTo, username, null, limit, offset);

        long totalCount = eventService.countEvents(id, title, category, priceFrom, priceTo, city, dateFrom, dateTo, username, null);

        if (include != null && !include.isBlank()) {
            events.forEach(e -> enrichEvent(e, include));
        }

        EventListResponse responseBody = EventListResponse.builder()
                .events(events)
                .count((int) totalCount)
                .build();

        return buildSuccessResponse(HttpStatus.OK, responseBody, sid, false);
    }

    /**
     * Добавляет лайк мероприятию. Доступно только для авторизованных пользователей.
     *
     * @param eventId идентификатор мероприятия
     * @param sid     идентификатор сессии
     * @return 204 No Content или ошибка (401, 404)
     */
    @Operation(summary = "Лайк на мероприятие", description = "Позволяет авторизованному пользователю поставить лайк мероприятию.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Лайк успешно поставлен",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Обновляет TTL сессии", schema = @Schema(type = "string"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Мероприятие не найдено",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Event not found\"}")
                    )
            )
    })
    @PostMapping("/{id}/like")
    public ResponseEntity<?> likeEvent(@Parameter(description = "ID мероприятия", example = "12e9c0b1a2b3c3d5e6f7a8b7") @PathVariable("id") String eventId, @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Event event = eventService.findEvent(eventId);
        if (event == null) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Event not found", sid);
        }

        eventService.likeEvent(eventId, userId);

        return buildSuccessResponse(HttpStatus.NO_CONTENT, null, sid, true);
    }

    /**
     * Добавляет дизлайк мероприятию. Доступно только для авторизованных пользователей.
     *
     * @param eventId идентификатор мероприятия
     * @param sid     идентификатор сессии
     * @return 204 No Content или ошибка (401, 404)
     */
    @Operation(summary = "Дизлайк на мероприятие", description = "Позволяет авторизованному пользователю поставить дизлайк мероприятию. При этом лайк (если он был) удаляется.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Дизлайк успешно поставлен",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Обновляет TTL сессии",
                            schema = @Schema(type = "string")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Удаляет куку (Max-Age=0)",
                            schema = @Schema(type = "string"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Мероприятие не найдено",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Event not found\"}")
                    )
            )
    })
    @PostMapping("/{id}/dislike")
    public ResponseEntity<?> dislikeEvent(@Parameter(description = "ID мероприятия", example = "12e9c0b1a2b3c3d5e6f7a8b7") @PathVariable("id") String eventId, @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {
        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.SET_COOKIE, cookieProvider.deleteSessionCookie().toString()).build();
        }

        Event event = eventService.findEvent(eventId);
        if (event == null) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Event not found", sid);
        }

        eventService.dislikeEvent(eventId, userId);

        return buildSuccessResponse(HttpStatus.NO_CONTENT, null, sid, true);
    }

    /**
     * Валидирует формат даты поиска (формат YYYYMMDD).
     *
     * @param dateStr строка даты
     * @return true если дата невалидна
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
     * Валидирует формат даты для событий (формат RFC3339).
     *
     * @param dateStr строка даты
     * @return true если дата невалидна
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
     *
     * @param status    статус ответа
     * @param body      тело ответа
     * @param sid       идентификатор сессии
     * @param updateTtl признак обновления TTL
     * @return объект ResponseEntity
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
     * Формирует ответ с ошибкой и обновляет сессию, если она активна.
     *
     * @param status  статус ответа
     * @param message сообщение об ошибке
     * @param sid     идентификатор сессии
     * @return объект ResponseEntity
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

    /**
     * Вспомогательный метод для обогащения мероприятия данными на основе параметра include
     */
    private void enrichEvent(Event event, String include) {
        if (include == null || include.isBlank()) return;

        List<String> includes = List.of(include.split(","));

        if (includes.contains("reactions")) {
            eventService.applyReactions(event);
        }
        if (includes.contains("reviews")) {
            eventService.enrichWithReviews(event);
        }
    }
}