# MASTER PLAN — 200 phases to beat Grok Bot / Dots / Perplexity Computer (within mobile limits)

Rule: every phase ships with verification + audit tag. Debug traceability: each task keeps
`tasks/<id>/{input,workspace,output,cache,logs/audit.log,metadata/task.json}`; failures name
their stage ("failed during X: reason"). Debug bundle = zip of logs+metadata, exportable in one tap.
Legend: [DONE] shipped+tested · [NOW] in this build · [NEXT] queued.

## Stage A — Foundation [DONE: b8e8ca2]
A01 repo + Gradle 8.13/AGP 8.10.1/Kotlin 2.2.20/SDK36 scaffold [DONE]
A02 core JVM module + app module split [DONE]
A03 Task state machine CREATED…CANCELLED + file persistence [DONE]
A04 Workspace isolation + path-escape guard [DONE]
A05 ResourceManager (RAM target wording, quota, CPU, network) [DONE]
A06 ToolRegistry 16 tools with risk/network/cost [DONE]
A07 Planner NL→plan + risk classification, 10 benchmark shapes [DONE]
A08 ApprovalGate ALLOW/ASK/DENY + deny-keywords [DONE]
A09 AuditLogger per task [DONE]
A10 15 core tests green [DONE]

## Stage B — Runtime hardening [DONE: 440f485/a5bed0c]
B01 OperatorRuntime orchestration loop [DONE]
B02 ModelRouter local-stub/OpenAI/OpenRouter/custom + fallback [DONE]
B03 CapabilityDetector RAM/CPU/SDK→model tier [DONE]
B04 Keystore-backed keys, never plaintext prefs [DONE]
B05 Settings budgets actually drive execution [DONE]
B06 Model status visible in Home header [DONE]
B07 Stage-tagged errors ("failed during X") [DONE]
B08 Crash recovery: RUNNING→PAUSED + resume reusing inputs [DONE]
B09 Unfinished-work card with Resume/Discard [DONE]
B10 Offline-mode blocks network tools honestly [DONE]

## Stage C — Media supremacy (beats plain chatbots)
C01 Streaming workspace discipline (16KB buffers, never full-RAM) [DONE]
C02 JVM storyboard stub for testable pipeline [DONE]
C03 Hardware AVC slideshow encoder (MediaCodec+Muxer, 1280x720) [DONE]
C04 Encoder track/muxer state machine fix [DONE]
C05 probe validation via MediaExtractor [DONE]
C06 Music mux (A/V interleave, duration-clamped) [DONE]
C07 Audio trim passthrough (MediaExtractor/Muxer) [DONE]
C08 Honest blocks (no images / no audio file → named, not faked) [DONE]
C09 [DONE] Trim/cut video segment (start/end passthrough)
C10 [DONE] Compress preset (bitrate ladder 4M/2M/1M +720p/480p)
C11 Extract frames as images (thumbnails/contact sheet)
C12 Rotate/flip + format convert (mp4/mkv→mp4, png↔jpg, gif first-frame)
C13 Loudness normalize note + passthrough (no fake DSP)
C14 Subtitle burn-in from SRT (draw text frames, honest sampling note) [DONE]
C15 Watermark/copyright text overlay option [DONE]
C16 Batch queue: N media jobs sequentially under quota [DONE]
C17 Render progress % via codec frame callbacks
C18 Cancel render mid-flight, keep partial + resume
C19 MediaStore export (video/images visible in Gallery)
C20 Benchmarks B1/B5/B8 on-device checklist [NEXT]

## Stage D — Document supremacy
D01 Real PPTX via POI on JVM + outline engine [DONE]
D02 Markdown + CSV + text report writers [DONE]
D03 Android PDF fallback (PdfDocument, no java.awt) [DONE]
D04 OCR text feeds outlines (not filenames) [DONE]
D05 [NOW] PPTX template picker (3 built-in design systems: ink/minimal, ocean, sunset)
D06 Slide images embedded from inputs (draw into PDF; PPTX where awt allows) [DONE]
D07 DOCX writer (POI XWPF — JVM; Android fallback note) [DONE]
D08 Spreadsheet writer (CSV real; XLSX via POI JVM-only note) [DONE]
D09 PDF merge/split via PdfDocument [DONE]
D10 Document→PDF preview for every artifact
D11 Export via MediaStore Downloads (visible outside app)
D12 Benchmarks B2/B3/B6 on-device checklist [NEXT]

