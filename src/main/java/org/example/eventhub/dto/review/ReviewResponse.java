package org.example.eventhub.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReviewResponse {
    private String id;
    private String event_id;
    private String comment;
    private String created_at; // Формат RFC3339
    private String created_by;
    private int rating;
    private String updated_at;
}