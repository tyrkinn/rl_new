# ReadLater — Domain Glossary

## Entry

The top-level entity in the system. Represents a single URL that has been added to the library, along with all metadata extracted or inferred about it. Stored as `article` in XTDB (the attribute namespace) and in Meilisearch.

**Do not say:** item, link, article (when referring to the container entity — `:article` is reserved for the kind value)

---

## Readable

An Entry whose primary purpose is to be read and then moved to the archive. Kinds: `:article`, `:video`. Lives in the Inbox until marked read, then moves to the Archive.

**Do not say:** article (when meaning the broader category), content

## Reference

An Entry saved as a permanent resource — not consumed and archived, but kept as an ongoing resource. Kinds: `:bookmark`, `:thread`, `:paper`. Lives in the Saved section indefinitely.

**Do not say:** bookmark (when meaning the category — `:bookmark` is a specific kind within Reference)

---

## Enrichment

The automated process that runs once after an Entry is added. Claude fetches the URL and extracts structured metadata: title, byline, TLDR, tags, topic, why-interesting, reading time, quality score, keywords, synonyms. Result: Entry moves from `status=:queued` → `status=:ready`.

**Do not say:** processing, indexing, analysis (when meaning this specific first-pass operation)

## Summary

An on-demand, deeper Claude operation that generates a full long-form summary of an Entry. Triggered manually by the user. Separate from Enrichment — runs after the Entry is already `:ready`.

## Synonym Aggregation

A weekly batch operation that scans the entire library, extracts all keywords/tags/synonyms across all Entries, and uses Claude to build a domain-specific synonym dictionary pushed to Meilisearch. Improves search quality over time.

**Do not say:** enrichment, synonym generation (when meaning the weekly batch — that's Synonym Aggregation)

---

## Recommendation

A curated collection of Entries from the user's own library, grouped by theme. Generated daily by Claude from Readables with `status=:ready`. Shown on the Today page. A Recommendation has a title, description, vibe, and 3–5 Entries with micro-blurbs.

**Do not say:** collection (when meaning a Recommendation — "collection" is ambiguous), suggestion

## Discovery

Externally-sourced content surfaced on the Today page from outside the user's library. Sources include HN, Lobsters, GitHub Trending, YouTube. Shown alongside Recommendations. Purpose: find new content to add to the library.

**Do not say:** external collection, recommendation (when meaning Discovery — these are distinct concepts)

---

## Entry Lifecycle

The sequence of statuses an Entry passes through:

```
queued → enriching → ready → read
                   ↘ failed
                   ↘ paywall
                   ↘ notfound
                   ↘ login-required
```

- **queued** — added, waiting for Enrichment
- **enriching** — Enrichment in progress
- **ready** — Enrichment complete, visible in Inbox or Saved
- **read** — Readable has been read; moves to Archive. Equivalent to "archived" in user-facing language.
- **failed / paywall / notfound / login-required** — terminal error states; visible in Queue

Note: `:archived` exists in the schema enum but is unused. `:read` is the canonical terminal state for consumed Readables.

**Do not say:** archived (as a status — say "read" or "in the Archive")

---

## Comment

A free-form timestamped note added by the user to an Entry. Not tied to any specific position in the text. Stored as an embedded vector on the Entry (`article/comments`). Shown in the sidebar of the article page.

**Do not say:** note, annotation (when meaning Comment)

## Highlight

A user-selected text fragment from an Entry's content, with an optional note. Tied to a specific character position (`char-start`, `char-end`). Stored as a separate `highlight` entity referencing the Entry. Shown in the sidebar alongside Comments.

**Do not say:** comment, annotation (when meaning Highlight)

---

## Folder

A user-created organizational container with a name and color. Entries are manually assigned to a Folder by the user. One Entry can belong to at most one Folder.

**Do not say:** category, collection, tag (when meaning Folder)

## Tag

A short keyword extracted automatically by Claude during Enrichment. Tags are read-only — the user cannot add, edit, or delete them. An Entry has 3–7 tags. Used for browsing (Tags page) and search.

**Do not say:** label, category (when meaning Tag — Tags are Claude-generated and immutable)

---

## Feed

An RSS or Atom source configured by the user. Polled automatically on a schedule. Each new URL found in a Feed is added as an Entry via the standard capture flow (status=`:queued` → Enrichment). The Feed's origin is recorded as `article/source`.

**Do not say:** RSS source, subscription (when meaning Feed)

## Capture

The act of adding a new Entry to the library. All capture paths — CLI, bookmarklet, Quick Action, Telegram bot, Feed poll — funnel through a single entry point and produce an Entry with `status=:queued`. The capture source is recorded but does not affect the Entry lifecycle.

**Do not say:** import, save, add (when meaning the broader Capture concept)

---

## Recommendation CTR

A metric measuring what percentage of Entries in a Recommendation batch were read within 7 days of the batch being generated. Used to evaluate the quality of the Recommendation algorithm over time.

Formula: `(count of Entries from batch with status=:read and read-at ≤ generated-at + 7 days) / (count of Entries in batch)`

---
