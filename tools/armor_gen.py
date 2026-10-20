#!/usr/bin/env python3
"""
Rift armor v2 (Cataclysm-style: bulky 3D plates, horns, spikes, glowing core).

Generates, from ONE cube table (so the model and the texture can never disagree):
  * textures/models/armor/rift_layer_1.png  (128x128: helmet, chestplate, boots)
  * textures/models/armor/rift_layer_2.png  (128x128: leggings)
  * textures/item/rift_<piece>.png          (16x16 icons)
  * src/main/java/com/darkrifts/client/RiftArmorParts.java  (GENERATED - do not edit by hand)

Called from generate_assets.py (make_armor).  Needs Pillow.
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "darkrifts", "client", "RiftArmorParts.java")
MOD = "darkrifts"
TEX = 128

# --------------------------------------------------------------------------- palette
OUT = (9, 4, 18)
DARK = (22, 11, 38)
BASE = (40, 24, 70)
MID = (62, 40, 108)
HI = (104, 76, 180)
LIGHT = (150, 122, 232)
SILVER = (198, 190, 226)
BONE = (222, 214, 236)
GLOW = (230, 110, 255)
HOT = (255, 214, 255)
TEAL = (90, 230, 240)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# --------------------------------------------------------------------------- cube table
# Each cube: slot, name, parent, kind, size(w,h,d), origin(x,y,z), pose(offset xyz, rot xyz), sym
#   parent: a vanilla part (head/body/right_arm/right_leg ...) or the name of an earlier cube (nesting).
#   sym=True builds the left twin automatically (mirrored x, shared UV, left_* parent).
#   kinds: plate, spike, horn, crystal
CUBES = []


def cube(slot, name, parent, kind, size, origin, pos=(0, 0, 0), rot=(0, 0, 0), sym=False):
    CUBES.append(dict(slot=slot, name=name, parent=parent, kind=kind, size=size, origin=origin,
                      pos=pos, rot=rot, sym=sym))


# ---- helmet (parent: head, box x[-4,4] y[-8,0] z[-4,4], armor grows it by 1)
cube("helmet", "brow", "head", "plate", (11, 2, 1), (-5.5, -7.5, -6.0))
cube("helmet", "nose", "head", "plate", (2, 5, 1), (-1, -5.5, -6.4))
cube("helmet", "cheek", "head", "plate", (1, 4, 5), (-6.2, -4.5, -4.0), sym=True)
cube("helmet", "horn_a", "head", "horn", (2, 6, 2), (-1, -6, -1), pos=(-4.8, -7.5, -0.5), rot=(0, 0, -0.85), sym=True)
cube("helmet", "horn_b", "horn_a", "horn", (2, 5, 2), (-1, -5, -1), pos=(0, -6, 0), rot=(0, 0, 0.55), sym=True)
cube("helmet", "horn_c", "horn_b", "horn", (1, 4, 1), (-0.5, -4, -0.5), pos=(0, -5, 0), rot=(0, 0, 0.65), sym=True)
cube("helmet", "crest", "head", "spike", (1, 3, 7), (-0.5, -11.5, -3.5))
cube("helmet", "back_spike", "head", "spike", (2, 5, 2), (-1, -5, -1), pos=(-2.2, -7.5, 4.2), rot=(-1.0, 0, 0.1), sym=True)

# ---- chestplate (parents: body, right_arm / left_arm)
cube("chest", "core_rim", "body", "plate", (6, 6, 1), (-3, 2, -3.6))
cube("chest", "core", "body", "crystal", (4, 4, 1), (-2, 3, -4.2))
cube("chest", "gorget", "body", "plate", (9, 2, 7), (-4.5, -2, -3.5))
cube("chest", "back_plate", "body", "plate", (8, 9, 1), (-4, 2, 3.0))
cube("chest", "spine_a", "body", "spike", (2, 6, 2), (-1, -6, -1), pos=(0, 2, 4.4), rot=(-0.55, 0, 0))
cube("chest", "spine_b", "body", "spike", (2, 5, 2), (-1, -5, -1), pos=(0, 5.5, 4.4), rot=(-0.55, 0, 0))
cube("chest", "spine_c", "body", "spike", (2, 4, 2), (-1, -4, -1), pos=(0, 9, 4.4), rot=(-0.55, 0, 0))
cube("chest", "pauldron", "right_arm", "plate", (7, 3, 7), (-5, -4, -3.5), sym=True)
cube("chest", "pauldron_rim", "right_arm", "plate", (8, 1, 8), (-5.5, -1.5, -4), sym=True)
cube("chest", "shoulder_a", "right_arm", "spike", (2, 6, 2), (-1, -6, -1), pos=(-4.2, -3.8, 0), rot=(0, 0, -0.95), sym=True)
cube("chest", "shoulder_b", "right_arm", "spike", (2, 5, 2), (-1, -5, -1), pos=(-1.5, -4, 1.5), rot=(-0.3, 0, -0.1), sym=True)
cube("chest", "bracer", "right_arm", "plate", (7, 4, 7), (-4.5, 6, -3.5), sym=True)
cube("chest", "elbow", "right_arm", "spike", (2, 4, 2), (-1, -4, -1), pos=(-4.2, 5.2, 1.4), rot=(-0.5, 0, -1.2), sym=True)

# ---- leggings (layer 2; parents: body, right_leg / left_leg; armor grows by 0.5)
cube("legs", "belt", "body", "plate", (10, 3, 6), (-5, 8.5, -3))
cube("legs", "buckle", "body", "crystal", (4, 4, 1), (-2, 8, -3.9))
cube("legs", "tasset_f", "body", "plate", (6, 5, 1), (-3, 0, -0.5), pos=(0, 11, -3.1), rot=(-0.18, 0, 0))
cube("legs", "tasset_b", "body", "plate", (6, 5, 1), (-3, 0, -0.5), pos=(0, 11, 3.1), rot=(0.18, 0, 0))
cube("legs", "hip", "body", "plate", (1, 5, 5), (-5.6, 8, -2.5), sym=True)
cube("legs", "thigh_f", "right_leg", "plate", (5, 6, 1), (-2.5, 0.5, -3.2), sym=True)
cube("legs", "thigh_o", "right_leg", "plate", (1, 7, 5), (-3.5, 0, -2.5), sym=True)
cube("legs", "knee", "right_leg", "plate", (5, 4, 2), (-2.5, 5, -3.6), sym=True)
cube("legs", "knee_spike", "right_leg", "spike", (2, 4, 2), (-1, -4, -1), pos=(0, 7, -3.6), rot=(1.2, 0, 0), sym=True)
cube("legs", "shin", "right_leg", "plate", (4, 4, 1), (-2, 8, -3.2), sym=True)

# ---- boots (layer 1; parents: right_leg / left_leg)
cube("boots", "cuff", "right_leg", "plate", (7, 3, 7), (-3.5, 8, -3.5), sym=True)
cube("boots", "toecap", "right_leg", "plate", (5, 2, 3), (-2.5, 10.5, -5.5), sym=True)
cube("boots", "toe_spike", "right_leg", "spike", (2, 5, 2), (-1, -5, -1), pos=(0, 11, -5.2), rot=(1.35, 0, 0), sym=True)
cube("boots", "heel_spike", "right_leg", "spike", (2, 4, 2), (-1, -4, -1), pos=(0, 11.5, 3.4), rot=(-1.2, 0, 0), sym=True)
cube("boots", "fin", "right_leg", "plate", (1, 5, 4), (-0.5, -5, -2), pos=(-3.6, 9.5, 1), rot=(0, 0, -0.5), sym=True)

SLOT_TEXTURE = {"helmet": 1, "chest": 1, "boots": 1, "legs": 2}
SLOT_DEFORM = {"helmet": "1.0F", "chest": "1.0F", "boots": "1.0F", "legs": "0.5F"}
SLOT_PARTS = {  # vanilla parts kept visible for the slot
    "helmet": ["head", "hat"],
    "chest": ["body", "right_arm", "left_arm"],
    "legs": ["body", "right_leg", "left_leg"],
    "boots": ["right_leg", "left_leg"],
}


# --------------------------------------------------------------------------- UV allocation
class Sheet:
    """Shelf packer over the part of the 128x128 sheet vanilla armor does not use."""

    def __init__(self):
        # vanilla armor UVs live in the top-left 64x32; keep the rest free
        self.rects = [(64, 0, 64, 128), (0, 32, 64, 96)]  # x, y, w, h  (free regions)
        self.cursor = [[r[0], r[1], 0] for r in self.rects]  # x, y, row height

    def alloc(self, w, h):
        for i, r in enumerate(self.rects):
            cx, cy, rowh = self.cursor[i]
            if cx + w > r[0] + r[2]:
                cx, cy, rowh = r[0], cy + rowh + 1, 0
            if cy + h <= r[1] + r[3] and cx + w <= r[0] + r[2]:
                self.cursor[i] = [cx + w + 1, cy, max(rowh, h)]
                return cx, cy
        raise RuntimeError("texture sheet is full")


def allocate():
    sheets = {1: Sheet(), 2: Sheet()}
    for c in sorted(CUBES, key=lambda c: -(c["size"][0] + c["size"][2]) * 2 * (c["size"][1] + c["size"][2])):
        w, h, d = c["size"]
        W, H = 2 * (d + w), d + h
        c["uv"] = sheets[SLOT_TEXTURE[c["slot"]]].alloc(W, H)


# --------------------------------------------------------------------------- painting
def put(img, x, y, col, a=255):
    if 0 <= x < TEX and 0 <= y < TEX:
        img.putpixel((x, y), (col[0], col[1], col[2], a))


def faces(u, v, w, h, d):
    """Pixel rectangles (x, y, w, h) of the six faces of a box with texOffs(u, v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def mid_of(c):
    return mix(c, HI, 0.35)


def draw_groove(img, x0, y0, w, h, rnd, col):
    """A jagged glowing crack running through the panel."""
    y = y0 + h // 2 + rnd.randint(-1, 1)
    for x in range(x0 + 1, x0 + w - 1):
        y = max(y0 + 1, min(y0 + h - 2, y + rnd.choice([-1, 0, 0, 1])))
        put(img, x, y, col)
        if rnd.random() < 0.4 and y + 1 < y0 + h - 1:
            put(img, x, y + 1, mix(col, HI, 0.55))


def panel(img, x0, y0, w, h, seed, base=BASE, hi=HI, glow=None, rivets=True, inset=True):
    """A plated rectangle: noisy metal, lit top/left edge, dark bottom/right edge, rivets, optional glow groove."""
    rnd = random.Random(seed)
    for y in range(h):
        for x in range(w):
            n = rnd.random()
            band = 0.5 + 0.5 * math.sin((x * 0.8 + y * 1.3) * 0.7)
            col = mix(base, mid_of(base), band * 0.5 + n * 0.25)
            put(img, x0 + x, y0 + y, col)
    for x in range(w):
        put(img, x0 + x, y0, hi)
        put(img, x0 + x, y0 + h - 1, OUT)
    for y in range(h):
        put(img, x0, y0 + y, mix(hi, base, 0.4))
        put(img, x0 + w - 1, y0 + y, mix(OUT, base, 0.3))
    if inset and w >= 5 and h >= 5:
        for x in range(1, w - 1):
            put(img, x0 + x, y0 + 1, mix(hi, base, 0.55))
        for y in range(1, h - 1):
            put(img, x0 + 1, y0 + y, mix(hi, base, 0.7))
    if rivets and w >= 6 and h >= 6:
        for (rx, ry) in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            put(img, x0 + rx, y0 + ry, LIGHT)
            put(img, x0 + rx + 1, y0 + ry + 1, OUT)
    if glow:
        draw_groove(img, x0, y0, w, h, rnd, glow)


def paint_cube(img, c):
    u, v = c["uv"]
    w, h, d = c["size"]
    kind = c["kind"]
    rnd = random.Random(sum(ord(ch) * (i + 1) for i, ch in enumerate(c["name"])))
    for fname, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        if fw <= 0 or fh <= 0:
            continue
        if kind == "plate":
            big = fw * fh >= 24
            panel(img, x0, y0, fw, fh, rnd.randint(0, 9999),
                  glow=GLOW if big and fname in ("front", "back") and fw >= 5 else None)
        elif kind == "crystal":
            for y in range(fh):
                for x in range(fw):
                    dx = abs(x - (fw - 1) / 2.0) / max(1.0, fw / 2.0)
                    dy = abs(y - (fh - 1) / 2.0) / max(1.0, fh / 2.0)
                    r = max(dx, dy)
                    col = mix(HOT, GLOW, r * 1.4)
                    if r > 0.78:
                        col = mix(GLOW, (110, 30, 170), (r - 0.78) * 4)
                    if (x + y) % 4 == 0 and r < 0.8:
                        col = mix(col, HOT, 0.5)
                    put(img, x0 + x, y0 + y, col)
            for x in range(fw):
                put(img, x0 + x, y0, (70, 20, 120))
                put(img, x0 + x, y0 + fh - 1, (70, 20, 120))
        elif kind in ("spike", "horn"):
            horn = kind == "horn"
            for y in range(fh):
                if fname == "top":
                    t = 1.0
                elif fname == "bottom":
                    t = 0.0
                else:
                    t = 1.0 - y / max(1.0, fh - 1.0)   # 1 at the tip (top row), 0 at the base
                if horn:
                    col = mix(mix(DARK, SILVER, t * 0.9), BONE, max(0.0, t - 0.6) * 1.5)
                    if fname not in ("top", "bottom") and y % 3 == 2:
                        col = mix(col, OUT, 0.45)        # growth rings
                    if t > 0.92:
                        col = mix(GLOW, HOT, 0.4)
                else:
                    col = mix(mix(DARK, HI, t * 0.8), LIGHT, max(0.0, t - 0.55) * 1.2)
                    if t > 0.85:
                        col = mix(GLOW, HOT, (t - 0.85) * 5)
                for x in range(fw):
                    shade = 0.85 if x == fw - 1 else 1.0
                    put(img, x0 + x, y0 + y, tuple(int(k * shade) for k in col))


# ---- vanilla UV regions (the base humanoid parts) --------------------------------------
def paint_vanilla(img1, img2):
    """Head / body / arms / legs of the two worn layers, in the standard armor layout."""
    # ---------------- layer 1 ----------------
    # head 8x8x8 at (0,0): top(8,0) bottom(16,0) right(0,8) front(8,8) left(16,8) back(24,8)
    panel(img1, 8, 0, 8, 8, 11, base=BASE, glow=GLOW)
    panel(img1, 16, 0, 8, 8, 12, base=DARK, rivets=False)
    panel(img1, 0, 8, 8, 8, 13, glow=GLOW)
    panel(img1, 16, 8, 8, 8, 14, glow=GLOW)
    panel(img1, 24, 8, 8, 8, 15, base=MID)
    panel(img1, 8, 8, 8, 8, 16, rivets=False)
    # glowing eye slits + vent
    for x in range(9, 15):
        put(img1, x, 11, OUT)
        put(img1, x, 12, GLOW)
    put(img1, 9, 13, HOT)
    put(img1, 14, 13, HOT)
    put(img1, 11, 12, HOT)
    put(img1, 12, 12, HOT)
    put(img1, 12, 11, OUT)
    put(img1, 11, 11, OUT)
    for x in (10, 12, 14):
        for y in (14, 15):
            put(img1, x, y, OUT)
    # body 8x12x4 at (16,16): top(20,16) bottom(28,16) right(16,20) front(20,20) left(28,20) back(32,20)
    panel(img1, 20, 16, 8, 4, 21, base=MID)
    panel(img1, 28, 16, 8, 4, 22, base=DARK, rivets=False)
    panel(img1, 16, 20, 4, 12, 23)
    panel(img1, 28, 20, 4, 12, 24)
    panel(img1, 32, 20, 8, 12, 25)
    panel(img1, 20, 20, 8, 12, 26)
    for y in (29, 31):                                   # ribs
        for x in range(21, 27):
            put(img1, x, y, OUT)
            put(img1, x, y - 1, HI)
    for y in range(26, 32):                              # central groove under the core
        put(img1, 23, y, mix(GLOW, OUT, 0.3))
        put(img1, 24, y, mix(GLOW, OUT, 0.3))
    for y in range(21, 31):                              # back: spine groove with glowing vertebrae
        put(img1, 35, y, OUT)
        put(img1, 36, y, OUT)
        if y % 3 == 0:
            put(img1, 35, y, GLOW)
            put(img1, 36, y, GLOW)
    # arm 4x12x4 at (40,16): top(44,16) bottom(48,16) right(40,20) front(44,20) left(48,20) back(52,20)
    panel(img1, 44, 16, 4, 4, 31, base=MID, rivets=False)
    panel(img1, 48, 16, 4, 4, 32, base=DARK, rivets=False)
    for i, ox in enumerate((40, 44, 48, 52)):
        panel(img1, ox, 20, 4, 12, 33 + i, glow=GLOW if i in (1, 3) else None)
    # legs (boots only use the bottom 4 rows): 4x12x4 at (0,16)
    panel(img1, 4, 16, 4, 4, 41, base=MID, rivets=False)
    panel(img1, 8, 16, 4, 4, 42, base=DARK, rivets=False)
    for i, ox in enumerate((0, 4, 8, 12)):
        panel(img1, ox, 28, 4, 4, 43 + i, base=BASE, inset=False, rivets=False)
        for x in range(ox, ox + 4):
            put(img1, x, 30, GLOW if (x + ox) % 2 == 0 else mix(GLOW, OUT, 0.4))

    # ---------------- layer 2 (leggings) ----------------
    panel(img2, 20, 16, 8, 4, 51, base=MID, rivets=False)
    panel(img2, 28, 16, 8, 4, 52, base=DARK, rivets=False)
    for (ox, ow) in ((16, 4), (20, 8), (28, 4), (32, 8)):   # belt rows of the body (bottom 5 rows)
        panel(img2, ox, 27, ow, 5, 53 + ox, glow=GLOW if (ow == 8 and ox == 32) else None)
    panel(img2, 4, 16, 4, 4, 61, base=MID, rivets=False)
    panel(img2, 8, 16, 4, 4, 62, base=DARK, rivets=False)
    for i, ox in enumerate((0, 4, 8, 12)):
        panel(img2, ox, 20, 4, 12, 63 + i, glow=GLOW if i == 1 else None)
        for x in range(ox, ox + 4):
            put(img2, x, 31, OUT)


def make_textures():
    allocate()
    img1 = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    img2 = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    paint_vanilla(img1, img2)
    for c in CUBES:
        paint_cube(img1 if SLOT_TEXTURE[c["slot"]] == 1 else img2, c)
    d = os.path.join(RES, "assets", MOD, "textures", "models", "armor")
    os.makedirs(d, exist_ok=True)
    img1.save(os.path.join(d, "rift_layer_1.png"))
    img2.save(os.path.join(d, "rift_layer_2.png"))


# --------------------------------------------------------------------------- icons
ICON_PAL = {"O": OUT + (255,), "D": DARK + (255,), "A": BASE + (255,), "M": MID + (255,), "H": HI + (255,),
            "L": LIGHT + (255,), "S": SILVER + (255,), "B": BONE + (255,), "G": GLOW + (255,), "W": HOT + (255,)}

ICONS = {
    "helmet": [
        "................",
        ".OB..........BO.",
        ".OSO........OSO.",
        ".OBSO.OOOO.OSBO.",
        "..OSO.OHHO.OSO..",
        "...OOOHMMHOOO...",
        "....OHMAAMHO....",
        "....OHAMMAHO....",
        "....OAGWWGAO....",
        "....OADGGDAO....",
        "....OAAOOAAO....",
        ".....OAAAAO.....",
        "......ODDO......",
        ".......OO.......",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        ".OS.OOO..OOO.SO.",
        ".OSOHHHOOHHHOSO.",
        "OBSOHMMMHHMMMOSO",
        "OSOHMAAAMMAAAMOS",
        ".OOHMAAAOOAAAMOO",
        "..OHAAMOWWOMAAHO",
        "..OHAAAOWWOAAAHO",
        "..OAAMAAOOAAMAAO",
        "..OAGAAMAAMAAGAO",
        "..OAAAMAAAAMAAAO",
        "..OAMAAGAAGAAMAO",
        "..OADAAAAAAAADAO",
        "...OOOAAAAAAOOO.",
        "......OOOOOO....",
        "................",
    ],
    "leggings": [
        "................",
        "...OOOOOOOOOO...",
        "..OHHHHWWHHHHO..",
        "..OAMMMGGMMMAO..",
        "..OAAAMMMMAAAO..",
        "..OAAAOOOOAAAO..",
        "..OAMAO..OAMAO..",
        "..OSBAO..OABSO..",
        "..OBSAO..OASBO..",
        "..OAAAO..OAAAO..",
        "..OAGAO..OAGAO..",
        "..OAAAO..OAAAO..",
        "..OAMAO..OAMAO..",
        "..ODDDO..ODDDO..",
        "...OOO....OOO...",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "...OOOO..OOOO...",
        "..OHAAAO.OAAAHO.",
        "..OMAAAO.OAAAMO.",
        "..OAAAAO.OAAAAO.",
        "..OAGAAO.OAAGAO.",
        ".OOAAAAO.OAAAAOO",
        "OSOAAAAAOOAAAAAO",
        "OBSAMMAAOOAAMMAS",
        ".OSOOOOOO.OOOOOO",
        "..OO......OO....",
        "................",
        "................",
    ],
}


def make_icons():
    d = os.path.join(RES, "assets", MOD, "textures", "item")
    os.makedirs(d, exist_ok=True)
    for name, rows in ICONS.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(rows[:16]):
            for x, ch in enumerate(row[:16]):
                if ch in ICON_PAL:
                    img.putpixel((x, y), ICON_PAL[ch])
        # armor.png file names keep the old pieces: chestplate/leggings/helmet/boots
        img.save(os.path.join(d, "rift_%s.png" % name))


# --------------------------------------------------------------------------- Java output
def f(x):
    s = ("%.3f" % x).rstrip("0").rstrip(".")
    if s in ("", "-0"):
        s = "0"
    return s + "F"


def side_variants(c):
    """(java_name, parent, origin_x, pos_x, rot_y, rot_z, mirror) for the right (-x) and, if sym, the left twin."""
    out = [(c["name"] + ("_r" if c["sym"] else ""), rename_parent(c["parent"], c, False), c["origin"][0], c["pos"][0],
            c["rot"][1], c["rot"][2], False)]
    if c["sym"]:
        ox = -(c["origin"][0] + c["size"][0])
        out.append((c["name"] + "_l", rename_parent(c["parent"], c, True), ox, -c["pos"][0], -c["rot"][1], -c["rot"][2], True))
    return out


def rename_parent(parent, c, left):
    vanilla = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}
    if parent in vanilla:
        return parent.replace("right_", "left_") if left else parent
    # nested: parent is another cube of the same slot (also sym in all our tables)
    base = next(x for x in CUBES if x["slot"] == c["slot"] and x["name"] == parent)
    if base["sym"]:
        return parent + ("_l" if left else "_r")
    return parent


