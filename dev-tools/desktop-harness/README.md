# Desktop test harness (not part of the app build)

These drivers were used to exercise the app's PDF code on a normal JVM, without an emulator.
They are kept so you can repeat the checks. They are NOT compiled by Gradle.

How it was done:
1. Build Apache PDFBox 2.0.27 (desktop) from source; PDFBox-Android has the same API under `com.tom_roush.pdfbox`.
2. Copy `pdf/PdfTools.kt`, `PdfForms.kt`, `PdfEditor.kt`, `PdfTextRuns.kt`, `PdfTextReplace.kt`, `SystemFonts.kt` and the `core/` files, replacing `com.tom_roush.pdfbox` with `org.apache.pdfbox`.
3. Replace the few Android-only calls (Bitmap to PDF image, PdfRenderer) with stubs; those paths are not covered.
4. Compile with kotlinc and run a driver: `Driver2` (page tools and forms), `Driver3` (500-page file, damaged and empty files, error messages), `Driver4` (text edits with the look-alike fallback), `Driver` (single in-place word edit).
5. Check outputs with `qpdf --check`, `pdftotext` and `pdftoppm`.

Drivers expect test PDFs (five.pdf, two.pdf, form.pdf, big500.pdf, lo.pdf) in a `pdf/` folder; generate them with reportlab and LibreOffice.

Added in round 2:
- `Fuzz.kt`: swaps one letter in random words of several PDFs (Chrome, LibreOffice, Cairo, rotated, object-stream) and checks the page text changes exactly as intended.
- `Bw.kt`: runs the black-and-white filter's core on a synthetic shadowed receipt and writes before/after images.
- Android check: the app's `core/` and `pdf/` Kotlin files were compiled with kotlinc against `android-36/android.jar` (from the Sable/android-platforms repository) and PDFBox-Android compiled from source, so every Android and PDFBox-Android call in those packages type-checks.

Added in round 3 (searchable-PDF text layer, bookmarks, comments, compress-that-keeps-text, page
rearrange): see `round3/README.md`. Same method, with the scripts parameterised by environment
variable instead of hardcoded sandbox paths, so they can actually be rerun elsewhere.
