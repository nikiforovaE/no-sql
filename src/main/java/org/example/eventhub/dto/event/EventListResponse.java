package org.example.eventhub.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.example.eventhub.model.Event;

import java.util.List;

/**
 * Объект ответа, содержащий список событий и их общее количество.
 */
@Data
@Builder
@Schema(description = "Объект ответа со списком мероприятий")
public class EventListResponse {

    @Schema(description = "Список найденных событий")
    private List<Event> events;

    @Schema(description = "Количество найденных событий в списке", example = "1")
    private int count;
}