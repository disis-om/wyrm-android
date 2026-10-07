"""
Cuts OM's sticker sheets (2026-10-07) into one clean, upright, trimmed PNG per
tag in tools/wyrm-tags/stickers/NN-name.png. NN is the Wyrm tag number that
travels in the skin block's corner, so the order here is permanent: only ever
append.

Every piece of alpha on a sheet goes to the grid cell its centre falls in, so
a sticker made of separate parts ("666", a pair of wings, claw marks) stays one
sticker.

    python tools/wyrm-tag-cut.py <folder with the sheets>
"""
import pathlib
import sys

import cv2
import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "tools/wyrm-tags/stickers"

SHEETS = [
    ("Kawaii Sticker Sheet Collection.png", 3, 3,
     ["cupcake", "cloud", "rocket", "strawberry", "moon", "fish", "gift", "cactus", "starfish"]),
    ("Kawaii 3×3 Sticker Icon Collection.png", 3, 3,
     ["teddy", "bee", "bandage", "mushroom", "ufo", "shark", "juice", "bell", "paw"]),
    ("Watercolor Floral Sticker Collection.png", 4, 2,
     ["blossom", "white-lily", "hibiscus", "anemone", "daisy", "blue-petals", "rose", "yellow-lily"]),
    ("Solar System Sticker Sheet.png", 6, 3,
     ["sun", "mercury", "venus", "earth", "moon-real", "mars",
      "jupiter", "saturn", "uranus", "neptune", "pluto", "ceres",
      "io", "europa", "ganymede", "callisto", "titan", "enceladus"]),
    ("Whimsical Game Icon Charm Grid.png", 3, 3,
     ["cassette", "fish-bone", "old-key", "paper-boat", "potion", "cracked-moon", "knight-helm", "shell", "voodoo"]),
    ("Grim Gothic Emblem Sticker Sheet.png", 6, 4,
     ["biohazard", "radiation", "666", "pentagram", "demon-skull", "skull-warning",
      "warning", "anarchy", "dark-cross", "dark-sun", "evil-eye", "serpent",
      "skull-star", "skull-cross", "thorn-heart", "flame-skull", "gas-mask", "chaos-star",
      "yin-yang", "claws", "triple-moon", "all-seeing-eye", "wings", "dark-crown"]),
]

ALPHA_ON = 128      # a pixel this opaque belongs to a sticker
MIN_PIECE = 12      # pixels; smaller specks are dust, not art
MAX_SIDE = 256      # masters are kept at most this big (the atlas uses less)


def cut(path: pathlib.Path, cols: int, rows: int, names):
    im = Image.open(path).convert("RGBA")
    rgba = np.asarray(im).copy()
    alpha = rgba[..., 3]
    h, w = alpha.shape
    count, labels, stats, cents = cv2.connectedComponentsWithStats((alpha >= ALPHA_ON).astype(np.uint8), 8)
    owner = np.full(count, -1)
    for i in range(1, count):
        if stats[i, cv2.CC_STAT_AREA] < MIN_PIECE:
            continue
        cx, cy = cents[i]
        owner[i] = min(rows - 1, int(cy / (h / rows))) * cols + min(cols - 1, int(cx / (w / cols)))
    pieces = []
    for cell, name in enumerate(names):
        keep = np.isin(labels, np.where(owner == cell)[0])
        if not keep.any():
            raise SystemExit(f"{path.name}: cell {cell} ({name}) is empty")
        # soft edges: the original alpha, but only around kept pixels
        grow = cv2.dilate(keep.astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
        a = np.where(grow, alpha, 0)
        ys, xs = np.where(a > 8)
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        out = rgba[y0:y1, x0:x1].copy()
        out[..., 3] = a[y0:y1, x0:x1]
        out[out[..., 3] == 0] = 0
        piece = Image.fromarray(out, "RGBA")
        if max(piece.size) > MAX_SIDE:
            sc = MAX_SIDE / max(piece.size)
            piece = piece.resize((max(1, round(piece.width * sc)), max(1, round(piece.height * sc))), Image.LANCZOS)
        pieces.append((name, piece))
    return pieces


def main() -> int:
    src = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()
    n = 0
    for file, cols, rows, names in SHEETS:
        assert len(names) == cols * rows, file
        for name, piece in cut(src / file, cols, rows, names):
            piece.save(OUT / f"{n:02d}-{name}.png", optimize=True)
            n += 1
    print(f"{n} stickers -> {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
