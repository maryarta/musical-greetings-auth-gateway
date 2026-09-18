package ru.musicalgreetings.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ru.musicalgreetings.auth.config.SessionProperties;
import ru.musicalgreetings.auth.data.LoginResponse;
import ru.musicalgreetings.auth.data.RefreshResponse;
import ru.musicalgreetings.auth.model.Session;
import ru.musicalgreetings.auth.model.SessionRepository;
import ru.musicalgreetings.auth.model.User;
import ru.musicalgreetings.auth.model.UserRepository;
import ru.musicalgreetings.auth.data.AccountType;

@ExtendWith(OutputCaptureExtension.class)
class AuthServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("7f3a2b1c-8e4d-47a0-9f60-1a5e8c7d2b03");
    private static final UUID SESSION_ID = UUID.fromString("3b68fdf2-709c-43ea-bb8a-f6da031fe634");

    private SessionRepository sessionRepository;
    private UserRepository userRepository;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(
                userRepository,
                sessionRepository,
                new RefreshTokenService(),
                jwtService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SessionProperties(Duration.ofDays(7))
        );
    }

    @Test
    void createsSessionWithConfiguredTtl(CapturedOutput output) {
        when(userRepository.save(any(User.class)))
                .thenReturn(new User(USER_ID, AccountType.ANONYMOUS));
        when(sessionRepository.save(any(Session.class)))
                .thenAnswer(invocation -> {
                    Session session = invocation.getArgument(0);
                    return new Session(
                            SESSION_ID,
                            session.getUserId(),
                            session.getRefreshTokenHash(),
                            session.getCreatedAt(),
                            session.getExpiresAt()
                    );
                });
        when(jwtService.issueAccessToken(any(UUID.class), any(UUID.class))).thenReturn("access-jwt");
        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);

        LoginResponse response = authService.createAnonymousSession();

        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(response.jwt()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).hasSize(43);
        assertThat(output)
                .contains("auth_operation_started operation=create_anonymous_session")
                .contains("auth_operation_succeeded operation=create_anonymous_session")
                .doesNotContain("access-jwt")
                .doesNotContain(response.refreshToken())
                .doesNotContain(USER_ID.toString())
                .doesNotContain(SESSION_ID.toString());
    }

    @Test
    void refreshesJwtAndExtendsActiveSession(CapturedOutput output) {
        Session session = sessionExpiringAt(NOW.plus(1, ChronoUnit.DAYS));
        when(sessionRepository.findByRefreshTokenHash(any(byte[].class)))
                .thenReturn(Optional.of(session));
        when(jwtService.issueAccessToken(USER_ID, SESSION_ID)).thenReturn("new-access-jwt");

        RefreshResponse response = authService.refreshJwt("existing-refresh-token");

        assertThat(response.jwt()).isEqualTo("new-access-jwt");
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(7, ChronoUnit.DAYS));
        assertThat(output)
                .contains("auth_operation_started operation=refresh")
                .contains("auth_operation_succeeded operation=refresh")
                .doesNotContain("existing-refresh-token")
                .doesNotContain("new-access-jwt")
                .doesNotContain(USER_ID.toString())
                .doesNotContain(SESSION_ID.toString());
    }

    @Test
    void logsSuccessfulLogoutWithoutSensitiveValues(CapturedOutput output) {
        Session session = sessionExpiringAt(NOW.plus(1, ChronoUnit.DAYS));
        when(sessionRepository.findByRefreshTokenHash(any(byte[].class)))
                .thenReturn(Optional.of(session));

        authService.logout("logout-refresh-token");

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        assertThat(output)
                .contains("auth_operation_started operation=logout")
                .contains("auth_operation_succeeded operation=logout")
                .doesNotContain("logout-refresh-token")
                .doesNotContain(USER_ID.toString())
                .doesNotContain(SESSION_ID.toString());
    }

    @Test
    void rejectsExpiredSession(CapturedOutput output) {
        Session session = sessionExpiringAt(NOW.minusSeconds(1));
        when(sessionRepository.findByRefreshTokenHash(any(byte[].class)))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.refreshJwt("expired-refresh-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(output)
                .contains("auth_operation_rejected operation=refresh reason=expired_or_revoked")
                .doesNotContain("expired-refresh-token");
    }

    @Test
    void rejectsUnknownRefreshToken(CapturedOutput output) {
        when(sessionRepository.findByRefreshTokenHash(any(byte[].class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshJwt("unknown-refresh-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(output)
                .contains("auth_operation_rejected operation=refresh reason=not_found")
                .doesNotContain("unknown-refresh-token");
    }

    @Test
    void rejectsBlankRefreshToken(CapturedOutput output) {
        assertThatThrownBy(() -> authService.refreshJwt(" "))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(output)
                .contains("auth_operation_rejected operation=refresh reason=missing");
    }

    @Test
    void rejectsBlankRefreshTokenOnLogout() {
        assertThatThrownBy(() -> authService.logout(" "))
                .isInstanceOf(UnauthorizedException.class);
    }

    private Session sessionExpiringAt(Instant expiresAt) {
        return new Session(
                SESSION_ID,
                USER_ID,
                new byte[32],
                NOW.minus(1, ChronoUnit.DAYS),
                expiresAt
        );
    }
}
