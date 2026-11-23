# JAScanner: where it wins, what is missing, and plan v2

Comparison is against the two Play Store listings and Adobe's mobile FAQ only. "Wins" means "offers more for free, or more privately, on paper". The app now compiles with Gradle and its JVM tests pass, but none of it is proven until it runs on a phone.

**Round 3 update:** the items below marked `[done]` were built and are described in `README.md` and `COMPARISON.md` — an embedded OCR text layer, compress that keeps text, a page-thumbnail grid with drag reorder/rotate/delete/duplicate, bookmarks and sticky-note comments stored inside the PDF, an image/tiled watermark, and a reading mode with read-aloud. Everything else below is still accurate.

## 1. Where JAScanner is ahead on paper

| Area | Adobe Acrobat Reader | Document Scanner (com.cv.docscanner) | JAScanner |
|---|---|---|---|
| Price | Merge/organise, compress, password, export and OCR need a subscription | Contains ads and in-app purchases; premium filters | Everything free, no ads, no account |
| Privacy | Account and cloud centred | Listing says data may be shared and cannot be deleted | Files stay on the phone; own manifest declares no permissions (verify the Merged Manifest) |
| Scan + PDF tools in one app | Reader only (scans come from a separate Adobe Scan app) | Scanner with basic PDF tools | One library for both |
| Search inside scans | OCR is paid | OCR listed as "upcoming" | On-device OCR feeds library search |
| Editing marks | Free tier: comments, highlight, fill and sign | Not listed | Pen widths, box highlight, line, arrow, rectangle, ellipse, white-out, text, signature, pictures, select/move/resize/delete marks, undo/redo |
| Forms | Fill and sign (free) | Not listed | Text, check box, radio, drop-down fields, optional flatten |
| Page tools | Paid | Split, images to PDF | Merge, split by parts or ranges, reorder, insert blank or from another PDF, rotate, delete, trim, A0-A6 |
| Image clean-up | n/a | Premium filters | Enhanced, grayscale, shadow-tolerant black and white, free |

## 2. Where it is NOT ahead (be honest)

- Edit existing PDF text: now word-by-word (see README). Whole-paragraph reflow and image editing: not implemented.
- Reading mode now reflows text and reads it aloud (see above), narrowing but not closing the gap with Adobe's Liquid Mode, which also reflows images and tables. AI assistant, PDF Spaces, Adobe Express, cloud accounts: not implemented.
- Word / Excel / PowerPoint conversion, web page to PDF, fax: not implemented.
- Maturity: both apps are proven at scale. JAScanner compiles and its JVM tests pass, but it has never been run on a phone.
- Scan quality is Google's scanner, not ours.

## 3. Editing: what is implemented and what is not

Implemented: pen, highlighter, box highlight, line, arrow, rectangle, ellipse, white-out, typed text, signature, pictures, select/move/resize/delete marks, undo/redo, form filling.

Not implemented:
1. Recolour a mark after placing it. Moving, resizing and deleting are `[done]` — the Select tool (`pdf/MarkGeometry.kt`, `ui/editor/EditorViewModel.kt`): tap a mark to select it, drag to move, drag the corner dot to resize, then Delete selected.
2. Highlight, underline and strikeout that snap to real text.
3. ~~Sticky notes and comments as real PDF annotations.~~ `[done]` — `PdfNotes`, the editor's Note tool, the viewer's Marks panel.
4. Edit or replace existing text and images in the PDF.
5. Secure redaction that removes the underlying text.
6. ~~Searchable-PDF output (invisible OCR text layer).~~ `[done]` — `PdfTextLayer`, the OCR screen's "Save a searchable PDF copy".
7. ~~Bookmarks~~ `[done]` — `PdfBookmarks`, the viewer's Marks panel. Table of contents as a separate outline view, and page numbers/headers/footers, are still not implemented.
8. ~~Page thumbnail grid with drag-and-drop reorder~~ `[done]` — the Pages screen, reached from the viewer's bottom bar. Visual crop with handles is still not implemented (Crop is still a percent-from-each-edge tool).
9. Zoom about the fingers, double-tap zoom.
10. Digital (certificate) signatures; creating new form fields.
11. Office (Word/Excel/PowerPoint) export and import, cloud upload, an AI assistant, and fax — not built, and not attempted this round either; see `COMPARISON.md`.

