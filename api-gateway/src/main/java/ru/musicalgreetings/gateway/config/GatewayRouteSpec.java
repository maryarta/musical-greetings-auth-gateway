package ru.musicalgreetings.gateway.config;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpMethod;

public record GatewayRouteSpec(
        String id,
        List<String> paths,
        List<HttpMethod> methods,
        TargetService targetService,
        AccessType accessType,
        ClientKeyType clientKeyType
) {

    public GatewayRouteSpec {
        Objects.requireNonNull(id, "id");
        paths = List.copyOf(paths);
        methods = List.copyOf(methods);
        Objects.requireNonNull(targetService, "targetService");
        Objects.requireNonNull(accessType, "accessType");
        Objects.requireNonNull(clientKeyType, "clientKeyType");
        if (id.isBlank() || paths.isEmpty() || methods.isEmpty()) {
            throw new IllegalArgumentException("Gateway route id, paths and methods must not be empty");
        }
    }

    public enum TargetService {
        AUTH,
        CONGRATS
    }

    public enum AccessType {
        PUBLIC,
        PROTECTED
    }

    public enum ClientKeyType {
        IP,
        USER
    }
}
