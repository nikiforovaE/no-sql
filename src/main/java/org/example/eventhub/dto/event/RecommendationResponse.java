package org.example.eventhub.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.eventhub.model.Event;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Объект ответа со списком рекомендованных мероприятий")
public class RecommendationResponse {

    @Schema(description = "Список рекомендованных событий")
    private List<Event> events;
}