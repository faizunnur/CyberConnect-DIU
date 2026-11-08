package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.common.model.ContentStatus;
import edu.diu.cyberconnect.common.model.PostIntent;
import edu.diu.cyberconnect.common.model.PostVisibility;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A post as seen by one viewer. {@code repostOf} carries the original (one level) when it is still visible;
 * {@code repostUnavailable} is true when the post is a repost whose original is gone or hidden. {@code removedReason}
 * is only present for the author of a REMOVED post and for moderators. {@code attachments} are photos (up to four) or a
 * single document, in display order.
 */
public record PostView(UUID id, MemberCard author, PostIntent intent, String body, PostVisibility visibility,
        List<String> hashtags, List<AttachmentView> attachments, int likeCount, int commentCount, int repostCount, boolean liked, boolean bookmarked,
        boolean own, ContentStatus status, String removedReason, PostView repostOf, boolean repostUnavailable,
        Instant createdAt, Instant editedAt) {
}
