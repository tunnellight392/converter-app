"""Render the adaptive launcher icon to a 512x512 PNG for the Play Store.

Reproduces app/src/main/res/drawable/ic_launcher_background.xml (blue gradient)
and ic_launcher_foreground.xml (white swap arrows + ruler ticks, group scale 0.9).
"""
from PIL import Image, ImageDraw

VP = 108            # vector viewport
OUT = 512           # Play Store icon size
SS = 4              # supersample factor
BIG = OUT * SS
f = BIG / VP        # viewport -> pixel scale


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---- background (rendered small, then upscaled smoothly) ----
bg = Image.new("RGB", (VP, VP))
px = bg.load()
c0, c1 = (0x5A, 0x95, 0xF6), (0x25, 0x63, 0xEB)   # diagonal gradient stops
cx, cy, rad = 40, 36, 72                           # radial highlight
for y in range(VP):
    for x in range(VP):
        t = (x + y) / (2 * (VP - 1))               # diagonal 0..1
        r, g, b = lerp(c0, c1, t)
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        hi = max(0.0, 1 - d / rad)                  # white highlight alpha (0x26 max)
        a = (0x26 / 255) * hi
        r = round(r + (255 - r) * a)
        g = round(g + (255 - g) * a)
        b = round(b + (255 - b) * a)
        px[x, y] = (r, g, b)
bg = bg.resize((BIG, BIG), Image.BICUBIC)

# ---- foreground (vector-crisp on a transparent layer) ----
fg = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
d = ImageDraw.Draw(fg)
W = (255, 255, 255, 255)


def T(x, y):
    """group pivot (54,54) scale 0.9, then viewport->pixel."""
    x = 54 + (x - 54) * 0.9
    y = 54 + (y - 54) * 0.9
    return x * f, y * f


def stroke(x1, y1, x2, y2, w):
    p1, p2 = T(x1, y1), T(x2, y2)
    rpx = w * 0.9 * f / 2
    d.line([p1, p2], fill=W, width=max(1, round(rpx * 2)))
    for (cxp, cyp) in (p1, p2):                     # round caps
        d.ellipse([cxp - rpx, cyp - rpx, cxp + rpx, cyp + rpx], fill=W)


def fill_tri(pts):
    d.polygon([T(x, y) for (x, y) in pts], fill=W)


# top arrow ->
stroke(32, 40, 60, 40, 7)
fill_tri([(58, 32.5), (58, 47.5), (74, 40)])
# bottom arrow <-
stroke(48, 57, 76, 57, 7)
fill_tri([(50, 49.5), (50, 64.5), (34, 57)])
# ruler baseline + ticks
stroke(33, 73, 75, 73, 3)
for (x, y2) in [(34, 65), (54, 65), (74, 65), (44, 68), (64, 68)]:
    stroke(x, 73, x, y2, 3)

# ---- composite + downsample ----
out = bg.convert("RGBA")
out.alpha_composite(fg)
out = out.convert("RGB").resize((OUT, OUT), Image.LANCZOS)
out.save("docs/play_store_icon_512.png")
print("wrote docs/play_store_icon_512.png", out.size)
