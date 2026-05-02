package org.example.eventhub.dto;

import lombok.Data;

/**
 * Данные для аутентификации пользователя.
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}