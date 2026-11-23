# JAScanner: build plan and architecture

`aktarjabed.com.jaascanner` · minSdk 32 · Kotlin 2.4.0 · single Gradle module `:app`

## 1. Product idea

Two kinds of apps dominate this space: **PDF readers/editors** (view, annotate, sign, organise, protect) and **document scanners** (capture, crop, clean up, OCR, search). Users end up installing both. JAScanner puts the useful core of each into one small, ad-free, private app:

1. Capture with great edge detection and clean-up (delegated to Google's on-device scanner).
2. Keep everything in one searchable library, including text found *inside* scans.
3. Do the everyday PDF jobs without a subscription: read, mark up, sign, merge, split, rotate, delete, compress, protect, convert, print, share.

Out of scope on purpose: cloud accounts, subscriptions, ads, AI chat, certificate-based digital signatures. (Form filling is in scope and implemented; see the feature matrix.)

## 2. Versions (as pinned in `gradle/libs.versions.toml`)

| Item | Version |
|---|---|
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.4.0 (built-in Kotlin: do **not** apply `org.jetbrains.kotlin.android`) |
| Kotlin | 2.4.0 (Compose compiler plugin uses the same version) |
| KSP | 2.3.11 |
| Compose BOM | 2026.09.00 (Material 3, dynamic colour) |
| Room | 2.8.5 (KSP, `exportSchema = false`) |
| DataStore | 1.2.1 |
| Lifecycle | 2.11.0 |
| activity-compose / core-ktx / fragment / biometric | 1.13.0 / 1.19.1 / 1.9.1 / 1.1.0 |
| Coroutines | 1.10.2 |
| ML Kit | document scanner 16.0.0, text recognition (Latin, Devanagari) 16.0.1, code scanner 16.1.0 |
| PDFBox-Android | 2.0.27.0 |
| ZXing core | 3.5.3 |
| JDK / compileSdk / targetSdk / minSdk | 17 / 37 / 36 / 32 |

If a version no longer resolves, bump it in the catalog; every module reads it from there.

## 3. Architecture

```
MainActivity (FragmentActivity: needed by BiometricPrompt)
  └─ JAScannerRoot  ── theme, snackbar, app-lock gate, screen host
       ├─ AppViewModel   back stack (SnapshotStateList<Screen>), lock state, intent requests
       └─ per-screen ViewModelStoreOwner  (ViewModels die when their screen is popped)
AppContainer (hand-written DI, one per process)
  appScope · Messenger · Room DB · SettingsRepository (DataStore) · DocumentRepository
  SignatureStore · OcrEngine · OcrIndexer · ThumbnailLoader
```

Choices and why:

- **No Hilt, no Navigation library.** Fewer moving parts and fewer version couplings to break on first sync. A tiny stack in `AppViewModel` plus one `ViewModelStoreOwner` per screen gives per-screen ViewModels with correct clean-up.
- **Google Play services document scanner** instead of custom OpenCV: edge detection, perspective correction, filters and gallery import without shipping a computer-vision stack or camera permission handling.
- **Platform `PdfRenderer`** for viewing (fast, no native libs); only one page may be open at a time, so every render is serialised by a mutex in `PdfPageRenderer`.
- **PDFBox-Android** for changing PDFs (merge, split, rotate, delete, protect, unlock, stamps, strokes).
- **Edits are always saved as a new document**; originals are never rewritten.
- **Pure-Kotlin logic is isolated and unit-tested** (`PageSelection`, `PdfCoordinateMapper`, `Formatters`).
- **Long jobs run in `appScope`** so a merge or compress finishes (and lands in the library) even if the user leaves the screen.

### PDF coordinate mapping (the part that is easy to get wrong)

User marks are stored as `(u, v)` fractions of the page *as displayed* (u right, v down). For a page box `(minX, minY, W, H)` the PDF point is:

| /Rotate | X | Y |
|---|---|---|
| 0 | `minX + u·W` | `minY + H − v·H` |
| 90 | `minX + v·W` | `minY + u·H` |
| 180 | `minX + W − u·W` | `minY + v·H` |
| 270 | `minX + W − v·W` | `minY + H − u·H` |

Stamps are drawn centred and rotated by the page rotation (plus any watermark tilt) so they appear upright on screen. `PdfCoordinateMapperTest` checks all four rotations at the corners.

## 4. Feature matrix

| Capability | Where it lives |
|---|---|
| Scan, import images from gallery | `scan/ScannerLauncher`, `ui/home/HomeScreen` |
| Library, folders, favourites, text search | `data/DocumentRepository`, `ui/home/Library*` |
| Viewer, zoom, night mode, find | `ui/viewer/*`, `pdf/PdfPageSource`, `pdf/PdfTextExtractor` |
| Pen, highlighter, shapes, white-out, text, signature, pictures | `ui/editor/*`, `pdf/PdfEditor`, `pdf/Shapes`, `data/SignatureStore` |
| Select, move, resize, delete a placed mark | `pdf/MarkGeometry`, `ui/editor/EditorScreen`, `ui/editor/EditorViewModel` |
| Image clean-up filters, adaptive black and white | `pdf/ImageFilters`, `core/Binarize` |
| Pinch-to-zoom | `ui/components/Pinch` |
| Merge, extract, split (equal or ranges), rotate, delete | `pdf/PdfTools`, `core/PageGroups`, `ui/tools/*` |
| Reorder, insert blank pages, insert pages from another PDF, trim margins | `pdf/PdfTools`, `core/PageGroups.PageOrder`, `ui/tools/*` |
| Fill in form fields | `pdf/PdfForms`, `ui/form/*` |
| Subfolders, pins, default scan folder | `core/FolderPath`, `data/db/*` (migration 1→2), `ui/home/*`, `ui/settings/*` |
| Compress, password, unlock, watermark | `pdf/PdfTools`, `pdf/PdfEditor`, `pdf/TextBitmaps` |
| Images to PDF, PDF to images | `pdf/PdfTools.imagesToPdf`, `pdf/PageImageExporter` |
| OCR + search index | `ocr/OcrEngine`, `ocr/OcrIndexer`, `ui/ocr/*` |
| QR scan and create | `ui/qr/QrScreen` (code scanner + ZXing) |
| Print, share, save a copy | `pdf/PdfPrintAdapter`, `data/ShareHelper`, SAF `CreateDocument` |
| App lock, screenshot block | `security/AppLock`, `ui/lock/LockScreen`, `MainActivity` |
| Open / receive PDFs from other apps | manifest intent filters, `AppViewModel.openExternalPdf` |

## 5. Source tree

See the file listing in the appendix at the end of this file.

## 6. First-sync checklist (riskiest spots first)

1. **Gradle plugins.** AGP 9 built-in Kotlin + KSP + Compose compiler plugin. If sync complains about the Kotlin plugin, check `app/build.gradle.kts` plugin block against the current AGP 9 migration notes.
2. **PDFBox-Android API names** in `pdf/PdfTools.kt` and `pdf/PdfEditor.kt`. These were checked against the 2.0.27.0 source (tag `v2.0.27.0`): `PDDocument.load(File, String)`, `protect`, `setAllSecurityToBeRemoved`, `setPreferAES`, `Matrix.getRotateInstance`, the `AppendMode` content-stream constructor, `JPEGFactory`/`LosslessFactory.createFromImage`, `PDExtendedGraphicsState` alpha setters, `Splitter.setSplitAtPage`, `PDFMergerUtility.mergeDocuments(MemoryUsageSetting)` and `InvalidPasswordException` (package `pdmodel.encryption`) all exist with the signatures used. Only worth revisiting if you bump the library.
3. **ML Kit result classes** in `scan/ScannerLauncher.kt` and `ui/qr/QrScreen.kt`.
4. **Material 3 opt-ins.** A few screens use `@OptIn(ExperimentalMaterial3Api::class)`; remove or add as the compiler asks.
5. **Version drift.** Any library that no longer resolves: bump it in `libs.versions.toml`.
6. **Text editing** (`pdf/PdfTextRuns.kt`, `PdfTextReplace.kt`, `SystemFonts.kt`) checked against PDFBox 2.0.27 source only. Most likely to need real-PDF testing: the flat-text alignment check and the font-code lookup.
7. **Run the unit tests first**; they exercise the pure logic and fail fast.

## 7. Test plan on a device

- Scan 3 pages, confirm the PDF opens, has 3 pages and the name is `Scan yyyy-MM-dd HH.mm`.
- Search the library for a word printed on a scan (needs auto-OCR on).
- Rotate page 2 by 90°, then sign page 2 and check the signature is upright in another PDF viewer.
- Merge two documents; split into 1-page files; delete the last page; try deleting all pages (must refuse).
- Add a password, reopen (viewer shows "cannot be shown" with *Remove the password*), unlock.
- Compress a 20-page scan and compare sizes; share a PDF to another app; open a PDF from Files and from WhatsApp.
- Turn on app lock, background the app for over 30 s, return (must ask to unlock). Turn on screenshot blocking.

- Edit text: on a Word-made PDF change one letter (e.g. 'Rate' to 'Raxe' if x occurs elsewhere), save, open in Chrome and Acrobat: no shift, same font. Then use a letter not on the page: expect the matching-font fallback and the message. Try a scanned page (expect 'No editable text'). Try Cursive.
- 500 pages: import or create a 500-page PDF, open in viewer, editor, run OCR, merge. Set Settings, Pages per scan to 500 and see whether the scanner accepts it.
- ID card: scan front and back with camera, rescan one side, add a third side from the gallery, save on one page and as separate pages; check the card prints at about 8.6 cm wide.
- Editor memory: choose a colour, leave and reopen the editor; Settings, Forget editor memory.
- Select tool: select each kind of mark (pen, highlighter, box highlight, line, rectangle, signature, picture, typed text, edited word, sticky note); drag it to move, drag the corner dot to resize, then delete it. Check a mark at the page edge stops at the edge, a thin diagonal stroke is only picked on the line itself, and the saved PDF matches what the editor showed.

- Races: double-tap "Apply" in the text editor and the Home scan button as fast as possible; only one edit / one scanner launch must happen. Tap Save twice in a tool.
- Dialog reset: rename two documents at the same list position one after the other; the second dialog must show that document's current name, not the first one's leftover text. Repeat for New folder, Add bookmark and the editor's note dialog.
- Rotated text: open a PDF with `/Rotate` 90, 180 and 270. Use Find and Edit text; word boxes must sit on the visible words and a replaced word must come out upright. Add a bookmark and a note to each rotated page and check them in another reader.
- Search: find an accented word and a Devanagari word that exist in a scan; search for a literal `%` and `_` (they must not behave as SQL wildcards). Search after renaming a folder and after clearing the search field.
- Viewer search race: type quickly in Find so several queries start; only the last query's results may appear. Leave the viewer mid-search and reopen it; no stale result or spinner may remain.
- OCR race: start OCR, leave the screen and immediately start OCR again; only one run's text may be stored, and the later run must win. Change the OCR language while a run is in progress; the stored text must match the language that actually ran.
- Bookmark race: add two bookmarks as fast as possible, then reopen the PDF in JAScanner and another reader; both must be present. Do the same for two sticky notes.
- Import limits and locking: share a PDF over 200 MB to JAScanner (refuse with a clear message, no partial import). Share a PDF while the app is locked (it must queue and import only after unlock). Share two PDFs at once from Files/WhatsApp (`ACTION_SEND_MULTIPLE`).
- Output cleanup: cancel a save/share by leaving the screen mid-action, and make a tool fail on a damaged PDF. No zero-byte or half-written file may appear in the library or app storage; a successful save must survive leaving the screen.
- Print ranges: print a 5-page PDF with pages 2-3 selected, using "Save as PDF"; the output must contain exactly pages 2 and 3, in order. Try a single page and an open-ended range.
- Export indices: PDF to JPEG on pages 2 and 4; the saved image names/indices must match the source pages, not 1 and 2 of the export.
- Bulk share: select several documents in the library and share them as PDFs; every selected file must arrive, and the share sheet must not crash when the selection changes.
- Folders: try blank, `..`, `a/b`, a name ending in a dot, and a duplicate name; each must be rejected or normalised without escaping the app's storage.
- Lock grace: set "Lock after being away for" to Immediately, switch away and back (must lock every time), then repeat with 30 seconds, 1 minute and 5 minutes.
- TalkBack: with TalkBack on, check that each Settings switch and radio announces its role and on/off state, that library cards announce their tap and long-press actions, and that the search field and QR/signature buttons have useful labels.
- Gestures: pinch with two fingers, lift one and put it back; the zoom must not jump. Draw with one finger while the other rests on the page; only the drawing finger may leave a mark.
- ID-card cache: remove and rescan each side several times, then check Settings > Apps > JAScanner > Storage; the cache must not grow by one photo per tap.
- Splash and theme: cold-start in light and dark mode; the splash colours must match the first screen with no visible flash. Toggle "Match my wallpaper" and rotate the device.
- Searchable PDF: save a searchable copy of a scan that includes a rotated page; confirm in another reader that the text is selectable and found by search, and that pages which already had real text were left alone.

## 8. Roadmap (the detailed next plan is in `NEXT_PLAN.md`)

Round 3 (31 Oct 2026) built several of these — done items are struck through and say where the code lives; see `NEXT_PLAN.md` and `dev-tools/desktop-harness/round3/` for what was actually run on desktop.

- Pinch-to-zoom and double-tap zoom in the viewer and editor. (Double-tap zoom is still open; pinch-to-zoom already existed.)
- ~~Embed an invisible OCR text layer so scans become searchable PDFs everywhere.~~ `[done]` — `pdf/PdfTextLayer.kt`, reached from the OCR screen's "Save a searchable PDF copy".
- ~~Drag-and-drop page reorder and a thumbnails grid~~ `[done]` — `ui/pages/PagesScreen.kt`, reached from the viewer's Pages tab. Page numbers and headers/footers are still open.
- Multi-select lists in forms; a visual crop preview; ~~bookmarks~~ `[done]` — `pdf/PdfBookmarks.kt`, the viewer's Marks panel. A table of contents as its own view (distinct from the Marks panel's flat/nested list) is still open.
- Underline and strikeout are still open. ~~Sticky-note comments~~ `[done]` — `pdf/PdfNotes.kt`, the editor's Note tool and the viewer's Marks panel. ~~Image (logo) watermark~~ `[done]` — the Watermark tool's Picture mode, which can also repeat across the page. ~~Read aloud~~ `[done]` — `ui/reader/ReadAloud.kt`, reached from the viewer's Read tab, alongside a text-reflow reading mode.
- Zoom about the fingers' focal point and double-tap zoom are still open.
- ~~Move, resize and delete single marks in the editor (today only undo and redo).~~ `[done]` — the Select tool in the editor, backed by `pdf/MarkGeometry.kt`. Text-selection highlight and underline are still open.
- Batch export to a user-chosen folder (SAF tree).
- Widget and quick-settings tile for "Scan".
- Still not attempted at all: Office (Word/Excel/PowerPoint) export and import, cloud upload, an AI assistant, fax — see `COMPARISON.md` section 6 for why these were left out of round 3.
- Baseline profile and R8 tuning after the first release build.

