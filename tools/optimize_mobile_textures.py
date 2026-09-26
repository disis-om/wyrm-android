"""Deterministically rebuild Vlither Enhanced textures for mobile Vulkan.

The atlas is resized cell-by-cell so Lanczos filtering never samples across
unrelated sprites. The accessory block is processed at its finer 8x4 grid.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path

import numpy as np
from PIL import Image


BACKGROUND_SOURCE_SIZE = (4096, 3548)
BACKGROUND_TARGET_SIZE = (2048, 1774)
ATLAS_SOURCE_SIZE = (7168, 9216)
ATLAS_TARGET_SIZE = (3136, 4032)
ATLAS_GRID = (7, 9)
ATLAS_SOURCE_CELL = 1024
ATLAS_TARGET_CELL = 448
ACCESSORY_START_CELL = (5, 8)
ACCESSORY_GRID = (8, 4)
ACCESSORY_SOURCE_CELL = 256
ACCESSORY_TARGET_CELL = 112
LAUNCHER_ICON_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def resize_premultiplied(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Resize RGBA without dark halos around translucent sprite edges."""
    rgba = np.asarray(image.convert("RGBA"), dtype=np.float32)
    alpha = rgba[..., 3:4] / 255.0
    rgba[..., :3] *= alpha
    premultiplied = Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), "RGBA")
    resized = premultiplied.resize(size, Image.Resampling.LANCZOS, reducing_gap=3.0)

    result = np.asarray(resized, dtype=np.float32).copy()
    out_alpha = result[..., 3:4]
    nonzero = out_alpha[..., 0] > 0.0
    result[..., :3][nonzero] = np.clip(
        result[..., :3][nonzero] * 255.0 / out_alpha[nonzero], 0.0, 255.0
    )
    result[..., :3][~nonzero] = 0.0
    return Image.fromarray(result.astype(np.uint8), "RGBA")


def optimize_background(path: Path) -> dict[str, object]:
    with Image.open(path) as source:
        source.load()
        if source.size != BACKGROUND_SOURCE_SIZE:
            raise ValueError(
                f"background source is {source.size}, expected {BACKGROUND_SOURCE_SIZE}"
            )
        output = source.convert("RGB").resize(
            BACKGROUND_TARGET_SIZE, Image.Resampling.LANCZOS, reducing_gap=3.0
        )
    atomic_png(output, path)
    return image_record(path)


def optimize_atlas(path: Path) -> dict[str, object]:
    with Image.open(path) as source:
        source.load()
        source = source.convert("RGBA")
        if source.size != ATLAS_SOURCE_SIZE:
            raise ValueError(
                f"atlas source is {source.size}, expected {ATLAS_SOURCE_SIZE}"
            )

        output = Image.new("RGBA", ATLAS_TARGET_SIZE, (0, 0, 0, 0))
        for row in range(ATLAS_GRID[1]):
            for column in range(ATLAS_GRID[0]):
                if row == ACCESSORY_START_CELL[1] and column >= ACCESSORY_START_CELL[0]:
                    continue
                left = column * ATLAS_SOURCE_CELL
                top = row * ATLAS_SOURCE_CELL
                cell = source.crop(
                    (left, top, left + ATLAS_SOURCE_CELL, top + ATLAS_SOURCE_CELL)
                )
                resized = resize_premultiplied(
                    cell, (ATLAS_TARGET_CELL, ATLAS_TARGET_CELL)
                )
                output.paste(
                    resized,
                    (column * ATLAS_TARGET_CELL, row * ATLAS_TARGET_CELL),
                    resized,
                )

        accessory_left = ACCESSORY_START_CELL[0] * ATLAS_SOURCE_CELL
        accessory_top = ACCESSORY_START_CELL[1] * ATLAS_SOURCE_CELL
        target_left = ACCESSORY_START_CELL[0] * ATLAS_TARGET_CELL
        target_top = ACCESSORY_START_CELL[1] * ATLAS_TARGET_CELL
        for row in range(ACCESSORY_GRID[1]):
            for column in range(ACCESSORY_GRID[0]):
                left = accessory_left + column * ACCESSORY_SOURCE_CELL
                top = accessory_top + row * ACCESSORY_SOURCE_CELL
                cell = source.crop(
                    (left, top, left + ACCESSORY_SOURCE_CELL, top + ACCESSORY_SOURCE_CELL)
                )
                resized = resize_premultiplied(
                    cell, (ACCESSORY_TARGET_CELL, ACCESSORY_TARGET_CELL)
                )
                output.paste(
                    resized,
                    (
                        target_left + column * ACCESSORY_TARGET_CELL,
                        target_top + row * ACCESSORY_TARGET_CELL,
                    ),
                    resized,
                )

    atomic_png(output, path)
    return image_record(path)


def install_brand_logo(source: Path, destination: Path) -> dict[str, object]:
    with Image.open(source) as image:
        image.load()
        if image.mode != "RGBA" or image.getchannel("A").getextrema()[0] != 0:
            raise ValueError("the in-app brand logo must be a transparent RGBA PNG")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".v110.tmp.png")
    shutil.copyfile(source, temporary)
    with Image.open(temporary) as validation:
        validation.verify()
    os.replace(temporary, destination)
    return image_record(destination)


def rebuild_launcher_icons(source: Path, android_res: Path) -> list[dict[str, object]]:
    with Image.open(source) as image:
        image.load()
        if image.width != image.height:
            raise ValueError("the launcher source must be square")
        source_rgb = image.convert("RGB")
        records = []
        for density, size in LAUNCHER_ICON_SIZES.items():
            destination = android_res / density / "ic_launcher.png"
            output = source_rgb.resize((size, size), Image.Resampling.LANCZOS)
            atomic_png(output, destination)
            records.append(image_record(destination))
    return records


def atomic_png(image: Image.Image, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".v110.tmp.png")
    image.save(temporary, format="PNG", optimize=True, compress_level=9)
    with Image.open(temporary) as validation:
        validation.verify()
    os.replace(temporary, destination)


def image_record(path: Path) -> dict[str, object]:
    with Image.open(path) as image:
        image.load()
        return {
            "path": path.as_posix(),
            "width": image.width,
            "height": image.height,
            "mode": image.mode,
            "bytes": path.stat().st_size,
        }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root", type=Path, default=Path(__file__).resolve().parents[1]
    )
    parser.add_argument("--brand-logo", type=Path)
    parser.add_argument("--launcher-logo", type=Path)
    parser.add_argument("--report", type=Path)
    arguments = parser.parse_args()
    root = arguments.root.resolve()
    texture_root = root / "app" / "res" / "textures"
    brand_logo = (
        arguments.brand_logo
        or root.parent / "vlither enhanced frontend" / "assets" / "vlither enhanced logo.png"
    ).resolve()
    launcher_logo = (
        arguments.launcher_logo or root / "assets" / "VE app logo.png"
    ).resolve()
    records = {
        "background": optimize_background(texture_root / "background_4k.png"),
        "atlas": optimize_atlas(texture_root / "tex_atlas_8k.png"),
        "brandLogo": install_brand_logo(
            brand_logo, texture_root / "vlither_enhanced_logo.png"
        ),
        "launcherIcons": rebuild_launcher_icons(
            launcher_logo, root / "android" / "app" / "src" / "main" / "res"
        ),
    }
    report_path = arguments.report or (
        root / "artifacts" / "changelogs" / "v1.1.0-texture-report.json"
    )
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(records, indent=2))


if __name__ == "__main__":
    main()
