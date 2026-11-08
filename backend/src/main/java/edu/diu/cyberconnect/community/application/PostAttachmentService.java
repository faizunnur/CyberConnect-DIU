package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.exception.ApiException;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.media.DocumentTypes;
import edu.diu.cyberconnect.common.media.ImageSanitizer;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.scanning.MalwareScanner;
import edu.diu.cyberconnect.common.scanning.ScanResult;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.common.storage.StoredObject;
import edu.diu.cyberconnect.community.domain.PostAttachment;
import edu.diu.cyberconnect.community.domain.PostAttachment.Kind;
import edu.diu.cyberconnect.community.infrastructure.PostAttachmentRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Photos and documents attached to posts. Intake mirrors the profile-photo pipeline: content sniffing (never the
 * declared type), images re-encoded without metadata, documents limited to PDF/Office formats without macros, malware
 * scan, private media bucket. Bytes are only ever served through the backend after a visibility check by the caller.
 */
@Service
@Transactional
public class PostAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(PostAttachmentService.class);

    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    public static final int MAX_IMAGE_EDGE = 2048;
    public static final int MAX_IMAGES_PER_POST = 4;
    static final int MAX_UNCLAIMED_PER_MEMBER = 20;
    static final int UPLOADS_PER_HOUR = 60;
    static final Duration UNCLAIMED_TTL = Duration.ofHours(24);

    private final PostAttachmentRepository attachments;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final MalwareScanner scanner;
    private final RateLimiter rateLimiter;

    public PostAttachmentService(PostAttachmentRepository attachments, ObjectStorage storage,
            StorageProperties storageProperties, MalwareScanner scanner, RateLimiter rateLimiter) {
        this.attachments = attachments;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.scanner = scanner;
        this.rateLimiter = rateLimiter;
    }

    /** Validates, sanitises, scans and stores one upload; it stays unclaimed until a post of the same member claims it. */
    public AttachmentView upload(UUID ownerId, byte[] original, String declaredFilename) {
        if (original == null || original.length == 0) {
            throw new BadRequestException("FILE_EMPTY", "The uploaded file is empty.");
        }
        if (original.length > MAX_UPLOAD_BYTES) {
            throw new BadRequestException("UPLOAD_TOO_LARGE", "Attachments may be at most 10 MB.");
        }
        rateLimiter.check("post-attachment-upload", ownerId.toString(), UPLOADS_PER_HOUR, Duration.ofHours(1));
        if (attachments.countUnclaimed(ownerId) >= MAX_UNCLAIMED_PER_MEMBER) {
            throw new BadRequestException("TOO_MANY_PENDING_UPLOADS",
                    "Publish or discard your pending attachments before uploading more.");
        }
        String filename = cleanFilename(declaredFilename);
        Kind kind;
        byte[] stored;
        String contentType;
        Integer width = null;
        Integer height = null;
        if (ImageSanitizer.detectImageType(original) != null) {
            ImageSanitizer.Fitted fitted = ImageSanitizer.fitJpeg(original, MAX_IMAGE_EDGE);
            kind = Kind.IMAGE;
            stored = fitted.bytes();
            contentType = "image/jpeg";
            width = fitted.width();
            height = fitted.height();
        } else {
            contentType = DocumentTypes.detect(original, filename);
            if (contentType == null) {
                throw new BadRequestException("UNSUPPORTED_FILE_TYPE",
                        "Attach JPEG or PNG photos, or PDF, Word, PowerPoint or Excel documents (without macros).");
            }
            kind = Kind.FILE;
            stored = original;
        }
        ScanResult scan = scanner.scan(new ByteArrayInputStream(stored), stored.length);
        if (scan.status() == ScanResult.Status.INFECTED) {
            log.warn("Rejected post attachment from {}: malware signature {}", ownerId, scan.signature());
            throw new BadRequestException("FILE_REJECTED_MALWARE", "The file was rejected by the malware scanner.");
        }
        if (scan.status() == ScanResult.Status.ERROR) {
            throw new ScanUnavailableException();
        }
        String extension = kind == Kind.IMAGE ? "jpg" : DocumentTypes.extensionFor(contentType);
        String key = "posts/" + ownerId + "/" + UUID.randomUUID() + "." + extension;
        storage.put(bucket(), key, new ByteArrayInputStream(stored), stored.length, contentType, Map.of());
        if (kind == Kind.IMAGE && filename != null) {
            filename = replaceExtension(filename, "jpg");
        }
        PostAttachment saved = attachments.save(PostAttachment.upload(ownerId, kind, key, contentType, stored.length,
                filename, width, height));
        return view(saved);
    }

    /** Discards one of the member's own unclaimed uploads (the composer's "remove" button). */
    public void discard(UUID ownerId, UUID attachmentId) {
        PostAttachment a = attachments.findById(attachmentId).orElseThrow(PostAttachmentService::notFound);
        if (!a.getOwnerId().equals(ownerId) || a.getPostId() != null) {
            throw notFound();
        }
        remove(a);
    }

    /**
     * Attaches the given uploads to a new post in the given order. All must be the author's own, unclaimed uploads;
     * a post carries up to four photos or a single document.
     */
    void claim(UUID authorId, UUID postId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
        Map<UUID, PostAttachment> byId = new HashMap<>();
        attachments.findAllById(distinct).forEach(a -> byId.put(a.getId(), a));
        List<PostAttachment> ordered = new ArrayList<>();
        for (UUID id : distinct) {
            PostAttachment a = byId.get(id);
            if (a == null || !a.getOwnerId().equals(authorId) || a.getPostId() != null) {
                throw new BadRequestException("ATTACHMENT_INVALID", "One of the attachments is no longer available.");
            }
            ordered.add(a);
        }
        long images = ordered.stream().filter(a -> a.getKind() == Kind.IMAGE).count();
        long files = ordered.size() - images;
        if (files > 1 || (files == 1 && images > 0)) {
            throw new BadRequestException("ATTACHMENT_MIX", "Attach either photos or a single document to a post.");
        }
        if (images > MAX_IMAGES_PER_POST) {
            throw new BadRequestException("TOO_MANY_ATTACHMENTS", "A post can carry at most " + MAX_IMAGES_PER_POST + " photos.");
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).claim(postId, i);
        }
    }

    /** Claimed attachments per post, in order, with backend URLs (member or moderator path). */
    @Transactional(readOnly = true)
    public Map<UUID, List<AttachmentView>> forPosts(Collection<UUID> postIds, boolean moderatorUrls) {
        Map<UUID, List<AttachmentView>> result = new HashMap<>();
        if (postIds.isEmpty()) {
            return result;
        }
        for (PostAttachment a : attachments.findForPosts(postIds)) {
            String base = moderatorUrls ? "/api/v1/moderation/posts/" : "/api/v1/posts/";
            result.computeIfAbsent(a.getPostId(), k -> new ArrayList<>())
                    .add(view(a, base + a.getPostId() + "/attachments/" + a.getId()));
        }
        return result;
    }

    /** Bytes of a claimed attachment; the caller has already checked that the viewer may see the post. */
    @Transactional(readOnly = true)
    public AttachmentContent load(UUID postId, UUID attachmentId) {
        PostAttachment a = attachments.findById(attachmentId).orElseThrow(PostAttachmentService::notFound);
        if (!postId.equals(a.getPostId())) {
            throw notFound();
        }
        try (StoredObject object = storage.get(bucket(), a.getStorageKey()).orElseThrow(PostAttachmentService::notFound);
                InputStream in = object.content()) {
            return new AttachmentContent(in.readAllBytes(), a.getContentType(), a.getKind() == Kind.IMAGE,
                    a.getOriginalFilename() != null ? a.getOriginalFilename()
                            : "attachment." + DocumentTypes.extensionFor(a.getContentType()));
        } catch (IOException e) {
            throw notFound();
        }
    }

    /** Deletes the objects and rows of a post the author deleted. */
    void deleteForPost(UUID postId) {
        attachments.findByPostIdOrderByPosition(postId).forEach(this::remove);
    }

    /** Hourly: uploads nobody claimed within a day are deleted with their objects. */
    @Scheduled(cron = "0 20 * * * *")
    public int purgeUnclaimed() {
        List<PostAttachment> stale = attachments.findUnclaimedBefore(Instant.now().minus(UNCLAIMED_TTL));
        stale.forEach(this::remove);
        if (!stale.isEmpty()) {
            log.info("Purged {} unclaimed post attachments", stale.size());
        }
        return stale.size();
    }

    private void remove(PostAttachment a) {
        try {
            storage.delete(bucket(), a.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("Could not delete attachment object {}: {}", a.getId(), e.getMessage());
        }
        attachments.delete(a);
    }

    private String bucket() {
        return storageProperties.buckets().media();
    }

    private static AttachmentView view(PostAttachment a) {
        return view(a, null);
    }

    private static AttachmentView view(PostAttachment a, String url) {
        return new AttachmentView(a.getId(), a.getKind().name(), a.getContentType(), a.getSizeBytes(),
                a.getOriginalFilename(), a.getWidth(), a.getHeight(), url);
    }

    /** Base name only, printable characters, at most 120 characters; {@code null} when nothing usable remains. */
    static String cleanFilename(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (name.length() > 120) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 8 ? name.substring(dot) : "";
            name = name.substring(0, 120 - ext.length()) + ext;
        }
        return name.isEmpty() ? null : name;
    }

    private static String replaceExtension(String filename, String extension) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename) + "." + extension;
    }

    static NotFoundException notFound() {
        return new NotFoundException("ATTACHMENT_NOT_FOUND", "Attachment not found.");
    }

    /** Attachment bytes with what the controller needs to serve them safely. */
    public record AttachmentContent(byte[] bytes, String contentType, boolean image, String filename) {
    }

    public static class ScanUnavailableException extends ApiException {
        public ScanUnavailableException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "SCAN_UNAVAILABLE",
                    "The malware scanner is unavailable. Please try again later.");
        }
    }
}
