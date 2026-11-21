# JAScanner compared with Adobe Acrobat Reader and Document Scanner – PDF Creator

Written 29 Oct 2026, updated 31 Oct 2026 after a round of implementing the features this file had flagged as most important. Read this first: **JAScanner has never been built with Gradle or run on a phone.** The other two apps have hundreds of millions and tens of millions of installs. Every "ahead" below is about a specific feature or about price and privacy, not about overall quality.

**What changed since 29 Oct:** an embedded, invisible OCR text layer (searchable PDF, not just library search); compress that keeps real text as text; a page-thumbnail grid with drag reorder, rotate, delete and duplicate; bookmarks and sticky-note comments stored inside the PDF; an image watermark that can repeat across the page; reading mode with text reflow and read-aloud; three more clean-up filters (Lighten, Magic colour, Strong contrast); an icon bottom bar and a share icon in the viewer. Office export/import, cloud upload, an AI assistant and fax were deliberately not attempted — see section 6.

## 1. What "using rea" means here

rea (morluto/rea) is a reverse-engineering toolkit. For Android it opens an APK with JADX and reports the manifest, permissions, classes and decompiled methods.

- **Not done: rea on the Adobe or Document Scanner APKs.** That would mean decompiling two commercial apps to see how they work. JAScanner's rule from the start has been original code only, and rea's own `DISCLAIMER.md` also lists "copying proprietary software or reproducing its protected code and assets" as something it does not support. Their APKs could not be fetched here either (Play Store is not reachable from the sandbox shell).
- **Not done: rea on JAScanner.** There is no APK yet, so there is nothing for it to open. The `apk-out` folder in the sandbox holds only compiled class files for type-checking.
- **Done instead:** the other apps are compared from their public Google Play listings and Adobe's public help pages (sources at the end). JAScanner's side comes from its own tests (`AUDIT.md`).
- **Where rea fits:** once you have built a debug APK, rea can check JAScanner itself. See section 5.

## 2. What the public listings say

| | Adobe Acrobat Reader: Edit PDF | Document Scanner – PDF Creator |
|---|---|---|
| Scale | 500M+ downloads, 4.5 stars, Editors' Choice | 50M+ downloads, 4.6 stars |
| Money | Contains ads and in-app purchases. Subscription for paid features, 7-day trial | Contains ads. The listing gives no price or free/premium split, and mentions "premium document filters" |
| Free per listing | Fill & Sign, Liquid Mode, annotate (sticky notes, comments, highlight), cloud file links, access to Adobe Scan scans | Scanning, enhancement, PDF creation, folders, sharing, QR codes |
| Paid per listing | AI Assistant, edit text and images, OCR, merge and organise pages, convert and export (Word, Excel, PowerPoint, images), compress and password-protect, read aloud (better voices), Google Drive editing | Premium filters |
| Data safety (developer-reported) | May share app activity and device IDs with third parties; data encrypted in transit; deletion on request | May share device IDs; data encrypted in transit; "data can't be deleted" |

## 3. Feature by feature

Key: **Ahead** = JAScanner does it and the listing does not show it for that app, or JAScanner gives it free where the other app charges. **Parity** = both do it. **Behind** = the other app does it better or JAScanner lacks it. **Missing** = not built.
"Run" means JAScanner's code for it was run on a desktop (see `AUDIT.md`). "Phone-untested" means the Android parts were never run.

