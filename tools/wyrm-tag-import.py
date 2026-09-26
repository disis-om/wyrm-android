"""
Imports the NTL tag set into Wyrm.

There are two halves to the set and they are stored differently, which is the
only reason this script is longer than a page:

  * the bundled tags — sixty images in the mod's `s/` folder, `t_0.webp` up,
    with their numbers packed into one semicolon-separated string in the mod's
    script: `w;h;c1;c2;bx;by`
  * the free tags — a hundred and four entries in `fstags.js`, each carrying
    its own artwork as a base64 WebP alongside the same six numbers

Both are then treated the same way, and it matters that they are treated the
way the mod treats them:

  A tag's *box* is `blbw` x `blbh`, and it is bigger than the artwork by
  exactly 42 pixels in each direction. The mod paints the art at (21, 21)
  inside that box with a black glow behind it, and every position it later
  works out — where the bobble hangs, how big it is drawn — is in terms of the
  box, not the art. Pack the bare artwork instead and every tag sits slightly
  wrong and slightly too small, which is exactly the kind of error that is
  invisible one tag at a time and obvious across a screen full of snakes.

So each tag is rebuilt into its box, glow and all, before it is packed.

Out of that come three files:

  app/res/textures/wyrm_tags.png     every tag on one sheet, because a hundred
                                     and sixty textures is a hundred and sixty
                                     binds a frame and one sheet is one
  app/src/game/tag_table.h           the numbers, for the engine
  android/.../ui/TagTable.kt         the same rectangles again, for the picker

Run it again whenever the tag set changes; nothing here is edited by hand.

    python tools/wyrm-tag-import.py <path to the mod folder>
"""

import base64
import io
import json
import math
import pathlib
import re
import sys

from PIL import Image, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
ATLAS = ROOT / "app/res/textures/wyrm_tags.png"
HEADER = ROOT / "app/src/game/tag_table.h"
# How many tags there are, on its own, so that code which only needs the count
# — the settings table, which has to know the highest tag a slider may reach —
# can have it without pulling a copy of the whole table into itself.
COUNT_HEADER = ROOT / "app/src/game/tag_count.h"
KOTLIN = ROOT / "android/app/src/main/java/com/wyrm/omrajput/ui/TagTable.kt"

# The art sits this far inside its box on every side, and the glow behind it is
# this wide. Both are the mod's numbers, not ours — see the note above.
INSET = 21
GLOW = 20

# The sheet is stored at half size. Nothing downstream measures the sheet: the
# geometry is all in box pixels and the lookups are all normalised, so this
# costs resolution nobody can see at the size a tag is drawn and saves three
# quarters of the texture memory.
STORE = 0.5

# One transparent pixel around each tag, so filtering at the edge of one cannot
# drag the tag beside it into view.
PADDING = 1

# Where the free tags start in the mod's own numbering. Wyrm packs everything
# into one run of indices instead, and carries the mod's number alongside so
# that a tag can still be named to NTL in NTL's terms.
FREE_TAG_BASE = 200


def built_in_numbers(script: pathlib.Path):
    """The bundled tags' numbers, out of the one string that holds them all."""
    text = script.read_text(encoding="utf-8", errors="replace")
    start = text.index('Wr="') + 4
    end = text.index('".split(",")', start)
    return [entry.split(";") for entry in text[start:end].split(",")]


def free_tags(source: pathlib.Path):
    text = source.read_text(encoding="utf-8")
    start = text.index("JSON.parse('") + len("JSON.parse('")
    end = text.rindex("')")
    return json.loads(text[start:end])


def decode(data_url: str) -> Image.Image:
    payload = re.sub(r"^data:image/\w+;base64,", "", data_url)
    return Image.open(io.BytesIO(base64.b64decode(payload))).convert("RGBA")


