package ru.musicalgreetings.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.musicalgreetings.auth.data.LoginResponse;
import ru.musicalgreetings.auth.data.RefreshResponse;
import ru.musicalgreetings.auth.model.Session;
import ru.musicalgreetings.auth.model.SessionRepository;
import ru.musicalgreetings.auth.model.UserRepository;
import ru.musicalgreetings.auth.service.AuthService;
import ru.musicalgreetings.auth.service.RefreshTokenService;
import ru.musicalgreetings.auth.service.UnauthorizedException;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class AuthServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void cleanDatabase() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createsUserAndSessionWithHashedRefreshToken() {
        LoginResponse response = authService.createAnonymousSession();

        assertThat(userRepository.count()).isOne();
        assertThat(sessionRepository.count()).isOne();
        Session session = onlySession();
        assertThat(session.getRefreshTokenHash())
                .containsExactly(refreshTokenService.hash(response.refreshToken()));
        assertThat(new String(session.getRefreshTokenHash()))
                .isNotEqualTo(response.refreshToken());
        assertThat(response.jwt()).isNotBlank();
    }

    @Test
    void refreshExtendsSessionAndReturnsNewJwt() {
        LoginResponse login = authService.createAnonymousSession();
        Session session = onlySession();
        Instant previousExpiry = Instant.now().plusSeconds(3600);
        session.extendUntil(previousExpiry);
        sessionRepository.save(session);

        RefreshResponse refreshed = authService.refreshJwt(login.refreshToken());

        assertThat(refreshed.jwt()).isNotBlank().isNotEqualTo(login.jwt());
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getExpiresAt())
                .isAfter(previousExpiry);
    }

    @Test
    void logoutRevokesSessionAndPreventsRefresh() {
        LoginResponse login = authService.createAnonymousSession();

        authService.logout(login.refreshToken());

        Session session = onlySession();
        assertThat(session.getRevokedAt()).isNotNull();
        assertThatThrownBy(() -> authService.refreshJwt(login.refreshToken()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void expiredSessionCannotBeRefreshed() {
        LoginResponse login = authService.createAnonymousSession();
        Session session = onlySession();
        session.extendUntil(Instant.now().minusSeconds(1));
        sessionRepository.save(session);

        assertThatThrownBy(() -> authService.refreshJwt(login.refreshToken()))
                .isInstanceOf(UnauthorizedException.class);
    }

    private Session onlySession() {
        return StreamSupport.stream(sessionRepository.findAll().spliterator(), false)
                .findFirst()
                .orElseThrow();
    }
}