## Appendix: file listing

Generated from the current checkout (build outputs and machine-specific `local.properties` excluded).

```
.gitignore
AUDIT.md
COMPARISON.md
FIX_PLAN.md
LICENSE
NEXT_PLAN.md
PLAN.md
README.md
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/aktarjabed/com/jaascanner/AppContainer.kt
app/src/main/java/aktarjabed/com/jaascanner/JAScannerApp.kt
app/src/main/java/aktarjabed/com/jaascanner/MainActivity.kt
app/src/main/java/aktarjabed/com/jaascanner/core/Binarize.kt
app/src/main/java/aktarjabed/com/jaascanner/core/CardLayout.kt
app/src/main/java/aktarjabed/com/jaascanner/core/ColorSampling.kt
app/src/main/java/aktarjabed/com/jaascanner/core/ContextExt.kt
app/src/main/java/aktarjabed/com/jaascanner/core/FolderNames.kt
app/src/main/java/aktarjabed/com/jaascanner/core/FolderPath.kt
app/src/main/java/aktarjabed/com/jaascanner/core/FontGuess.kt
app/src/main/java/aktarjabed/com/jaascanner/core/Formatters.kt
app/src/main/java/aktarjabed/com/jaascanner/core/Messenger.kt
app/src/main/java/aktarjabed/com/jaascanner/core/PageGroups.kt
app/src/main/java/aktarjabed/com/jaascanner/core/PageList.kt
app/src/main/java/aktarjabed/com/jaascanner/core/PageSelection.kt
app/src/main/java/aktarjabed/com/jaascanner/core/PdfCoordinateMapper.kt
app/src/main/java/aktarjabed/com/jaascanner/core/PixelFilters.kt
app/src/main/java/aktarjabed/com/jaascanner/core/TaskExt.kt
app/src/main/java/aktarjabed/com/jaascanner/core/TextDiff.kt
app/src/main/java/aktarjabed/com/jaascanner/core/TextReflow.kt
app/src/main/java/aktarjabed/com/jaascanner/core/TextSearch.kt
app/src/main/java/aktarjabed/com/jaascanner/core/WatermarkLayout.kt
app/src/main/java/aktarjabed/com/jaascanner/data/DocumentRepository.kt
app/src/main/java/aktarjabed/com/jaascanner/data/SettingsRepository.kt
app/src/main/java/aktarjabed/com/jaascanner/data/ShareHelper.kt
app/src/main/java/aktarjabed/com/jaascanner/data/SignatureStore.kt
app/src/main/java/aktarjabed/com/jaascanner/data/db/AppDatabase.kt
app/src/main/java/aktarjabed/com/jaascanner/data/db/DocumentDao.kt
app/src/main/java/aktarjabed/com/jaascanner/data/db/DocumentEntity.kt
app/src/main/java/aktarjabed/com/jaascanner/ocr/OcrEngine.kt
app/src/main/java/aktarjabed/com/jaascanner/ocr/OcrIndexer.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/BitmapImageRecoder.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/ImageDecoding.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/ImageFilters.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PageImageExporter.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/MarkGeometry.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfBookmarks.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfEditor.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfForms.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfImageCompress.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfNotes.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfPageRenderer.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfPageSource.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfPrintAdapter.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfTextExtractor.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfTextLayer.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfTextReplace.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfTextRuns.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/PdfTools.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/Shapes.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/SystemFonts.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/TextBitmaps.kt
app/src/main/java/aktarjabed/com/jaascanner/pdf/TextRunBuilder.kt
app/src/main/java/aktarjabed/com/jaascanner/scan/ScannerLauncher.kt
app/src/main/java/aktarjabed/com/jaascanner/security/AppLock.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/AppViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/JAScannerRoot.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/ViewModels.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/cards/CardScanScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/cards/CardScanViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/components/Dialogs.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/components/PageView.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/components/Pinch.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/components/ThumbnailLoader.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/editor/EditorScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/editor/EditorViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/editor/SignatureDialog.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/form/FormScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/form/FormViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/home/HomeScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/home/LibraryScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/home/LibraryViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/home/ToolsHome.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/lock/LockScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/nav/Screen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/ocr/OcrScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/ocr/OcrViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/pages/PagesScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/pages/PagesViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/qr/QrScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/reader/ReadAloud.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/reader/ReaderScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/reader/ReaderViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/settings/SettingsScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/theme/Color.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/theme/Shape.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/theme/Theme.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/theme/Type.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/tools/ToolScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/tools/ToolViewModel.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/viewer/ViewerScreen.kt
app/src/main/java/aktarjabed/com/jaascanner/ui/viewer/ViewerViewModel.kt
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/ic_launcher_monochrome.xml
app/src/main/res/drawable/ic_shortcut_scan.xml
app/src/main/res/mipmap-anydpi/ic_launcher.xml
app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
app/src/main/res/values-night/colors.xml
app/src/main/res/values-night/themes.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/xml/file_paths.xml
app/src/main/res/xml/shortcuts.xml
app/src/test/java/aktarjabed/com/jaascanner/core/CardLayoutTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/FolderNamesTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/FormattersTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/PageGroupsTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/PageListTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/PageSelectionTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/PdfCoordinateMapperTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/ReadingFiltersTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/ShapesBinarizeTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/TextEditCoreTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/TextSearchTest.kt
app/src/test/java/aktarjabed/com/jaascanner/core/WatermarkLayoutTest.kt
app/src/test/java/aktarjabed/com/jaascanner/pdf/MarkGeometryTest.kt
app/src/test/java/aktarjabed/com/jaascanner/pdf/TextRunBuilderTest.kt
build.gradle.kts
dev-tools/desktop-harness/Bw.kt
dev-tools/desktop-harness/Driver.kt
dev-tools/desktop-harness/Driver2.kt
dev-tools/desktop-harness/Driver3.kt
dev-tools/desktop-harness/Driver4.kt
dev-tools/desktop-harness/Fuzz.kt
dev-tools/desktop-harness/README.md
dev-tools/desktop-harness/round3/README.md
dev-tools/desktop-harness/round3/Stubs3.kt
dev-tools/desktop-harness/round3/build_tc3.sh
dev-tools/desktop-harness/round3/drivers/PdfFeatures.kt
dev-tools/desktop-harness/round3/drivers/RotTest.kt
dev-tools/desktop-harness/round3/drivers/Smoke.kt
dev-tools/desktop-harness/round3/drivers/TextLayerTest.kt
dev-tools/desktop-harness/round3/kstub_Stubs.kt
dev-tools/desktop-harness/round3/ktest_stub/Stub.kt
dev-tools/desktop-harness/round3/ktest_stub/Stub2.kt
dev-tools/desktop-harness/round3/make_fixtures.py
dev-tools/desktop-harness/round3/run_unit.sh
dev-tools/setup-termux.sh
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
settings.gradle.kts
```
