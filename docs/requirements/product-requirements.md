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

## Profiles and networking (business rules D-25..D-29, decided 30 Oct 2026)
- D-25 Privacy defaults: profile visible to all verified members; email hidden (NOBODY); organisation, location and
  mentor availability visible to all members; searchable; messaging policy CONNECTIONS_ONLY. Each setting is per field.
- D-26 A CONNECTIONS_ONLY profile still exposes name, headline, photo and verified affiliation types to any verified
  member so that a connection request can be made; everything else requires an accepted connection.
- D-27 Connections: one live row per member pair. A request while the other member's request is pending accepts it
  instead of creating a reversed duplicate. Declined requests are kept but allow a new request later; requests are
  rate-limited (30 per hour per member). Removing a connection or cancelling a request is silent.
- D-28 Blocking is one-directional but symmetric in effect: neither side can see the other's profile, photo or
  directory card, nor send requests; any connection or pending request between the two is deleted. The blocked member
  is not notified; lookups answer a uniform 404 `MEMBER_NOT_FOUND`. Non-verified accounts are never visible.
- D-29 The display name is editable and defaults to the registered legal name, which moderators keep seeing in the
  admin area. Profile photos are re-encoded to a 512 px square JPEG (metadata dropped), malware-scanned and served
  only through the backend; student ids and documents never appear on profiles.

## Community (business rules D-30..D-34, decided 30 Oct 2026)
- D-30 Content is plain text: control and zero-width characters are stripped, blank lines collapsed, markup is kept
  as inert text and never rendered as HTML. Posts ≤ 5000 and comments ≤ 2000 characters; up to 10 hashtags per post
  are extracted from `#tags` (Unicode letters, marks, digits, underscore) and lower-cased.
- D-31 Post visibility follows the author's choice per post (ALL_MEMBERS or CONNECTIONS_ONLY). Posts by suspended or
  deactivated members and posts between members who block each other are hidden in both directions. Only original,
  all-members posts can be reposted (one level, optional commentary); the original's counter follows the repost.
- D-32 Authors edit and soft-delete their own content only. Moderators (MODERATION_ACTION) remove and restore;
  the author sees the reason, everyone else sees nothing. A deleted or removed top-level comment stays as a placeholder
  while it has replies. Counters are maintained server-side and never accepted from clients.
- D-33 Reports: any member may report a post, comment or member once per open case; reports carry a text snapshot.
  Moderators resolve with content removal, no action, or dismissal; removing content settles every open report on the
  same item. Member reports route to member actions (warn, suspend). Reporters learn only that moderators will review.
- D-34 Abuse limits per member: 20 posts, 60 comments and 20 reports per hour (Redis).

## Opportunities, research and mentorship (business rules D-35..D-39, decided 30 Oct 2026)
- D-35 Opportunities are DRAFT until published; OPEN listings expire automatically after their deadline (nightly job,
  and the listing already disappears from search once the deadline passes); authors close/reopen; moderators remove
  and restore with an audited reason. Only INTERNAL listings take applications on the platform; EXTERNAL_URL and EMAIL
  listings show the link or address. Authors cannot apply to their own listing.
- D-36 One application per member and listing; withdrawing and re-applying is allowed while the listing is open.
  Authors see applicants and set REVIEWED/SHORTLISTED/ACCEPTED/REJECTED; applicants see their own status only.
  CV uploads are deferred (cover note only) until the document pipeline is extended for member files.
- D-37 Research projects have one lead (the creator). The lead accepts or declines applications, removes members and
  sets the status; members may leave; the lead cannot leave. Applications are only possible while the project is
  PROPOSED/ACTIVE and recruiting.
- D-38 Mentorship requests need the mentor to advertise availability on their profile (visible to the requester);
  one live request per mentee/mentor pair; the mentor accepts or declines, either party completes an accepted
  mentorship, the mentee cancels a pending one. Completed mentorships are the source for M7 impact records.
- D-39 Listings and projects by suspended/deactivated members, or between members who block each other, are hidden.
  Skills on listings and projects come from the shared catalogue (members may add new ones).

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
