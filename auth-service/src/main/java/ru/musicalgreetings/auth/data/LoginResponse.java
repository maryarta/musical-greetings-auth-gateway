package ru.musicalgreetings.auth.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        String jwt,
        @JsonProperty("refresh_token") String refreshToken
) {
}
