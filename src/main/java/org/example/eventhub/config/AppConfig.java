package org.example.eventhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper; // Добавьте этот импорт
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Конфигурация приложения.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppConfig {
    /**
     * Время жизни сессии пользователя.
     */
    private Long userSessionTtl;
    /**
     * Время жизни лайков в кэше в секундах.
     */
    private Long likeTtl;


    /**
     * Создает и настраивает бин для шифрования паролей.
     *
     * @return экземпляр {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Необходим для сериализации/десериализации объектов в Redis.
     *
     * @return ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}