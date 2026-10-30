# ADR-0007: Identity-document encryption and private access

Status: Accepted (30 Oct 2026)

## Context
Identity documents are the most sensitive data in the system. Storage compromise, insider misuse and caching must all
be considered.

## Decision
Upload pipeline: backend-only upload → size limit → magic-byte allowlist (JPEG, PNG, PDF) → image re-encode with
EXIF stripped / PDF structural checks → malware scan (ClamAV; NoOp refused in production) → AES-256-GCM encryption
(`AesGcmEncryptor`, random 12-byte IV, document id as associated data, key id stored for rotation) → private bucket
under a random opaque key. Viewing: authenticated streaming endpoint only, `DOCUMENT_VIEW` authority, step-up MFA,
`Cache-Control: no-store`, inline disposition, `document_access_log` row per view. Retention purge after the
configured window; the feature flag `verification.real_documents_enabled` gates real collection.

## Consequences
The storage provider never sees plaintext. Key rotation requires re-encryption, tracked by `encryption_key_id`.
