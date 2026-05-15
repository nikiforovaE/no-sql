package org.example.eventhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Данные для аутентификации пользователя.
 */
@Data
@Schema(description = "Данные для входа в систему")
public class LoginRequest {

    @Schema(
            description = "Имя пользователя (username)",
            example = "j0hnd0e42",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String username;

    @Schema(
            description = "Пароль пользователя",
            example = "svp4_dvp4_str0ng_passw0rd",
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "password"
    )
    private String password;
}