"""
Wyrm's own tags (OM, 2026-10-07): appends tools/wyrm-tags/stickers/*.png to
the NTL tag set without touching it.

The NTL set (164 tags) cannot be re-imported (wyrm-tag-import.py reads an old
NTL build), so its sheet and rows are frozen once in tools/wyrm-tags/ntl-sheet.png
and ntl-table.json, taken from the tree as it was. The new sheet is twice as
tall: the NTL sheet unchanged on top (so its rows only halve their v), Wyrm's
stickers packed underneath.

A sticker is drawn upright. A tag hangs off the rope's end with its box's +x
running along the rope away from the snake (tags.c draw_tag), so each sticker is
stored turned 90 degrees anticlockwise: its top faces the rope, and it trails
like a pendant on a string. The rope is tied on the line through the sticker's
centre of mass, at its first solid pixel from the top, so it hangs balanced.
The picker turns the thumbnail back upright (wyrm number >= WYRM_TAG_BASE).

Writes: app/res/textures/wyrm_tags.png, app/src/game/tag_table.h, tag_count.h,
android ui/TagTable.kt, the tags block of ui/SkinCatalog.kt, and the same
engine files into Wyrm iOS/SharedEngine (+ SHA256.json); then run Wyrm iOS's
Scripts/generate-swift-skin-catalog.py.

    python tools/wyrm-tag-append.py
"""
import hashlib
import json
import math
import pathlib
import re

import numpy as np
from PIL import Image, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
WYRM = ROOT.parent
TAGS = ROOT / "tools/wyrm-tags"
ATLAS = ROOT / "app/res/textures/wyrm_tags.png"
HEADER = ROOT / "app/src/game/tag_table.h"
COUNT_HEADER = ROOT / "app/src/game/tag_count.h"
KOTLIN = ROOT / "android/app/src/main/java/com/wyrm/omrajput/ui/TagTable.kt"
CATALOG = ROOT / "android/app/src/main/java/com/wyrm/omrajput/ui/SkinCatalog.kt"
IOS = WYRM / "Wyrm iOS/SharedEngine"

INSET = 21          # NTL's: the art sits this far inside its box
GLOW = 20           # NTL's shadowBlur
ART = 136           # longest side of a sticker's art (NTL's median box is 178 = 136 + 42)
STORE = 0.75        # stored scale on the sheet (NTL's are 0.5)
PADDING = 1
WYRM_TAG_BASE = 100000   # a Wyrm tag's `ntl` field: base + its number; NTL's own stop at 65535
TIE_IN = 5          # the rope is tied this far inside the first solid pixel

ROW = re.compile(r"\{(\d+), (\d+), (-?\d+), (-?\d+), 0x(\w{6}), 0x(\w{6}), (\d+), "
                 r"\{([0-9.]+)f, ([0-9.]+)f, ([0-9.]+)f, ([0-9.]+)f\}\},")
ART_ROW = re.compile(r"TagArt\((\d+), (\d+), (\d+), (\d+)\)")


def freeze_ntl():
    """The NTL set as the tree has it, saved once and reused ever after."""
    sheet, table = TAGS / "ntl-sheet.png", TAGS / "ntl-table.json"
    if sheet.exists() and table.exists():
        return Image.open(sheet).convert("RGBA"), json.loads(table.read_text())
    head = HEADER.read_text(encoding="utf-8")
    rows = ROW.findall(head)
    arts = ART_ROW.findall(KOTLIN.read_text(encoding="utf-8"))
    assert len(rows) == 164 and len(arts) == 164, (len(rows), len(arts))
    assert "#define TAG_ATLAS_HEIGHT 1024" in head
    entries = [{"w": int(r[0]), "h": int(r[1]), "bx": int(r[2]), "by": int(r[3]), "c1": r[4], "c2": r[5],
                "ntl": int(r[6]), "uv": [float(x) for x in r[7:11]], "art": [int(x) for x in a]}
               for r, a in zip(rows, arts)]
    TAGS.mkdir(parents=True, exist_ok=True)
    Image.open(ATLAS).convert("RGBA").save(sheet, optimize=True)
    table.write_text(json.dumps(entries, indent=0))
    return Image.open(sheet).convert("RGBA"), entries


