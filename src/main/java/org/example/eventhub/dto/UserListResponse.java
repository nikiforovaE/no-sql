package org.example.eventhub.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Объект ответа, содержащий список пользователей и их количество.
 */
@Data
@Builder
public class UserListResponse {
    private List<UserInfo> users;
    private int count;

    /**
     * Информация о пользователе
     */
    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String full_name;
        private String username;
    }
}