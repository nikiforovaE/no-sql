package org.example.eventhub.service;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.model.Event;
import org.example.eventhub.repository.EventRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

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
        return eventRepository.save(event);
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
     * Выполняет поиск событий с использованием фильтрации по названию и пагинации.
     *
     * @param title  подстрока для поиска в названии
     * @param limit  максимальное количество результатов
     * @param offset количество пропускаемых результатов
     * @return список найденных событий
     */
    public List<Event> findEvents(String title, Integer limit, Integer offset) {
        Query query = new Query();

        if (title != null && !title.isBlank()) {
            query.addCriteria(Criteria.where("title").regex(title, "i"));
        }

        if (offset != null) query.skip(offset);
        if (limit != null) query.limit(limit);

        return mongoTemplate.find(query, Event.class);
    }
}