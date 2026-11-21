# Desktop test harness — round 3 additions (not part of the app build)

Added for the "fully implement the most important missing features" pass: embedded OCR text
layer, bookmarks stored in the PDF, sticky-note comments, compress-that-keeps-text, and page
rearrange (reorder/rotate/delete/duplicate). Same approach as the round-1/2 harness one level up:
desktop Apache PDFBox instead of PDFBox-Android, Android calls stubbed out, run with plain
`kotlinc`/`java`, no Gradle, no emulator.

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
- `run_unit.sh` — compiles `core/` (minus the two Android-only files) and every unit test under
  `app/src/test` with a plain Kotlin compiler and runs them under JUnit 4. This is the same script
  that produced the "72 tests pass" result; it covers `PageListTest` and `ReadingFiltersTest` from
  this round too, since those are pure-Kotlin and need no PDFBox stub.
- `drivers/Smoke.kt`, `TextLayerTest.kt`, `RotTest.kt`, `PdfFeatures.kt` — the actual test code
  that was run.
- `kstub_Stubs.kt`, `Stubs3.kt`, `ktest_stub/Stub.kt`, `ktest_stub/Stub2.kt` — the Android/PDFBox
  stand-ins these drivers and tests compiled against.

Both scripts read their external paths from environment variables (with defaults matching this
sandbox), so point them at your own machine instead of editing the scripts:

| Variable | Needed by | What it is |
|---|---|---|
| `JASCANNER_SRC` | both | path to the app's `app/src/main/java/.../jaascanner` (`build_tc3.sh`) or `app/src` (`run_unit.sh`) — defaults to the right place if this folder stays inside the project |
| `GRADLE_LIB` | both | a Gradle distribution's `lib/` folder, for the Kotlin compiler, stdlib, coroutines, JUnit and hamcrest jars it ships (built against Gradle 8.14.3's bundled Kotlin 2.0.21, one minor version behind the project's pinned 2.4.0 — close enough to catch real mistakes, not a stand-in for the real compiler) |
| `PDFBOX_CLASSES` | `build_tc3.sh` | a directory of compiled `.class` files for desktop Apache PDFBox 2.0.27 (build pdfbox+fontbox from source, or unjar the release jars into one tree) |
| `ANDROID_JAR` | `build_tc3.sh` | an `android.jar` stub (real method signatures, `UnsupportedOperationException` bodies) to satisfy the `android.graphics.*` calls inside the sed'd files — the "android-36" jar from the Sable/android-platforms repository was used here, the same one `AUDIT.md` mentions for the round-1/2 type-check |
| `WORK_DIR` | both | scratch output folder, default `./out` next to the script |

## Test PDFs

`build_tc3.sh` expects test PDFs in `<WORK_DIR>/pdf/` (it `cd`s into `WORK_DIR` before running
the driver, same as the drivers' relative `pdf/...` paths). They are not shipped here — the
largest is 13.7 MB and none of them are this project's own content — but this is what each one
needs to contain to repeat the checks:

- `scan.pdf` + `scan_boxes.tsv` — an image-only (no text layer) PDF of a few pages, with a
  tab-separated file of `page, text, u0, v0, u1, v1` boxes (fractions of the page, u right, v
  down) to feed `PdfTextLayer.add`. Made by rendering a few pages of real-looking text to images
  with no underlying text, and noting word boxes by hand or with a throwaway OCR run.
- `photos.pdf` — three noisy photographed pages plus some pages with real text, the kind of file
  `PdfImageCompress` is meant for (it leaves the real-text pages alone and only re-encodes
  pictures). Any multi-megapixel JPEGs dropped onto PDF pages work.
- `book.pdf` — half a dozen pages of normal paragraph text, for bookmarks, notes and compress's
  "pages that already have text" skip path.
- `rot.pdf` — a scanned-looking PDF with pages at `/Rotate` 90, 180 and 270, to check the text
  layer and notes land right-side up through `PdfCoordinateMapper`.

`qpdf`, `pdftotext -bbox`, `pdftoppm`, `pdfimages` and `pypdf` were used to check the outputs
(`qpdf --check` on everything written; `pdftotext -bbox` to confirm text-layer boxes land where
the picture's text actually is; `pdftoppm`-rendered pages compared by eye for the invisible text
layer; `pdfimages`/file size for compress; `pypdf` to read back bookmarks and notes).

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
- **`PageList` / `TextReflow` / `PixelFilters` / `WatermarkLayout`** (pure Kotlin, no PDF
  involved): covered by `run_unit.sh`'s 72 passing tests, not by `build_tc3.sh`.

## What this does NOT verify

Everything that only exists once Compose, ML Kit and Android's `PdfRenderer` are real: the Pages
screen's drag-to-reorder UI and its thumbnails, the Reader screen's reflow and read-aloud (the
`ReadAloud`/TextToSpeech class itself was not run anywhere, desktop or phone), the Editor's note
dialog and marker, the Viewer's new bottom bar and bookmarks/comments panel, `BitmapImageRecoder`
(only type-checked against `android.jar`, never run — the desktop driver uses a different
`ImageRecoder` implementation), and ML Kit actually producing the `RecognisedLine` boxes that
`PdfTextLayer` consumes (the driver above feeds it hand-made boxes instead). All of that needs a
real device or emulator.
