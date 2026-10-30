# ADR-0005: PostgreSQL as the primary database

Status: Accepted (30 Oct 2026)

## Context
Relational integrity (foreign keys, unique and check constraints, partial unique indexes) is central to the
verification, connection and impact rules. Search needs are modest.

## Decision
PostgreSQL 17 with UUID keys, `timestamptz` UTC, text + CHECK enumerations, Flyway-versioned migrations and
`ddl-auto=validate`. Full-text search via `tsvector` generated columns + GIN and `pg_trgm` for fuzzy matching; no
Elasticsearch. Two roles: `cyberconnect_migrator` owns the schema; `cyberconnect_app` has least privilege and cannot
UPDATE/DELETE append-only audit tables.

## Consequences
Every append-only table must explicitly REVOKE UPDATE/DELETE from `${app_role}` in its migration. Tests verify the
restriction against a real PostgreSQL container.
