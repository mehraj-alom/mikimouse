# First Vertical Slice (MVP)

## Slice A — Benchmark 1 (build first)
IN: `photo1.jpg + photo2.jpg + clip.mp4`, instruction "Create a professional
30-second video".
Flow: SAF/MediaStore pick → `WorkspaceManager.createTask` → import to `input/` →
`Planner.plan` (steps: inspect → storyboard → normalize 1280x720 → timeline →
title card → audio-passthrough → render mp4 → validate → export) →
`ApprovalGate` (LOW auto) → `OperatorRuntime` executes `MediaPipeline` streaming →
`ArtifactManager.validate` (mp4 ftyp + size) → SAF export `final_video.mp4` →
verify checksum → `CleanupManager` deletes cache/workspace, keeps audit + artifact
record. Originals untouched. Survives process death via `task.json`.

## Slice B — Benchmark 2 (build second)
IN: screenshots, "Make a presentation from these".
Flow: import → OCR/vision stub extracts text (P1: real ML Kit/vision model) →
outline → design system → POI PPTX render → PDF preview → validate (zip +
`[Content_Types].xml`) → export PPTX + PDF → cleanup.

## What "real" means (§35)
Every button in `OperatorScreen` is wired: Pick files opens SAF; Run executes the
pipeline; Export opens `ACTION_CREATE_DOCUMENT`; no mock outputs — core tests
assert real files (mp4 header, PPTX zip entries, checksums, quota denials,
recovery resume). Impossible paths (e.g. unattainable app API) show the blocker
text instead of fake success.

## Gates before calling MVP done (§38 checklist maps to tests)
`core:test` must pass: isolation, quota, cleanup, recovery, artifact, auth,
approval, model-switch, media e2e, document e2e, 10 benchmark plan shapes.
`app:assembleDebug` must succeed on `~/android-sdk`. Manifest grep must show no
`AccessibilityService`. On-device: run Benchmarks 1–3 manually and keep the APK
artifacts.
