package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.exception.ApiException;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.media.ImageSanitizer;
import edu.diu.cyberconnect.common.scanning.MalwareScanner;
import edu.diu.cyberconnect.common.scanning.ScanResult;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.common.storage.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Profile photos follow the same intake rules as documents (magic bytes, re-encode, scan) but are stored
 * unencrypted in the private media bucket because they are served to every member who may see the profile.
 */
@Component
public class ProfilePhotoService {

    public static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;
    public static final int PHOTO_SIZE = 512;
    public static final String CONTENT_TYPE = "image/jpeg";

    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final MalwareScanner scanner;

    public ProfilePhotoService(ObjectStorage storage, StorageProperties storageProperties, MalwareScanner scanner) {
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.scanner = scanner;
    }

    /** Returns the opaque storage key of the new photo. */
    public String store(UUID userId, byte[] original) {
        if (original == null || original.length == 0) {
            throw new BadRequestException("FILE_EMPTY", "The uploaded file is empty.");
        }
        if (original.length > MAX_PHOTO_BYTES) {
            throw new BadRequestException("UPLOAD_TOO_LARGE", "Profile photos may be at most 2 MiB.");
        }
        if (ImageSanitizer.detectImageType(original) == null) {
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "Only JPEG and PNG photos are accepted.");
        }
        byte[] jpeg = ImageSanitizer.toSquareJpeg(original, PHOTO_SIZE);
        ScanResult scan = scanner.scan(new ByteArrayInputStream(jpeg), jpeg.length);
        if (scan.status() == ScanResult.Status.INFECTED) {
            throw new BadRequestException("PHOTO_REJECTED_MALWARE", "The file was rejected by the malware scanner.");
        }
        if (scan.status() == ScanResult.Status.ERROR) {
            throw new ScanUnavailableException();
        }
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + ".jpg";
        storage.put(bucket(), key, new ByteArrayInputStream(jpeg), jpeg.length, CONTENT_TYPE, Map.of());
        return key;
    }

    public void delete(String key) {
        if (key != null) {
            storage.delete(bucket(), key);
        }
    }

    public Optional<ProfilePhoto> load(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Optional<StoredObject> object = storage.get(bucket(), key);
        if (object.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = object.get().content()) {
            return Optional.of(new ProfilePhoto(in.readAllBytes(), CONTENT_TYPE));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String bucket() {
        return storageProperties.buckets().media();
    }

    public static class ScanUnavailableException extends ApiException {
        public ScanUnavailableException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "SCAN_UNAVAILABLE",
                    "The malware scanner is unavailable. Please try again later.");
        }
    }
}
