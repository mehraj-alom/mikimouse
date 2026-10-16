# mikimouse — Mobile Operator / Pocket Computer

Android app: a sandboxed operator runtime inside the phone. NL instruction →
plan → capability tools → isolated workspace → validated artifact → user-approved
export → cleanup. No AccessibilityService. Banking apps unaffected.

## Modules
- `core/` (Kotlin JVM, tested): task state machine, workspace isolation, resource
  budgets (RAM shown as **target**, never reservation), 16-tool registry, planner,
  model providers (local-stub / OpenAI-compatible / OpenRouter / custom),
  capability detector, media + document pipelines (POI for real PPTX), artifacts
  with SHA-256, crash recovery, audit log.
- `app/` (Android + Compose): Computer-Mode UI, SAF import/export, MediaStore,
  Keystore-backed keys, foreground render service, resource monitor.

## Build
- `./gradlew :core:test` — 15 tests (isolation, quota, recovery, artifacts, auth,
  approval, models, 10 benchmarks + 2 e2e).
- `./gradlew :app:assembleDebug` — needs `~/android-sdk` (see `local.properties`).

## Docs
`docs/00-architecture-assessment.md` … `docs/06-benchmarks.md`. Security model:
least privilege, originals immutable, uploads ledgered, dangerous ops gated.

## Skills (reference)
`.agents/skills/{composio,pr-watch}` from `elie222/rakazo` (open Grok Bot
alternative) kept as agent reference; not part of the app runtime.
