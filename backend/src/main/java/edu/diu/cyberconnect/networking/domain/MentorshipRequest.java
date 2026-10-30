package edu.diu.cyberconnect.networking.domain;

import edu.diu.cyberconnect.common.model.MentorshipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mentorship_requests")
public class MentorshipRequest {

    @Id
    private UUID id;

    @Column(name = "mentee_id", nullable = false)
    private UUID menteeId;

    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MentorshipStatus status;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String message;

    @Column(name = "response_note")
    private String responseNote;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MentorshipRequest() {
    }

    public static MentorshipRequest create(UUID menteeId, UUID mentorId, String topic, String message) {
        MentorshipRequest r = new MentorshipRequest();
        r.id = UUID.randomUUID();
        r.menteeId = menteeId;
        r.mentorId = mentorId;
        r.status = MentorshipStatus.PENDING;
        r.topic = topic;
        r.message = message;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    public void respond(boolean accepted, String note) {
        this.status = accepted ? MentorshipStatus.ACCEPTED : MentorshipStatus.DECLINED;
        this.responseNote = note;
        this.respondedAt = Instant.now();
        this.updatedAt = this.respondedAt;
    }

    public void complete() {
        this.status = MentorshipStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void cancel() {
        this.status = MentorshipStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public boolean involves(UUID userId) {
        return menteeId.equals(userId) || mentorId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMenteeId() {
        return menteeId;
    }

    public UUID getMentorId() {
        return mentorId;
    }

    public MentorshipStatus getStatus() {
        return status;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessage() {
        return message;
    }

    public String getResponseNote() {
        return responseNote;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
