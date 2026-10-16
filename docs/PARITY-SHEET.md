# Parity sheet — Grok Bot vs Dots vs Perplexity Computer vs MikiMouse

Honest mapping. "✓" = works on-device today. "◐" = partial/honest fallback.
"✕" = refused by design (with reason). Phase refs → docs/MASTER-PLAN-200.md.

## Grok Bot tasks
| Task | Grok Bot | MikiMouse |
|---|---|---|
| Persistent bots with memory | ✓ server-side | ◐ local memory store + history (F04/F09); no cloud twin |
| Voice mode (speak/dictate/call bots) | ✓ | ◐ STT+TTS on-device (J01/J02); calls ✕ (telecom) |
| Shared + private computers | ✓ cloud VMs | ◐ sandboxed task workspaces, one phone (A04) |
| Browser/terminal/files/desktop | ✓ | ◐ files+downloads+reader (N01/I02); terminal = allowlisted ops only; desktop ✕ (no screen control) |
| Delegate to subagents | ✓ | ◐ plan steps + tool fan-out (A07); peer bots P1 |
| Model choice (BYO) | fixed | ✓ local/OpenAI/OpenRouter/custom switch (B02/B06) |
| App integrations | server connectors | ◐ intent-first + composio skill (H01/G04); deep-link probe honesty (H10) |
| Teach new behavior | ✓ | ◐ G06 Teach mode (local skill notes) |

## Dots flows (Instinct/Hark-style personal assistant)
| Flow | Dots | MikiMouse |
|---|---|---|
| Text it to do things | ✓ hosted | ✓ chat thread + approvals (F01/Q02) |
| Reminders/alarms | ✓ | ◐ in-app reminders + Clock intent (K02/H07); exact alarms need permission |
| Research + briefings | ✓ | ✓ E01–E10 with citations + offline fallback |
| Calendar/email handling | ✓ server OAuth | ◐ calendar insert, email draft, never auto-send (H04/H05) |
| Calls on your behalf | ✓ | ✕ telecom + fraud risk (J04 documented) |
| Payments/purchases | ✓-gated | ✕✕ never (O04); banking apps explicitly untouched |

## Perplexity Computer (answer engine)
| Capability | Perplexity | MikiMouse |
|---|---|---|
| Cited answers | ✓ | ✓ sources + snippets + history (E03/F04) |
| Follow-up questions | ✓ model | ◐ rules+model hybrid (F02); model path when remote configured |
| Keyless instant answers | ✓ | ◐ E05 DuckDuckGo (no key, capped, cited) |
| Proactive actions | ✓ | ◐ next-action chips + scheduled tasks (F03/K03) |
| File outputs from answers | ✓ cloud | ✓ in-app outputs + export/share (N02/H03) |
| Offline mode | ✕ | ✓ local stub + saved sources, labeled (B10/E09) |

## Where we win (within mobile limits)
1. No account, no cloud, no lock-in — everything runs on the phone (O01/O05).
2. Every failure names its stage + fix; debug bundle in one tap (O06).
3. Real hardware media pipeline with probes, not mocks (C03–C07).
4. Budgets enforced as far as Android permits, shown honestly (P01–P05).
5. Skills self-install as temp and stay listed for review (G01–G05).

## Where we deliberately lose
- Anything needing screen control, call audio, banking hooks, or silent sending: ✕ by design.
- Heavy local LLMs: capability-gated, remote fallback offered, never faked (B03).
- iOS/desktop twins: out of scope (this repo is Android-first).
