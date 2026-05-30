package org.example.eventhub.controller;

import lombok.RequiredArgsConstructor;
import org.example.eventhub.service.RecommendationService;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.util.CookieProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final SessionService sessionService;
    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<?> getRecommendations(
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {

        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var response = recommendationService.getRecommendations(userId);
        return ResponseEntity.ok(response);
    }
}