def boxed(art: Image.Image, width: int, height: int) -> Image.Image:
    """Rebuilds one tag inside its box, glow and all.

    The glow is a blurred black copy of the tag's own silhouette, which is what
    a canvas shadow is. A canvas blur of N is a Gaussian of N/2, so the mod's
    shadowBlur of 20 is a radius of 10 here.
    """
    box = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    if art.size != (width - INSET * 2, height - INSET * 2):
        # Not fatal: a couple of the mod's own entries are a pixel or two out.
        # The box is what everything downstream measures, so the box wins.
        art = art.resize(
            (max(1, width - INSET * 2), max(1, height - INSET * 2)),
            Image.LANCZOS,
        )

    silhouette = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    shadow = Image.new("RGBA", art.size, (0, 0, 0, 255))
    shadow.putalpha(art.getchannel("A"))
    silhouette.paste(shadow, (INSET, INSET))
    silhouette = silhouette.filter(ImageFilter.GaussianBlur(GLOW / 2.0))

    box.alpha_composite(silhouette)
    box.alpha_composite(art, (INSET, INSET))
    return box


def pack(images):
    """Shelf packing: sort by height, lay rows, wrap at the sheet's width.

    Good enough for a set this size — the sheet comes out within a few per cent
    of the ideal, and a cleverer packer would only save memory nobody is short
    of.
    """
    area = sum((i.width + PADDING * 2) * (i.height + PADDING * 2) for i in images)
    width = 1 << max(9, math.ceil(math.log2(math.sqrt(area) * 1.15)))

    order = sorted(range(len(images)), key=lambda i: -images[i].height)
    places = [None] * len(images)
    x = y = row_height = 0
    for index in order:
        image = images[index]
        w, h = image.width + PADDING * 2, image.height + PADDING * 2
        if x + w > width:
            x, y, row_height = 0, y + row_height, 0
        places[index] = (x + PADDING, y + PADDING)
        x += w
        row_height = max(row_height, h)

    height = 1 << math.ceil(math.log2(y + row_height))
    sheet = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for index, image in enumerate(images):
        sheet.paste(image, places[index])
    return sheet, places


def colour(value, fallback: str) -> str:
    """A tag's accent, as #rrggbb.

    A handful of the mod's entries say "red" or "yellow" rather than a hex
    triple. Those are named CSS colours and the browser resolves them; here
    they are spelled out, and anything else falls back rather than failing —
    a wrong-coloured rope is a smaller problem than no tag set at all.
    """
    named = {
        "red": "#ff0000", "yellow": "#ffff00", "white": "#ffffff",
        "black": "#000000", "blue": "#0000ff", "green": "#008000",
        "orange": "#ffa500", "pink": "#ffc0cb", "purple": "#800080",
        "cyan": "#00ffff", "magenta": "#ff00ff", "gold": "#ffd700",
        "silver": "#c0c0c0", "gray": "#808080", "grey": "#808080",
        "lime": "#00ff00", "navy": "#000080", "teal": "#008080",
    }
    text = str(value or fallback).strip().lower()
    text = named.get(text, text)
    if not re.fullmatch(r"#[0-9a-f]{6}", text):
        text = fallback
    return text


