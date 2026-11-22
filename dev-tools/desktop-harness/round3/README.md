# Desktop test harness — round 3 additions (not part of the app build)

Added for the "fully implement the most important missing features" pass: embedded OCR text
layer, bookmarks stored in the PDF, sticky-note comments, compress-that-keeps-text, and page
rearrange (reorder/rotate/delete/duplicate). Same approach as the round-1/2 harness one level up:
desktop Apache PDFBox instead of PDFBox-Android, Android calls stubbed out, run with plain
`kotlinc`/`java`, no Gradle, no emulator.

**This is a pure-logic / PDF-library check only.** It cannot exercise the Android UI, Compose,
`PdfRenderer`, ML Kit, the Android bitmap recoder, drag gestures or text-to-speech. A green run
here means the pure Kotlin and PDFBox code compiled and behaved correctly on the JVM; it is not
a phone test.

## What's here

- `build_tc3.sh` — compiles `pdf/PdfTextLayer.kt`, `PdfBookmarks.kt`, `PdfNotes.kt`,
  `PdfImageCompress.kt`, `PdfTools.kt`, `SystemFonts.kt` and `core/FontGuess.kt`,
  `PdfCoordinateMapper.kt`, `CardLayout.kt`, `PageList.kt` against desktop PDFBox (via a `sed`
  that swaps `com.tom_roush.pdfbox` for `org.apache.pdfbox` and drops the one PDFBox-Android-only
  import), plus the Android stub (`kstub_Stubs.kt`) and the JPEGFactory stub (`Stubs3.kt`, since
  that class is Android-only and the real re-encoding path — `BitmapImageRecoder.kt` — can't run
  here), plus one driver from `drivers/`. Run it as `./build_tc3.sh <DriverFileName> <MainClass>`
  — the main class is Kotlin's generated name for a driver's top-level `main`, i.e. the file name
  with `Kt` appended (`TextLayerTest.kt` → `TextLayerTestKt`).
- `run_unit.sh` — compiles `core/` (minus the two Android-only files), the pure
  `pdf/Shapes.kt` and `pdf/TextRunBuilder.kt`, and every unit test under `app/src/test` with a
  plain Kotlin compiler, then runs them under JUnit 4. This is the script that reproduces the
  **99 tests** reported in `AUDIT.md` and `README.md`. It does not need PDFBox or an Android
  emulator.
- `make_fixtures.py` — generates the PDF fixtures the drivers expect (`book.pdf`, `rot.pdf`,
  `scan.pdf`, `scan_boxes.tsv`, `photos.pdf`, and `big500.pdf` with `--big`) using only the
  Python standard library. Run `python3 make_fixtures.py` from this folder (or pass an output
  directory). No third-party packages and no downloaded PDFs are needed.
- `drivers/Smoke.kt`, `TextLayerTest.kt`, `RotTest.kt`, `PdfFeatures.kt` — the actual test code
  that was run.
- `kstub_Stubs.kt`, `Stubs3.kt`, `ktest_stub/Stub.kt`, `ktest_stub/Stub2.kt` — the Android/PDFBox
  stand-ins these drivers and tests compiled against.

Both scripts use `set -euo pipefail` and fail when the Kotlin compiler exits non-zero; compiler
output is captured to `compile.log` and shown on failure. The earlier scripts ended the compiler
pipeline with `grep ... || true`, which could hide a broken build. A deliberate syntax error in
any compiled file now stops the script with a non-zero exit.

## Versions and paths

Both scripts read their external paths and versions from environment variables, so point them at
your own machine instead of editing the scripts:

| Variable | Needed by | What it is |
|---|---|---|
| `JASCANNER_SRC` | both | path to the app's `app/src/main/java/.../jaascanner` (`build_tc3.sh`) or `app/src` (`run_unit.sh`) — defaults to the right place if this folder stays inside the project |
| `GRADLE_LIB` | both | a Gradle distribution's `lib/` folder for the Kotlin compiler, stdlib, coroutines, reflect and annotations jars. Defaults to `/opt/gradle-9.6.0/lib`; on Termux the Gradle 9.6 wrapper distribution works. |
| `KOTLIN_VERSION` | both | compiler/stdlib version (default `2.3.21`, the version bundled with the Gradle 9.6 distribution used here). Set it to `2.4.0` if your `GRADLE_LIB` has the project's pinned compiler jars. |
| `COROUTINES_VERSION` | both | `kotlinx-coroutines-core-jvm` version (default `1.10.2`, matching `libs.versions.toml`) |
| `ANNOTATIONS_VERSION` | both | JetBrains annotations version (default `24.0.1`) |
| `JUNIT_VERSION`, `JUNIT_JAR` | `run_unit.sh` | JUnit 4 version/path (default `4.13.2`); set `JUNIT_JAR` when JUnit is in the Gradle cache rather than `GRADLE_LIB` |
| `HAMCREST_VERSION`, `HAMCREST_JAR` | `run_unit.sh` | Hamcrest core version/path (default `1.3`) |
| `PDFBOX_CLASSES` | `build_tc3.sh` | a directory of compiled `.class` files for desktop Apache PDFBox 2.0.27 (build pdfbox+fontbox from source, or unjar the release jars into one tree) |
| `ANDROID_JAR` | `build_tc3.sh` | an `android.jar` stub (real method signatures, `UnsupportedOperationException` bodies) to satisfy the `android.graphics.*` calls inside the sed'd files — the "android-36" jar from the Sable/android-platforms repository was used here, the same one `AUDIT.md` mentions for the round-1/2 type-check |
| `WORK_DIR` | both | scratch output folder, default `./out` next to the script |

