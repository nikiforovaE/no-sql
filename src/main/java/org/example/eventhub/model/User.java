package org.example.eventhub.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Модель пользователя для хранения в коллекции "users" MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
@Schema(description = "Модель пользователя")
public class User {

    /**
     * Уникальный идентификатор документа.
     */
    @Id
    @Schema(description = "Идентификатор пользователя (hex)", example = "65e9c0b1a2b3c4d5e6f7a8b9")
    private String id;

    /**
     * Полное имя пользователя.
     */
    @Field("full_name")
    @Schema(description = "Полное имя", example = "Иван Иванов")
    private String fullName;

    /**
     * Уникальное имя пользователя (логин).
     */
    @Indexed(unique = true)
    @Schema(description = "Никнейм для входа", example = "ivan_ivanov")
    private String username;

    /**
     * Хеш пароля пользователя.
     */
    @Field("password_hash")
    @Schema(
            description = "Хеш пароля (BCrypt)",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            example = "$2a$10$Xr0DDNUTfpbLihAp0ZbGPei1oFP8g5FNypIvaXdW7W.KWJaobPA5q"
    )
    private String passwordHash;
}