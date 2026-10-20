# Legacy Relven Data Migration Design

**Status:** Migration direction approved by the project owner on 20 October 2026; detailed spec awaits review.

## Goal

Let an existing Relven user upgrade to Relven 2.0 with the required application ID `se.hampusandersson.relven`, recover supported running workouts into the new local workout library, and retain every other readable old record in an exportable archive without modifying the old database.

## Context

The old phone app stores Room data in `relven-workouts.db` (latest checked schema version 18). Its `cached_workout` key includes server origin, owner, workout ID, and revision. Other tables hold cached plans, completed activities, active-session snapshots, schedule edits, and pending sync operations. Relven 2.0 uses RunnerUp's `runnerup.db` and file based workout definitions; copying Room rows into the new store would not preserve their meaning.

Relven 2.0 already captures a read-only, integrity-checked JSON table archive on startup when the old database is present. The archive is written to a pending file and renamed only after completion, and the Maintenance settings can export it. The current `RelvenWorkoutImporter` accepts only a bounded executable subset of Relven v3 running workout documents. There is no validated importer for old activities, active sessions, pending sync operations, or legacy schedule edits.

## User flow

1. On the first Relven 2.0 launch, keep the existing automatic archive capture. It must finish before any migration reads the old database, and it must not open the old database for writes.
2. In Maintenance settings, expose a legacy-data review action when an old database or archive is available. Show counts of running workouts that can be imported, unsupported workout documents, plans and scheduled changes, activities, active-session data, and pending operations.
3. Let the user review a deterministic preview. Explain that importing a workout makes a local executable copy, does not activate a plan or create calendar entries, and that non-importable records remain in the exportable archive.
4. Require an explicit Import action. Cancellation leaves the new workout library unchanged.
5. After import, show imported, already imported, unsupported, and failed counts, plus an action to export the complete archive.

If capture or archive validation fails, offer a retry and keep migration disabled until a valid archive exists. The app must remain usable for new workouts; the failure must be visible and must not silently report migration success.

## Import rules

- Read source rows only from the validated archive and use the existing `RelvenWorkoutImporter` for each `cached_workout.documentJson`. Only records accepted by its current schema and semantic checks are candidates.
- Preserve the complete source identity (`serverOrigin`, `ownerUserId`, `workoutId`, `revision`) in a private migration manifest associated with each generated workout file. The original document remains in the archive.
- Use a deterministic destination filename derived from that complete identity. Re-running migration must recognize records already imported and must not overwrite a user-created workout or a changed imported file. A conflicting destination is reported for review rather than replaced.
- Prepare and validate the entire selected batch before changing the workout library. A failed parse or write must not leave an unreported partial migration. Persist enough per-record progress to safely resume after process death.
- Do not replay old server operations or credentials. Do not activate, reschedule, or edit a plan as a side effect of importing its workout definitions.
- Retain legacy plans, activity history, active-session snapshots, plan move/skip history, and pending operations in the archive and report their counts. They are not represented as migrated into the new app until a dedicated importer and duplicate policy exist.
- Never delete or rewrite `relven-workouts.db` or its archive as part of migration.

## Error handling and privacy

Archive corruption, unsupported records, duplicate identities, filename conflicts, and storage failures are reported with category and record identity where safe. A failure must not expose workout contents or account credentials in logs. The archive remains in app-private storage and is exported only to a destination the user selects. All work is local and requires no network service.

## Verification

- Unit tests use synthetic schema-v18-like fixtures and supported/unsupported v3 workout documents; no production backup or credential is checked into the repository.
- Verify source archive round trips for text, null, numeric, and blob cells; source database version and rows remain unchanged.
- Verify preview counts, importer rejection, stable identity, duplicate detection, cancellation, successful import, retry after interruption, storage failure, and conflict handling.
- Run phone debug build, unit tests, lint, Java formatting, and the repository CI workflow. Do not use the owner's physical device or ADB during implementation.
- After every product phase is complete and the app is ready for manual testing, verify an upgrade using the existing upload key and a test copy of old app data. Do not upload to Play as part of this feature.

## Explicit limits

This milestone migrates only running workout definitions that pass the existing importer. It preserves but does not yet convert legacy activities, training plans, calendar state, or pending sync operations. Those records remain recoverable in the archive and must not be described in the UI or release notes as imported. Activity migration, plan activation/scheduling migration, and a Play upload remain separate work.
