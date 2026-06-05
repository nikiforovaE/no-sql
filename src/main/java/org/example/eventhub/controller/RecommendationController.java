package org.example.eventhub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.eventhub.dto.event.RecommendationResponse;
import org.example.eventhub.service.RecommendationService;
import org.example.eventhub.service.SessionService;
import org.example.eventhub.util.CookieProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Events", description = "Управление событиями и рекомендациями")
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final SessionService sessionService;
    private final RecommendationService recommendationService;

    @Operation(
            summary = "Рекомендации мероприятий",
            description = "Возвращает персональный список рекомендованных мероприятий на основе лайков. Результат кэшируется в Redis."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешное получение рекомендаций",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecommendationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован"
            )
    })
    @GetMapping
    public ResponseEntity<?> getRecommendations(
            @Parameter(description = "ID сессии из Cookie", example = "3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d")
            @CookieValue(name = CookieProvider.SESSION_COOKIE_NAME, required = false) String sid) {

        String userId = sessionService.getUserId(sid);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RecommendationResponse response = recommendationService.getRecommendations(userId);
        return ResponseEntity.ok(response);
    }
}