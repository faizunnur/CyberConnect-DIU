# Product requirements (condensed, authoritative for implementation)

Derived from the Project Proposal v1.0 (30 Oct 2026), the Initial Technical Assessment and the owner's master
instructions. Precedence: owner instructions → assessment decisions → proposal → engineering judgment.

## Members and access
- Eligible: DIU Cybersecurity students (STUDENT), DIU Cybersecurity alumni (ALUMNI), eligible CSC DIU members
  (CSC_MEMBER). One primary affiliation at registration; more can be requested later, each verified separately.
- Roles: MEMBER (default), MODERATOR, ADMIN, SUPER_ADMIN. Assigned only by authorised admins; never by registration.
- Account status: PENDING → VERIFIED | REJECTED; REJECTED → PENDING (allowed resubmission) | appeal → VERIFIED/REJECTED;
  VERIFIED → SUSPENDED → VERIFIED (audited lift); VERIFIED → DEACTIVATED → restore within 30 days.
  Denied appeal is terminal unless an ADMIN reopens (audited).
- Sign-in allowed before email verification; email verification required for document submission, appeals and community
  access. Non-VERIFIED accounts reach only their own onboarding/status/appeal endpoints (enforced server-side).

## Verification (business rules D-05..D-09, D-19, D-20, D-24)
- Evidence defaults (configurable in `system_settings`): STUDENT = student id, batch, department, student ID card;
  ALUMNI = student id, graduation year, alumni card/degree certificate/transcript; CSC_MEMBER = CSC member id,
  membership card or CSC letter. Student-id regex configurable, permissive default.
- Max 3 submissions per affiliation; one appeal per rejected submission; reason codes decide resubmission eligibility;
  applicant sees neutral text only; internal/fraud notes never exposed.
- Separation of duties: no self-review; appeal decided by a different reviewer than the rejection.
- Documents: JPEG/PNG/PDF, 8 MiB each, max 3 per submission; magic-byte validation, re-encode + EXIF strip, malware
  scan, AES-256-GCM, private bucket, backend streaming only, per-view log, step-up MFA, `Cache-Control: no-store`.
- Retention 30 days after final decision (configurable), scheduled purge keeps metadata + audit.
- Real-document collection disabled by feature flag until institutional approval is recorded.

## Modules
1. Identity & onboarding: register, login, logout, email verification, password reset, sessions, MFA (TOTP + recovery
   codes), rate limiting, session revocation.
2. Affiliations: per-affiliation status, additional affiliation requests.
3. Verification: submissions, documents, reviews, resubmission, appeals, reviewer queue and assignment.
4. Profiles: photo, name, headline, bio, skills, experience, education, organization, job title, location,
   specializations, mentor availability, privacy settings (profile/email/organization/country visibility,
   searchability, messaging policy, mentor-availability visibility). Student ids and documents never shown.
5. Directory: search + filters (affiliation, skills, experience, organization), pagination, privacy-aware.
6. Networking: connection requests, accept/decline/remove, no reversed duplicates, blocking.
7. Community: posts (intents JOB_OPPORTUNITY, RESEARCH_COLLABORATION, SEEKING_HELP, MENTORSHIP,
   TECHNICAL_DISCUSSION, ACHIEVEMENT, EVENT), edit/delete own, comments, one-level replies, likes, reposts with
   commentary, bookmarks, hashtags, reports, moderation. Plain text only; visibility column (ALL_MEMBERS now,
   CONNECTIONS_ONLY later).
8. Opportunities: FULL_TIME, PART_TIME, INTERNSHIP, RESEARCH_ASSISTANT, FREELANCE, SCHOLARSHIP; publish/edit/close/
   expire; INTERNAL, EXTERNAL_URL, EMAIL application methods. Mentorship is not an opportunity type.
9. Research hub: projects, needed skills, applications, members, status.
10. Mentorship: availability on profile; mentorship requests workflow.
11. Messaging: direct conversations, message requests, accept/reject, history, read status, blocking, reporting,
    authenticated WebSocket updates. Moderators never browse conversations; reports carry a snapshot only.
12. Notifications: in-app, email, preferences, real-time.
13. Impact: either party proposes; counterpart confirms/disputes; must reference an on-platform opportunity, research
    project or mentorship request; disputes resolved by moderation; only CONFIRMED counts; counters never editable.
14. Moderation & admin: verification/appeal queues, reports, member actions (suspend/unsuspend/deactivate), role
    management (last SUPER_ADMIN protected), system settings, audit history (append-only).

## Non-functional
Server-side authorization everywhere; UUIDs are not authorization; authorization test matrix for PENDING, VERIFIED,
REJECTED, SUSPENDED, DEACTIVATED across every protected endpoint; IDOR, ownership, conversation membership, profile
visibility, document permissions, mass assignment, session revocation, CSRF and WebSocket tests.
WCAG 2.2 AA target; responsive (desktop three-column, mobile single column with bottom navigation); light default + dark.
Portable to a VPS: no localhost assumptions or dev secrets in production configuration.

## UI direction
Palette navy #142B45, blue #2563EB, background #F8FAFC, white #FFFFFF, success #16A34A, warning #D97706,
error #DC2626, secondary text #475569. Calm, professional, minimal; no hacker aesthetics or heavy gradients.
Use DIU and CSC DIU logos (permission required before public launch).
