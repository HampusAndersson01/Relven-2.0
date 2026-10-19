# Feature parity

Status describes this checkout, not planned designs.

| Capability | Current status | Evidence or next check |
| --- | --- | --- |
| Phone GPS, pace and distance | Inherited, runtime unverified | Baseline APK compiled; device test pending |
| Structured intervals, repeats and targets | Inherited, runtime unverified | Workout model and selected tests present |
| Audio and Swedish TTS | Inherited, runtime unverified | Swedish voice/device test pending |
| Activity history and GPX/TCX export | Inherited, runtime unverified | File round-trip test pending |
| Wear workout display and controls | Inherited, runtime unverified | Paired-device test pending |
| Watch heart-rate measurement | Initial integration, unverified | [PR #1378 audit](wear-hr-audit.md); Galaxy Watch6 Classic, screen-off and permission tests pending |
| FIT Workout import | Pending | Decode FIT file type 5 with tests |
| Versioned JSON workouts and plans | Partial running-workout v3 import; v1 running-plan picker, validation and private storage | Supported workouts convert into inherited files; plan activation, strength and device execution pending |
| Calendar and strength details | Basic month view for imported running/rest entries; strength pending | Device UI test, scheduling actions and Stitch correction pending |
| Offline exercise catalog and gym logging | 871-record offline catalog with search, instruction detail, favorites and custom exercise editing; gym logging pending | Source/license pinned; device test pending |
| AI Plan Kit ZIP | Pending | Generate from implemented schema only |
| Free map and route coloring | osmdroid map now colors valid route segments by pace, speed, heart rate or elevation, with a numeric legend | Visual, long-route and recorded-data tests pending; tile-cache policy still to audit |
| New Compose phone/watch UI | Phone foundation/preview only | Debug-only native screen rendered on a phone; production navigation and Wear UI pending |

Build success does not prove device behavior. No phone or watch device was connected during the baseline build.
