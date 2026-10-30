# Identity-document handling (as implemented in M2)

| Stage | Implementation | Where |
|---|---|---|
| Upload | multipart to `POST /api/v1/verification/submissions/{id}/documents`; only the draft's owner; `verification.real_documents_enabled` gates production uploads | `VerificationController`, `SubmissionService.addDocument` |
| Size | `verification.documents.max_file_bytes` (8 MiB default) checked before any processing; Spring multipart limit 10 MB | `DocumentPipeline.process` |
| Type | magic bytes only (JPEG `FF D8 FF`, PNG signature, `%PDF-`); declared content type and filename are ignored | `DocumentPipeline.detectType` |
| Images | decoded with a dimension/pixel bomb guard (8000 px, 40 MP) and re-encoded (JPEG q=0.9 / PNG), which discards EXIF/GPS/ICC | `DocumentPipeline.reencodeImage` |
| PDFs | rejected if they contain `/JavaScript`, `/JS`, `/Launch`, `/EmbeddedFile`, `/RichMedia`, `/XFA` or lack `%%EOF` | `DocumentPipeline.checkPdf` |
| Malware | `MalwareScanner` port; ClamAV INSTREAM adapter; `INFECTED` → 400 `DOCUMENT_REJECTED_MALWARE`, `ERROR` → 503 `SCAN_UNAVAILABLE` (fail closed); NoOp scanner refuses to start in prod | `ClamAvMalwareScanner`, `ScannerProductionGuard` |
| Encryption | AES-256-GCM, random 96-bit IV, AAD = document id, key id stored for rotation | `AesGcmEncryptor` |
| Storage | private bucket, opaque key `documents/{submissionId}/{documentId}`, content type `application/octet-stream`; no presigned or public URLs anywhere | `S3ObjectStorage` |
| Metadata | `verification_documents` row: sha256 of the sanitised bytes, size, scan engine, key id; never the original bytes or a URL | `VerificationDocument` |
| Viewing | `GET /api/v1/moderation/verifications/{id}/documents/{docId}/content`: `DOCUMENT_VIEW` authority + fresh step-up MFA; decrypted in memory and streamed inline with `Cache-Control: no-store`, `X-Frame-Options: SAMEORIGIN`, `frame-ancestors 'self'`; one `document_access_log` row + audit event per view | `ModerationVerificationController.document`, `DocumentAccessService` |
| Applicant access | applicants never receive document content, even their own; they only see metadata | `VerificationViews.DocumentView` |
| Retention | `RetentionPurgeJob` daily at 03:30: documents of submissions whose final decision (or decided appeal) is older than `verification.documents.retention_days` are deleted from storage; metadata keeps `purged_at`; audit `VERIFICATION_DOCUMENT_PURGED` | `RetentionPurgeJob` |
| Audit | upload, rejection by pipeline, removal, submission, assignment, approval, rejection, appeal events, document views, purges | `VerificationAuditActions` |

Tests: `DocumentPipelineTest` (magic bytes, re-encode, PDF active content, infected/unscanned never stored),
`S3ObjectStorageIT` (private bucket, encrypted round trip, multipart), `VerificationFlowIT` (end-to-end including
document streaming headers, access log and purge), `ClamAvMalwareScannerTest` (protocol), `ClamAvEicarIT`
(opt-in with `CLAMAV_IT=true`, real engine + EICAR string).

Open items for M8: watermarking the streamed image with the reviewer id, and key rotation tooling.

## Test fixtures (development only)
`POST /api/v1/dev/fixtures/verified-member` creates already-verified members for Playwright. The controller and
service exist only outside the `prod` profile and only when `E2E_FIXTURES_ENABLED=true`; `E2eFixturesGuard` refuses to
start a production build with the flag set. The endpoint never touches documents or real accounts.