def main() -> int:
    mod = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    script = mod / "main-mt.js"
    listing = mod / "fstags.js"
    if not script.is_file() or not listing.is_file():
        print(f"no main-mt.js and fstags.js in {mod}", file=sys.stderr)
        return 1

    entries = []
    images = []

    for index, row in enumerate(built_in_numbers(script)):
        art = mod / "s" / f"t_{index}.webp"
        if not art.is_file():
            print(f"bundled tag {index} has no artwork, skipped", file=sys.stderr)
            continue
        w, h = int(row[0]), int(row[1])
        images.append(boxed(Image.open(art).convert("RGBA"), w, h))
        entries.append({
            "w": w, "h": h,
            "bx": int(float(row[4])), "by": int(float(row[5])),
            "c1": colour(row[2], "#ffffff"), "c2": colour(row[3], "#ffffff"),
            "ntl": index,
        })

    for index, tag in enumerate(free_tags(listing)):
        w, h = int(float(tag["fst_blbw"])), int(float(tag["fst_blbh"]))
        images.append(boxed(decode(tag["fst_src"]), w, h))
        entries.append({
            "w": w, "h": h,
            "bx": int(float(tag["fst_blbx"])), "by": int(float(tag["fst_blby"])),
            "c1": colour(tag.get("fst_atc1"), "#ffffff"),
            "c2": colour(tag.get("fst_atc2"), "#ffffff"),
            "ntl": FREE_TAG_BASE + index,
        })

    stored = [
        i.resize((max(1, round(i.width * STORE)), max(1, round(i.height * STORE))),
                 Image.LANCZOS)
        for i in images
    ]
    sheet, places = pack(stored)

    ATLAS.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(ATLAS, optimize=True)

    rows = []
    kotlin_rows = []
    for index, entry in enumerate(entries):
        image, (x, y) = stored[index], places[index]
        rows.append(
            "    {{{w}, {h}, {bx}, {by}, 0x{c1}, 0x{c2}, {ntl}, "
            "{{{u0:.6f}f, {v0:.6f}f, {u1:.6f}f, {v1:.6f}f}}}},".format(
                w=entry["w"], h=entry["h"], bx=entry["bx"], by=entry["by"],
                c1=entry["c1"][1:], c2=entry["c2"][1:], ntl=entry["ntl"],
                u0=x / sheet.width, v0=y / sheet.height,
                u1=(x + image.width) / sheet.width,
                v1=(y + image.height) / sheet.height,
            )
        )
        kotlin_rows.append(f"    TagArt({x}, {y}, {image.width}, {image.height}),")

    COUNT_HEADER.write_text(
        "/* Generated by tools/wyrm-tag-import.py. Do not edit. */\n"
        "#ifndef TAG_COUNT_H\n#define TAG_COUNT_H\n\n"
        f"#define TAG_COUNT {len(entries)}\n\n#endif\n",
        encoding="utf-8",
    )

    HEADER.write_text(
        "/* Generated by tools/wyrm-tag-import.py. Do not edit. */\n"
        "#ifndef TAG_TABLE_H\n#define TAG_TABLE_H\n\n"
        "#include \"tag_count.h\"\n\n"
        "/*\n"
        " * The tag set, as one sheet and one table.\n"
        " *\n"
        " * `w`/`h` are the bobble's box — bigger than the artwork, because the\n"
        " * artwork is inset inside it with a glow behind. Every position is in\n"
        " * terms of that box. `bx`/`by` are where the box hangs from the end of\n"
        " * the rope, in box pixels and both negative — up and to the left.\n"
        " * `c1`/`c2` are the accents the rope is drawn in, 0xRRGGBB. `ntl` is\n"
        " * the number the mod itself gives this tag, kept so a tag can still be\n"
        " * named in the mod's terms. `uv` is where the artwork sits on the\n"
        " * sheet.\n"
        " */\n"
        "typedef struct tag_entry {\n"
        "  int w;\n  int h;\n  int bx;\n  int by;\n"
        "  unsigned int c1;\n  unsigned int c2;\n  int ntl;\n  float uv[4];\n"
        "} tag_entry;\n\n"
        f"#define TAG_ATLAS_WIDTH {sheet.width}\n"
        f"#define TAG_ATLAS_HEIGHT {sheet.height}\n\n"
        "static const tag_entry TAG_TABLE[TAG_COUNT] = {\n"
        + "\n".join(rows)
        + "\n};\n\n#endif\n",
        encoding="utf-8",
    )

    KOTLIN.write_text(
        "package com.wyrm.omrajput.ui\n\n"
        "/* Generated by tools/wyrm-tag-import.py. Do not edit. */\n\n"
        "/**\n"
        " * Where each tag sits on the sheet, for the picker to cut out.\n"
        " *\n"
        " * The engine has the same table with the anchors and colours in it;\n"
        " * the picker only needs the artwork, so this is the short version.\n"
        " * Both come out of one run of the importer, so the two can never\n"
        " * disagree about what tag 57 is.\n"
        " */\n"
        "data class TagArt(val x: Int, val y: Int, val w: Int, val h: Int)\n\n"
        "const val TAG_ATLAS_ASSET = \"textures/wyrm_tags.png\"\n\n"
        "val TAG_ART = listOf(\n" + "\n".join(kotlin_rows) + "\n)\n",
        encoding="utf-8",
    )

    print(f"{len(entries)} tags -> {ATLAS.relative_to(ROOT)} ({sheet.width}x{sheet.height})")
    print(f"table -> {HEADER.relative_to(ROOT)}")
    print(f"picker table -> {KOTLIN.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
