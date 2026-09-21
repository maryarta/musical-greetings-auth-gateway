package ru.team22.musicalgreetings.security;

import java.util.UUID;

public record AuthenticatedUser (UUID userId, UUID sessionId) {
}
