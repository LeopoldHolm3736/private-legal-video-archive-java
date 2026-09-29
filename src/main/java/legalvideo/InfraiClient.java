package legalvideo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiClient {
    private final ServiceConfig config;
    private final HttpClient http;

    public InfraiClient(ServiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Map<String, Object> createBucket() {
        return call("POST", "/v1/storage/bucket/create", Map.of("name", config.bucket()));
    }

    public Map<String, Object> createRoom(String room) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", room);
        body.put("max_participants", 8);
        body.put("empty_timeout_s", 900);
        body.put("region", "auto");
        return call("POST", "/v1/rtc/room/create", body);
    }

    public Map<String, Object> issueRoomToken(String room, String identity, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("room", room);
        body.put("identity", identity);
        body.put("display_name", displayName);
        body.put("ttl_s", 3600);
        body.put("can_publish", true);
        body.put("can_subscribe", true);
        return call("POST", "/v1/rtc/token/issue", body);
    }

    public Map<String, Object> presignPut(String objectKey, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("op", "put");
        body.put("expires_seconds", 1800);
        body.put("content_type", "video/webm");
        body.put("max_bytes", 1_073_741_824L);
        body.put("idempotency_key", requestId);
        return call("POST", "/v1/storage/object/presign/" + segment(config.bucket()) + "/" + segment(objectKey), body);
    }

    public Map<String, Object> presignGet(String objectKey, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("op", "get");
        body.put("expires_seconds", 900);
        body.put("response_disposition", "attachment");
        body.put("idempotency_key", requestId);
        return call("POST", "/v1/storage/object/presign/" + segment(config.bucket()) + "/" + segment(objectKey), body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                .build();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach Infrai", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", e);
            }

            Map<String, Object> envelope = Json.object(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map
                    ? (Map<String, Object>) envelope.get("error") : Map.of();
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "INFRAI_REJECTED")),
                    String.valueOf(error.getOrDefault("message", "Infrai rejected the request")), response.statusCode());
            }
            Object data = envelope.get("data");
            return data instanceof Map ? (Map<String, Object>) data : Map.of("value", data);
        }
        throw new IllegalStateException("Retry limit reached");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
            .map(value -> { try { return Long.parseLong(value) * 1000L; } catch (NumberFormatException ignored) { return 0L; } })
            .filter(value -> value > 0)
            .orElse(250L * (1L << attempt));
    }

    private static void pause(long milliseconds) {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Retry interrupted", e); }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
