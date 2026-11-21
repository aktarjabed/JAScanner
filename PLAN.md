# JAScanner: build plan and architecture

`aktarjabed.com.jaascanner` · minSdk 32 · Kotlin 2.4.0 · single Gradle module `:app`

## 1. Product idea

Two kinds of apps dominate this space: **PDF readers/editors** (view, annotate, sign, organise, protect) and **document scanners** (capture, crop, clean up, OCR, search). Users end up installing both. JAScanner puts the useful core of each into one small, ad-free, private app:

1. Capture with great edge detection and clean-up (delegated to Google's on-device scanner).
2. Keep everything in one searchable library, including text found *inside* scans.
3. Do the everyday PDF jobs without a subscription: read, mark up, sign, merge, split, rotate, delete, compress, protect, convert, print, share.

Out of scope on purpose: cloud accounts, subscriptions, ads, AI chat, form filling, certificate-based digital signatures.

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
| JDK / compileSdk / targetSdk / minSdk | 17 / 36 / 36 / 32 |

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

## 8. Roadmap (the detailed next plan is in `NEXT_PLAN.md`)

Round 3 (31 Oct 2026) built several of these — done items are struck through and say where the code lives; see `NEXT_PLAN.md` and `dev-tools/desktop-harness/round3/` for what was actually run on desktop.

- Pinch-to-zoom and double-tap zoom in the viewer and editor. (Double-tap zoom is still open; pinch-to-zoom already existed.)
- ~~Embed an invisible OCR text layer so scans become searchable PDFs everywhere.~~ `[done]` — `pdf/PdfTextLayer.kt`, reached from the OCR screen's "Save a searchable PDF copy".
- ~~Drag-and-drop page reorder and a thumbnails grid~~ `[done]` — `ui/pages/PagesScreen.kt`, reached from the viewer's Pages tab. Page numbers and headers/footers are still open.
- Multi-select lists in forms; a visual crop preview; ~~bookmarks~~ `[done]` — `pdf/PdfBookmarks.kt`, the viewer's Marks panel. A table of contents as its own view (distinct from the Marks panel's flat/nested list) is still open.
- Underline and strikeout are still open. ~~Sticky-note comments~~ `[done]` — `pdf/PdfNotes.kt`, the editor's Note tool and the viewer's Marks panel. ~~Image (logo) watermark~~ `[done]` — the Watermark tool's Picture mode, which can also repeat across the page. ~~Read aloud~~ `[done]` — `ui/reader/ReadAloud.kt`, reached from the viewer's Read tab, alongside a text-reflow reading mode.
- Zoom about the fingers' focal point and double-tap zoom are still open.
- Move, resize and delete single marks in the editor (today only undo and redo); text-selection highlight and underline.
- Batch export to a user-chosen folder (SAF tree).
- Widget and quick-settings tile for "Scan".
- Still not attempted at all: Office (Word/Excel/PowerPoint) export and import, cloud upload, an AI assistant, fax — see `COMPARISON.md` section 6 for why these were left out of round 3.
- Baseline profile and R8 tuning after the first release build.

## Appendix: file listing

```
.gradle/
  8.14.3/
    checksums/
      checksums.lock
    expanded/
    fileChanges/
      last-build.bin
    fileHashes/
      fileHashes.lock
    vcsMetadata/
    gc.properties
  buildOutputCleanup/
    buildOutputCleanup.lock
    cache.properties
  vcs-1/
    gc.properties
app/
  src/
    main/
      java/
        aktarjabed/
          com/
            jaascanner/
              core/
                Binarize.kt
                CardLayout.kt
                ColorSampling.kt
                ContextExt.kt
                FolderPath.kt
                FontGuess.kt
                Formatters.kt
                Messenger.kt
                PageGroups.kt
                PageSelection.kt
                PdfCoordinateMapper.kt
                TaskExt.kt
                TextDiff.kt
              data/
                db/
                  AppDatabase.kt
                  DocumentDao.kt
                  DocumentEntity.kt
                DocumentRepository.kt
                SettingsRepository.kt
                ShareHelper.kt
                SignatureStore.kt
              ocr/
                OcrEngine.kt
                OcrIndexer.kt
              pdf/
                ImageDecoding.kt
                ImageFilters.kt
                PageImageExporter.kt
                PdfEditor.kt
                PdfForms.kt
                PdfPageRenderer.kt
                PdfPageSource.kt
                PdfPrintAdapter.kt
                PdfTextExtractor.kt
                PdfTextReplace.kt
                PdfTextRuns.kt
                PdfTools.kt
                Shapes.kt
                SystemFonts.kt
                TextBitmaps.kt
              scan/
                ScannerLauncher.kt
              security/
                AppLock.kt
              ui/
                cards/
                  CardScanScreen.kt
                  CardScanViewModel.kt
                components/
                  Dialogs.kt
                  PageView.kt
                  Pinch.kt
                  ThumbnailLoader.kt
                editor/
                  EditorScreen.kt
                  EditorViewModel.kt
                  SignatureDialog.kt
                form/
                  FormScreen.kt
                  FormViewModel.kt
                home/
                  HomeScreen.kt
                  LibraryScreen.kt
                  LibraryViewModel.kt
                  ToolsHome.kt
                lock/
                  LockScreen.kt
                nav/
                  Screen.kt
                ocr/
                  OcrScreen.kt
                  OcrViewModel.kt
                qr/
                  QrScreen.kt
                settings/
                  SettingsScreen.kt
                theme/
                  Theme.kt
                tools/
                  ToolScreen.kt
                  ToolViewModel.kt
                viewer/
                  ViewerScreen.kt
                  ViewerViewModel.kt
                AppViewModel.kt
                JAScannerRoot.kt
                ViewModels.kt
              AppContainer.kt
              JAScannerApp.kt
              MainActivity.kt
      res/
        drawable/
          ic_launcher_foreground.xml
          ic_launcher_monochrome.xml
          ic_shortcut_scan.xml
        mipmap-anydpi/
          ic_launcher.xml
          ic_launcher_round.xml
        values/
          colors.xml
          strings.xml
          themes.xml
        values-night/
          themes.xml
        xml/
          file_paths.xml
          shortcuts.xml
      AndroidManifest.xml
    test/
      java/
        aktarjabed/
          com/
            jaascanner/
              core/
                CardLayoutTest.kt
                FormattersTest.kt
                PageGroupsTest.kt
                PageSelectionTest.kt
                PdfCoordinateMapperTest.kt
                ShapesBinarizeTest.kt
                TextEditCoreTest.kt
  build.gradle.kts
  proguard-rules.pro
dev-tools/
  desktop-harness/
    Bw.kt
    Driver.kt
    Driver2.kt
    Driver3.kt
    Driver4.kt
    Fuzz.kt
    README.md
gradle/
  wrapper/
    gradle-wrapper.properties
  libs.versions.toml
.gitignore
AUDIT.md
build.gradle.kts
gradle.properties
NEXT_PLAN.md
PLAN.md
README.md
settings.gradle.kts
```
