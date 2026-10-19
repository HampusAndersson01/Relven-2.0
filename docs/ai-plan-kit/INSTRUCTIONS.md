# Instructions for plan generation

Return **one JSON object only** in the format shown by `examples/plan-running.json`. Set `format` to `relven.plan`, `schemaVersion` to 1, and include a unique nonempty plan `id`, `name`, IANA `timezone`, `workouts`, and dated `days`.

Use only running workout documents with `schemaVersion: 3`, `sport: "running"`, unique workout IDs and nonempty `steps`. Supported steps are segments, sequences and repeats. For a segment, use a warmup, work, recovery or cooldown phase. Supported ends are time in seconds, distance in meters, and manual. Targets may use numeric pace in seconds per kilometer (`minSecondsPerKm` / `maxSecondsPerKm`), speed in meters per second (`minMetersPerSecond` / `maxMetersPerSecond`), or heart rate in beats per minute (`minBpm` / `maxBpm`). Do not use effort, composite or heart-rate-below ends, strength exercises or exercise snapshots: the current app rejects them. Include realistic recovery between hard intervals and warm-up/cooldown where appropriate. Do not require heart-rate targets when the user lacks a sensor.

Each `days` entry needs a unique ID and real local date in YYYY-MM-DD. A workout day must reference an included workout ID. A rest day has no workout ID and cannot share a date with a workout day. Preserve unrelated workout IDs and dates when revising a plan. Do not fabricate completed sessions.

Validate against `schemas/plan-v1.schema.json` and its relative reference to `schemas/workout-v3.schema.json`. Then check the semantic rules above. Save the result as UTF-8 JSON no larger than 4 MB. The app's importer may reject valid schema documents that use unsupported workout semantics.