def boxed(art: Image.Image) -> Image.Image:
    """NTL's box: the art at (21, 21) with a blurred black copy behind."""
    w, h = art.width + INSET * 2, art.height + INSET * 2
    box = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    shadow = Image.new("RGBA", art.size, (0, 0, 0, 255))
    shadow.putalpha(art.getchannel("A"))
    sil = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sil.paste(shadow, (INSET, INSET))
    box.alpha_composite(sil.filter(ImageFilter.GaussianBlur(GLOW / 2.0)))
    box.alpha_composite(art, (INSET, INSET))
    return box


def accents(art: Image.Image):
    """The rope's two colours, from the sticker: its mean colour, dark and light."""
    a = np.asarray(art, np.float32)
    w = a[..., 3] / 255.0
    mean = (a[..., :3] * w[..., None]).sum((0, 1)) / max(1.0, w.sum())
    dark = np.clip(mean * 0.55, 0, 255)
    light = np.clip(mean + (255 - mean) * 0.35, 0, 255)
    hexc = lambda c: "".join(f"{int(round(v)):02x}" for v in c)
    return hexc(dark), hexc(light)


def sticker_entry(path: pathlib.Path, number: int):
    up = Image.open(path).convert("RGBA")
    sc = ART / max(up.size)
    up = up.resize((max(1, round(up.width * sc)), max(1, round(up.height * sc))), Image.LANCZOS)
    art = up.rotate(90, expand=True)            # anticlockwise: the top now faces -x, the rope
    alpha = np.asarray(art, np.float32)[..., 3]
    ys = np.arange(alpha.shape[0])
    cy = float((alpha.sum(1) * ys).sum() / alpha.sum())       # centre of mass across the rope
    row = int(round(cy))
    band = alpha[max(0, row - 2):row + 3].max(0)
    solid = np.where(band >= 128)[0]
    first = int(solid.min()) if len(solid) else 0
    tie_x = INSET + min(first + TIE_IN, art.width - 1)
    tie_y = INSET + cy
    box = boxed(art)
    c1, c2 = accents(up)
    return box, {"w": box.width, "h": box.height, "bx": -int(round(tie_x)), "by": -int(round(tie_y)),
                 "c1": c1, "c2": c2, "ntl": WYRM_TAG_BASE + number}


def pack_under(base: Image.Image, images):
    """Shelf-pack `images` into a 2048-wide band under the NTL sheet."""
    width = base.width
    order = sorted(range(len(images)), key=lambda i: -images[i].height)
    places = [None] * len(images)
    x, y, row_h = 0, base.height, 0
    for i in order:
        w, h = images[i].width + PADDING * 2, images[i].height + PADDING * 2
        if x + w > width:
            x, y, row_h = 0, y + row_h, 0
        places[i] = (x + PADDING, y + PADDING)
        x += w
        row_h = max(row_h, h)
    height = 1 << math.ceil(math.log2(y + row_h))
    assert height == base.height * 2, f"stickers need a {height}-tall sheet"
    sheet = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    sheet.paste(base, (0, 0))
    for i, im in enumerate(images):
        sheet.paste(im, places[i])
    return sheet, places


def c_rows(entries):
    return "\n".join(
        "    {{{w}, {h}, {bx}, {by}, 0x{c1}, 0x{c2}, {ntl}, {{{u0:.6f}f, {v0:.6f}f, {u1:.6f}f, {v1:.6f}f}}}},".format(
            w=e["w"], h=e["h"], bx=e["bx"], by=e["by"], c1=e["c1"], c2=e["c2"], ntl=e["ntl"],
            u0=e["uv"][0], v0=e["uv"][1], u1=e["uv"][2], v1=e["uv"][3])
        for e in entries)


