package edu.diu.cyberconnect.community.api;

import edu.diu.cyberconnect.common.model.PostIntent;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.community.application.FeedQuery;
import edu.diu.cyberconnect.community.application.PostService;
import edu.diu.cyberconnect.community.application.PostView;
import edu.diu.cyberconnect.community.application.TrendingTag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FeedController {

    private final PostService posts;

    public FeedController(PostService posts) {
        this.posts = posts;
    }

    /** Newest-first feed of posts visible to the caller, with optional intent, hashtag, author and text filters. */
    @GetMapping("/feed")
    public Page<PostView> feed(@RequestParam(required = false) PostIntent intent,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String normalizedTag = tag == null || tag.isBlank() ? null : PlainText.normalizeTag(tag);
        FeedQuery query = new FeedQuery(intent, normalizedTag, q, author, false);
        return posts.feed(CurrentUser.require().getId(), query, pageable(page, size));
    }

    @GetMapping("/bookmarks")
    public Page<PostView> bookmarks(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return posts.feed(CurrentUser.require().getId(), FeedQuery.bookmarks(), pageable(page, size));
    }

    @GetMapping("/hashtags/trending")
    public List<TrendingTag> trending() {
        return posts.trending();
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50));
    }
}
