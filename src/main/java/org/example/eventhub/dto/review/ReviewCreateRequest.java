package org.example.eventhub.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Данные для создания отзыва
 */
@Data
@Schema(description = "Запрос на оставление отзыва на мероприятие")
public class ReviewCreateRequest {

    @Schema(
            description = "Комментарий к отзыву (любые символы, максимум 300)",
            example = "Великолепный спектакль! Идите, даже не думайте!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @Size(max = 300)
    private String comment;

    @Schema(
            description = "Оценка от 1 до 5 (только целые числа)",
            example = "5",
            minimum = "1",
            maximum = "5",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @Min(1)
    @Max(5)
    private int rating;

}