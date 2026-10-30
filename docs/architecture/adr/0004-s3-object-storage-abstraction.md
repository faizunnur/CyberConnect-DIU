# ADR-0004: S3-compatible object storage abstraction (RustFS locally)

Status: Accepted (30 Oct 2026)

## Context
The proposal specified MinIO. MinIO stopped publishing community images in October 2025 and archived the project in
April 2026; no `minio/minio` tag can be pulled. The platform must keep identity documents in private object storage
and remain portable to institution-hosted or cloud storage.

## Decision
The backend depends only on `common.storage.ObjectStorage`, implemented with the AWS SDK for Java v2 S3 client
(path-style, checksum calculation `WHEN_REQUIRED` for third-party compatibility). Local development and tests use
`rustfs/rustfs:1.0.4` (Apache-2.0, S3 API). Garage, SeaweedFS, AWS S3 or any compatible service can replace it by
changing endpoint and credentials. No presigned or public URLs are ever produced; content is streamed through the
backend after authorization. Buckets are auto-created only in local/docker/test profiles.

## Consequences
Integration tests run against the real RustFS image (put/get/head/delete, metadata, multipart, private bucket,
encrypted round trip). Production bucket provisioning is an operator step documented in M8.
