# JAScanner

All-in-one document scanner and PDF toolkit for Android. Original code, built from scratch in Kotlin with Jetpack Compose.

- **App name:** JAScanner
- **applicationId / namespace:** `aktarjabed.com.jaascanner`
- **minSdk:** 32 (Android 12L) · **compileSdk:** 37 · **targetSdk:** 36
- **Language:** Kotlin 2.4.0 (AGP 9.4.0 built-in Kotlin, Compose compiler plugin, KSP for Room)

## What it does

| Area | Features |
|---|---|
| Scan | Live edge detection, auto-crop, perspective fix, clean-up filters, multi-page capture up to 500 pages per scan (set in Settings), import from gallery (Google's on-device document scanner), saved as PDF |
| ID cards | **ID card scan**: capture front, back and any number of further sides (up to 20), each from the camera or the gallery, straightened by the scanner; save all sides on one A4 page at real card width, or one page per side |
| Library | Grid or list, folders and subfolders (a folder also shows its subfolders' documents), pin documents to the top, favourites, sort, multi-select, rename, delete, search by name **and by text inside scans**. Choose a default folder for new scans in Settings. |
| Read | Fast PDF viewer, pinch-to-zoom and zoom buttons, night mode, go-to-page, find-in-document (text layer), icon bottom bar (Pages, Marks, Edit, Text, Read), share icon in the top bar, save a copy, print / save as PDF, open PDFs from other apps ("Open with JAScanner" and "Share to JAScanner") |
| Reading mode | Reflows a PDF's real text to fit the screen at a size you choose, with serif/plain and white/sepia/dark paper, and **read aloud** (on-device text-to-speech, pause/resume/stop, 0.8–1.5× speed, a Hindi voice for mostly-Devanagari text) |
| Organise pages | A page-thumbnail grid with drag reorder, move-to-position, rotate, delete and duplicate, saved as a new copy. Jump straight to a page from the viewer |
| Bookmarks and comments | Bookmarks and sticky-note comments stored **inside the PDF itself**, so any reader shows them — add, jump to, and remove from the viewer's Marks panel; comments can also be added as a drawing-tool mark in the editor |
| Forms | Fill in text boxes, check boxes, radio buttons and single-choice drop-downs of PDF forms, optionally flatten, saved as a new copy |
| Edit existing text | **Edit text** tool: tap a word on a real-text PDF; the app detects its font, size and colours. If the original font already has the new letters (a one-letter change nearly always does) the PDF's own text is changed, so it looks identical. Otherwise the word is covered and redrawn in a matching font, or in a handwriting-style font (Cursive, Casual) if you choose |
| Edit and sign | Pen with three widths, freehand highlighter, box highlight, line, arrow, rectangle, ellipse, white-out box, sticky note, typed text (colour, size, typeface incl. Cursive and Casual handwriting styles, today's date, recently typed text one tap away), drawn signature (kept for reuse), your own pictures (logo, stamp, photo), **Select** to move, resize or delete a single mark after placing it, undo and redo, pinch-to-zoom. Saved as a **new copy**; the original is never changed |
| Organise | Merge, extract pages, split into equal parts or into named page ranges, rotate pages, reorder pages (drag in the page grid, or type the new order), insert blank pages, insert pages from another document, trim margins, delete pages |
| Convert | Images to PDF: A0 to A6, US Letter or match each picture; reorder or remove single pictures; clean-up filters (Enhanced, Grayscale, Black and white that copes with shadows, Lighten, Magic colour, Strong contrast). PDF to JPEG images (share or save to gallery) |
| Shrink and protect | Compress, in two modes — **keep text** (re-compresses only the pictures inside the PDF; real text stays real, selectable text) or **smallest** (the original approach: every page becomes a picture) — add password (AES-128), remove password (when you know it), watermark in text or your own picture, stamped once or **repeated across the whole page** |
| OCR | On-device text recognition (Latin and Devanagari scripts): copy, share, save `.txt`, make a scan searchable in the library, or **save a searchable PDF copy** with the recognised text hidden under each scanned page (selectable and searchable in any PDF reader; pages that already have real text are left alone) |
| QR and barcodes | Scan any code, create QR codes and share them as pictures |
| Security | Optional fingerprint / face / screen-lock gate with a configurable delay (immediately, 30 seconds, 1 or 5 minutes), hide app preview and block screenshots |
| Comfort | JAScanner's own branded colour scheme by default (light and dark), with an opt-in "Match my wallpaper" switch for Material You, light/dark/system theme, a splash screen, home-screen shortcut "Scan document" |

## Privacy

- JAScanner's own manifest declares **no permissions** (no camera, storage or internet). `AndroidManifest.xml` has a comment explaining exactly which permissions are expected to show up in a *Merged Manifest* view anyway (camera, internet) and from which library, so you have something concrete to check it against after your first build (*app > manifests > AndroidManifest.xml > Merged Manifest* in Android Studio) rather than taking this section's word for it. It also explicitly removes the Advertising ID permission (`tools:node="remove"` on `com.google.android.gms.permission.AD_ID`) in case a Play-services-based library tries to merge it in, since JAScanner has no ads or analytics to attribute. The same explanation, in plain language, is in the app itself under Settings > Privacy & permissions.
- No ads, no accounts, no in-app purchases, and no analytics code of its own.
- Files stay in the app's private storage until you share or save them. Nothing is uploaded by JAScanner.
- When App lock is on, Settings > Privacy & permissions has a "Lock after being away for" choice: immediately, 30 seconds, 1 minute or 5 minutes. A PDF shared to JAScanner while it is locked is queued, never imported, and is opened only after you unlock.
- Scanning and the barcode scanner come from **Google Play services** (on-device modules that Play services may download on first use); text recognition uses ML Kit. Those Google components follow Google's own privacy practices, which this project cannot change. On a phone without Google Play services the scan button will not work; importing and editing PDFs still do.

## Open it

1. Install **Android Studio Quail 4 (2026.1.4) or newer** with JDK 17.
2. *File > Open* the `JAScanner` folder. Android Studio downloads Gradle 9.6.0 from `gradle/wrapper/gradle-wrapper.properties` and syncs; the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) is included.
3. Run on a device or emulator with Android 12L (API 32) or newer and Google Play services.
4. Unit tests (plain JVM): `./gradlew :app:testDebugUnitTest`.

### Free Termux build (no Android Studio)

On an aarch64 Android device with Termux, `dev-tools/setup-termux.sh` installs a free toolchain:
Termux's OpenJDK 21 and native aarch64 `aapt2`, the Android SDK command-line tools, platform 37
and build-tools 37. It writes `local.properties` and the user-level Gradle setting that replaces
AGP's x86-64 `aapt2` (which cannot run on an Android host). Everything is installed outside the
repository (`$PREFIX` and `~/android-sdk`) and the script can be run again safely.

```sh
bash dev-tools/setup-termux.sh
cd JAScanner-repo
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it on the same phone, open that file from **Files → Downloads** (copy it there first if needed). If `termux-open` shows a parsing error, use the Files app instead; some Android builds reject install intents launched from Termux.

## Honest status

This project was originally **written but not compiled** in the environment that generated it (the sandbox could not reach Maven or Google's repositories). It has since been compiled on an aarch64 Termux device: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` both succeed (**112 JVM tests, 0 failures**, debug APK at `app/build/outputs/apk/debug/app-debug.apk`), after fixing a handful of first-sync API mismatches. It has still never been run on a phone; `PLAN.md` section 7 lists the riskiest spots for device testing.

## Testing status

See `AUDIT.md`: the pure logic (112 unit tests in this checkout) and the PDF code (on real PDFs, including a 500-page file) were run; the Android parts were not.

A further multi-agent audit after the source was imported to GitHub filed 26 issues. `FIX_PLAN.md` and [issue #1](https://github.com/aktarjabed/JAScanner/issues/1) are the roadmap for fixing them; Gradle sync and debug builds now work on Termux, but a device test is still outstanding.

Round 5 was a dedicated bug and robustness sweep of the whole codebase (not tied to one feature): five parallel reviews, one per area, each looking specifically for resource leaks, off-by-one errors, unhandled exceptions, mutation bugs and coroutine-cancellation bugs, followed by an independent re-check of every fix against the real code. 16 confirmed issues were fixed — crashes on malformed input, a search bug that could freeze the whole Library screen, several storage leaks, stale dialog text, and about a dozen places where leaving a screen mid-action was mistaken for a real error. See `AUDIT.md` section 6 for the full list, including what was checked and found to already be correct. The code now compiles as a whole and the pure-Kotlin fixes are covered by the 112-test JVM suite; the Android-side fixes have still not been exercised on a phone, so they remain independently re-checked reading rather than device-proven.

`COMPARISON.md` sets JAScanner against the public listings of Adobe Acrobat Reader and Document Scanner – PDF Creator, feature by feature, and says where JAScanner is ahead, level, behind or missing.

## Known limits

- The custom branded colour scheme (`ui/theme/Color.kt`) was hand-authored following Material 3's tonal-role conventions, not generated by Material Color Utilities or checked with a contrast-auditing tool, so it has not been verified against WCAG contrast ratios in every role combination. The splash screen, the pill-shaped search bar, the centred home top bar and the grouped settings cards have not been seen on a device or emulator, like the rest of the UI.
- Page counts: JAScanner asks the Google scanner for up to 500 pages; Google may enforce a lower limit on some devices (not verified). If so, scan in several sessions and use Merge. OCR indexing now covers up to 500 pages but stores at most 400,000 characters per document. Images to PDF accepts any number of pictures ("From files" has no cap; the gallery picker may cap each pick at about 100, repeat with Add more).
- The editor has no cap on marks, edits or pages, only the phone's memory. Very long sessions on a 500-page file are untested.
- Edit text: only words in real text (not scans), one word at a time, horizontal text. In-place editing needs a plain page content stream and letters the font already contains; the fallback is a flat-colour cover, which shows on photos or gradients and leaves the old text inside the file. Complex scripts are not shaped in fallback text. Handwriting styles come from the phone's own fonts (Android's cursive and casual families) and are drawn as pictures, not selectable text.
- The editor remembers colours, widths, typeface and recent text on this phone only; Settings has a button to forget it. Unsaved marks are not auto-saved.
- Select picks up marks placed in the current editor session, not annotations or form fields already inside the PDF. Moving and resizing happen before Save; undo and redo still step through whole marks (add/remove), so a move or resize is not separately undoable.
- Text recognition covers Latin and Devanagari scripts only.
- White-out only covers content; the text underneath stays inside the file, so it is not secure redaction.
- Pinch-to-zoom scales the page about its top-left, not about your fingers, and drawing needs one finger (two fingers zoom).
- Form filling covers text boxes, check boxes, radio buttons and single-choice lists. Multi-select lists, push buttons and signature fields are shown but cannot be changed. Text in scripts other than Latin may not display in filled fields.
- Reorder: the page-organiser grid supports drag and drop (with basic edge auto-scroll; "Move to" is faster for long jumps) and the old typed-order tool is still there for quick cases.
- OCR can now embed an invisible, selectable text layer into the PDF ("Save a searchable PDF copy"), as well as just indexing the text for library search. It only adds the layer to pages that don't already have real text, and the line boxes are axis-aligned rectangles (no per-character shaping), so very skewed scans will have a text layer that is searchable but not pixel-perfectly aligned.
- Compress has two modes: "Keep text" re-compresses only the pictures and leaves real text as real text (a PDF with no pictures will not shrink this way); "Smallest" is the old behaviour — every page becomes a picture, so any existing text layer is dropped and OCR re-runs automatically if enabled.
- Bookmarks are rewritten as a flat list from what JAScanner reads back each time a bookmark is added or removed, so another reader's bookmark colours or expanded/collapsed state are not preserved (the title, page and nesting level are).
- Read aloud uses the phone's installed text-to-speech voices; a phone with none installed, or none for the detected language, shows a message rather than silently failing.
- Navigation state is not restored after the system kills the app in the background; the library always is.

## Originality

The feature list was drawn from the public Play Store descriptions of PDF-reader and document-scanner apps (Adobe Acrobat Reader and Document Scanner – PDF Creator). No code, assets, names or branding were copied, nothing was decompiled, and no third-party app was modified. The launcher icon is an original vector drawing.
