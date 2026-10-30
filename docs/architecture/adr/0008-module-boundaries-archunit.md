# ADR-0008: Module boundaries enforced with ArchUnit

Status: Accepted (30 Oct 2026)

## Context
Modular monoliths erode when a convenient import crosses a module boundary. Reviews alone do not catch this.

## Decision
`ModuleBoundariesTest` (ArchUnit core API, plain JUnit 5) fails the build when: a module's `domain` or
`infrastructure` package is depended on from outside the module; an `api` package is used by anything but itself;
an `api` class depends on `infrastructure`; `common` depends on a feature module; or a controller depends on a JPA
entity. Cross-module data flows through `application` interfaces, DTOs, UUID references and Spring events.
Spring Modulith was considered and rejected for M1 to avoid an extra framework; ArchUnit covers the need.

## Consequences
Adding a module means adding its name to the test's module list. Shared security primitives (for example the
authenticated principal) live in `common.security`, not in `identity.domain`.
