# JAScanner audit (rounds 1 through 5)

Scope: bugs, robustness, error handling, UI and top bars against market apps. Compiled with Gradle on Termux and run as far as possible without an emulator or phone. Anything not covered below has NOT been tested. **Current status: `./gradlew :app:testDebugUnitTest` passes 116 tests with 0 failures, and `./gradlew :app:assembleDebug` produces a debug APK; the app has still never been run on a phone.**

Round 3 (31 Oct 2026) added the features `NEXT_PLAN.md` and `COMPARISON.md` had flagged as most important: an embedded OCR text layer, bookmarks and comments stored in the PDF, compress that keeps text, a page-thumbnail grid with drag reorder/rotate/delete/duplicate, an image/tiled watermark, reading mode with read-aloud, and three more filters. See section 0 below for what was run this round; sections 1-5 are unchanged from round 2.

Round 4 (1 Nov 2026) was a UI-design and permissions pass: a branded Material 3 colour/type/shape system (replacing the default look), an opt-in Material You toggle, a splash screen, a pill-shaped search bar and centred home top bar, settings regrouped into cards, an in-app "Privacy & permissions" disclosure, and manifest hardening (AD_ID explicitly removed, clearer comments on what's expected in the merged manifest and why). **None of round 4 is checkable by the desktop harness** — it touches only Compose UI, theme resources and the manifest, none of which has any pure-Kotlin logic to run under JUnit or desktop PDFBox. It got the same static checker pass as every other round (import/member resolution across all files) and a read-through, and nothing more; see `dev-tools/desktop-harness/round3/README.md`'s "what this does NOT verify" for the standing list this adds to. Treat every visual claim in this round (colours, spacing, the splash screen, the settings layout) as unseen until it's opened in Android Studio.

Round 5 (13 Nov 2026) was a dedicated bug/robustness/error-handling sweep across the whole app: every file under `core/`, `pdf/`, `data/` (including the Room layer), `ocr/`, `scan/`, `security/` and all of `ui/` was read with that one question in mind, by five separate reviews run in parallel, each scoped to one area of the codebase. Section 6 below lists every confirmed issue and its fix. A second, independent pass then re-read every changed line (without having written any of it) specifically looking for a fix that was wrong, incomplete, or introduced a new problem; it confirmed all of them.

At the time it was written, round 5 could not be compiled: the fixes were checked by the static checker (98 files, 1,896 declarations, all resolve), the desktop unit-test harness (77 tests then; the current suite has **116, all passing**), and close reading for everything in `pdf/`, `data/` and `ui/` that the harness cannot touch (PDFBox-Android, Room, Compose, coroutine lifecycle). The project has since been compiled with Android Gradle on Termux — `:app:testDebugUnitTest` and `:app:assembleDebug` both succeed — so the Kotlin now compiles as a whole. That still does not prove the Android-side fixes at runtime: none of this has been run on a phone. Treat every fix outside `core/` as logically reasoned through, compiled and independently re-checked, not as proven by execution on a device.

Round 6 (24 Nov 2026) settled the one permissions question every earlier round had only inferred. Round 4 added manifest comments and an in-app disclosure that said what the merged manifest *should* contain; this round installed a real Android toolchain (JDK 17, SDK platform 37, build-tools 37.0.0) in a development VM and ran the actual manifest merger, so section 7 below is generated output rather than an expectation. The result changed the privacy wording: `INTERNET` is real, comes from Google's ML Kit telemetry rather than from module downloads, and that telemetry is on by default. `README.md`, the `AndroidManifest.xml` comment, the Settings screen, `COMPARISON.md` and `NEXT_PLAN.md` were corrected to match. Nothing else in the app was touched, and no device or emulator was available, so the uploads themselves were not observed.

## 0. Round 3: what was actually run

| Check | How | Result |
|---|---|---|
| Unit tests (now including `PageList` and the reading-mode text/pixel helpers) | Same plain-Kotlin + JUnit setup as section 1, extended with 24 new tests | 72 of 72 at the time; the current checkout runs 116 of 116 (see `dev-tools/desktop-harness/round3/`) |
| OCR text layer (`PdfTextLayer`) on real PDFs | Desktop PDFBox, driven by `TextLayerTest`/`PdfFeatures` drivers | Invisible (pages render identically to the untouched scan), positioned correctly on upright and 90/180/270-rotated pages, handles accented and unencodable (₹) text, blank lines, zero-size boxes and an out-of-range page without crashing |
| Compress-keeps-text (`PdfImageCompress`) on real PDFs | A desktop stand-in for the Android `BitmapImageRecoder` (same shrink-then-JPEG logic, `java.awt` instead of `android.graphics`) | Shrinks a picture-heavy PDF at several quality settings (13.7 MB to as low as 201 KB at the lowest quality tried), leaves a PDF with no pictures untouched and now reports that case explicitly, leaves pages with real text alone |
| Bookmarks (`PdfBookmarks`) on real PDFs | Add, remove, nesting, bad page/index | Reads back correctly with `pypdf`; a failed add/remove leaves no stray output file |
| Notes (`PdfNotes`) on real PDFs | Add on upright and rotated pages, read, remove, bad index, a 5000-character note (clamped to 2000) | Appearance streams present, correct rects and print/no-zoom/no-rotate flags, upright on rotated pages |
| Page rearrange (`PdfTools.rearrange`/`PagePlan`) on real PDFs | Reorder, rotate, delete-by-omission, duplicate (including duplicating an already-rotated page), bad page index, empty plan, non-90° rotation | Correct order and absolute rotation in every case, including the duplicate-of-a-rotated-page case that was wrong in an earlier draft (now fixed — see section 2 below) |
| All outputs above | `qpdf --check` | All valid |
| Everything else (Compose screens, ML Kit's actual OCR boxes, TTS, `BitmapImageRecoder` on a real Bitmap, drag gestures, `PdfRenderer` thumbnails) | — | **Not run.** Only type-checked where the desktop harness's stand-ins made that possible (see `dev-tools/desktop-harness/round3/README.md` for exactly which parts) |

### Bugs found and fixed in round 3

1. `PdfTools.rearrange`: duplicating a page that had already been rotated by an earlier entry in the plan would add the rotation a second time (90° + 90° = 180° instead of 90°). Fixed by capturing each source page's base rotation before the loop and setting every output page's rotation absolutely (`base + requested`), not by accumulating.
2. The reading-mode `ReadAloud` controller declared its `UtteranceProgressListener` after the `TextToSpeech.init` block that referenced it, which would have been a use-before-initialization error; moved the listener above `init`.
3. Early drafts of `PagesViewModel.deleteSelected`/`duplicateSelected` put side effects (posting a message, mutating the id counter) inside a `StateFlow.update { }` block, which can run more than once under contention; restructured so the side effects happen once, outside the update block.

### Known weak spots from round 3 (found by reading, not fixed)

- Bookmarks are rewritten as a flat list from what's read back each time one is added or removed, so another PDF reader's bookmark colours or expanded/collapsed state are not preserved.
- The page-grid's drag-to-reorder has basic edge auto-scroll only; moving a page a long distance is faster with "Move to".
- The OCR text layer's line boxes are axis-aligned rectangles from ML Kit, so a strongly skewed scan will have search/select hit-boxes that are a bit off from the slanted text, even though the underlying words are correct.
- `BitmapImageRecoder` (the Android half of compress-keeps-text) could only be type-checked against `android.jar`, not run — the real re-encoding path (decode, scale, `JPEGFactory.createFromImage`) has never executed.
- Read aloud depends entirely on whatever TTS voices are installed on the phone; there's a message for "no engine" and "no voice for this language", but the actual voice quality is unverified.

## 1. What was actually run

| Check | How | Result |
|---|---|---|
| Unit tests (pure Kotlin: page selection, page groups, coordinates, shapes, binarize, folders, formatters, card layout, font guess, colour sampling, text diff) | Compiled with a real Kotlin compiler (2.0.21; the project pins 2.4.0) and run under JUnit | 48 of 48 at the time; the current checkout runs 116 of 116 |
| PDF tools on real PDFs (merge, extract, delete, rotate, split, reorder, insert blank, insert from PDF, crop, password add/remove) | Run on desktop PDFBox 2.0.27, outputs checked with qpdf, pdftotext, pdfinfo | Correct; all outputs valid |
| Form fill (text, check box, radio, drop-down, flatten) | Same | Correct |
| 500-page file | Merge 500+500, extract, delete, reorder, split, crop, rotate, insert, text edit on page 251, 300 marks on one page, 512 MB heap | All finish in well under a second on a desktop; a phone will be slower |
| Edit text in place | LibreOffice-made PDF (subset fonts), reportlab PDFs (standard and embedded fonts); before/after pages rendered and compared by eye | Single-letter changes are visually identical; letters the font lacks are refused correctly |
| Edit text fallback (cover and redraw) | Same files | Works; the redrawn word is in a similar, not identical, font |
| Damaged, empty and wrong-password files | Same | Clear error messages, no crash |
| Type-check of the PDF and core code against the real Android 36 API and PDFBox-Android | kotlinc against android.jar and PDFBox-Android compiled from source | Compiles with no errors (only deprecation warnings) |
| Word editor fuzz | 6 PDFs from Chrome, LibreOffice, Cairo, a rotated page and an object-stream file; 196 random one-letter edits | 112 edited in place, 84 refused because the font lacks the letter, 0 wrong results, 0 crashes. Edited files pass qpdf |
| Chrome-made PDF with Hindi text | Edit an English word next to Devanagari, render before/after | Identical apart from the changed letter; Hindi untouched |
| Room migration 1 to 2 | Same SQL run in SQLite on a version 1 database with data | Rows kept, pinned = 0, resulting tables identical to a fresh install |
| All 16 database queries | Run through SQLite's query planner | No syntax or column errors |
| Resources, manifest, version catalog | XML parse, resource reference check, TOML and alias check | No problems. Gradle itself could not resolve the Android plugin offline, so a real sync is still needed |
| Black-and-white filter | Synthetic shadowed receipt, 2100x2800, 94 ms | Much better than a plain cut-off; sensor noise speckle in dark shadows fixed with a minimum contrast; some speckle remains at hard shadow edges |

## 2. Bugs found and fixed in this round

1. Extract pages with a page number that does not exist produced an empty 0-page PDF without any error. Now every page tool (extract, delete, rotate, trim, insert from PDF) rejects a page that is not in the document.
2. Text replace shifted the whole rest of a line unnaturally when the new word was longer (a width correction that squeezed the space). Removed; the line now flows like a word processor.
3. Searching inside a document restarted the whole-document text read on every key press (slow on long files) and a cancelled search could overwrite a newer one. The read now runs once and is shared; searching waits 250 ms after typing stops.
4. Damaged PDFs showed "Could not read or write the file"; the message now says the PDF may be damaged.
5. Black-and-white filter produced speckle from sensor noise in dark shadows; it now needs a minimum contrast (two new unit tests).
6. Page-limit related caps (OCR 25 and 50 pages, scanner 100) raised to 500.

## 3. Not testable here (needs a phone or emulator)

Everything that uses Android or Google Play services: scanner and ID card capture, PdfRenderer viewing, Compose layout, gestures, biometric lock, share and print, Room migration, file pickers, handwriting fonts, system font fallback, the whole screen flow. The full app now compiles with Android Gradle on Termux (`:app:assembleDebug` produces a debug APK), but none of it has been run on a phone. First-run UI and runtime errors are still possible.

## 4. Known weak spots (found by reading or measuring, not fixed)

- Black and white on a very large photo needs roughly 150 MB of memory at 6 megapixels; on a low-memory phone it may stop with a "not enough memory" message instead of finishing.

- Several screens catch `Exception` around suspend code, which also swallows a cancelled job; harmless in practice but untidy.
- Navigation state is lost if Android kills the app in the background.
- The in-place text edit needs a plain page content stream; PDFs that draw text inside nested forms fall back to cover-and-redraw.
- ~~Compress rebuilds pages as pictures, so the text layer is lost.~~ Fixed in round 3: compress now has a "keep text" mode; see section 0 above. The old rebuild-as-pictures behaviour is still there as a "smallest" option.
- ~~Reorder is typed numbers, not drag and drop; trim has no preview.~~ Reorder got a drag-and-drop page grid in round 3 (see section 0); trim still has no preview.
- ~~No page thumbnails strip in the viewer~~; fixed in round 3 (see section 0). Accessibility labels are still thin (not revisited this round).

## 5. Comparison with market apps (UI and top bars)

| Area | Adobe Acrobat Reader | CamScanner / Adobe Scan | JAScanner now |
|---|---|---|---|
| Home layout | Bottom tabs, search, recent files | Bottom tabs, large scan button | Bottom tabs (Library, Tools, Settings), search field, filters, sort and grid/list buttons. Comparable |
| Scan entry | Separate app | One big scan button with a menu (batch, ID card, import) | Scan button now opens a menu: document, ID card, photos to PDF, import. Comparable |
| Selection mode | Bottom action bar | Bottom action bar | Top bar with Share, Delete, More, and now Select all. Slightly less discoverable |
| Viewer top bar | Back, title, search, share, more | Back, title, share, more | Back, title, search, share (own icon, round 3), more | **Parity** |
| Viewer tools | Page thumbnails, bookmarks, comment, fill and sign in one bar | Edit, share, OCR | Icon `NavigationBar` (round 3): Pages, Marks, Edit, Text, Read, plus zoom buttons over the page | **Parity** on layout; Adobe's is proven on-device |
| Page thumbnails and reorder by dragging | Yes (paid) | Yes | Yes, free, round 3 (page grid with drag reorder, rotate, delete, duplicate) | **Parity on paper**, phone-untested |
| Edit and sign | Strong, paid for editing text | Annotate, sign | Draw, shapes, text, signature, pictures, edit existing words, select/move/resize/delete marks, undo/redo |
| Look and feel | Polished | Polished | A branded Material 3 colour/type/shape system since round 4 (no custom illustrations); unseen on a device |

Biggest UI gaps to close next: bottom action bar for selection, accessibility labels, and a proper first-run tour. (The icon-based bottom bar and the page thumbnail strip with drag reorder, both listed here as gaps as of round 2, were built in round 3 — see section 0 above. Like everything else in this file, none of it has run on a real device yet.)

## 6. Round 5: bugs found and fixed

A fix-everything pass, not tied to one feature. Found by five parallel reads of the whole source tree (one per area), each told to look specifically for resource leaks, off-by-one errors, unhandled exceptions, mutation bugs and coroutine-cancellation bugs; a sixth, independent pass then re-checked every fix below against the real code before this list was finalised.

**Crashes / data loss, fixed:**

1. `PdfNotes.addNote()` threw `IllegalArgumentException` when a PDF's crop box was smaller than the sticky-note icon (24pt) — reachable on a malformed or deliberately tiny page in an opened/imported PDF, not from JAScanner's own scans. Now clamps instead of throwing.
2. `WatermarkLayout.tiled(columns = 1, ...)` made every odd row's single stamp land fully off the page and get silently dropped, emptying that row. Not reachable from today's UI (the one call site always uses 3 columns) but fixed at the source, with a new unit test, since nothing stops a future "choose your own grid" watermark option from passing 1.
3. `CardLayout.stack()` could go negative on a page smaller than twice the margin, producing inverted rectangles, on a malformed ID-card page size. Now clamped.
4. `TextDiff.change()` (used by in-place text editing) could split a UTF-16 surrogate pair in two if a word's emoji was changed to a different emoji sharing the same leading surrogate, handing a lone surrogate to the PDF text-replace code. Fixed, with a regression test.

**Silent data loss or stuck UI, fixed:**

5. `LibraryViewModel`'s search (`idsMatchingText`) had no error handling; a failure there (e.g. malformed stored OCR text) would have permanently frozen the whole Library screen's state, not just search, until it was left and reopened. Now caught and degrades to name-only search instead.
6. `PdfTools.splitRanges()` was the one page tool that never validated page numbers before use (every sibling tool does), so a bad page number gave a generic "Something went wrong" instead of the usual "Page N does not exist" and could, in principle, leave a partial split behind. Now validated up front like its siblings.
7. A fast double-tap on "Apply" in the text editor, or on the scan button on Home, could re-enter before the first tap's work finished: the editor could add two marks for the same edit, and a second scan launch before the first's `IntentSender` returned can throw on some phones. Both now guarded.
8. Fourteen places across the viewer, home, OCR, card-scan, form, tools, QR and signature screens caught `Exception` broadly enough to also catch coroutine cancellation (navigating away mid-share, mid-save or mid-scan), which can leave a screen's state half-updated or show a spurious error for what was really just leaving the screen. All now let cancellation propagate instead of swallowing it; this is the same class of problem as the `PagesViewModel` bug fixed in round 3, found this round in many more places by a dedicated search for the pattern. Later passes extended the same guard to the remaining catch sites in `ui/` and `data/`, including `ToolViewModel.run()` and `OcrIndexer.index()` (see the last paragraph of this section).
9. `DocumentRepository.importFromUri` had the same cancellation-swallowing bug one level down, in the data layer itself (opening a PDF shared from another app).

**Storage leaks and stale UI state, fixed:**

10. The ID-card scanner (`CardScanViewModel`) never deleted a side's old cached photo when it was cleared or re-scanned — every "Remove" or "Rescan" tap leaked one file in the cache forever.
11. A failed form-fill (`FormViewModel.save`) left its half-written output file behind with no database row ever pointing at it, unlike every other tool's save path.
12. `TextInputDialog` (used for every rename/new-folder/new-bookmark prompt — eight call sites) and the editor's sticky-note dialog kept whatever text was typed and not sent the last time they were opened at that same screen position, so reopening either dialog for a *different* item could show stale text left over from a different one. Both now reset correctly per target.
13. A signature's filename used only a millisecond timestamp, so two signatures saved within the same millisecond could overwrite each other. Now unique regardless of timing.
14. Deleting a folder touched three separate database tables as three separate operations; now one transaction, so a process death mid-delete can no longer leave documents or subfolders only half-detached.
15. Searching the library for a literal `%` or `_` was treated as a SQL wildcard instead of the character itself, giving wrong matches. Now escaped.
16. Signature save/delete inside the editor's signature dialog had no error handling at all (every other write path in the app has some); a failed save or delete now shows a message instead of failing silently or crashing.

**Checked and found correct, no change:** `PageList.delete()`'s refusal to delete every page is intentional, documented behaviour, not a bug; `PdfPageRenderer.pageCountOrZero` genuinely never throws (confirmed by reading `open()`), so the one unguarded call to it is safe; `ToolViewModel.run()` and `OcrIndexer.index()` intentionally run in the app-wide scope so a save survives the user leaving the screen, and both now rethrow `CancellationException` (which fires when that scope itself is cancelled, e.g. process shutdown) instead of reporting it as a tool failure; `LibraryViewModel.deleteFolder()`'s filter-clearing only needs to match the folder's own id because deleting a folder detaches its subfolders rather than deleting them, so a viewed subfolder never disappears out from under the filter; resource handling, bitmap recycling, and the rearrange/rotation math throughout `pdf/` were all re-checked and are sound.

Items 7, 10 and 12 above were completed in the later verification pass: the scan and Apply guards now stay set until their result returns, the ID-card cache deletes the old photo on remove/rescan, and both dialogs reset per target. The code now matches the text. `DocumentRepository.delete()` can leave a document's file on disk if the underlying `File.delete()` call itself fails (rare, no security or correctness impact) — left as is rather than adding complexity for a low-value case.

## 7. Round 6: the merged manifest, actually merged

Run with `./gradlew :app:processDebugMainManifest` and `:app:processReleaseMainManifest`, then read from `app/build/intermediates/merged_manifest/` with the merger's blame reports and `app/build/outputs/logs/manifest-merger-*-report.txt`. Debug and release produce the same permission list; the only component difference is Compose's `PreviewActivity` in debug.

| Permission | Merged from | Why it is there |
|---|---|---|
| `CAMERA` | JAScanner's own manifest | The built-in camera fallback; requested at runtime only on that screen |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | `androidx.biometric` 1.1.0 | App lock |
| `ACCESS_NETWORK_STATE` | `androidx.media3-common` 1.9.0 (also declared by `datatransport`) | CameraX video in the fallback camera |
| `INTERNET` | `com.google.android.datatransport:transport-backend-cct` 2.3.3, pulled in by `play-services-mlkit-document-scanner` 16.0.0 | ML Kit telemetry; nothing in JAScanner's own code opens a connection |

No storage or media permission, no notification permission, no location, no foreground service, no `AD_ID` (explicitly removed). `minSdk` 32, `targetSdk` 36.

**The telemetry sends something.** `transport-backend-cct` registers `TransportBackendDiscovery` with `backend:com.google.android.datatransport.cct.CctBackendFactory` and `JobInfoSchedulerService` in both merged manifests, so the uploader is wired in release builds too. In the AAR bytecode (read with `javap`), CCT's default endpoint decodes — the URL is split across strings and reassembled by `StringMerger` — to `https://firebaselogging.googleapis.com/v0cc/log/batch?format=json_proto3`; `mlkit-common` 18.11.0 enables firelog with event type 1 and its sender calls `Transport.send(Event.ofTelemetry(...))` with no opt-out check. Google's [ML Kit terms](https://developers.google.com/ml-kit/terms) and [data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) describe the payload as device and app information, per-installation identifiers, performance metrics, event types and error codes, and state that images, video and text inputs and outputs stay on-device. No opt-out is documented or present in the bundled classes.

**Not verified:** with no phone or emulator, no upload was observed — not its timing, its frequency, nor whether Google's backend accepts it. Everything above comes from the merged manifests and the library bytecode.
