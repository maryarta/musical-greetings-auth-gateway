package ru.team22.musicalgreetings.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String SESSION_ID_HEADER = "X-Session-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        String sessionIdHeader = request.getHeader(SESSION_ID_HEADER);
        if (userIdHeader != null && sessionIdHeader != null) {
            try {
                var principal = new AuthenticatedUser(UUID.fromString(userIdHeader), UUID.fromString(sessionIdHeader));
                var authentication = new PreAuthenticatedAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                authentication.setAuthenticated(true);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException malformedUuid) {
                // оставляем анонимным; решение о 401 принимает авторизация на уровне маршрута
            }
        }
        chain.doFilter(request, response);
    }
}
