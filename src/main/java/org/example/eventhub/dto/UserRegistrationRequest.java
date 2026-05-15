package org.example.eventhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Данные для регистрации нового пользователя.
 */
@Data
@Schema(description = "Запрос на регистрацию нового пользователя")
public class UserRegistrationRequest {

    @Schema(description = "Полное имя пользователя", example = "Джон Доу", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @JsonProperty("full_name")
    private String fullName;

    @Schema(description = "Уникальный никнейм (логин)", example = "j0hnd0e42", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String username;

    @Schema(description = "Пароль", example = "svp4_dvp4_str0ng_passw0rd", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    @NotBlank
    private String password;
}