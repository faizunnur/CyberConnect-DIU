package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.common.model.ContentStatus;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code body} and {@code author} are null for a deleted/removed top-level comment kept as a placeholder. */
public record CommentView(UUID id, UUID postId, UUID parentId, MemberCard author, String body, ContentStatus status,
        boolean own, List<CommentView> replies, Instant createdAt, Instant editedAt) {
}
