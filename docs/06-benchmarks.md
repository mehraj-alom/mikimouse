# Benchmark Suite (on-device + CI)

CI (`:core:test --tests BenchmarkSuiteTest`): plan-shape for all 10 + e2e for 1–2.
On-device (APK `com.mikimouse.app`, physical phone):
1. 2 images + 1 video → "Create a professional 30-second video" → expect `final_video.mp4`, originals untouched.
2. Screenshots → "Make a presentation" → expect `presentation.pptx` + MD preview, POI-validated.
3. Images → PDF (render path via `render_document`).
4. Topic → 5-page report (needs network + provider key; OFFLINE must refuse with reason, not fake).
5. Compress videos (streaming re-encode on Android; JVM stub only asserts pipeline wiring).
6. Screenshots → DOCX (OCR stub extracts filenames in MVP; P1 wires ML Kit/vision model).
7. Download + organize (network policy ASK → approval dialog; size cap enforced).
8. Social video (same as 1 with square crop note).
9. Configured API content (set endpoint/key in settings → `ModelRouter` uses it; fallback to local stub logged).
10. Offline-only (network ALLOW denied → planner still produces local plan; remote steps refused with ledger entry).

Record per run: device RAM/SDK/free storage (ResourceMonitor), task audit.log, artifact checksums.
