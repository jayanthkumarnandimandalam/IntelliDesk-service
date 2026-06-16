package com.intellidesk.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for the text chat endpoint.
 */
public record ChatRequest(
        @NotBlank(message = "Session ID is required")
        @JsonProperty("session_id")
        String sessionId,

        @NotBlank(message = "Message is required")
        @Size(max = 4000, message = "Message must not exceed 4000 characters")
        String message
) {}
