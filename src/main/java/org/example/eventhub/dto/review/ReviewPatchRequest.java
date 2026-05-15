package org.example.eventhub.dto.review;

import lombok.Data;

@Data
public class ReviewPatchRequest {
    private Integer rating;
    private String comment;
}