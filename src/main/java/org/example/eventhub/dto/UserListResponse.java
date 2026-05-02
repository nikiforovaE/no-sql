package org.example.eventhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Объект ответа, содержащий список пользователей и их количество.
 */
@Data
@Builder
@Schema(description = "Объект ответа со списком пользователей (организаторов)")
public class UserListResponse {

    @Schema(description = "Список найденных пользователей")
    private List<UserInfo> users;

    @Schema(description = "Количество пользователей в списке", example = "1")
    private int count;

    /**
     * Информация о пользователе
     */
    @Data
    @Builder
    @Schema(description = "Публичная информация о пользователе")
    public static class UserInfo {

        @Schema(description = "Идентификатор пользователя (MongoDB ID)", example = "65e9c0b1a2b3c4d5e6f7a8b9")
        private String id;

        @Schema(description = "Полное имя пользователя", example = "Иван Иванов")
        private String full_name;

        @Schema(description = "Уникальное имя пользователя (логин)", example = "ivan_ivanov")
        private String username;
    }
}