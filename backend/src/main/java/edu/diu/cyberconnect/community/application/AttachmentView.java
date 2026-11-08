package edu.diu.cyberconnect.community.application;

import java.util.UUID;

/**
 * An attachment as the client sees it. {@code url} is a same-origin backend path (never a storage URL); it is null for
 * an upload that no post has claimed yet (the browser previews those from the local file).
 */
public record AttachmentView(UUID id, String kind, String contentType, long sizeBytes, String filename, Integer width,
        Integer height, String url) {
}
