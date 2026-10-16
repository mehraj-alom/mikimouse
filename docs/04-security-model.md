# Security Model

## Principles
Least privilege by default. No AccessibilityService. No banking/payment
interference (no overlay, no input interception, no secure-screen reads).
No Android security bypass. Originals immutable. Local-first; remote only with
provider configured + network policy allows + upload ledger entry.

## Capability tools (never ambient authority)
Tools receive only what the task grants: `taskId`, workspace paths, allowlisted
tool names, budget, deadline, network policy. No tool gets repo root, no tool gets
raw `ContentResolver` beyond its input URIs, no tool deletes outside its task dir.
Banned without HIGH approval: `arbitrary_delete_user_file`, `arbitrary_shell`,
`unrestricted_filesystem_access`. `ShellTool` risk=HIGH, default DENIED unless the
exact command allowlist + user approval both pass.

## Isolation
`WorkspaceManager` path-guard: every write resolved + normalized and must start
with the task dir; `..` escapes denied. Inputs copied into `input/`; render reads
from there; outputs land in `output/`; only `output/` files the user picks are
exported. Cleanup deletes `workspace/cache` + unexported `output` only after
export verification; originals never touched.

## Approvals (autonomy without recklessness)
- LOW (inspect, resize, transcode, draft): auto-run.
- MEDIUM (network download, share intent, export): confirm once per task (configurable).
- HIGH (send message, purchase, delete user data, replace original, install APK,
  finance credential, publish, irreversible, sensitive perm): always confirm with
  explicit artifact/parameter shown. Deny-keywords (password/otp/bypass/hidden
  record/surveillance) → DENY, matching sibling `Policy`.

## Secrets
API keys in Android Keystore (AES/GCM, `SecureKeyStore`); never plaintext
`SharedPreferences`; memory-only when in use; core tests use env/file provider.
Network ledger per task: what was uploaded, where, why (model endpoint + bytes).

## Audit
Per-task `logs/audit.log` append-only: plan, approvals, tool calls, uploads,
exports, cleanup. Retained after temp deletion. User can export/delete/disable
memory stores.

## Accessibility isolation
Accessibility is NOT a capability of the core runtime. If a future connector
ever needs privileged interaction it ships as a separate optional adapter,
disabled by default, with its own disclosure, and core benchmarks must pass
with it absent. Manifest must not declare it for MVP (CI grep gate).
