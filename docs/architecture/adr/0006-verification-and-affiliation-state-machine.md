# ADR-0006: Verification and affiliation state machine

Status: Accepted (30 Oct 2026)

## Context
Affiliation (why someone is eligible), role (what they may administer) and account status (lifecycle) were at risk of
being conflated. The proposal left resubmission, appeals, suspension lifting and deactivation undefined.

## Decision
- `AccountStatus` transitions are explicit (`AccountStatus.canTransitionTo`): PENDING→VERIFIED|REJECTED|DEACTIVATED,
  REJECTED→PENDING|VERIFIED|DEACTIVATED, VERIFIED→SUSPENDED|DEACTIVATED, SUSPENDED→VERIFIED|DEACTIVATED,
  DEACTIVATED→VERIFIED|PENDING (restore within the window).
- Each `user_affiliations` row carries its own status; a verification submission targets one affiliation.
- One active submission per affiliation (partial unique index); max 3 submissions; one appeal per rejected submission;
  reason codes carry `allows_resubmission`; denied appeal is terminal unless an ADMIN reopens (audited).
- Reviewer ≠ applicant; appeal reviewer ≠ rejecting reviewer (application-layer checks with integration tests).
- Every decision is an append-only review/appeal row plus an audit event.

## Consequences
Illegal transitions throw and are never persisted. The rules are unit-tested independently of the web layer.
