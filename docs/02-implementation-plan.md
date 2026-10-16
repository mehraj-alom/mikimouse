# Implementation Plan — phases to MVP

PHASE 0 (done): repo audit → this doc set.
PHASE 1 Runtime foundation: `Task`, `AuditLogger`, `ApprovalGate`, `OperatorRuntime`
  state machine + policy loop. Test: approval + retry + evidence.
PHASE 2 Workspace isolation: `WorkspaceManager` creates
  `tasks/<id>/{input,workspace,output,cache,logs,metadata}`, URI import copies into
  `input/` only, originals never written, quota pre-check, path-escape guard.
PHASE 3 Resource governance: `ResourceManager` (RAM target, storage quota, CPU
  Conservative/Balanced/Performance → worker concurrency 1/2/3, network
  OFFLINE/ASK/ALLOW/TRUSTED). Enforcement = concurrency + chunking + streaming +
  temp discipline + early deny. UI says "Target runtime memory budget".
PHASE 4 Tool registry: `ToolRegistry` with 16 tools (file/media/document/
  presentation/web/download/ocr/image/audio/archive/calendar/notification/intent/
  share/model/shell-restricted/browser/app-discovery). Each declares perms, cost,
  network, risk, schemas. Shell = HIGH, always gated.
PHASE 5 Planner: keyword/intent router (video/presentation/pdf/docx/research/
  compress/ocr/download/offline) → steps + tools + estimate + risk. No LLM needed
  for MVP planning; model used for content (outline/summary) when configured.
PHASE 6 Models: `ModelProvider` interface + `LocalStub` + `OpenAICompatible` +
  `OpenRouter` + `CustomAPI`; `CapabilityDetector` (RAM/CPU/GPU/SDK/storage →
  0.5–1.5B / 1.5–3B / 3B+ recommendation); remote fallback; keys via `SecureKeyStore`
  (Android Keystore) in app, env/file provider in core tests.
PHASE 7 File operator: SAF import (`ACTION_OPEN_DOCUMENT*`), MediaStore query,
  `ContentResolver` copy into `input/`, export via `ACTION_CREATE_DOCUMENT` /
  `MediaStore` insert, checksum verify after export.
PHASE 8 Media operator: `MediaPipeline.plan Inputs+instruction → storyboard →
  transcode plan (resolution/bitrate/codec)`; Android impl uses
  MediaExtractor/Codec/Muxer streaming; core JVM impl concatenates/copies for
  testability. Never loads whole video into RAM (16 KB buffers, chunked).
PHASE 9 Document operator: `DocumentPipeline` outline→slides→render→validate.
  PPTX via Apache POI (real lib), PDF via text-layout validation in core and
  `PdfDocument` on Android, DOCX/TXT/MD/CSV writers. Remote content assist optional.
PHASE 10 Web operator (MVP slice): `download_to_workspace(url)` with network-policy
  check + size cap + checksum. Full browser automation deferred (P1).
PHASE 11 Intents/connectors: `IntentTool` (VIEW/SEND/SHARE/OPEN_DOCUMENT) + connector
  registry (name → deep-link/API, capability flag). Unavailable → explain blocker.
PHASE 12 Artifact/export: `ArtifactManager` (id/type/filename/size/checksum/task),
  validate (magic bytes + size>0 + PPTX zip+`[Content_Types]`), export-copy-verify.
PHASE 13 Crash recovery: `TaskManager` persists `task.json` + `state` per task;
  on start, rehydrate; `RUNNING` → `PAUSED` (resume) or `FAILED` (retry/discard).
PHASE 14 Security hardening: capability tools only, no `arbitrary_shell`, Keystore
  keys, network ledger, audit per task. Manifest contains no AccessibilityService.
PHASE 15 Perf/battery: event-driven workers, no polling loops, foreground service
  only during render with notification, release model/handles on cleanup.
PHASE 16 Benchmarks: `PlannerBenchmarkTest` encodes benchmarks 1–10 as plan-shape
  assertions + two end-to-end pipeline tests (video, presentation).
PHASE 17 Hardening: signed release, backup rules (no workspace backup), R8 note.

Vertical slice first: Benchmark 1 (2 images + 1 video → final_video.mp4) then
Benchmark 2 (screenshots → PPTX/PDF). See `05-first-vertical-slice.md`.
