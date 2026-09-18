package ru.musicalgreetings.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ru.musicalgreetings.auth.controller.AuthController;
import ru.musicalgreetings.auth.error.GlobalExceptionHandler;
import ru.musicalgreetings.auth.logging.RequestLoggingFilter;
import ru.musicalgreetings.auth.data.LoginResponse;
import ru.musicalgreetings.auth.data.RefreshResponse;
import ru.musicalgreetings.auth.service.AuthService;
import ru.musicalgreetings.auth.service.UnauthorizedException;

@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTests {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    void returnsOpenApiUnauthorizedError() throws Exception {
        when(authService.refreshJwt(anyString())).thenThrow(new UnauthorizedException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Refresh-Token", "invalid-token")
                        .header("X-Request-Id", "request-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "request-123"))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Сессия недействительна"))
                .andExpect(jsonPath("$.request_id").value("request-123"));
    }

    @Test
    void returnsCreatedForAnonymousSession() throws Exception {
        when(authService.createAnonymousSession()).thenReturn(new LoginResponse("jwt", "refresh"));

        mockMvc.perform(post("/api/v1/auth/anonymous"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jwt").value("jwt"))
                .andExpect(jsonPath("$.refresh_token").value("refresh"));
    }

    @Test
    void returnsCreatedForJwtRefresh() throws Exception {
        when(authService.refreshJwt("refresh")).thenReturn(new RefreshResponse("new-jwt"));

        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Refresh-Token", "refresh"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jwt").value("new-jwt"));
    }

    @Test
    void returnsNoContentForLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").header("X-Refresh-Token", "refresh"))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsMethodNotAllowedForDirectUnsupportedMethod() throws Exception {
        mockMvc.perform(get("/api/v1/auth/anonymous"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("method_not_allowed"));
    }

    @Test
    void returnsNotFoundForDirectMissingResource() throws Exception {
        MockMvc missingResourceMvc = MockMvcBuilders
                .standaloneSetup(new MissingResourceController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();

        missingResourceMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void treatsMissingRefreshHeaderAsUnauthorized(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andReturn();

        String responseRequestId = result.getResponse().getHeader("X-Request-Id");
        String bodyRequestId = JsonPath.read(result.getResponse().getContentAsString(), "$.request_id");
        assertThat(bodyRequestId).isEqualTo(responseRequestId);
        assertThat(output).containsPattern(
                "http_request_completed.*requestId=" + java.util.regex.Pattern.quote(responseRequestId)
        ).contains("auth_operation_rejected operation=refresh reason=missing");
    }

    @Test
    void hidesUnexpectedExceptionBehindInternalError(CapturedOutput output) throws Exception {
        IllegalArgumentException cause = new IllegalArgumentException("jdbc-password-secret");
        when(authService.createAnonymousSession())
                .thenThrow(new IllegalStateException("database-password-secret", cause));

        mockMvc.perform(post("/api/v1/auth/anonymous").header("X-Request-Id", "request-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "request-500"))
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value("Что-то пошло не так, попробуйте ещё раз"))
                .andExpect(jsonPath("$.request_id").value("request-500"));

        assertThat(output)
                .contains("auth_request_failed")
                .contains("path=/api/v1/auth/anonymous")
                .contains("requestId=request-500")
                .contains("exceptionType=java.lang.IllegalStateException")
                .contains("causeType=java.lang.IllegalArgumentException")
                .doesNotContain("database-password-secret")
                .doesNotContain("jdbc-password-secret")
                .doesNotContain("at ru.musicalgreetings");
    }

    @RestController
    static class MissingResourceController {

        @GetMapping("/missing")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing", "missing");
        }
    }
}
