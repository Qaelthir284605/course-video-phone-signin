package dev.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiGateway {
    private final String baseUrl;
    private final String key;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public InfraiGateway(@Value("${infrai.base-url}") String baseUrl, @Value("${infrai.api-key}") String key) {
        this.baseUrl = baseUrl;
        this.key = key;
    }

    public void sendLoginCode(String phone) {
        post("/v1/auth/phone/send_code", Map.of("phone", phone, "purpose", "login"));
    }

    public void verifyLogin(String phone, String code) {
        post("/v1/auth/phone/verify", Map.of("phone", phone, "code", code, "login", true));
    }

    public void sendDeliveryCode(String phone, String lessonId) {
        post("/v1/sms/otp", Map.of("to", phone, "idempotency_key", lessonId));
    }

    private JsonNode post(String path, Map<String, ?> body) {
        if (key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .method("POST", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long delay = response.headers().firstValue("Retry-After").map(InfraiGateway::seconds)
                            .orElse(1L << attempt);
                    Thread.sleep(Math.min(delay, 30) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new Rejection(error.path("code").asText("REQUEST_REJECTED"), response.statusCode());
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Upstream request failed");
                return envelope.path("data");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to complete request", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", exception);
            }
        }
        throw new Rejection("RATE_LIMITED", 429);
    }

    private static long seconds(String value) {
        try { return Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 1; }
    }

    public static final class Rejection extends RuntimeException {
        private final String code;
        private final int status;
        public Rejection(String code, int status) { super(code); this.code = code; this.status = status; }
        public String code() { return code; }
        public int status() { return status; }
    }
}
