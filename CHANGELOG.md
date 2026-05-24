# Changelog

## [Unreleased] — 2026-05-25

### Saved page
- Added fuzzy search via Meilisearch with 300ms debounce
- Added filter chips: All / Bookmarks / Threads / Papers
- `kind` field added to Meilisearch index and `filterableAttributes`
- New `saved-hits` function returns full XTDB docs matching filter + query

### Copy link button
- Added copy-link icon button on all inbox/saved cards (article, video, bookmark, thread, paper)
- Also available in `week-row` (inbox list rows) alongside mark-read and external-link buttons
- Uses `navigator.clipboard.writeText` with a success toast

### Discover section redesign (Today page)
- Replaced horizontal scroll row with responsive CSS grid (1 col mobile → 2 col tablet → 3 col desktop)
- Each card has a source-colored left border and tinted header (HN orange, Lobsters red, GitHub dark, YouTube red)
- Item rows are compact with hover-reveal "+" add-to-inbox button
- On touch devices (`@media (hover:none)`) the add button is always visible
- Focus-within also reveals the add button (keyboard navigation)
- Dark mode: correct hover/focus row background (`rgba(255,255,255,0.05)`) instead of light `stone-50`
- Dark mode: `disc-add` button uses dark background with green accent on hover/focus

### Article page — read/unread toggle
- "Mark read" button now shows "Move to unread" (`rotate-ccw` icon) when article status is already `:read`
- "Move to unread" reloads the page after reverting; "Mark read" redirects to `/inbox` as before
- New `POST /api/articles/:id/unread` endpoint sets `status: :ready`, clears `read-at`
- Both `mark-read` and `mark-unread` now re-index the article in Meilisearch so status is immediately searchable

### Archive page fix
- Archive query now filters by `article/status = :read` instead of presence of `read-at`
- Articles moved back to unread no longer appear in archive
