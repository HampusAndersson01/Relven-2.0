# Wear Telemetry Clock Anchor Design

## Intent

Improve mixed phone/watch route chronology when the devices' wall clocks differ, using the existing Wear clock synchronization exchange. The phone remains the activity authority and the current route reconciliation remains responsible for coverage, pause boundaries, and saved totals.

## Approaches

1. **Use a bounded, same-boot clock anchor in each telemetry batch (selected).** The sender attaches its most recent NTP-style watch-to-phone wall-clock offset, its watch monotonic and wall-clock anchor, and the measured round-trip time. The phone applies that correction only when sample and anchor boot origins agree within a bounded tolerance. This corrects supported samples without adding another clock or persisting a mutable global offset.
2. **Change phone route ordering to use watch monotonic timestamps directly.** Rejected because phone and watch monotonic clocks have unrelated origins; this would make route placement incorrect.
3. **Always subtract the latest wall-clock offset from stored telemetry.** Rejected because delayed replay after a watch reboot or wall-clock adjustment could apply a correction from a different clock epoch.

## Design

- Extend telemetry batch protocol v1 to v2 with an optional clock anchor. Keep decoding v1 messages and keep sample identity independent of anchor metadata.
- The Wear sender attaches an anchor only when the existing wall-clock sync sample is fresh and its round-trip time passed the current clock estimator's bound. When pending telemetry has no fresh anchor, it waits for at most one 5.5-second synchronization attempt, then sends with the existing timestamp fallback so sensor history is never blocked indefinitely.
- The phone stores the exact raw sample plus its batch anchor in the existing durable inbox. A duplicate sequence compares sample content and does not conflict solely because a later retry carries a newer anchor.
- During route materialization, derive the watch boot wall-time origin from each sample's wall timestamp and elapsed-realtime timestamp. Apply the watch-to-phone wall offset only when that origin is within two minutes of the anchor's origin and the round-trip time is no greater than two seconds.
- Missing, stale, rebooted, or inconsistent anchors use the current raw-wall-time fallback. Such samples remain durable and cannot create a second workout or alter authoritative totals.
- Keep route speed, pause, coverage, and Health Connect ownership rules unchanged.

## Constraints

- Preserve `se.hampusandersson.relven`, current workout/session IDs, and telemetry sample sequence IDs.
- Keep message size bounded by the existing 32 KiB ceiling.
- Maintain v1 decoder compatibility and previously stored inbox sample compatibility.
- Never use physical-device ADB; use unit/Robolectric tests and emulator-only checks if available.
- Maintain one Relven PR at a time and rebuild phone and Wear debug APKs after merge.

## Verification

Test v1 decoding; v2 anchor round-trip and bounds; same-boot offset correction; fallback for no anchor, a rebooted clock origin, or an excessive round trip; duplicate sample replay with changed anchor; and end-to-end chronological placement in the existing route reconciler. Run all common/app/Wear unit tests, both debug APK assemblies, relevant lint, Spotless, and hosted CI.
