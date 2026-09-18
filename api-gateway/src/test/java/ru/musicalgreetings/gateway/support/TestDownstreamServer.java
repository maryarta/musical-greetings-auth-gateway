package ru.musicalgreetings.gateway.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public final class TestDownstreamServer implements AutoCloseable {

    private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
    private final DisposableServer server;

    public TestDownstreamServer(String name) {
        this.server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asByteArray()
                        .defaultIfEmpty(new byte[0])
                        .flatMap(body -> {
                            Map<String, List<String>> headers = request.requestHeaders().names().stream()
                                    .collect(java.util.stream.Collectors.toMap(
                                            header -> header,
                                            header -> request.requestHeaders().getAll(header)
                                    ));
                            requests.add(new RecordedRequest(
                                    request.method().name(),
                                    request.uri(),
                                    headers,
                                    body
                            ));
                            if (request.uri().startsWith("/api/v1/auth/logout")) {
                                response.status(HttpResponseStatus.NO_CONTENT);
                                return response.send().then();
                            }
                            response.status(HttpResponseStatus.OK)
                                    .header(HttpHeaderNames.CONTENT_TYPE, "application/json");
                            return response.sendString(Mono.just("{\"downstream\":\"" + name + "\"}"))
                                    .then();
                        }))
                .bindNow(Duration.ofSeconds(10));
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    public RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = requests.poll(5, TimeUnit.SECONDS);
        if (request == null) {
            throw new AssertionError("Downstream request was not received");
        }
        return request;
    }

    public void clear() {
        requests.clear();
    }

    public int requestCount() {
        return requests.size();
    }

    @Override
    public void close() {
        server.disposeNow(Duration.ofSeconds(5));
    }

    public record RecordedRequest(
            String method,
            String uri,
            Map<String, List<String>> headers,
            byte[] body
    ) {
        public String firstHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }

        public String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
