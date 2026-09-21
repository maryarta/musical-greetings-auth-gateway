package ru.team22.musicalgreetings;

import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

class DemoBackendTests {
    static ConfigurableApplicationContext context;
    static String base;
    static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll static void start() {
        context = SpringApplication.run(Main.class, "--server.port=0");
        base = "http://localhost:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }
    @AfterAll static void stop() { if (context != null) context.close(); }
    HttpResponse<String> get(String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path));
        if (headers.length > 0) request.headers(headers);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void publicCatalogWorksWithoutIdentity() throws Exception {
        assertEquals(200, get("/api/v1/holidays").statusCode());
        assertEquals(200, get("/api/v1/music-types").statusCode());
        assertEquals("birthday", get("/api/v1/holidays/2").body());
        assertEquals(404, get("/api/v1/holidays/99").statusCode());
    }
    @Test void historyReturnsTrustedIdentityAndRequestId() throws Exception {
        var user = "11111111-1111-4111-8111-111111111111";
        var session = "22222222-2222-4222-8222-222222222222";
        var response = get("/api/v1/congrats/history", "X-User-Id", user,
                "X-Session-Id", session, "X-Request-Id", "demo-request");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"userId\":\"" + user + "\""));
        assertTrue(response.body().contains("\"sessionId\":\"" + session + "\""));
        assertEquals("demo-request", response.headers().firstValue("X-Request-Id").orElseThrow());
    }
    @Test void missingOrMalformedIdentityIsUnauthorized() throws Exception {
        var response = get("/api/v1/congrats/history");
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("UNAUTHORIZED"));
        assertFalse(response.headers().firstValue("X-Request-Id").orElseThrow().isBlank());
        assertEquals(401, get("/api/v1/congrats/history", "X-User-Id", "invalid",
                "X-Session-Id", "22222222-2222-4222-8222-222222222222").statusCode());
    }
}
