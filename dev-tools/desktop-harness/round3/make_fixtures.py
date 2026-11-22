#!/usr/bin/env python3
"""Generate the small PDF fixtures used by the round-3 desktop drivers.

No third-party packages: this writes a minimal, valid PDF 1.4 file directly. It is not a
replacement for real scanned documents, but it makes the harness reproducible from a clean
checkout instead of depending on five PDFs that only existed in one sandbox.

Usage:
    python3 make_fixtures.py [output-dir] [--big]

Creates (in output-dir, default ./pdf):
    book.pdf   6 pages of ordinary text
    rot.pdf    3 pages with /Rotate 90, 180 and 270
    scan.pdf   2 image-only pages (no text layer)
    scan_boxes.tsv  hand-made word boxes for scan.pdf
    photos.pdf 3 pages with an embedded RGB image
    big500.pdf 500 text pages (only with --big)
"""

import os
import sys
import zlib

PAGE_W = 612
PAGE_H = 792


def pdf_string(value: str) -> bytes:
    escaped = value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    return ("(" + escaped + ")").encode("latin-1", "replace")


def text_stream(lines):
    out = [b"BT", b"/F1 14 Tf", b"72 720 Td"]
    for index, line in enumerate(lines):
        if index:
            out.append(b"0 -22 Td")
        out.append(pdf_string(line) + b" Tj")
    out.append(b"ET")
    return b"\n".join(out)


def image_object() -> bytes:
    # 8x8 RGB gradient, Flate-compressed. PDFBox sees a real image, which is all the compress
    # driver needs; it is deliberately tiny so the repository stays small.
    pixels = bytearray()
    for y in range(8):
        for x in range(8):
            pixels += bytes((x * 32, y * 32, (x + y) * 16))
    compressed = zlib.compress(bytes(pixels))
    header = (
        b"<< /Type /XObject /Subtype /Image /Width 8 /Height 8 "
        b"/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
        + str(len(compressed)).encode()
        + b" >>\nstream\n"
    )
    return header + compressed + b"\nendstream"


def image_stream():
    return b"q 400 0 0 400 100 300 cm /Im1 Do Q"


def write_pdf(path, pages):
    """pages: list of dicts with optional 'text' (list of lines), 'rotate' (int), 'image' (bool)."""
    objects = {}
    # 1 catalog, 2 pages tree, 3 font, 4+ page/content/image objects.
    objects[1] = b"<< /Type /Catalog /Pages 2 0 R >>"
    objects[3] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

    kids = []
    next_id = 4
    for page in pages:
        image_id = None
        if page.get("image"):
            image_id = next_id
            next_id += 1
            objects[image_id] = image_object()

        content_id = next_id
        next_id += 1
        content = b""
        if page.get("image"):
            content += image_stream()
        if page.get("text"):
            if content:
                content += b"\n"
            content += text_stream(page["text"])
        objects[content_id] = (
            b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"\nendstream"
        )

        page_id = next_id
        next_id += 1
        resources = b"<< /Font << /F1 3 0 R >>"
        if image_id is not None:
            resources += b" /XObject << /Im1 " + str(image_id).encode() + b" 0 R >>"
        resources += b" >>"
        page_dict = (
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
            + str(PAGE_W).encode()
            + b" "
            + str(PAGE_H).encode()
            + b"] /Resources "
            + resources
            + b" /Contents "
            + str(content_id).encode()
            + b" 0 R"
        )
        if page.get("rotate"):
            page_dict += b" /Rotate " + str(page["rotate"]).encode()
        page_dict += b" >>"
        objects[page_id] = page_dict
        kids.append(page_id)

    objects[2] = (
        b"<< /Type /Pages /Count " + str(len(kids)).encode() + b" /Kids ["
        + b" ".join(str(k).encode() + b" 0 R" for k in kids)
        + b"] >>"
    )

    # Write objects in numeric order and remember their byte offsets for the xref table.
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for object_id in sorted(objects):
        offsets[object_id] = len(out)
        out += str(object_id).encode() + b" 0 obj\n" + objects[object_id] + b"\nendobj\n"
    xref_at = len(out)
    size = max(objects) + 1
    out += b"xref\n0 " + str(size).encode() + b"\n"
    out += b"0000000000 65535 f \n"
    for object_id in range(1, size):
        offset = offsets.get(object_id, 0)
        out += ("%010d 00000 n \n" % offset).encode()
    out += (
        b"trailer\n<< /Size " + str(size).encode() + b" /Root 1 0 R >>\nstartxref\n"
        + str(xref_at).encode() + b"\n%%EOF\n"
    )
    with open(path, "wb") as handle:
        handle.write(out)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out_dir = args[0] if args else os.path.join(os.path.dirname(os.path.abspath(__file__)), "pdf")
    big = "--big" in sys.argv
    os.makedirs(out_dir, exist_ok=True)

    paragraph = [
        "JAScanner desktop fixture",
        "The quick brown fox jumps over the lazy dog.",
        "Pack my box with five dozen liquor jugs.",
        "Bookmarks, notes and compression are checked against this page.",
    ]
    write_pdf(os.path.join(out_dir, "book.pdf"), [{"text": paragraph} for _ in range(6)])

    write_pdf(
        os.path.join(out_dir, "rot.pdf"),
        [
            {"text": ["Rotated ninety degrees", "The text layer must still land upright."], "rotate": 90},
            {"text": ["Rotated one hundred and eighty degrees", "Second line."], "rotate": 180},
            {"text": ["Rotated two hundred and seventy degrees", "Last line."], "rotate": 270},
        ],
    )

    write_pdf(os.path.join(out_dir, "scan.pdf"), [{"image": True}, {"image": True}])

    write_pdf(
        os.path.join(out_dir, "photos.pdf"),
        [{"image": True}, {"image": True, "text": ["A page with a picture and real text."]}, {"image": True}],
    )

    with open(os.path.join(out_dir, "scan_boxes.tsv"), "w", encoding="utf-8") as boxes:
        boxes.write("0\tHello scan\t0.10\t0.20\t0.45\t0.24\n")
        boxes.write("0\tsecond line\t0.10\t0.26\t0.50\t0.30\n")
        boxes.write("1\tAnother page\t0.10\t0.20\t0.45\t0.24\n")

    if big:
        write_pdf(
            os.path.join(out_dir, "big500.pdf"),
            [{"text": ["Page %d of the 500-page fixture." % (i + 1)]} for i in range(500)],
        )

    print("fixtures written to", out_dir)


if __name__ == "__main__":
    main()