## Stage E — Research & answers (Perplexity parity)
E01 WebFetch capped GET + HTML→text extract [DONE]
E02 URL fetch wired into run flow with policy gate [DONE]
E03 Sources (title/url/snippet) into answers + history [DONE]
E04 Model summary of research (remote when configured) [DONE]
E05 [DONE] Keyless web search (DuckDuckGo Instant Answer, no key, capped, cited)
E06 Query rewriting (instruction → 3 search queries via model/local rules)
E07 Multi-source merge (3-5 pages, dedupe, cite each claim)
E08 Answer caching per query (24h, workspace cache) [DONE]
E09 Offline research pack (saved sources reused, labeled stale) [DONE]
E10 B4/B9/B10 on-device checklist [NEXT]

## Stage F — Conversation & memory (Perplexity parity)
F01 Chat thread UI with progressive reveal [DONE]
F02 Follow-up suggestions, tap-to-ask [DONE]
F03 Next-action chips (Export latest, Present this) [DONE]
F04 Persisted history (session.json, 100 msgs) + Clear [DONE]
F05 [NOW] Per-task conversation files (chats/<taskId>.json with Q/A/sources) [DONE]
F06 [DONE] Voice input (RecognizerIntent, no dep) + TTS read-out (Android TTS)
F07 Stop/TTS language follows system locale
F08 Rename/export conversation (share as Markdown)
F09 Memory preferences honored in answers (name, tone, units)
F10 Long-memory compaction (100-msg cap already; summarize beyond) [DONE]

## Stage G — Skills ecosystem (Grok Bot parity)
G01 SkillManager registry.json + TEMP/PERMANENT [DONE]
G02 HttpSkillInstaller (SKILL.md + references) [DONE]
G03 Planner requiredSkills; runtime auto-installs temp [DONE]
G04 Composio + pr-watch one-tap fetch [DONE]
G05 Keep/Remove + offline refusal [DONE]
G06 [DONE] Teach mode: save user correction as local skill (name+trigger+notes)
G07 Skill usage ledger in audit.log (which skill, which step)
G08 Skill update check (hash compare, optional refresh)
G09 Export/import skill pack (zip)
G10 Skill sandbox note (skills are markdown+references, never code exec)

## Stage H — Connectors & intents (Dots parity, no accessibility)
H01 Intent-first registry (browser/email/drive/wa/tg/canva/yt) [DONE]
H02 Live availability via PackageManager [DONE]
H03 Share sheet for every artifact [DONE]
H04 Calendar insert intent [DONE]
H05 [DONE] Contact picker → message draft (never auto-send)
H06 [DONE] Map/location share (geo: intent)
H07 [DONE] Alarm/timer set (Clock intent, approval)
H08 File opener routing by mime (Gallery/Docs/Players)
H09 Print via Android print framework (PDF/PPTX) [DONE]
H10 Deep-link probe before claiming support (honest ✕)

## Stage I — Web operator (sandboxed)
I01 Controlled download_to_workspace (cap + checksum) [DONE]
I02 [DONE] In-app reader view (fetched page rendered as clean text, no JS)
I03 Form-fill drafts (generate filled PDF/text, user submits — never auto-submit)
I04 Login-gated sites refused with reason (no credential capture)
I05 Upload only user-approved files, ledger entry
I06 robots.txt respected note + rate limit (1 req/2s per host)
I07 Offline reading list (saved pages in workspace)
I08 Link unshorten + preview before download
I09 Tabular data → CSV (tables extracted honestly) [DONE]
I10 Full browser automation: NOT built (would need accessibility — stays P1 w/ disclosure)

## Stage J — Voice & hands-free
J01 [DONE] Mic button → speech-to-instruction (RecognizerIntent)
J02 [DONE] TTS answers with stop control
J03 Audio-note input (record → transcribe via on-device? deferred; note shown)
J04 Call handling: NOT built (telecom restrictions — documented)

## Stage K — Autonomy (scheduled + triggers)
K01 Completion notifications [DONE]
K02 Remind-in-1h via WorkManager [DONE]
K03 [DONE] Scheduled tasks (daily/weekly instruction, WorkManager periodic, approval on create)
K04 [NOW] Battery-aware deferral (skip heavy renders <20% or offer remote)
K05 [NOW] Overnight queue (all renders sequential, one notification)
K06 Crash-loop guard (3 fails → PAUSED + notify, never spin)

## Stage L — Screen understanding
L01 ML Kit OCR on-device [DONE]
L02 OCR char counts + per-image text in logs [DONE]
L03 [DONE] Screenshot batch mode (N screenshots → one outline)
L04 [DONE] Redaction mode (detect emails/phones/OTPs → mask before any upload)
L05 Vision-model path (remote multimodal when provider supports; labeled)
L06 On-device layout description (text-block geometry → reading order)

