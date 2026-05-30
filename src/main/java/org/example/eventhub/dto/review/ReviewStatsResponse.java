package org.example.eventhub.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Усредненная информация об отзывах на мероприятие (данные из Redis)")
public class ReviewStatsResponse {

    @Schema(
            description = "Общее количество всех отзывов на мероприятие (по его названию)",
            example = "123"
    )
    private int count;

    @Schema(
            description = "Средний рейтинг всех отзывов, округлённый до одного знака после запятой",
            example = "4.8"
    )
    private double rating;
}