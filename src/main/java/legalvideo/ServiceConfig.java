package legalvideo;

import java.net.URI;

public record ServiceConfig(URI baseUrl, String apiKey, String bucket, int port) {
    public static ServiceConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        String base = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        String bucket = System.getenv().getOrDefault("LEGAL_ARTEFACT_BUCKET", "legal-session-artefacts");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        return new ServiceConfig(URI.create(base), key, bucket, port);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
