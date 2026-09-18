package ru.musicalgreetings.gateway.error;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
        String code,
        String message,
        @JsonProperty("request_id") String requestId
) {
}
