package org.example.eventhub.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Запрос на частичное изменение отзыва")
public class ReviewPatchRequest {

    @Schema(
            description = "Новая оценка от 1 до 5. Только если нужно изменить.",
            example = "3",
            minimum = "1",
            maximum = "5"
    )
    private Integer rating;

    @Schema(
            description = "Новый комментарий (макс. 300 символов). Только если нужно изменить.",
            example = "На самом деле, так себе спектакль..."
    )
    private String comment;
}