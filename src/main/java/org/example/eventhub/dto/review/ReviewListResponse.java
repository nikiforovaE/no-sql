package org.example.eventhub.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Ответ со списком отзывов и общим количеством отзывов")
public class ReviewListResponse {
    @Schema(description = "Список найденных отзывов")
    private List<ReviewResponse> reviews;

    @Schema(description = "Общее количество найденных отзывов", example = "1")
    private int count;
}