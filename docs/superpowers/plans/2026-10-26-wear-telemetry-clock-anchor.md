# Wear Telemetry Clock Anchor Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Place delayed watch telemetry into the phone route using a validated same-boot clock correction.

**Architecture:** Extend the existing bounded telemetry batch with an optional immutable clock anchor. Persist that anchor alongside raw sample bytes in the existing phone inbox and apply the correction only during idempotent route materialization. Keep the session protocol, Tracker, activity IDs, and route coverage policy unchanged.

**Tech Stack:** Java protocol in `common`, Wear `StateService`, Kotlin phone telemetry inbox and route reconciler, existing JUnit/Robolectric suites.

**Spec:** `docs/superpowers/specs/2026-10-26-wear-telemetry-clock-anchor-design.md`.

## Global Constraints

- Preserve application ID `se.hampusandersson.relven`.
- Keep one Relven PR at a time; merge it before starting the next.
- Do not use physical phone/watch ADB.
- Decode existing telemetry protocol v1 and preserve previously stored inbox samples.
- Do not create or modify workout/session state machines or authoritative activity totals.
- Apply clock correction only with a validated same-boot anchor and round-trip time at most 2,000 ms.

## Review Focus

- A protocol v1 batch remains readable after the v2 rollout.
- A retry with identical samples but a different clock anchor is idempotent.
- A rebooted watch or changed wall-time origin does not apply an unrelated correction.
- Missing anchors preserve the current reconciliation fallback.
- Uncertain timestamps do not fabricate elapsed time, distance, or pace.

---

### Task 1: Versioned clock-anchor wire contract

**Files:**
- Modify `common/src/main/java/org/runnerup/common/wear/WorkoutTelemetryProtocol.java`.
- Modify `common/src/test/java/org/runnerup/common/wear/WorkoutTelemetryProtocolTest.java`.

- [x] Add failing tests for v1 compatibility, v2 anchor round-trip, invalid anchor bounds, and unchanged sample equality.
- [x] Run the protocol test and verify the failures identify missing v2 support.
- [x] Add optional immutable `ClockAnchor` metadata to `Batch`; encode v2 and decode both v1 and v2.
- [x] Run protocol tests and confirm v1/v2 cases pass.

### Task 2: Attach fresh anchors and retain them through phone inbox retries

**Files:**
- Modify `wear/src/main/java/org/runnerup/service/StateService.java`.
- Modify `app/src/main/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryInboxStore.kt`.
- Modify `app/test/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryInboxStoreTest.kt`.

- [x] Add failing tests showing identical samples with changed anchor metadata remain a duplicate.
- [x] Attach only a fresh existing Wear clock estimate to outgoing batches; wait one bounded sync attempt before falling back without an anchor.
- [x] Store the original sample and its first committed anchor; compare duplicate samples without comparing anchors.
- [x] Verify old stored v1 sample rows and duplicate replay remain readable.

### Task 3: Apply bounded correction during route materialization

**Files:**
- Modify `app/src/main/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryRouteReconciler.kt`.
- Modify `app/test/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryRouteReconcilerTest.kt`.

- [x] Add failing cases for same-boot correction, no anchor, reboot-origin mismatch, and excessive RTT.
- [x] Normalize only the timestamp supplied to the existing route coverage/reconciliation policy; preserve raw inbox samples.
- [x] Verify the corrected point is chronologically placed and an invalid anchor follows the current fallback.

### Task 4: Full verification and serial PR

- [x] Run common, app, and Wear unit tests; relevant lint; both debug APK builds; Spotless; and `git diff --check`.
- [x] Review protocol bounds, legacy compatibility, data ownership, and migration behavior.
- [ ] Open one PR, wait for hosted CI, and merge before any next PR.
- [ ] Fast-forward master and rebuild both debug APKs from the merged commit.

**Local verification (2026-10-26):** `BUILD SUCCESSFUL`; common 123 tests, app 753 tests, Wear 90 tests (all passed); app/Wear lint passed; phone and Wear debug APKs assembled; Spotless and `git diff --check` passed. Hosted CI and merge remain pending.

## Verification commands

`.\gradlew.bat :common:testDebugUnitTest :app:testLatestDebugUnitTest :wear:testDebugUnitTest :app:lintLatestDebug :wear:lintDebug :app:assembleLatestDebug :wear:assembleDebug spotlessCheck`
