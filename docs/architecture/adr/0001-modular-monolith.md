# ADR-0001: Modular monolith

Status: Accepted (30 Oct 2026)

## Context
A small team must deliver fourteen product modules with strong security requirements on a single laptop and later a
single VPS. Microservices would multiply deployment, observability and authorization surfaces.

## Decision
One Spring Boot application with package-by-feature modules, each split into api/application/domain/infrastructure.
Modules communicate through application interfaces, DTOs and Spring application events. ArchUnit enforces the rules.
No Kubernetes, Kafka, Elasticsearch or service mesh.

## Consequences
Single deployable, single transaction boundary, simple local stack. If scale ever justifies it, messaging,
notifications or search can be extracted along the existing module seams.