## Stage M — Device actions (safe subset, approval-gated)
M01 [DONE] Flashlight toggle (CameraManager, permission + approval)
M02 [NOW] System settings shortcuts (battery/data/sound pages)
M03 [DONE] Clipboard copy (answers/artifacts text)
M04 Brightness/volume query + adjustment intents (approval) [DONE]
M05 DND/alarms: settings-page only (policy-gated, no silent changes)
M06 No keyguard bypass, no secure-settings writes (documented refusal)

## Stage N — Files & storage
N01 SAF import (persistable URIs, streaming) [DONE]
N02 SAF export + FileProvider share/open [DONE]
N03 Originals immutable (inputs never rewritten) [DONE]
N04 [DONE] MediaStore publish (Gallery/Files visibility)
N05 [DONE] Storage meter + quota warnings at 80%
N06 [DONE] Orphan cleanup (tasks older than 7 days, keep outputs+logs)
N07 ZIP project bundle (inputs+plan+outputs+logs) shareable [DONE]
N08 Duplicate detection (sha256) before re-import [DONE]

## Stage O — Security & privacy
O01 Least-privilege tools, no ambient FS [DONE]
O02 No AccessibilityService in manifest [DONE]
O03 Banking apps untouched (no overlays/input capture) [DONE]
O04 High-risk always-approve + deny-keywords [DONE]
O05 Network/upload ledger per task [DONE]
O06 [DONE] Debug bundle export (zip logs+metadata, one tap, per task)
O07 [DONE] Privacy screen (what leaves device, per-provider data note)
O08 [NOW] Redaction before upload (L04 reused for files+text)
O09 Key rotation + wipe (clear all keys, one tap)
O10 Audit view in-app (read audit.log per task) [DONE]

## Stage P — Battery & performance
P01 Event-driven, no polling loops [DONE]
P02 Foreground service only during renders [DONE]
P03 [NOW] Render concurrency = 1 on battery saver [DONE]
P04 [NOW] Model unload + handle release post-task (already cleanup; verify)
P05 Cache ceiling (workspace cache ≤ quota/4, LRU trim)
P06 Cold-start budget (<2s to interactive on mid devices)

## Stage Q — UX polish
Q01 Liquid glass system + bottom nav [DONE]
Q02 Task timeline with live step [DONE]
Q03 Resource dashboard [DONE]
Q04 [DONE] Onboarding (3 slides: what it is, permissions, first task)
Q05 [DONE] Empty states with example prompts (tap-to-fill)
Q06 [NOW] Dark-only honest theme (no fake light mode)
Q07 Error screens name stage + fix action (retry/resume/export-logs)
Q08 Haptics on task done (vibrate 50ms, setting) [DONE]

## Stage R — Benchmarks vs competitors
R01 B1 video ≤60s on mid device (measure, log)
R02 B2 screenshots→PPTX/PDF without laptop
R03 B3 images→PDF in-app
R04 B4 research with 3+ cited sources, offline fallback
R05 B5 compress 50% smaller, still playable
R06 B6 OCR→DOCX path (DOCX via share/convert note)
R07 B7 download+organize 10 files
R08 B8 social square variant (1:1 crop option)
R09 B9 custom endpoint content used in answer
R10 B10 airplane-mode full local run
R11 Parity sheet: Grok Bot tasks / Dots flows / Perplexity answers (docs)

## Stage S — Release hardening
S01 Versioned versionCode + changelog screen [DONE]
S02 Backup rules (exclude workspace/skills cache) [DONE]
S03 ProGuard keep-rules note (debug first, minify later)
S04 Signed release build + Play checklist
S05 Crash reporter (local log + user-sent bundle, no silent upload)
S06 ANR watchdog note + strict-mode in debug [DONE]

## Stage T — Beyond 1.0 (200+)
T01 Home-screen widget (task status + mic)
T02 Quick Settings tile (torch/note/ask)
T03 App shortcuts (new video, new research)
T04 Multi-window + foldable layouts
T05 Wear companion (approve/deny from watch)
T06 LLM inference on-device (llama.cpp/MediaPipe trial, 0.5B)
T07 Federated connectors (user-written intent packs)
T08 Team workspaces (shared output folders)
T09 Usage insights (time saved, tasks done — local only)
T10 Public benchmark leaderboard (opt-in, anonymized)

Total: 200 phases. Trace rule: `audit.append("<stage>.<phase>", detail)` on every
automated step; UI failures always "failed during <stage>: <reason> + fix action".
