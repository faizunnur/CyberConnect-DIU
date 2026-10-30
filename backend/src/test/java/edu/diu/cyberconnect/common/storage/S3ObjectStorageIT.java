package edu.diu.cyberconnect.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.crypto.AesGcmEncryptor;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/** Verifies the S3 abstraction against the real RustFS image, including encryption and private-bucket behaviour. */
class S3ObjectStorageIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private StorageProperties properties;

    @Autowired
    private AesGcmEncryptor encryptor;

    @Autowired
    private S3Client s3;

    @Test
    void putGetHeadDeleteRoundTrip() throws Exception {
        String bucket = properties.buckets().documents();
        String key = "it/" + UUID.randomUUID();
        byte[] payload = "fictional document bytes".getBytes(StandardCharsets.UTF_8);

        storage.put(bucket, key, new ByteArrayInputStream(payload), payload.length, "text/plain",
                Map.of("purpose", "integration-test"));

        assertThat(storage.exists(bucket, key)).isTrue();
        ObjectInfo info = storage.head(bucket, key).orElseThrow();
        assertThat(info.size()).isEqualTo(payload.length);
        assertThat(info.contentType()).startsWith("text/plain");
        assertThat(info.metadata()).containsEntry("purpose", "integration-test");

        try (StoredObject object = storage.get(bucket, key).orElseThrow()) {
            assertThat(object.content().readAllBytes()).isEqualTo(payload);
        }

        storage.delete(bucket, key);
        assertThat(storage.exists(bucket, key)).isFalse();
        assertThat(storage.get(bucket, key)).isEmpty();
    }

    @Test
    void encryptedContentSurvivesStorageRoundTrip() throws Exception {
        String bucket = properties.buckets().documents();
        String key = "it/enc-" + UUID.randomUUID();
        byte[] plaintext = new byte[64 * 1024];
        new SecureRandom().nextBytes(plaintext);
        byte[] aad = key.getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = encryptor.encrypt(plaintext, aad);
        storage.put(bucket, key, new ByteArrayInputStream(ciphertext), ciphertext.length,
                "application/octet-stream", Map.of("enc", encryptor.keyId()));

        byte[] stored;
        try (StoredObject object = storage.get(bucket, key).orElseThrow()) {
            stored = object.content().readAllBytes();
        }
        assertThat(stored).isNotEqualTo(plaintext);
        assertThat(encryptor.decrypt(stored, aad)).isEqualTo(plaintext);
        storage.delete(bucket, key);
    }

    @Test
    void bucketsAreNotPubliclyReadable() throws Exception {
        String bucket = properties.buckets().documents();
        String key = "it/private-" + UUID.randomUUID();
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        storage.put(bucket, key, new ByteArrayInputStream(payload), payload.length, "text/plain", Map.of());

        HttpResponse<String> anonymous = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(storageEndpoint() + "/" + bucket + "/" + key)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isIn(401, 403);
        assertThat(anonymous.body()).doesNotContain("secret");

        storage.delete(bucket, key);
    }

    @Test
    void multipartUploadIsSupported() throws Exception {
        String bucket = properties.buckets().media();
        String key = "it/multipart-" + UUID.randomUUID();
        byte[] part1 = new byte[5 * 1024 * 1024];
        byte[] part2 = new byte[1024 * 1024];
        new SecureRandom().nextBytes(part1);
        new SecureRandom().nextBytes(part2);

        String uploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(key).contentType("application/octet-stream").build()).uploadId();
        String etag1 = s3.uploadPart(UploadPartRequest.builder().bucket(bucket).key(key).uploadId(uploadId)
                .partNumber(1).contentLength((long) part1.length).build(), RequestBody.fromBytes(part1)).eTag();
        String etag2 = s3.uploadPart(UploadPartRequest.builder().bucket(bucket).key(key).uploadId(uploadId)
                .partNumber(2).contentLength((long) part2.length).build(), RequestBody.fromBytes(part2)).eTag();
        s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(List.of(
                        CompletedPart.builder().partNumber(1).eTag(etag1).build(),
                        CompletedPart.builder().partNumber(2).eTag(etag2).build())).build())
                .build());

        ObjectInfo info = storage.head(bucket, key).orElseThrow();
        assertThat(info.size()).isEqualTo(part1.length + part2.length);
        try (StoredObject object = storage.get(bucket, key).orElseThrow()) {
            byte[] all = object.content().readAllBytes();
            assertThat(all.length).isEqualTo(part1.length + part2.length);
            assertThat(java.util.Arrays.copyOfRange(all, 0, 1024)).isEqualTo(java.util.Arrays.copyOfRange(part1, 0, 1024));
        }
        storage.delete(bucket, key);
    }
}