def emit_java():
    lines = []
    w = lines.append
    w("package com.darkrifts.client;")
    w("")
    w("import net.minecraft.client.model.HumanoidModel;")
    w("import net.minecraft.client.model.geom.PartPose;")
    w("import net.minecraft.client.model.geom.builders.CubeDeformation;")
    w("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    w("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    w("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    w("import net.minecraft.client.model.geom.builders.PartDefinition;")
    w("")
    w("/** GENERATED by tools/armor_gen.py - the cube table and the texture sheet come from the same source. Do not edit. */")
    w("public final class RiftArmorParts {")
    w("    private RiftArmorParts() {}")
    w("")
    for slot, method in (("helmet", "helmet"), ("chest", "chest"), ("legs", "legs"), ("boots", "boots")):
        w("    public static LayerDefinition %s() {" % method)
        w("        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(%s), 0.0F);" % SLOT_DEFORM[slot])
        w("        PartDefinition root = mesh.getRoot();")
        declared = set()
        for c in [c for c in CUBES if c["slot"] == slot]:
            for (jn, parent, ox, px, ry, rz, mirror) in side_variants(c):
                pvar = parent
                if pvar in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg") and pvar not in declared:
                    w('        PartDefinition %s = root.getChild("%s");' % (pvar, pvar))
                    declared.add(pvar)
                wd, hd, dd = c["size"]
                u, v = c["uv"]
                oy, oz = c["origin"][1], c["origin"][2]
                builder = "CubeListBuilder.create().texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s)" % (
                    u, v, ".mirror()" if mirror else "", f(ox), f(oy), f(oz), f(wd), f(hd), f(dd))
                pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (
                    f(px), f(c["pos"][1]), f(c["pos"][2]), f(c["rot"][0]), f(ry), f(rz))
                w('        PartDefinition %s = %s.addOrReplaceChild("%s", %s, %s);' % (jn, pvar, jn, builder, pose))
                declared.add(jn)
        w("        return LayerDefinition.create(mesh, %d, %d);" % (TEX, TEX))
        w("    }")
        w("")
    w("}")
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def main():
    make_icons()
    make_textures()
    emit_java()


if __name__ == "__main__":
    main()
