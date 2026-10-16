# Architecture Assessment — Mobile Operator / Pocket Computer (mikimouse)

Date: 2026-10-17. Repo: `mehraj-alom/mikimouse`. Sibling reference: `Mickey-Squirrel`.

## What exists in mikimouse today
- Empty operator repo: `README.md`, `.agents/skills/{composio,pr-watch}`, `skills-lock.json`.
- No Android modules, no runtime, no tests. Greenfield for operator code.

## What exists in Mickey-Squirrel (reusable, audited)
- `core/` (Kotlin JVM, tested): `Task/Step/TaskReport`, `OperatorLoop` (policy + retry +
  evidence verification), `Policy` (LOW/MED allow, HIGH/CRITICAL ask + deny-keywords),
  `ResourceGovernor` (storage/workers/screenshots/llm-calls/duration/battery caps),
  `AuditLog`, `UniversalLlm`, `PhoneTool`, `Crew*` planner/runner/memory.
- `app/` (Android + Compose): `MainActivity`, `TaskService`, `TaskRepository`,
  `LlmProfiles`, Compose screens. Builds with AGP 8.10.1 / Kotlin 2.2.20 / SDK 36,
  `~/android-sdk` present with platforms android-36, build-tools 35/36.
- Gradle 8.13 wrapper + version catalogue. `core:test` runs without emulator.

## Assessment against the 39-section mission spec
| Area | Spec demand | Mickey baseline | Verdict |
|---|---|---|---|
| Workspace isolation | `/operator/tasks/<id>/{input,workspace,output,cache,logs,metadata}`, immutable inputs, quota, cleanup | Not present (tasks are in-memory + repo files) | Must build new `WorkspaceManager` in mikimouse `core` |
| Resource governance | RAM target (not reservation), storage quota, CPU policy, network policy | `ResourceCaps` covers storage/workers/llm/duration/battery, no RAM-target wording, no network policy | Extend, rename UI to "Target runtime memory budget" |
| Tool runtime | Capability tools, allowlist, risk levels, no arbitrary shell/FS | `PhoneTool` allowlist exists, no formal `ToolRegistry` with cost/network/risk/schema | Build `ToolRegistry` |
| Planner | NL → steps + tools + estimate + risk (LOW/MED/HIGH) | `CrewPlan` exists, no media/document pipelines | Build `Planner` + `MediaPipeline` + `DocumentPipeline` |
| Models | `ModelProvider` (local/OpenAI-compatible/OpenRouter/custom), Keystore keys, capability detector, fallback | `UniversalLlm` + profiles, no provider interface, keys not Keystore-bound | Build provider abstraction + `SecureKeyStore` in app |
| Files | SAF + MediaStore + URI perms, copy-to-workspace only for processing | `TaskRepository` file-based, no SAF import/export flow | Build SAF import/export in app |
| Media | Streaming/chunked, MediaCodec/Muxer/Extractor, HW codecs, no full-RAM loads | None | Build `MediaPipeline` (Android impl) + JVM stub for tests |
| Docs | PPTX/PDF/DOCX/TXT/MD/CSV via real libs, never hand-rolled OOXML | None | POI for PPTX (core JVM), PdfDocument on Android, validation |
| Web | Search/extract/download/upload/forms in controlled env | None | `WebTool` descriptor + controlled downloader (MVP: download_to_workspace only) |
| Connectors | Intent-first, deep-link/API, accessibility only as isolated fallback | Intents used ad-hoc | Build `IntentTool` + connector registry, no AccessibilityService in manifest |
| Approvals | Dangerous ops (send/purchase/delete/replace-original/install/APK/finance/publish) require confirmation | Policy ASK for HIGH/CRITICAL | Build `ApprovalGate` with explicit dangerous-op list |
| Lifecycle | WorkManager + foreground service w/ notification, crash recovery, task persistence | `TaskService` exists in sibling, no state machine | Build `TaskManager` state machine + file persistence + `OperatorWorker` |
| Privacy | Local-first, explicit remote upload record, memory view/export/delete | Sibling docs cover privacy, no memory store here | Build local memory store + upload ledger in `AuditLogger` |

## Decision
Do not fork Mickey-Squirrel. Build mikimouse as a clean Android project that
reuses its proven Gradle pattern (`core` JVM + `app` Android) and its
policy/retry/evidence ideas, but implements the full operator runtime natively
to the mission spec. No AccessibilityService in the manifest. No banking
interference by construction (no overlay, no key-event interception).
