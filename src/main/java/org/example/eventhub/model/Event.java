package org.example.eventhub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Модель события для хранения в коллекции "events" MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "events")

@CompoundIndexes({@CompoundIndex(name = "title_created_by_idx", def = "{'title': 1, 'created_by': 1}")})
public class Event {

    /**
     * Уникальный идентификатор документа.
     */
    @Id
    private String id;

    /**
     * Название события (уникальное).
     */
    @Indexed(unique = true)
    private String title;

    /**
     * Описание события.
     */
    private String description;

    /**
     * Место проведения события.
     */
    private Location location;

    /**
     * Дата создания записи
     */
    @Field("created_at")
    private String createdAt;

    /**
     * Идентификатор пользователя, создавшего событие.
     */
    @Indexed
    @Field("created_by")
    private String createdBy;

    /**
     * Дата и время начала в формате RFC3339.
     */
    @Field("started_at")
    private String startedAt;

    /**
     * Дата и время завершения в формате RFC3339.
     */
    @Field("finished_at")
    private String finishedAt;

    /**
     * Категория мероприятия
     */
    private String category;

    /**
     * Цена билета на мероприятие
     */
    private Integer price;

    /**
     * Вложенный объект для хранения данных о локации.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        /**
         * Физический адрес проведения.
         */
        private String address;
        /**
         * Наименование города проведения.
         */
        private String city;
    }
}