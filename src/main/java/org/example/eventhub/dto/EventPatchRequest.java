package org.example.eventhub.dto;

import lombok.Data;

/**
 * Данные для редактирования существующего события.
 */
@Data
public class EventPatchRequest {
    private String category;
    private Integer price;
    private String city;

}