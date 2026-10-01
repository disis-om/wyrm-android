"""Wyrm's own arena floors (OM, 2026-10-01): seven seamless, dark, quiet tiles
that keep the eye on snakes and food.

Every pattern is a function of the tile's own periodic coordinates (lattices
with whole counts per tile, FFT noise, wrapped distances), so the right edge
continues the left and the bottom the top, exactly. Drawn at 2x and
box-averaged down, which never mixes opposite edges.

Sizes. The floor shader samples at `world / (tile * bg_scale)`, and the default
`bg_scale` is 599/4096 (the Wyrm floor's 4096 px tile spans 599 world units).
So a tile here is designed in world units (a hex of 40 units sits next to a
29-unit snake head) and declared as `world * 4096 / 599`, which puts it at
exactly that size at the default scale. Line patterns are drawn at 2 pixels a
world unit so they stay crisp when the camera zooms in.

Writes app/res/textures/backgrounds/wyrm_*.png (Android) and copies them to
../Wyrm iOS/Resources/Backgrounds/ (iOS adds them in prepare-original-engine.py).
Prints the backgrounds.h rows with the exact tile sizes.

    python tools/generate-focus-backgrounds.py
"""
import math
import shutil
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "res" / "textures" / "backgrounds"
IOS = ROOT.parent / "Wyrm iOS" / "Resources" / "Backgrounds"
SS = 2                      # supersampling
DEFAULT_BG_SCALE = 599.0 / 4096.0