def main() -> int:
    base, ntl = freeze_ntl()
    stickers = sorted((TAGS / "stickers").glob("*.png"))
    assert stickers and len(stickers) <= 256, len(stickers)
    boxes, wyrm = [], []
    for n, path in enumerate(stickers):
        assert path.name.startswith(f"{n:02d}-"), path.name
        box, entry = sticker_entry(path, n)
        boxes.append(box.resize((round(box.width * STORE), round(box.height * STORE)), Image.LANCZOS))
        wyrm.append(entry)
    sheet, places = pack_under(base, boxes)

    entries = []
    for e in ntl:
        u0, v0, u1, v1 = e["uv"]
        entries.append({**e, "uv": [u0, v0 * base.height / sheet.height, u1, v1 * base.height / sheet.height]})
    for e, im, (x, y) in zip(wyrm, boxes, places):
        e["uv"] = [x / sheet.width, y / sheet.height, (x + im.width) / sheet.width, (y + im.height) / sheet.height]
        e["art"] = [x, y, im.width, im.height]
        entries.append(e)

    sheet.save(ATLAS, optimize=True)
    old_header = HEADER.read_text(encoding="utf-8")
    head = old_header[:old_header.index("static const tag_entry TAG_TABLE")]
    head = re.sub(r"#define TAG_ATLAS_WIDTH \d+\n#define TAG_ATLAS_HEIGHT \d+",
                  f"#define TAG_ATLAS_WIDTH {sheet.width}\n#define TAG_ATLAS_HEIGHT {sheet.height}", head)
    HEADER.write_text(head + "static const tag_entry TAG_TABLE[TAG_COUNT] = {\n" + c_rows(entries) + "\n};\n\n#endif\n",
                      encoding="utf-8")
    COUNT_HEADER.write_text(
        "/* Generated by tools/wyrm-tag-import.py and tools/wyrm-tag-append.py. Do not edit. */\n"
        "#ifndef TAG_COUNT_H\n#define TAG_COUNT_H\n\n"
        f"#define TAG_COUNT {len(entries)}\n\n"
        "/* Wyrm's own tags follow NTL's in the table; their `ntl` is this plus the\n"
        "   Wyrm tag number (the byte that travels in the skin block's corner). */\n"
        f"#define WYRM_TAG_BASE {WYRM_TAG_BASE}\n#define WYRM_TAG_COUNT {len(wyrm)}\n\n#endif\n",
        encoding="utf-8")

    kotlin = KOTLIN.read_text(encoding="utf-8")
    start = kotlin.index("val TAG_ART = listOf(")
    KOTLIN.write_text(kotlin[:start] + "val TAG_ART = listOf(\n"
                      + "\n".join(f"    TagArt({a[0]}, {a[1]}, {a[2]}, {a[3]})," for a in (e["art"] for e in entries))
                      + "\n)\n", encoding="utf-8")

    cat = CATALOG.read_text(encoding="utf-8")
    rows = [f"        SkinTagAsset({i}, {e['w']}, {e['h']}, {e['bx']}, {e['by']}, 0x{e['c1']}, 0x{e['c2']}, {e['ntl']}, "
            f"{e['uv'][0]:.6f}f, {e['uv'][1]:.6f}f, {e['uv'][2]:.6f}f, {e['uv'][3]:.6f}f)," for i, e in enumerate(entries)]
    first = cat.index("        SkinTagAsset(0,")
    last = cat.index("\n", cat.rindex("        SkinTagAsset(")) + 1
    CATALOG.write_text(cat[:first] + "\n".join(rows) + "\n" + cat[last:], encoding="utf-8")

    manifest = IOS / "SHA256.json"
    sums = json.loads(manifest.read_text())
    for src, rel in ((ATLAS, "app/res/textures/wyrm_tags.png"), (HEADER, "app/src/game/tag_table.h"),
                     (COUNT_HEADER, "app/src/game/tag_count.h")):
        dst = IOS / rel
        dst.write_bytes(src.read_bytes())
        if rel in sums:
            sums[rel] = hashlib.sha256(dst.read_bytes()).hexdigest()
    manifest.write_text(json.dumps(sums, indent=2) + "\n")

    print(f"{len(ntl)} NTL + {len(wyrm)} Wyrm = {len(entries)} tags -> {ATLAS.relative_to(ROOT)} ({sheet.width}x{sheet.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
