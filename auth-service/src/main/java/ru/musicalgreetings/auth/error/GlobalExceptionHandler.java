package ru.musicalgreetings.auth.error;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ru.musicalgreetings.auth.service.UnauthorizedException;
import ru.musicalgreetings.auth.logging.RequestLoggingFilter;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ErrorResponse> handleUnauthorized(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Сессия недействительна",
                requestId(request)
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        String requestId = requestId(request);
        if ("X-Refresh-Token".equalsIgnoreCase(exception.getHeaderName())) {
            String operation = request.getRequestURI().endsWith("/logout")
                    ? "logout"
                    : "refresh";
            LOGGER.warn("auth_operation_rejected operation={} reason=missing", operation);
            return errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Сессия недействительна",
                    requestId
            );
        }
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Запрос не прошёл проверку",
                requestId
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "not_found",
                "Маршрут не найден",
                requestId(request)
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "method_not_allowed",
                "Метод не поддерживается",
                requestId(request)
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = requestId(request);
        String causeType = exception.getCause() == null
                ? "none"
                : exception.getCause().getClass().getName();
        LOGGER.error(
                "auth_request_failed path={} requestId={} exceptionType={} causeType={}",
                request.getRequestURI(),
                requestId,
                exception.getClass().getName(),
                causeType
        );
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "Что-то пошло не так, попробуйте ещё раз",
                requestId
        );
    }

    private ResponseEntity<ErrorResponse> errorResponse(
            HttpStatus status,
            String code,
            String message,
            String requestId
    ) {
        return ResponseEntity.status(status)
                .header(REQUEST_ID_HEADER, requestId)
                .body(new ErrorResponse(code, message, requestId));
    }

    private String requestId(HttpServletRequest request) {
        Object requestAttribute = request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE);
        if (requestAttribute instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
    }
}
