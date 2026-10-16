# Gap Analysis — what must be built for MVP

MVP bar (spec §34/38): select files → NL instruction → real plan → tools execute →
isolated files, originals untouched → budgets enforced → model switchable (local +
remote arch) → video works → presentation works → export → cleanup → survives
restart → dangerous ops gated → no accessibility → tests + docs + on-device benchmarks.

## Gaps (P0 = MVP-blocking)
- P0 Workspace isolation: no `/operator/tasks/<id>/` tree, no input immutability, no quota.
- P0 Planner: no NL→plan for video/presentation/pdf, no resource estimate, no LOW/MED/HIGH gate.
- P0 Tool registry: no declared cost/network/risk/schemas, no allowlist enforcement.
- P0 Media operator: no storyboard→render→validate pipeline, no streaming discipline.
- P0 Document operator: no PPTX/PDF generation via real libs.
- P0 Artifact/export: no checksum/validation, no SAF export verification.
- P0 Cleanup: no export-verify-then-delete-temp, no originals-never-delete guard.
- P0 Crash recovery: no persisted `CREATED…CANCELLED` state machine.
- P0 Model abstraction: no switchable provider, no Keystore keys, no fallback.
- P0 Android shell: no SAF import, no foreground render service, no Compose operator UI,
  no resource monitor, no approval dialogs.
- P0 Tests: no isolation/quota/cleanup/recovery/artifact/auth/approval/model tests.

## Gaps (P1 = production hardening, after MVP)
- Web operator beyond controlled download; full browser automation sandbox.
- Connectors (WhatsApp/Telegram/Drive/YouTube/Canva) beyond Intent registry.
- Screenshot OCR→vision→intent pipeline (needs ML Kit / vision model wiring).
- Local inference (llama.cpp/MLC/ONNX/MediaPipe/ExecuTorch) beyond capability detector + stub.
- Battery/storage dashboards with historical graphs; remote-model cost tracking.

## Non-gaps (reuse, do not rebuild)
- Gradle 8.13 + AGP 8.10.1 + Kotlin 2.2.20 + SDK 36 toolchain (copy pattern).
- Policy ALLOW/ASK/DENY + retry-with-evidence loop (adapt into `OperatorRuntime`).
- `ResourceCaps` khan (extend, not replace).