| Feature | Adobe Acrobat Reader | Document Scanner | JAScanner | Verdict |
|---|---|---|---|---|
| View, zoom, search text | Yes, plus Liquid Mode reflow | Basic viewer | Viewer, night mode, go-to-page, find in text, icon bottom bar, share icon. Plus a separate reflow + read-aloud reading mode (below). Phone-untested | **Behind** Adobe on reading comfort (Liquid Mode also reflows images and tables; ours is text-only), **narrower gap** than before |
| Annotate | Sticky notes, comments, highlight (free) | Not named in the listing | Pen, highlighter, box highlight, line, arrow, shapes, text, pictures, **sticky notes as real PDF annotations**, undo/redo. Notes **run** on real PDFs (appearance streams, flags, rotated pages) | **Parity** on comments and drawing |
| Fill forms and sign | Free | Not named | Text, check box, radio, single-choice list, flatten; drawn signature. Form code **run** on real PDFs | **Parity**. Adobe has more form types |
| Edit existing text | Paid; text and images, paragraphs | No | One word at a time on real-text PDFs, free. Same-font in-place change, fallback cover-and-redraw. 196 random edits **run**: 112 in place, 84 refused, 0 wrong. No paragraph or image editing | **Ahead on price only**, **Behind** on power |
| Handwriting-style text | Not named | Not named | Cursive and Casual faces. Font availability on phones **not checked** | Unverified |
| Editor memory (colours, widths, recent text) | Not named | Not named | Yes, local only, with a forget button | Small **Ahead** |
| Reading mode, read aloud | Liquid Mode reflow (free); read aloud is a paid, better-voice feature | No | Text reflow (size, serif/plain, paper colour) and read aloud, both free, using the phone's own TTS voices. Reflow/chunking logic **run** (72 unit tests); the TTS controller itself phone-untested | **Ahead on price**, **Behind** on Liquid Mode's image/table reflow and Adobe's higher-quality voices |
| OCR | Paid; makes the PDF searchable and editable | "Upcoming" in the listing | On-device (Latin and Devanagari): copy, share, .txt, searchable in the library, **and now an embedded invisible text layer** ("Save a searchable PDF copy") so any reader can select and search it. Text-layer placement, rotation handling and odd-input cases **run** on real PDFs; ML Kit box detection itself phone-untested | **Ahead** of Document Scanner, **close to parity** with Adobe on the searchable-PDF feature itself (Adobe's OCR is also said to make text editable, which JAScanner's does not) |
| Scan capture | Separate Adobe Scan app | Auto and manual enhance, smart crop, noise removal, flashlight | Google's on-device scanner: edge detection, crop, perspective, filters, gallery import, up to 500 pages requested. Phone-untested | **Parity on paper**, unproven |
| Filters | Not named | B/W, Lighten, Color, dark; premium filters | Enhanced, Grayscale, Black and white, **Lighten, Magic colour, Strong contrast**. B/W handles shadows (synthetic test, speckle at hard edges remains); the three new filters **run** on synthetic images only | **Closer to parity** on filter count, still unproven on real photos for the new ones |
| ID card, multi-side | Not named | Not named in the listing text I read | Up to 20 sides, camera or gallery, one A4 page at real card width or one page per side. Layout maths **run**; capture phone-untested | **Ahead on paper**, unproven |
| Up to 500 pages | Not stated | Not stated | PDF tools **run** on 500 pages. Google may cap the scan size lower | Unverified for scanning |
| Merge, split, extract, rotate, delete, insert, crop | Paid | Split with advanced options | All of them, free, **run** on real PDFs. Split by page ranges included | **Ahead** on price, **Parity** on function |
| Reorder pages | Drag (paid) | Not named | **Drag and drop in a page-thumbnail grid**, free, plus rotate/delete/duplicate; the old typed-order tool is also still there. Rearrange logic (order, rotation, duplicates, base-rotation handling) **run** on real PDFs; the drag gesture itself is phone-untested | **Parity** on the drag interaction (unverified on a phone), **ahead on price** |
| Page thumbnails, bookmarks | Yes | Not named | **Both now built**: a thumbnail grid (above) and bookmarks stored inside the PDF, shown and editable from the viewer's Marks panel. Bookmark add/remove/nesting **run** on real PDFs; the grid UI phone-untested | **Parity on paper**, Adobe's is proven on-device and ours is not |
| Images to PDF, page sizes | Paid converter | A1 to A6, Postcard, Letter, Note | A0 to A6, Letter, match picture. Per-picture reorder and remove | **Parity** |
| PDF to JPEG | Paid export | Not named | Yes | **Ahead** on price |
| Export to Word, Excel, PowerPoint; convert Office and web pages | Paid | Not named | No | **Missing** (not attempted this round — see section 6) |
| Compress | Paid | Optimise modes | **Two modes now**: "Keep text" re-compresses only the pictures and leaves real text as real text; "Smallest" is the old rebuild-as-images approach. Both **run** on real PDFs (image-heavy, no-picture, and scan files) | **Ahead** of the old parity-with-a-drawback: the text-loss problem is now opt-in, not forced |
| Password | Paid | Not named | Add and remove, AES-128 | **Parity** |
| Watermark | Not named | New image watermark tool | **Text or your own picture**, stamped once or **repeated across the whole page**. Geometry logic (tiling, placement) **run**; not verified visually on a phone | **Parity** with Document Scanner's image watermark |
| Folders, subfolders, pin, default folder | Starred files | Yes | Yes, all four. Migration and queries **run** in SQLite | **Parity** |
| QR and barcodes | Not named | Scan and create | Scan and create | **Parity** |
| Cloud links and upload | OneDrive, Dropbox, Drive | Drive, Dropbox | None. Share sheet and save-a-copy only | **Missing**; also the reason for no internet permission (not attempted this round) |
| Print, fax | Print | Print and fax | Print and save as PDF; no fax | **Parity** minus fax (not attempted this round) |
| Read aloud, AI Assistant | Yes | No | Read aloud: **built**, see above. AI Assistant: **not attempted** this round | **Parity** on read aloud, **Missing** on AI |
| App lock | Not named | Not named | Biometric / screen lock, hide preview, block screenshots. Phone-untested | **Ahead** (not named by the others) |
| Ads, accounts, purchases | Ads, purchases | Ads | None in the code | **Ahead**, and the strongest honest point |
| Data handling | Developer reports sharing with third parties | Developer reports sharing; deletion not possible | JAScanner declares no permissions of its own and uploads nothing. Google ML Kit pieces follow Google's policies. Merged manifest **not checked** | **Ahead on design**, to be verified |
| Maturity | Years of device testing | Years of device testing | Never compiled with Gradle or run on a device | **Far behind** |

