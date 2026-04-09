package org.example.eventhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Объект запроса для создания нового события.
 */
@Data
public class EventCreateRequest {
    private String title;
    private String description;
    private String address;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("finished_at")
    private String finishedAt;
}