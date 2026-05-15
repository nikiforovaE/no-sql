package org.example.eventhub.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.eventhub.dto.ReactionResponse;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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
@Schema(description = "Полная модель мероприятия")
public class Event {

    /**
     * Уникальный идентификатор документа.
     */
    @Id
    @Schema(description = "Идентификатор события (hex)", example = "12e9c0b1a2b3c3d5e6f7a8b7")
    private String id;

    /**
     * Название события (уникальное).
     */
    @Indexed
    @Schema(description = "Название события", example = "Мой день рождения")
    private String title;

    /**
     * Описание события.
     */
    @Schema(description = "Подробное описание", example = "Приглашаю вас отпраздновать мое 30-с-чем-то-летие")
    private String description;

    /**
     * Место проведения события.
     */
    @Schema(description = "Данные о месте проведения")
    private Location location;

    /**
     * Дата создания записи
     */
    @Field("created_at")
    @Schema(description = "Дата создания записи (RFC3339)", example = "2026-03-14T14:59:32+03:00")
    private String createdAt;

    /**
     * Идентификатор пользователя, создавшего событие.
     */
    @Indexed
    @Field("created_by")
    @Schema(description = "ID создателя (User hex ID)", example = "65e9c0b1a2b3c4d5e6f7a8b9")
    private String createdBy;

    /**
     * Дата и время начала в формате RFC3339.
     */
    @Field("started_at")
    @Schema(description = "Дата начала (RFC3339)", example = "2026-04-01T12:00:00+03:00")
    private String startedAt;

    /**
     * Дата и время завершения в формате RFC3339.
     */
    @Field("finished_at")
    @Schema(description = "Дата окончания (RFC3339)", example = "2026-04-01T23:00:00+03:00")
    private String finishedAt;

    /**
     * Категория мероприятия
     */
    @Schema(description = "Категория", allowableValues = {"meetup", "concert", "exhibition", "party", "other"}, example = "party")
    private String category;

    /**
     * Цена билета на мероприятие
     */
    @Schema(description = "Цена билета (0 для бесплатных)", example = "0")
    private Integer price;

    /**
     * Вложенный объект для хранения данных о локации.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Географическое расположение события")
    public static class Location {
        /**
         * Физический адрес проведения.
         */
        @Schema(description = "Полный адрес", example = "г. Москва, ул. Пушкина, дом Колотушкина")
        private String address;
        /**
         * Наименование города проведения.
         */
        @Schema(description = "Город", example = "Москва")
        private String city;
    }


    /**
     * Счетчики реакций (не хранится в MongoDB).
     */
    @Transient
    @Schema(description = "Объект со счетчиками реакций (лайков и дизлайков)")
    private ReactionResponse reactions;

    @Transient
    @Schema(description = "Статистика отзывов")
    private org.example.eventhub.dto.ReviewStatsResponse reviews;
}