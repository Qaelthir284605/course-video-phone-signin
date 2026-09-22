package dev.learning;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class MediaService {
    private final InfraiGateway gateway;
    private final Map<String, Lesson> lessons = new ConcurrentHashMap<>();

    public MediaService(InfraiGateway gateway) { this.gateway = gateway; }
    public static void main(String[] args) { SpringApplication.run(MediaService.class, args); }

    public record Phone(String phone) {}
    public record Login(String phone, String code) {}
    public record Ingest(String phone, String code, String title, String source, String creatorPhone) {}
    public record Lesson(String id, String title, String source, String creatorPhone, String state) {}

    @PostMapping("/login/code")
    public Map<String, String> sendCode(@RequestBody Phone input) {
        gateway.sendLoginCode(input.phone());
        return Map.of("state", "CODE_SENT");
    }

    @PostMapping("/login/verify")
    public Map<String, String> verify(@RequestBody Login input) {
        gateway.verifyLogin(input.phone(), input.code());
        return Map.of("state", "VERIFIED");
    }

    @PostMapping("/lessons")
    public Lesson ingest(@RequestBody Ingest input) {
        if (input.title() == null || input.title().isBlank() || input.source() == null || input.source().isBlank()
                || input.creatorPhone() == null || input.creatorPhone().isBlank()) {
            throw new IllegalArgumentException("title, source and creatorPhone are required");
        }
        gateway.verifyLogin(input.phone(), input.code());
        Lesson queued = new Lesson(UUID.randomUUID().toString(), input.title(), input.source(), input.creatorPhone(), "PROCESSING");
        lessons.put(queued.id(), queued);
        // Delivery is issued only after the authenticated lesson enters the processing queue.
        gateway.sendDeliveryCode(queued.creatorPhone(), queued.id());
        Lesson delivered = new Lesson(queued.id(), queued.title(), queued.source(), queued.creatorPhone(), "CREATOR_NOTIFIED");
        lessons.put(delivered.id(), delivered);
        return delivered;
    }

    @ExceptionHandler(InfraiGateway.Rejection.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiGateway.Rejection error) {
        return ResponseEntity.status(error.status() >= 400 && error.status() < 500 ? error.status() : 502)
                .body(Map.of("error", error.code()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error.getMessage()));
    }
}
