package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.model.Event;
import org.example.eventhub.repository.EventRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Сервис для управления событиями.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final MongoTemplate mongoTemplate;
    private final UserService userService;
    private final ReactionService reactionService;
    private final ReviewService reviewService;
    private final Neo4jSyncService neo4jSyncService;

    public void enrichWithReviews(Event event) {
        if (event == null)
            return;

        if (event.getTitle() == null || event.getId() == null) {
            event.setReviews(new org.example.eventhub.dto.review.ReviewStatsResponse(0, 0.0));
            return;
        }

        var stats = reviewService.getReviewStats(event.getTitle(), event.getId());
        if (stats != null) {
            event.setReviews(stats);
        } else {
            event.setReviews(new org.example.eventhub.dto.review.ReviewStatsResponse(0, 0.0));
        }
    }

    /**
     * Проверяет, занято ли указанное название события.
     *
     * @param title название для проверки
     * @return true, если название уже существует
     */
    public boolean isTitleBusy(String title) {
        return eventRepository.existsByTitle(title);
    }

    /**
     * Сохраняет событие, устанавливая текущую дату создания в формате RFC3339.
     *
     * @param event объект события
     * @return сохраненный объект с сгенерированным ID
     */
    public Event saveEvent(Event event) {
        event.setCreatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        Event savedEvent = eventRepository.save(event);
        neo4jSyncService.syncEvent(savedEvent.getId(), savedEvent.getTitle());
        return savedEvent;
    }

    /**
     * Обновляет существующее мероприятие.
     *
     * @param event объект события
     */
    public void updateEvent(Event event) {
        eventRepository.save(event);
    }

    /**
     * Выполняет поиск событий по id.
     *
     * @param id идектификатор события
     * @return сохраненный объект с соответсвующим ID
     */
    public Event findEvent(String id) {
        return eventRepository.findById(id).orElse(null);
    }

    /**
     * Выполняет поиск мероприятий по набору динамических фильтров.
     *
     * @param id        точный ID события
     * @param title     подстрока названия
     * @param category  категория события
     * @param priceFrom минимальная цена
     * @param priceTo   максимальная цена
     * @param city      город проведения
     * @param dateFrom  начальная дата поиска (YYYYMMDD)
     * @param dateTo    конечная дата поиска (YYYYMMDD)
     * @param username  никнейм создателя события
     * @param limit     ограничение количества результатов
     * @param offset    количество пропускаемых результатов
     * @return список найденных мероприятий
     */
    public List<Event> findEvents(
            String id, String title, String category,
            Integer priceFrom, Integer priceTo, String city,
            String dateFrom, String dateTo, String username,
            String creatorId,
            Integer limit, Integer offset
    ) {
        Query query = new Query();

        if (id != null && !id.isBlank())
            query.addCriteria(Criteria.where("_id").is(id));
        if (title != null && !title.isBlank())
            query.addCriteria(Criteria.where("title").regex(title, "i"));
        if (category != null && !category.isBlank())
            query.addCriteria(Criteria.where("category").is(category));
        if (city != null && !city.isBlank())
            query.addCriteria(Criteria.where("location.city").is(city));
        if (creatorId != null && !creatorId.isBlank())
            query.addCriteria(Criteria.where("created_by").is(creatorId));

        if (priceFrom != null || priceTo != null) {
            Criteria priceCriteria = Criteria.where("price");
            if (priceFrom != null) priceCriteria.gte(priceFrom);
            if (priceTo != null) priceCriteria.lte(priceTo);
            query.addCriteria(priceCriteria);
        }

        if (dateFrom != null || dateTo != null) {
            Criteria dateCriteria = Criteria.where("started_at");
            if (dateFrom != null) {
                dateCriteria.gte(formatSearchDate(dateFrom));
            }
            if (dateTo != null) {
                dateCriteria.lte(formatSearchDate(dateTo) + "T23:59:59Z");
            }
            query.addCriteria(dateCriteria);
        }

        if (username != null && !username.isBlank()) {
            var user = userService.findByUsername(username);
            if (user.isPresent()) {
                query.addCriteria(Criteria.where("created_by").is(user.get().getId()));
            } else {
                return List.of();
            }
        }

        if (offset != null) query.skip(offset);
        if (limit != null) query.limit(limit);

        return mongoTemplate.find(query, Event.class);
    }

    /**
     * Регистрирует лайк пользователя на мероприятие.
     *
     * @param eventId идентификатор мероприятия
     * @param userId  идентификатор пользователя
     */
    public void likeEvent(String eventId, String userId) {
        Event event = findEvent(eventId);
        if (event != null) {
            reactionService.saveReaction(eventId, userId, 1, event.getTitle());
            neo4jSyncService.syncLike(userId, eventId);
        }
    }

    /**
     * Регистрирует дизлайк пользователя на мероприятие.
     * Использует $addToSet для дизлайка и $pull для удаления лайка, если он был.
     *
     * @param eventId идентификатор мероприятия
     * @param userId  идентификатор пользователя
     */
    public void dislikeEvent(String eventId, String userId) {
        Event event = findEvent(eventId);
        if (event != null) {
            reactionService.saveReaction(eventId, userId, -1, event.getTitle());
        }
    }

    /**
     * Загружает и устанавливает данные о реакциях для заданного события.
     *
     * @param event событие, которое необходимо наполнить данными о реакциях
     */
    public void applyReactions(Event event) {
        event.setReactions(reactionService.getReactions(event.getTitle()));
    }

    /**
     * Вспомогательный метод для приведения даты из строки поиска к ISO формату.
     *
     * @param date строка даты в формате YYYYMMDD
     * @return строковое представление даты (ISO)
     */
    private String formatSearchDate(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd")).toString();
    }

    /**
     * Подсчитывает общее количество найденных мероприятий по критериям без учета пагинации.
     */
    public long countEvents(
            String id, String title, String category,
            Integer priceFrom, Integer priceTo, String city,
            String dateFrom, String dateTo, String username,
            String creatorId
    ) {
        Query query = new Query();

        if (id != null && !id.isBlank())
            query.addCriteria(Criteria.where("_id").is(id));
        if (title != null && !title.isBlank())
            query.addCriteria(Criteria.where("title").regex(title, "i"));
        if (category != null && !category.isBlank())
            query.addCriteria(Criteria.where("category").is(category));
        if (city != null && !city.isBlank())
            query.addCriteria(Criteria.where("location.city").is(city));
        if (creatorId != null && !creatorId.isBlank())
            query.addCriteria(Criteria.where("created_by").is(creatorId));

        if (priceFrom != null || priceTo != null) {
            Criteria priceCriteria = Criteria.where("price");
            if (priceFrom != null) priceCriteria.gte(priceFrom);
            if (priceTo != null) priceCriteria.lte(priceTo);
            query.addCriteria(priceCriteria);
        }

        if (dateFrom != null || dateTo != null) {
            Criteria dateCriteria = Criteria.where("started_at");
            if (dateFrom != null) {
                dateCriteria.gte(formatSearchDate(dateFrom));
            }
            if (dateTo != null) {
                dateCriteria.lte(formatSearchDate(dateTo) + "T23:59:59Z");
            }
            query.addCriteria(dateCriteria);
        }

        if (username != null && !username.isBlank()) {
            var user = userService.findByUsername(username);
            if (user.isPresent()) {
                query.addCriteria(Criteria.where("created_by").is(user.get().getId()));
            } else {
                return 0L;
            }
        }

        return mongoTemplate.count(query, Event.class);
    }
}