The project pins Kotlin 2.4.0 in `gradle/libs.versions.toml`. The harness defaults to the
Kotlin 2.3.21 bundled with Gradle 9.6 because that is the compiler available in the environment
where it was last run; this is four minor versions behind the project, not "one minor version"
as an earlier README claimed. Set `KOTLIN_VERSION=2.4.0` and point `GRADLE_LIB` at a distribution
that contains those jars to match the app exactly. Either way the harness is a smoke check, not a
substitute for `./gradlew :app:testDebugUnitTest`.

## Test PDFs

`build_tc3.sh` expects test PDFs in `<WORK_DIR>/pdf/` (it `cd`s into `WORK_DIR` before running
the driver, same as the drivers' relative `pdf/...` paths). Generate them with:

```sh
cd dev-tools/desktop-harness/round3
python3 make_fixtures.py
```

The generator writes small synthetic files. They are enough to exercise the code paths but are
not real scans:

- `book.pdf` — six pages of ordinary text, for bookmarks, notes and compress's "pages that
  already have text" skip path.
- `rot.pdf` — three pages with `/Rotate` 90, 180 and 270, to check the text layer and notes land
  right-side up through `PdfCoordinateMapper`.
- `scan.pdf` + `scan_boxes.tsv` — image-only pages with a tab-separated file of
  `page, text, u0, v0, u1, v1` boxes (fractions of the page, u right, v down) to feed
  `PdfTextLayer.add`.
- `photos.pdf` — pages with an embedded RGB image, for `PdfImageCompress`.
- `big500.pdf` — 500 text pages, only when `--big` is passed.

`qpdf`, `pdftotext -bbox`, `pdftoppm`, `pdfimages` and `pypdf` were used to check the outputs of
the original round (`qpdf --check` on everything written; `pdftotext -bbox` to confirm text-layer
boxes land where the picture's text actually is; `pdftoppm`-rendered pages compared by eye for the
invisible text layer; `pdfimages`/file size for compress; `pypdf` to read back bookmarks and
notes).

## What was actually verified this way

- **Text layer** (`PdfTextLayer`): invisible (renders identically to the untouched page),
  positioned correctly (bbox x-range exact, y within the line's box) on upright and on 90/180/270
  rotated pages, and handles accented text, an unencodable rupee sign (falls back to a font that
  has it, or drops the character rather than writing garbage), a blank line, a zero-size box, and
  an out-of-range page number without crashing or corrupting the file.
- **Compress-keeps-text** (`PdfImageCompress` + the desktop `ImageRecoder`): shrinks a
  picture-heavy PDF at several quality settings, leaves a PDF with no pictures untouched (and the
  tool now reports that case explicitly), and does not touch pages whose text layer the OCR
  button can see is already real.
- **Bookmarks** (`PdfBookmarks`): add, remove, nesting, page clamping, and the bad-page /
  bad-index error paths leave no stray output file and read back correctly with `pypdf`.
- **Notes** (`PdfNotes`): appearance streams are present, rects and the print/no-zoom/no-rotate
  flags are correct, and the icon renders upright on rotated pages.
- **Rearrange** (`PagePlan` / `PdfTools.rearrange`): reordering, rotating, deleting and duplicating
  pages (including a duplicate of an already-rotated page) produce the expected page order and
  absolute rotation, verified with `qpdf --check` and `pdftoppm`.
- **`PageList` / `TextReflow` / `PixelFilters` / `WatermarkLayout` / `TextRunBuilder`** (pure
  Kotlin, no PDF involved): covered by `run_unit.sh`'s 99 passing tests, not by `build_tc3.sh`.

## What this does NOT verify

Everything that only exists once Compose, ML Kit and Android's `PdfRenderer` are real: the Pages
screen's drag-to-reorder UI and its thumbnails, the Reader screen's reflow and read-aloud (the
`ReadAloud`/TextToSpeech class itself was not run anywhere, desktop or phone), the Editor's note
dialog and marker, the Viewer's bottom bar and bookmarks/comments panel, `BitmapImageRecoder`
(only type-checked against `android.jar`, never run — the desktop driver uses a different
`ImageRecoder` implementation), and ML Kit actually producing the `RecognisedLine` boxes that
`PdfTextLayer` consumes (the driver above feeds it hand-made boxes instead). All of that needs a
real device or emulator. See `PLAN.md` section 7 for the device checklist.
