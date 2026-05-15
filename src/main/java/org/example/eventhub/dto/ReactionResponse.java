package org.example.eventhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Объект, содержащий счетчики реакций на мероприятие.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Счетчики реакций (лайков и дизлайков)")
public class ReactionResponse {

    @Schema(description = "Количество лайков", example = "24")
    private long likes;

    @Schema(description = "Количество дизлайков", example = "3")
    private long dislikes;
}