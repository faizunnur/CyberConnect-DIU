package edu.diu.cyberconnect.networking.application;

import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.UUID;

public record ConnectionView(UUID id, MemberCard member, Instant connectedAt) {
}
