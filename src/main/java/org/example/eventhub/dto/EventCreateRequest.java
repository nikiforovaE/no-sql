package org.example.eventhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Данные для создания нового события.
 */
@Data
@Schema(description = "Запрос на создание нового события")
public class EventCreateRequest {

    @Schema(description = "Название события", example = "Мой день рождения", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "Описание события", example = "Приглашаю вас отпраздновать мое 30-с-чем-то-летие")
    private String description;

    @Schema(description = "Адрес проведения", example = "г. Санкт-Петербург, ул. Пушкина, дом Колотушкина", requiredMode = Schema.RequiredMode.REQUIRED)
    private String address;

    @Schema(
            description = "Дата и время начала в формате RFC3339",
            example = "2026-04-01T12:00:00+03:00",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @JsonProperty("started_at")
    private String startedAt;

    @Schema(
            description = "Дата и время окончания в формате RFC3339",
            example = "2026-04-01T23:00:00+03:00",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @JsonProperty("finished_at")
    private String finishedAt;
}