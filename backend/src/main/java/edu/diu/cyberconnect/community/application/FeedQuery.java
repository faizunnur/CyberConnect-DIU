package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.common.model.PostIntent;
import java.util.UUID;

public record FeedQuery(PostIntent intent, String tag, String q, UUID authorId, boolean bookmarkedOnly) {

    public static FeedQuery bookmarks() {
        return new FeedQuery(null, null, null, null, true);
    }
}
