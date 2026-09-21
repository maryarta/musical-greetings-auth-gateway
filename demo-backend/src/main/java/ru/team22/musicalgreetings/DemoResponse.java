package ru.team22.musicalgreetings;

import ru.team22.musicalgreetings.security.AuthenticatedUser;

public record DemoResponse(AuthenticatedUser user, String message) {}
