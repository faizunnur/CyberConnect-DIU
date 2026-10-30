package edu.diu.cyberconnect.networking.application;

import edu.diu.cyberconnect.common.model.ConnectionStatus;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.UUID;

/** A request as seen by one side; {@code member} is always the other party. */
public record ConnectionRequestView(UUID id, MemberCard member, String message, ConnectionStatus status,
        boolean incoming, Instant createdAt) {
}
