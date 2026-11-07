# Theo 2.0 — Milestone 1: Memory reliability

**Status:** First implementation slice delivered for CI/device validation  
**Client:** Native Kotlin + Jetpack Compose  
**Persistence:** Existing Room database, schema version unchanged

## Goal

Make Theo's existing local memory useful to manage and correct without replacing the current Memory screen, resetting the database, or adding a cloud dependency.

## Included in this slice

- Edit an existing memory's category, key, and value.
- Ask for confirmation before deleting a memory.
- Route Settings → Saved memories to the same Memory tab and Room data already used by the dock.
- When a new manual or conversational memory has the same category and key (case-insensitive and whitespace-trimmed), update the latest matching row instead of creating another copy.
- Preserve the matched row's ID and original creation timestamp when correcting its value.
- Record memory additions, corrections, edits, and deletions in local activity history.
- Add DAO tests for correcting an existing memory and retrieving a row by ID.

## Data safety

- No Room entity columns or table definitions change in this slice.
- The Room database version remains 1; no destructive migration or database recreation is introduced.
- Existing memories are not batch-deleted, renamed, or silently rewritten.
- Old seeded records are left untouched. Correcting a fact only happens when the user explicitly saves it or asks Theo to remember it.
- Existing duplicate records are not automatically purged. The upsert path updates the latest matching record, avoiding an unsafe bulk cleanup.

## Deferred work

- Export/import with validation and conflict preview.
- Pinned, disabled, and source/provenance metadata.
- User/project/agent/task memory scopes.
- Memory suggestions and automatic-save controls.
- Database schema export and explicit migration framework before any future schema expansion.
- Full-text indexing or embeddings; current search remains lightweight and local.

## Acceptance checklist

- [ ] testDebugUnitTest passes on the exact commit.
- [ ] assembleDebug succeeds on the exact commit.
- [ ] A saved memory can be edited and the changed value appears after leaving and reopening the tab.
- [ ] Delete requires an explicit confirmation.
- [ ] Saving the same category/key corrects the existing row rather than increasing the memory count.
- [ ] Settings → Manage opens the same Memory screen as the bottom dock.
- [ ] Existing chat, Gemini, local Qwen, speech, accessibility automation, and screen memory remain intact.
- [ ] Verify on the target Android device before calling the slice complete.
