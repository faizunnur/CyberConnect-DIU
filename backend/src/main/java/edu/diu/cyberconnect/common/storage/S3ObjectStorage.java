package edu.diu.cyberconnect.common.storage;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 API implementation of {@link ObjectStorage}; works with RustFS, AWS S3, Garage and other compatible stores. */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;

    public S3ObjectStorage(S3Client s3) {
        this.s3 = s3;
    }

    @Override
    public void put(String bucket, String key, InputStream content, long contentLength, String contentType,
            Map<String, String> metadata) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .metadata(metadata == null ? Map.of() : metadata)
                .build();
        s3.putObject(request, RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public Optional<StoredObject> get(String bucket, String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            GetObjectResponse response = stream.response();
            ObjectInfo info = new ObjectInfo(key, response.contentLength(), response.contentType(),
                    response.metadata(), response.lastModified());
            return Optional.of(new StoredObject(info, stream));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ObjectInfo> head(String bucket, String key) {
        try {
            HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new ObjectInfo(key, response.contentLength(), response.contentType(),
                    response.metadata(), response.lastModified()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        return head(bucket, key).isPresent();
    }

    @Override
    public void delete(String bucket, String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
