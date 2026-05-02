package org.example.eventhub.model;

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
public class User {

    /**
     * Уникальный идентификатор документа.
     */
    @Id
    private String id;

    /**
     * Полное имя пользователя.
     */
    @Field("full_name")
    private String fullName;

    /**
     * Уникальное имя пользователя (логин).
     */
    @Indexed(unique = true)
    private String username;

    /**
     * Хеш пароля пользователя.
     */
    @Field("password_hash")
    private String passwordHash;
}