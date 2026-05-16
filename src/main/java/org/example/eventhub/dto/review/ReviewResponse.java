package org.example.eventhub.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Schema(description = "Данные конкретного отзыва")
@AllArgsConstructor
@NoArgsConstructor
public class ReviewResponse {

    @Schema(description = "ID отзыва", example = "56e2c0b3a2b4c1a5e6f7f8b3")
    private String id;

    @Schema(description = "ID мероприятия в MongoDB", example = "12e9c0b1a2b3c3d5e6f7a8b7")
    private String event_id;

    @Schema(description = "Комментарий пользователя", example = "Великолепный спектакль! Идите, даже не думайте!")
    private String comment;

    @Schema(description = "Оценка от 1 до 5", example = "5")
    private int rating;

    @Schema(description = "Дата создания (RFC3339)", example = "2026-03-14T14:59:32+03:00")
    private String created_at;

    @Schema(description = "ID автора отзыва", example = "65e9c0b1a2b3c4d5e6f7a8b9")
    private String created_by;

    @Schema(description = "Дата обновления (RFC3339)", example = "2026-03-14T14:59:32+03:00")
    private String updated_at;
}