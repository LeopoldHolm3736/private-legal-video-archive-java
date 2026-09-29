package legalvideo;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MatterSessionService {
    private final InfraiClient infrai;
    private final Clock clock;

    public MatterSessionService(InfraiClient infrai, Clock clock) {
        this.infrai = infrai;
        this.clock = clock;
    }

    public Map<String, Object> open(MatterIntake intake) {
        String stem = slug(intake.matterId());
        String room = "matter-" + stem;
        String recordingKey = "matters/" + stem + "/sessions/" + slug(intake.sessionId()) + ".webm";
        Map<String, Object> roomData = infrai.createRoom(room);
        Map<String, Object> tokenData = infrai.issueRoomToken(room, intake.participantId(), intake.participantName());
        Map<String, Object> uploadData = infrai.presignPut(recordingKey, intake.requestId() + "-recording");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matter_id", intake.matterId());
        result.put("room", roomData);
        result.put("participant_token", tokenData);
        result.put("recording_upload", uploadData);
        result.put("recording_object_key", recordingKey);
        result.put("follow_up", followUp(intake.deadline(), LocalDate.now(clock)));
        return result;
    }

    public Map<String, Object> signedDocument(String matterId, String documentId, String requestId) {
        String key = "matters/" + slug(matterId) + "/signed/" + slug(documentId) + ".pdf";
        return Map.of("object_key", key, "download", infrai.presignGet(key, requestId + "-signed-document"));
    }

    public static FollowUp followUp(LocalDate deadline, LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, deadline);
        if (days < 0) return new FollowUp("OVERDUE", "Escalate to the matter owner today", days);
        if (days <= 3) return new FollowUp("DUE_SOON", "Confirm filing readiness today", days);
        return new FollowUp("PLANNED", "Review at the next matter check-in", days);
    }

    private static String slug(String value) {
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank()) throw new IllegalArgumentException("Identifier must contain a letter or digit");
        return slug;
    }

    public record MatterIntake(String matterId, String sessionId, String participantId,
                               String participantName, LocalDate deadline, String requestId) {}
    public record FollowUp(String state, String action, long daysRemaining) {}
}
