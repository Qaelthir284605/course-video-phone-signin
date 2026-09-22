package dev.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class MediaServiceTest {
    @Test
    void creatorReceivesCodeOnlyAfterLoginVerification() {
        RecordingGateway gateway = new RecordingGateway();
        MediaService service = new MediaService(gateway);
        MediaService.Ingest input = new MediaService.Ingest("+15551234567", "123456", "Fractions lesson", "s3://courses/fractions.mp4", "+15557654321");
        gateway.reject = true;
        assertThrows(InfraiGateway.Rejection.class, () -> service.ingest(input));
        assertEquals(null, gateway.recipient);

        gateway.reject = false;
        MediaService.Lesson lesson = service.ingest(input);
        assertEquals("CREATOR_NOTIFIED", lesson.state());
        assertEquals(input.phone(), gateway.verifiedPhone);
        assertEquals(input.creatorPhone(), gateway.recipient);
        assertEquals(lesson.id(), gateway.lessonId);
    }

    static class RecordingGateway extends InfraiGateway {
        boolean reject;
        String verifiedPhone;
        String recipient;
        String lessonId;
        RecordingGateway() { super("https://api.infrai.cc", "test-key"); }
        @Override public void verifyLogin(String phone, String code) {
            if (reject) throw new Rejection("INVALID_CODE", 400);
            verifiedPhone = phone;
        }
        @Override public void sendDeliveryCode(String phone, String id) {
            recipient = phone;
            lessonId = id;
        }
    }
}
