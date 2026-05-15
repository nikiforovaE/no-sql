package org.example.eventhub.dto;

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

    @NotBlank
    @Size(max = 300)
    private String comment;

    @Min(1)
    @Max(5)
    private int rating;

}