## 4. Plan v2 (in order)

### Phase 0: make it real (blocks everything)
- ~~Compile it and run the unit tests.~~ `[done]` — `./gradlew :app:testDebugUnitTest` (112 tests) and `:app:assembleDebug` succeed on Termux.
- Run on a real phone: scan, import, every tool once, form fill, app lock. Check the Merged Manifest for added permissions.
- Test the database migrations (1→2 and 2→3) on a phone that had an earlier build.
- Exit: debug build installs and the test plan in PLAN.md section 7 passes.

### Phase 1: editing that people notice
| Item | How | Risk |
|---|---|---|
| ~~Select, move, resize, delete marks~~ `[done]` | Built as planned: marks stay in the editor state, `MarkGeometry` does pure-Kotlin hit-testing, movement and scaling (strokes are hit-tested on their path, not their bounding box), and the PDF is only written on Save. Recolouring is still open | — |
| Text-snapped highlight / underline / strikeout | PDFTextStripper with text positions gives word boxes; tap or drag selects words; draw fill or line marks with existing code | Medium |
| ~~Sticky notes and comments~~ `[done]` | Built as planned: `PDAnnotationText` via `PdfNotes`, a Note tool in the editor, a Marks panel in the viewer | — |
| Redo and per-page clear | Small state changes | Low |

### Phase 2: scans become first-class PDFs
| Item | How | Risk |
|---|---|---|
| ~~Searchable PDF~~ `[done]` | Built close to planned: ML Kit's `textBlocks`/`lines` give boxes (`OcrEngine.recognizePdfPages`); invisible text (render mode 3) is written with `PdfTextLayer`, using a system font (`SystemFonts.pick`, Helvetica fallback with unencodable characters filtered) rather than a bundled OFL font | — |
| ~~Page thumbnails grid with drag reorder~~ `[done]` | Built as planned, plus rotate/delete/duplicate: `LazyVerticalGrid` with `detectDragGesturesAfterLongPress`, saved through the new `PdfTools.rearrange`/`PagePlan` (not the old `reorderPages`, which only reorders) | — |
| Visual crop with handles | Preview page, draggable rectangle, writes crop box | Medium |

### Phase 3: reach Adobe's paid edit features, honestly
| Item | How | Risk |
|---|---|---|
| Replace text | White-out the old text box, overlay new text (not true re-flow); make the limit clear in the UI | Medium |
| Real redaction | Rasterise the redacted page (render, paint black, rebuild page) so no text remains | Medium |
| ~~Bookmarks~~ `[done]`, TOC still open | Built as planned: `PdfBookmarks` reads/writes the PDF outline with PDFBox, shown in the viewer's Marks panel. A dedicated table-of-contents view (vs. the flat/nested list in the panel) is still open | — / Medium |
| Page numbers, headers, footers, Bates numbers | Stamp text per page with a template | Low |

### Phase 4: polish
- Zoom about the fingers, double-tap zoom, smoother large-file scrolling.
- Accessibility labels, large-text check, dark-theme review.
- Baseline profile, R8 check, instrumented tests for scan to save, merge, form fill.
- ~~Optional: read aloud with the phone's own text-to-speech.~~ `[done]` — the Reader screen's reflow view plus `ReadAloud` (Android `TextToSpeech`), reached from the viewer's Read tab.

## 5. Rules for every phase
- Each feature ships with a unit test where the logic is pure, and a line in the device test plan.
- No new permission without a written reason.
- A feature that cannot be done properly is listed as a limit in the README instead of being faked.
