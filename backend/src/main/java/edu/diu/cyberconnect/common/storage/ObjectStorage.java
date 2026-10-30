package edu.diu.cyberconnect.common.storage;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Port for private object storage. Implementations must never produce public or presigned URLs; callers stream
 * content through the backend after authorization.
 */
public interface ObjectStorage {

    void put(String bucket, String key, InputStream content, long contentLength, String contentType,
            Map<String, String> metadata);

    Optional<StoredObject> get(String bucket, String key);

    Optional<ObjectInfo> head(String bucket, String key);

    boolean exists(String bucket, String key);

    void delete(String bucket, String key);
}
