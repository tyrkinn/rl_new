# ADR-0001: Single Entry entity for Readables and References

**Status:** Accepted

## Context

Readables (`:article`, `:video`) and References (`:bookmark`, `:thread`, `:paper`) behave differently:
- Readables flow through Inbox → Archive after being read
- References live in Saved indefinitely and are never "consumed"

The question was whether to model these as separate XTDB entities or unify them under one.

## Decision

Use a single `article` entity for all Entry kinds. The `article/kind` attribute determines whether an Entry is a Readable or a Reference, and query functions (`inbox-kind?`, `saved-kind?`) handle routing at read time.

## Consequences

- Schema is simpler: one entity, one Meilisearch index, one Enrichment pipeline
- Adding a new kind requires only a new `kind` enum value and a predicate update — no new entity, migration, or index
- Trade-off: the entity has optional fields that only apply to specific kinds (e.g. `paper-authors` is meaningless for `:video`). Accepted as a reasonable cost given the benefits of a unified pipeline.

**The alternative** — separate entities per kind or per Readable/Reference split — was rejected because it would fragment the Enrichment worker, Meilisearch indexing, and Recommendation generation logic across multiple code paths.
