package org.example.eventhub.dto;

import lombok.Builder;
import lombok.Data;
import org.example.eventhub.model.Event;

import java.util.List;

/**
 * Объект ответа, содержащий список событий и их общее количество.
 */
@Data
@Builder
public class EventListResponse {
    private List<Event> events;
    private int count;
}