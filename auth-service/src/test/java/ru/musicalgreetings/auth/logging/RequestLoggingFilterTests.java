package ru.musicalgreetings.auth.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTests {

    @Test
    void preservesRequestIdForMdcResponseAndCompletionLog(CapturedOutput output) throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/refresh");
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            requestIdInsideChain.set(MDC.get("requestId"));
            ((MockHttpServletResponse) currentResponse).setStatus(201);
        });

        assertThat(requestIdInsideChain.get()).isEqualTo("request-123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-123");
        assertThat(output)
                .contains("http_request_completed")
                .contains("method=POST")
                .contains("path=/auth/refresh")
                .contains("status=201")
                .contains("durationMs=")
                .contains("requestId=request-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void generatesRequestIdAndClearsMdcAfterRequest(CapturedOutput output) throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/anonymous");
        request.addHeader("X-Request-Id", " ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (currentRequest, currentResponse) ->
                requestIdInsideChain.set(MDC.get("requestId")));

        String requestId = response.getHeader("X-Request-Id");
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(requestIdInsideChain.get()).isEqualTo(requestId);
        assertThat(output).contains("requestId=" + requestId);
        assertThat(MDC.get("requestId")).isNull();
    }
}
