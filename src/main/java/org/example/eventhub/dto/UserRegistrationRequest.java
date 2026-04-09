package org.example.eventhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRegistrationRequest {
    @NotBlank
    @JsonProperty("full_name")
    private String fullName;

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}