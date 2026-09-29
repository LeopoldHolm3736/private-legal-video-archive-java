package legalvideo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LegalSessionController {
    private final MatterSessionService service;

    public LegalSessionController(MatterSessionService service) { this.service = service; }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/matters/intake", this::intake);
        server.createContext("/matters/signed-document", this::signedDocument);
        server.start();
        return server;
    }

    private void intake(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "POST required")); return; }
        try {
            Map<String, Object> body = Json.object(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            MatterSessionService.MatterIntake intake = new MatterSessionService.MatterIntake(
                required(body, "matter_id"), required(body, "session_id"), required(body, "participant_id"),
                required(body, "participant_name"), LocalDate.parse(required(body, "deadline")), required(body, "request_id"));
            send(exchange, 201, service.open(intake));
        } catch (InfraiException e) {
            send(exchange, e.clientStatus(), Map.of("error", Map.of("code", e.code(), "message", e.getMessage())));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    private void signedDocument(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "POST required")); return; }
        try {
            Map<String, Object> body = Json.object(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, service.signedDocument(required(body, "matter_id"),
                required(body, "document_id"), required(body, "request_id")));
        } catch (InfraiException e) {
            send(exchange, e.clientStatus(), Map.of("error", Map.of("code", e.code(), "message", e.getMessage())));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    private static String required(Map<String, Object> body, String name) {
        Object value = body.get(name);
        if (!(value instanceof String) || ((String) value).isBlank()) throw new IllegalArgumentException(name + " is required");
        return (String) value;
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
