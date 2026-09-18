package ru.musicalgreetings.auth.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.musicalgreetings.auth.config.SessionProperties;
import ru.musicalgreetings.auth.data.AccountType;
import ru.musicalgreetings.auth.data.LoginResponse;
import ru.musicalgreetings.auth.data.RefreshResponse;
import ru.musicalgreetings.auth.model.Session;
import ru.musicalgreetings.auth.model.SessionRepository;
import ru.musicalgreetings.auth.model.User;
import ru.musicalgreetings.auth.model.UserRepository;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final Clock clock;
    private final SessionProperties sessionProperties;

    public AuthService(UserRepository userRepository, SessionRepository sessionRepository, RefreshTokenService refreshTokenService, JwtService jwtService, Clock clock, SessionProperties sessionProperties) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.clock = clock;
        this.sessionProperties = sessionProperties;
    }

    @Transactional
    public LoginResponse createAnonymousSession(){
        LOGGER.info("auth_operation_started operation=create_anonymous_session");
        Instant now = clock.instant();
        User user = userRepository.save(new User(AccountType.ANONYMOUS));

        String refreshToken = refreshTokenService.generate();
        byte[] refreshTokenHash = refreshTokenService.hash(refreshToken);

        Session session = sessionRepository.save(new Session(
                user.getId(),
                refreshTokenHash,
                now,
                now.plus(sessionProperties.ttl())
        ));

        String accessToken = jwtService.issueAccessToken(user.getId(), session.getId());

        LOGGER.info("auth_operation_succeeded operation=create_anonymous_session");
        return new LoginResponse(accessToken, refreshToken);
    }

    @Transactional
    public void logout(String refreshToken){
        LOGGER.info("auth_operation_started operation=logout");
        Instant now = clock.instant();
        Session session = getActiveSession(refreshToken, now, "logout");

        session.revoke(now);

        sessionRepository.save(session);
        LOGGER.info("auth_operation_succeeded operation=logout");
    }

    @Transactional
    public RefreshResponse refreshJwt(String refreshToken){
        LOGGER.info("auth_operation_started operation=refresh");
        Instant now = clock.instant();
        Session session = getActiveSession(refreshToken, now, "refresh");

        session.extendUntil(now.plus(sessionProperties.ttl()));
        sessionRepository.save(session);

        String accessToken = jwtService.issueAccessToken(session.getUserId(), session.getId());
        LOGGER.info("auth_operation_succeeded operation=refresh");
        return new RefreshResponse(accessToken);
    }

    private Session getActiveSession(String refreshToken, Instant now, String operation) {
        if (refreshToken == null || refreshToken.isBlank()) {
            LOGGER.warn("auth_operation_rejected operation={} reason=missing", operation);
            throw new UnauthorizedException();
        }

        byte[] refreshTokenHash = refreshTokenService.hash(refreshToken);
        Session session = sessionRepository
                .findByRefreshTokenHash(refreshTokenHash)
                .orElse(null);

        if (session == null) {
            LOGGER.warn("auth_operation_rejected operation={} reason=not_found", operation);
            throw new UnauthorizedException();
        }

        if (!session.isActiveAt(now)) {
            LOGGER.warn("auth_operation_rejected operation={} reason=expired_or_revoked", operation);
            throw new UnauthorizedException();
        }
        return session;
    }
}
