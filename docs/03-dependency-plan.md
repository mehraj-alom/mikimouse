# Dependency Plan

## Toolchain (pinned, reuse sibling)
- Gradle 8.13 (wrapper jar committed), AGP 8.10.1, Kotlin 2.2.20, JDK 17 target
  (Termux has JDK 21; `jvmTarget=17` keeps AGP happy), compile/target SDK 36, min 26.
- Repos: `google()` + `mavenCentral()` only, `FAIL_ON_PROJECT_REPOS`.

## Libraries
| Module | Dep | Why | Size note |
|---|---|---|---|
| core | `kotlinx-coroutines-core 1.10.2` | structured concurrency in runtime | ~1 MB |
| core | `kotlinx-serialization-json 1.9.0` | task persistence, plans, artifacts | small |
| core | `poi-ooxml 5.2.5` | REAL PPTX generation (spec §7/35: no hand-rolled OOXML) | ~15 MB transitive; test-only shading not needed |
| core tests | `kotlin-test`, `junit4`, `coroutines-test` | unit + benchmark tests | small |
| app | Compose BOM 2025.10.00 (ui/foundation/material3) | Computer-Mode UI, timeline | cached in sibling build (~200 MB first sync) |
| app | `activity-compose`, `lifecycle-runtime-compose` | SAF + state | small |
| app | `work-runtime-ktx 2.9.0` | `OperatorWorker` for renders | small |
| app | `coroutines-android` | UI + service scope | small |
| app | `project(:core)` | runtime reuse | — |

Explicitly NOT added for MVP: Media3 (framework MediaCodec/Muxer suffices),
Room/KSP/Hilt (file-JSON persistence avoids KSP in Termux), ML Kit (OCR deferred
to P1 with stub tool descriptor), llama.cpp/ONNX (capability detector + stub
provider; real binding is P1).

## Licensing
- POI: Apache-2.0, allowed. No FFmpeg binary shipped (spec §6 "only where
  licensing/distribution permits"): Android impl uses framework codecs; doc notes
  optional user-side FFmpeg via `ShellTool(HIGH)` with approval, never bundled.
- No AccessibilityService dependency at all.

## SDK
`~/android-sdk` (platform android-36, build-tools 35/36). `local.properties`
`sdk.dir=/data/data/com.termux/files/home/android-sdk`.
