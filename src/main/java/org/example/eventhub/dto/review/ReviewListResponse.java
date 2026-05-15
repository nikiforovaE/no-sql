package org.example.eventhub.dto.review;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReviewListResponse {
    private List<ReviewResponse> reviews;
    private int count;
}