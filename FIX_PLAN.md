# JAScanner fix plan (post-audit, Nov 2026)

Source of truth: [issue #1 — audit master tracker](https://github.com/aktarjabed/JAScanner/issues/1).
This document turns the 25 filed findings into an ordered, testable programme of work.
It supersedes the "Plan v2" ordering in `NEXT_PLAN.md` for the next few releases: nothing new is built until the app compiles, the high-severity defects are closed and the result has had a first device test.

## Current state

- Source imported from the delivered ZIP in commit `bc5190e`; 142 tracked files.
- The Android build now succeeds on an aarch64 Termux device: `./gradlew :app:testDebugUnitTest` runs **99 tests with 0 failures**, and `./gradlew :app:assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`. No device testing has happened yet.
- Phase 1 fixes for #5-#15 and #26 are implemented in source and compile; every issue stays open until the device checks in `PLAN.md` section 7 pass.
- Phase 2 fixes for #16-#22 (dialogs/viewer, library states, tool validation and cleanup, external intents, settings, sharing, accessibility) and Phase 3 fixes for #4 and #23-#25 are implemented in source and compile; they also stay open until the same verification ladder is completed.
- The desktop harness now fails on compiler errors (issue #4 fixed), is parameterised, ships a standard-library fixture generator, and reproduces all 99 JVM tests.

## Verification ladder (every fix climbs as far as it can)

1. **Static** — resolve imports/members/resources; no `catch` that swallows `CancellationException`; every `newOutputFile()` has a delete-on-failure path.
2. **JVM unit test** — pure logic and PDFBox logic run on real PDFs; rotated pages must be covered explicitly.
3. **Harness** — only after issue #4 is fixed, so a compiler error actually fails the run.
4. **Device** — add a concrete step to `PLAN.md` section 7 for anything touching Compose, Play services, `PdfRenderer`, TTS, biometrics, gestures or files.
5. **Docs** — update `README.md`, `AUDIT.md` and `NEXT_PLAN.md` in the same commit. Never leave an "already fixed" claim that is only partly true.

## Phases

### Phase 0 — Make it build (milestone 1) — done

| Issue | Work | Exit criterion |
|---|---|---|
| #2 `[done]` | `compileSdk` 36 -> 37 for Compose 1.12; align README/PLAN | A clean Gradle sync succeeds |
| #3 `[done]` | Ship the wrapper or fix the documented test command | `:app:testDebugUnitTest` runs from a fresh clone |
| #1 | Keep the tracker checklist current | All Phase 0 boxes checked |

This phase blocked everything else and is now complete on Termux. If a library version has drifted, bump it in `gradle/libs.versions.toml`, not in scattered files.

### Phase 1 — Correctness and security (milestone 2)

| Issue | Work | Exit criterion |
|---|---|---|
| #5 | Remove the unauthenticated app-lock bypass | No route from `LockScreen` to `Unlocked` or `setAppLock(false)` without auth |
| #6 | Fall back to defaults instead of a blank screen when DataStore fails | Simulated failure shows a usable error state |
| #7 | Serialise bookmark/comment writes | Two rapid edits both survive in the PDF |
| #8 | Fix word boxes on 90/180/270-degree pages | Regression test plus real rotated PDF; tap selects the correct word |
| #26 | Make app-scoped save navigation conditional | Leaving mid-save preserves the current screen |
| #9 | Extend the scan double-tap guard to the result callback | Exactly one scanner launch on a double tap |

Then the supporting correctness batch: #10 (data transactions, PDF header read), #11 (card-scan leaks/state), #12 (OCR races), #13 (editor races/scaling), #14 (print ranges), #15 (PDF robustness).

Exit criterion for Phase 1: all P0/P1 issues closed, each with a regression test or a documented device step; `qpdf --check` passes for every modified PDF fixture.

### Phase 2 — UX, accessibility and polish (milestone 3)

Issues #16-#22: viewer search/dialog state, library selection and copy, tool validation and orphan files, external intents while locked, settings feedback and lock timing, sharing/scanner guards, accessibility/gestures/theme.

Sequence rationale: fix correctness before rearranging UI, and fix feedback (validation, error states) before adding new controls. Keep the existing no-ads/no-account promise; any new settings must not add a permission without a written reason.

Exit criterion: the device smoke plan in `PLAN.md` section 7 passes end to end, with TalkBack used on the main flows.

### Phase 3 — Docs, tests and hardening (milestone 4)

| Issue | Work |
|---|---|
| #4 `[done]` | Make the harness fail on compiler errors; align toolchain versions; ship fixtures |
| #23 `[done]` | Unicode/numeric edge cases (surrogate-safe truncation, sampling, guards) |
| #24 `[done]` | Correct doc drift: stale PLAN appendix, "form filling out of scope", round-5 overclaims |
| #25 `[done]` | Fill test gaps: rotated boxes, races, dialogs, output cleanup |

Exit criterion: no doc statement is contradicted by source; every high/medium issue has a regression test or a device test step.

## Explicitly out of scope for this programme

Office export/import, cloud sync, AI assistant, fax, iOS/desktop apps. They are real market gaps but the wrong move before the app builds and runs. Secure redaction, selectable marks, text-snapped highlights, visual crop, finger-anchored zoom, a first-run tour and a bottom selection bar are the next product batch after Phase 3.

## Rules

- Every commit fixes one issue or one coherent batch and links it in the message.
- Every write path reports failure to the user; no silent `emptySet()`/`getOrNull()` fallbacks that look like success.
- Every output file created in `filesDir` is deleted on failure.
- Do not claim an AUDIT fix is complete unless the corresponding issue is closed.
