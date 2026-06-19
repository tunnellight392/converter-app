"""Render a 1024x500 Play Store feature graphic, on-brand with the app icon."""
from PIL import Image, ImageDraw, ImageFont
import os

W, H = 1024, 500
SS = 3
BW, BH = W * SS, H * SS

c0, c1 = (0x5A, 0x95, 0xF6), (0x25, 0x63, 0xEB)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def font(names, size):
    for n in names:
        p = os.path.join(os.environ.get("WINDIR", "C:/Windows"), "Fonts", n)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# ---- background: diagonal gradient + soft highlight ----
bg = Image.new("RGB", (128, 64))
px = bg.load()
for y in range(64):
    for x in range(128):
        t = (x / 127 + y / 63) / 2
        r, g, b = lerp(c0, c1, t)
        d = (((x - 30) / 128) ** 2 + ((y - 16) / 64) ** 2) ** 0.5
        hi = max(0.0, 1 - d / 0.7) * (0x22 / 255)
        px[x, y] = (round(r + (255 - r) * hi), round(g + (255 - g) * hi), round(b + (255 - b) * hi))
img = bg.resize((BW, BH), Image.BICUBIC).convert("RGBA")
d = ImageDraw.Draw(img)

# ---- app-icon badge on the left ----
badge = 300 * SS
bx, by = 90 * SS, (H - 300) // 2 * SS
# badge background: icon's own gradient
ib = Image.new("RGB", (108, 108))
ip = ib.load()
for y in range(108):
    for x in range(108):
        t = (x + y) / (2 * 107)
        r, g, b = lerp(c0, c1, t)
        dd = ((x - 40) ** 2 + (y - 36) ** 2) ** 0.5
        hi = max(0.0, 1 - dd / 72) * (0x26 / 255)
        ip[x, y] = (round(r + (255 - r) * hi), round(g + (255 - g) * hi), round(b + (255 - b) * hi))
ib = ib.resize((badge, badge), Image.BICUBIC).convert("RGBA")
mask = Image.new("L", (badge, badge), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, badge - 1, badge - 1], radius=int(badge * 0.22), fill=255)

# foreground mark on the badge
fg = Image.new("RGBA", (badge, badge), (0, 0, 0, 0))
fd = ImageDraw.Draw(fg)
f = badge / 108
WHT = (255, 255, 255, 255)


def T(x, y):
    x = 54 + (x - 54) * 0.9
    y = 54 + (y - 54) * 0.9
    return x * f, y * f


def stroke(x1, y1, x2, y2, w):
    p1, p2 = T(x1, y1), T(x2, y2)
    r = w * 0.9 * f / 2
    fd.line([p1, p2], fill=WHT, width=max(1, round(r * 2)))
    for (cx, cy) in (p1, p2):
        fd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHT)


def tri(pts):
    fd.polygon([T(x, y) for (x, y) in pts], fill=WHT)


stroke(32, 40, 60, 40, 7); tri([(58, 32.5), (58, 47.5), (74, 40)])
stroke(48, 57, 76, 57, 7); tri([(50, 49.5), (50, 64.5), (34, 57)])
stroke(33, 73, 75, 73, 3)
for (x, y2) in [(34, 65), (54, 65), (74, 65), (44, 68), (64, 68)]:
    stroke(x, 73, x, y2, 3)
ib.alpha_composite(fg)
img.paste(ib, (bx, by), mask)

# ---- text on the right ----
tx = bx + badge + 70 * SS
avail = BW - tx - 60 * SS                       # usable width for text

# auto-fit the title to the available width
title = "Unit Converter"
ts = 96 * SS
title_f = font(["segoeuib.ttf", "arialbd.ttf", "Arialbd.ttf"], ts)
while d.textlength(title, font=title_f) > avail and ts > 20:
    ts -= 2 * SS
    title_f = font(["segoeuib.ttf", "arialbd.ttf", "Arialbd.ttf"], ts)
tag_f = font(["segoeui.ttf", "arial.ttf", "Arial.ttf"], 40 * SS)
sub_f = font(["seguisb.ttf", "segoeui.ttf", "arial.ttf"], 32 * SS)


def th(f):
    a = f.getmetrics()
    return a[0] + a[1]


# vertically center the stacked text block
gap1, gap2 = 18 * SS, 14 * SS
block = th(title_f) + gap1 + th(tag_f) + gap2 + th(sub_f)
y = (BH - block) // 2
d.text((tx, y), title, font=title_f, fill=(255, 255, 255, 255))
y += th(title_f) + gap1
d.text((tx + 2 * SS, y), "Fast • Offline • No Ads", font=tag_f, fill=(255, 255, 255, 235))
y += th(tag_f) + gap2
d.text((tx + 2 * SS, y), "20+ categories, instant results", font=sub_f, fill=(255, 255, 255, 205))

img = img.convert("RGB").resize((W, H), Image.LANCZOS)
img.save("docs/play_store_feature_1024x500.png")
print("wrote docs/play_store_feature_1024x500.png", img.size)
