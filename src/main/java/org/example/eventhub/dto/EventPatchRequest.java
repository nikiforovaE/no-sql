package org.example.eventhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Данные для редактирования существующего события.
 */
@Data
@Schema(description = "Запрос на частичное обновление мероприятия")
public class EventPatchRequest {

    @Schema(
            description = "Категория мероприятия",
            allowableValues = {"meetup", "concert", "exhibition", "party", "other"},
            example = "concert"
    )
    private String category;

    @Schema(
            description = "Цена билета (целое число в абстрактных единицах)",
            example = "1000",
            minimum = "0"
    )
    private Integer price;

    @Schema(
            description = "Наименование города. При пустом значении - удаляет город из локации мероприятия.",
            example = "Москва"
    )
    private String city;

}