package edu.diu.cyberconnect.common.storage;

import edu.diu.cyberconnect.common.config.StorageProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Creates the private buckets on start-up. Enabled in local/docker/test profiles only; production buckets are
 * provisioned by the operator so the runtime credentials never need bucket-creation rights.
 */
@Component
@ConditionalOnProperty(name = "storage.ensure-buckets", havingValue = "true")
public class ObjectStorageInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageInitializer.class);

    private final S3Client s3;
    private final StorageProperties properties;

    public ObjectStorageInitializer(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String bucket : List.of(properties.buckets().documents(), properties.buckets().media())) {
            ensureBucket(bucket);
        }
    }

    private void ensureBucket(String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created private storage bucket '{}'", bucket);
        }
    }
}
