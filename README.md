# mikimouse — Mobile Operator / Pocket Computer

An Android app that works like a small computer inside your phone. Describe the job,
it plans, works in an isolated workspace, and hands you the finished file.

## What it does today (110/200 plan phases, 27 tests green)

- **Video**: hardware-encoded slideshows from your photos (720p/540p/360p presets),
  trim, frames extract, SRT captions burned in, music muxed, battery-aware deferral
- **Documents**: real PPTX (POI), PDF with your photos inside (PdfDocument), DOCX from
  OCR text, CSV + XLSX from tables, Markdown, PDF merge, system print
- **Research**: keyless web answers with cited sources, page fetch + reader view,
  24h answer cache, model summaries when you configure a provider
- **Chat**: conversational answers with sources, follow-ups, next actions, history,
  voice input, read-out, per-task transcripts
- **Autonomy**: approval dialogs, offline mode that blocks honestly, crash recovery with
  Resume, unfinished-work list, scheduled tasks, run queue, debug bundles with stage errors
- **Media extras**: rotate/flip/convert/watermark, audio trim, Gallery publish, share sheet,
  email drafts with attachments, SMS drafts, maps, alarms, volume, flashlight, contacts
- **System**: budgets (RAM target/quota/CPU/network), Keystore-locked API keys, local/OpenAI/
  OpenRouter/custom models, skill auto-install (temp) with Keep/Remove/Teach, resource
  dashboard, benchmark checklist, liquid water-drop glass UI, shortcuts + QS tile

No AccessibilityService. Banking apps untouched. Originals never modified.

## Build

- `./gradlew :core:test` — unit + benchmark suite
- `./gradlew :app:assembleDebug` — needs `~/android-sdk` (see `local.properties`)
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Docs

`docs/00-architecture-assessment.md` … `docs/06-benchmarks.md`,
`docs/MASTER-PLAN-200.md` (110/200), `docs/PARITY-SHEET.md`.

## Skills reference

`.agents/skills/{composio,pr-watch}` from `elie222/rakazo` (open Grok Bot alternative).
Also auto-installable in-app.