## 4. Scoreboard

- **Genuinely ahead**: no ads, accounts or purchases; page tools, form filling and JPEG export without a subscription; ID card multi-side layout; on-device OCR versus a competitor that lists it as upcoming, now with an embedded searchable-PDF text layer; drag-reorder page thumbnails, bookmarks and sticky-note comments at no cost; a free image/tiled watermark; free read aloud; editor memory; app lock.
- **Parity**: scanning basics, folders, QR, images to PDF, annotations and comments, drag reorder, page thumbnails and bookmarks (on paper; Adobe's versions are proven on-device and these are not), watermark, read aloud, compress (now without forcing the text-loss trade-off).
- **Behind**: reading comfort versus Liquid Mode specifically (which also reflows images and tables, not just text), depth of text editing, polish, Adobe's higher-quality TTS voices.
- **Missing**: Office export and import, cloud upload, AI assistant, fax — all four were deliberately left out of this round; see section 6.
- **Unproven**: everything that touches the Android UI, the Google scanner, ML Kit's actual box detection, TTS voices on a real phone, drag gestures, fonts, performance on low-memory phones. This round added code but no device testing, so the gap between "built" and "proven" is now bigger than it was, not smaller.

Word-for-word, the claim "unbeatable" does not hold. The honest claim is: a free, ad-free, offline toolkit that now covers most of what the two apps charge for or advertise — including several things that were gaps a few days ago — plus a few extras, and a shorter but still real list of remaining gaps, none of it proven on an actual phone yet.

## 5. How to use rea on JAScanner itself (not run yet)

After Android Studio builds a debug APK (`app/build/outputs/apk/debug/app-debug.apk`):

1. Get a full JDK 17+ and the pinned `jadx-headless-mcp-0.7.1-all.jar`; set `REA_JADX_MCP_JAR` to it. `docs/android-analysis.md` in rea has the download and SHA-256 check.
2. Run `rea inspect-android-package app-debug.apk` and read:
   - **Permissions in the merged manifest.** Expect no camera, storage or internet from JAScanner. Anything from ML Kit or Play services will appear here, and this is the one place README's privacy claim gets checked for real.
   - **Exported components.** Only the main activity, the "Open with" / "Share to" intent filters and the shortcut should be reachable from other apps.
   - **Class and method counts**, to spot anything unexpected after R8 shrinking.
3. Use `rea inspect-android-class` or `inspect-android-method` only on JAScanner's own classes.

## 6. What to do next

1. Open the project in Android Studio, sync, build, and send the first errors back. This is still the biggest gap, and it has grown: round 3 added a Compose page-grid with drag gestures, a TTS controller, and more screens, none of which has been near a compiler that actually understands Android.
2. Deliberately not attempted this round, in rough order of how often the two listings mention them: Office (Word/Excel/PowerPoint) export and import, cloud upload (Drive/Dropbox/OneDrive), an AI assistant, fax. Each would need either a new library (Office formats), a cloud SDK and account system (upload — a real change to the "no internet permission" privacy story), or a different kind of feature entirely (AI). None were started so as not to half-build any of them.
3. Once the project compiles: table of contents as its own view (bookmarks exist but are shown as a flat/nested list, not a TOC page), visual crop with drag handles, select/move/resize/delete a single editor mark after placing it, text-snapped highlight/underline/strikeout.

## Sources

- [Adobe Acrobat Reader: Edit PDF on Google Play](https://play.google.com/store/apps/details?id=com.adobe.reader&hl=en_US)
- [Document Scanner – PDF Creator on Google Play](https://play.google.com/store/apps/details?id=com.cv.docscanner&hl=en_IN)
- [Acrobat mobile app FAQ (Adobe)](https://helpx.adobe.com/acrobat/mobile-app-faq.html)
- rea: `README.md`, `DISCLAIMER.md`, `docs/android-analysis.md` in the cloned morluto/rea repository
- `AUDIT.md` and `README.md` in this project, and `dev-tools/desktop-harness/round3/README.md` for what was actually run to check the round-3 features above