def hex_rgb(code):
    code = code.lstrip("#")
    return np.array([int(code[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def mix(a, b, t):
    t = np.clip(t, 0.0, 1.0)[..., None]
    return a * (1 - t) + b * t


def world_grid(W, H, k):
    """World-unit coordinates of every supersampled pixel centre of a tile that
    spans W x H world units at k pixels per unit (pixel counts rounded; the
    coordinates are stretched onto the exact period, so it still wraps)."""
    w, h = int(round(W * k)), int(round(H * k))
    xs = (np.arange(w * SS) + 0.5) / SS * (W / w)
    ys = (np.arange(h * SS) + 0.5) / SS * (H / h)
    x, y = np.meshgrid(xs, ys)
    return x, y, w, h


def down(img):
    """2x2 box average: exact, and never wraps one edge into the other."""
    h, w, c = img.shape
    return img.reshape(h // SS, SS, w // SS, SS, c).mean(axis=(1, 3))


def wrap(d, period):
    return (d + period / 2) % period - period / 2


def periodic_noise(w, h, beta, seed, low=1.0):
    """Tileable 1/f^beta noise in 0..1, at the supersampled size."""
    rng = np.random.default_rng(seed)
    white = rng.standard_normal((h * SS, w * SS))
    f = np.fft.fft2(white)
    fy = np.fft.fftfreq(h * SS)[:, None] * h
    fx = np.fft.fftfreq(w * SS)[None, :] * w
    r = np.sqrt(fx * fx + fy * fy)
    r[0, 0] = 1.0
    amp = 1.0 / np.power(np.maximum(r, low), beta)
    amp[0, 0] = 0.0
    n = np.real(np.fft.ifft2(f * amp))
    n -= n.min()
    n /= n.max()
    return n


def save(name, rgb):
    img = down(rgb)
    # A whisper of ordered dither so dark gradients do not band.
    h, w, _ = img.shape
    bayer = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5
    dither = np.tile(bayer, (h // 4 + 1, w // 4 + 1))[:h, :w][..., None] / 255.0
    img = np.clip(img + dither, 0, 1)
    data = (img * 255.0 + 0.5).astype(np.uint8)
    path = OUT / f"wyrm_{name}.png"
    Image.fromarray(data, "RGB").save(path, optimize=True)
    return path, w, h


# --- 1. Midnight: a navy hex lattice, lines barely lit -------------------------
def midnight():
    s = 40.0                          # hex circumradius, world units
    cols, rows = 8, 8                 # whole hexes per tile (rows even)
    hw = math.sqrt(3) * s
    vh = 1.5 * s
    W, H = cols * hw, rows * vh
    x, y, w, h = world_grid(W, H, 2)
    best = np.full(x.shape, 1e9)
    for dj in (-1, 0, 1):
        j = np.floor(y / vh + 0.5) + dj
        off = np.where((j.astype(int) % 2) == 1, hw / 2, 0.0)
        i = np.floor((x - off) / hw + 0.5)
        for di in (-1, 0, 1):
            cx = (i + di) * hw + off
            cy = j * vh
            dx = wrap(x - cx, W)
            dy = wrap(y - cy, H)
            ax, ay = np.abs(dx), np.abs(dy)
            d = np.maximum(ax, ax * 0.5 + ay * math.sqrt(3) / 2)
            best = np.minimum(best, d)
    edge = (s * math.sqrt(3) / 2) - best          # distance to the cell's edge
    line = np.exp(-(edge / 0.75) ** 2)
    glow = np.exp(-(edge / 5.0) ** 2) * 0.35
    img = mix(hex_rgb("0A1322"), hex_rgb("1C2F4A"), line * 0.9 + glow)
    return "midnight", "Midnight", save("midnight", img), (W, H)


# --- 2. Carbon: a 2/2 twill weave in graphite ---------------------------------
def carbon():
    c = 9.0                        # cell, world units
    n = 48
    W = H = c * n
    x, y, w, h = world_grid(W, H, 2)
    i = np.floor(x / c).astype(int)
    j = np.floor(y / c).astype(int)
    fx = (x % c) / c
    fy = (y % c) / c
    horizontal = ((i + j) % 4) < 2
    across = np.where(horizontal, fy, fx)          # 0..1 across the strand
    along = np.where(horizontal, fx, fy)
    round_ = np.sin(across * math.pi)              # a strand is a cylinder
    seam = np.minimum(across, 1 - across)
    groove = np.exp(-(seam / 0.06) ** 2)
    sheen = 0.5 + 0.5 * np.cos((along - 0.5) * math.pi)
    t = 0.18 + 0.55 * round_ * (0.75 + 0.25 * sheen) - 0.35 * groove
    img = mix(hex_rgb("0E1013"), hex_rgb("25292F"), t)
    return "carbon", "Carbon", save("carbon", img), (W, H)


# --- 3. Abyss: deep water with soft caustic threads ---------------------------
def abyss():
    W = H = 1024.0
    x, y, w, h = world_grid(W, H, 1)
    n1 = periodic_noise(w, h, 3.0, 11, low=2.0)
    n2 = periodic_noise(w, h, 2.6, 12, low=4.0)
    ridge = 1.0 - np.abs(n2 - 0.5) * 2.0
    threads = np.power(np.clip(ridge, 0, 1), 14.0)
    deep = mix(hex_rgb("041014"), hex_rgb("0B252D"), n1)
    img = mix(deep, hex_rgb("16484F"), threads * 0.42)
    return "abyss", "Abyss", save("abyss", img), (W, H)


# --- 4. Nebula: near-black violet clouds, a few far stars ---------------------
def nebula():
    W = H = 1024.0
    x, y, w, h = world_grid(W, H, 1)
    cloud = periodic_noise(w, h, 2.4, 21, low=1.5)
    tint = periodic_noise(w, h, 2.0, 22, low=1.5)
    colour = mix(hex_rgb("1C1030"), hex_rgb("0B1A33"), tint)
    img = mix(hex_rgb("06050B"), colour, np.power(cloud, 1.6) * 0.9)
    rng = np.random.default_rng(23)
    stars = np.zeros(x.shape)
    for _ in range(170):
        sx, sy = rng.uniform(0, W), rng.uniform(0, H)
        size = rng.uniform(0.5, 1.3)
        bright = rng.uniform(0.25, 0.75)
        # Only the neighbourhood: a star is a few pixels wide.
        x0, x1 = int((sx - 6) * SS), int((sx + 6) * SS)
        y0, y1 = int((sy - 6) * SS), int((sy + 6) * SS)
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                py, px = yy % (h * SS), xx % (w * SS)
                dx = wrap(x[py, px] - sx, W)
                dy = wrap(y[py, px] - sy, H)
                stars[py, px] = max(stars[py, px], bright * math.exp(-(dx * dx + dy * dy) / (size * size)))
    img = mix(img, hex_rgb("C9D2FF"), stars)
    return "nebula", "Nebula", save("nebula", img), (W, H)


# --- 5. Dot grid: a quiet charcoal sheet of dots ------------------------------
def dotgrid():
    p = 40.0
    n = 10
    W = H = p * n
    x, y, w, h = world_grid(W, H, 2)
    dx = wrap(x - p / 2, p)
    dy = wrap(y - p / 2, p)
    d = np.sqrt(dx * dx + dy * dy)
    major = ((np.floor(x / p).astype(int) % 5 == 2) & (np.floor(y / p).astype(int) % 5 == 2))
    r = np.where(major, 2.4, 1.7)
    dot = np.clip((r - d) * 2.0 + 0.5, 0, 1)
    img = mix(hex_rgb("101215"), np.where(major[..., None], hex_rgb("3C434D"), hex_rgb("2B3139")), dot)
    return "dotgrid", "Dot grid", save("dotgrid", img), (W, H)


# --- 6. Contours: a dark topographic map --------------------------------------
def contours():
    W = H = 1024.0
    x, y, w, h = world_grid(W, H, 1)
    n = periodic_noise(w, h, 2.6, 31, low=1.5)
    levels = n * 14.0
    f = levels - np.floor(levels)
    dist = np.minimum(f, 1 - f)
    # Periodic central differences: np.gradient is one-sided at the edges,
    # which would thin the lines there and show the seam.
    gx = (np.roll(levels, -1, axis=1) - np.roll(levels, 1, axis=1)) * 0.5
    gy = (np.roll(levels, -1, axis=0) - np.roll(levels, 1, axis=0)) * 0.5
    grad = np.sqrt(gx * gx + gy * gy) * SS + 1e-6
    px = dist / grad
    line = np.exp(-(px / 0.9) ** 2)
    major = (np.floor(levels).astype(int) % 5 == 0)
    strength = np.where(major, 0.95, 0.55)
    base = mix(hex_rgb("0B1115"), hex_rgb("101A1F"), n)
    img = mix(base, hex_rgb("26394A"), line * strength)
    return "contours", "Contours", save("contours", img), (W, H)


# --- 7. Scales: snake scales in green-black -----------------------------------
def scales():
    R = 30.0
    cols, rows = 8, 16             # whole scales per tile (rows even)
    W, H = cols * 2 * R, rows * R
    x, y, w, h = world_grid(W, H, 2)
    owner = np.full(x.shape, -1e9)
    dist = np.full(x.shape, 1e9)
    k0 = np.floor(y / R)
    # Later rows sit on top of earlier ones: the largest row index wins.
    for dk in (-1, 0, 1):
        k = k0 + dk
        off = np.where((k.astype(int) % 2) == 1, R, 0.0)
        cy = k * R
        i = np.floor((x - off) / (2 * R) + 0.5)
        for di in (-1, 0, 1):
            cx = (i + di) * 2 * R + off
            dx = wrap(x - cx, W)
            dy = wrap(y - cy, H)
            d = np.sqrt(dx * dx + dy * dy)
            take = (d < R) & ((k > owner) | ((k == owner) & (d < dist)))
            owner = np.where(take, k, owner)
            dist = np.where(take, d, dist)
    t = np.clip(dist / R, 0, 1)
    rim = np.exp(-((1 - t) / 0.04) ** 2)
    body = 0.25 + 0.55 * (1 - t) ** 1.5
    img = mix(hex_rgb("08100D"), hex_rgb("1A2B23"), body)
    img = mix(img, hex_rgb("2E4838"), rim * 0.7)
    return "scales", "Scales", save("scales", img), (W, H)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    IOS.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("wyrm_*.png"):
        stale.unlink()
    rows = []
    for make in (midnight, carbon, abyss, nebula, dotgrid, contours, scales):
        key, label, (path, w, h), (W, H) = make()
        shutil.copyfile(path, IOS / path.name)
        tw, th = W / DEFAULT_BG_SCALE, H / DEFAULT_BG_SCALE
        rows.append((key, label, path.name, tw, th, w, h, path.stat().st_size))
    for key, label, name, tw, th, w, h, size in rows:
        print(f'    {{"wyrm_{key}", "{label}", "app/res/textures/backgrounds/{name}", {tw:.2f}f, {th:.2f}f}},'
              f"  /* {w}x{h}, {size // 1024} KB */")


if __name__ == "__main__":
    main()
