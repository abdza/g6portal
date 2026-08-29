#!/usr/bin/env python3
"""Builds docs/developer-guide.html from its source and screenshots.

The published guide has every screenshot inlined as a data: URI so it is a single
self-contained file. Keeping the source and the PNGs separate is what makes it
editable: edit developer-guide.src.html (or drop a new PNG into guide-shots/),
run this, and the built file is regenerated.

    python3 docs/build-developer-guide.py

Placeholders in the source look like {{IMG:13_tracker_fields}} and resolve to
guide-shots/13_tracker_fields.png. An unresolved placeholder or a missing file is
an error, so the built file can never ship a broken image.
"""
import base64
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "developer-guide.src.html"
OUT = HERE / "developer-guide.html"
SHOTS = HERE / "guide-shots"

html = SRC.read_text()
missing: list[str] = []
used: set[str] = set()
raw_bytes = 0


def inline(match: re.Match) -> str:
    global raw_bytes
    name = match.group(1)
    png = SHOTS / f"{name}.png"
    if not png.exists():
        missing.append(name)
        return ""
    data = png.read_bytes()
    raw_bytes += len(data)
    used.add(name)
    return "data:image/png;base64," + base64.b64encode(data).decode()


html = re.sub(r"\{\{IMG:([A-Za-z0-9_]+)\}\}", inline, html)

if missing:
    sys.exit(f"missing screenshots in {SHOTS}: {', '.join(sorted(set(missing)))}")

leftover = re.findall(r"\{\{[^}]+\}\}", html)
if leftover:
    sys.exit(f"unresolved placeholders: {', '.join(sorted(set(leftover)))}")

OUT.write_text(html)
print(f"embedded {len(used)} screenshots ({raw_bytes / 1e6:.2f} MB of PNG)")
print(f"wrote {OUT.relative_to(HERE.parent)} ({len(html) / 1e6:.2f} MB)")

unused = sorted({p.stem for p in SHOTS.glob("*.png")} - used)
if unused:
    print("not referenced by the guide:", ", ".join(unused))
