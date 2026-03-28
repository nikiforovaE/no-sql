package org.example.eventhub.dto;

import lombok.Data;

/**
 * Объект запроса для аутентификации пользователя.
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}