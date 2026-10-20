#!/usr/bin/env python3
"""
Item icons v2 (16x16): mirrorite tools and weapons, bow (+3 pulling frames), ingot, raw ore, Reaver heart, Mirror Blade,
Rift Blade, dark water bucket. Called from generate_assets.py after arsenal.py (overwrites its icons):
    items_gen.build(RES, MOD)
Every icon is built from shapes, shaded by the same rule (lit up-left edge, dark down-right edge), glow pixels and a
dark outline added automatically.
"""
import math
import os

from PIL import Image

OUT = (10, 5, 20)
M_DARK = (44, 26, 92)
M_MID = (96, 64, 176)
M_LIGHT = (158, 126, 240)
M_HI = (228, 212, 255)
GLOW = (236, 110, 255)
HOT = (255, 232, 255)
TEAL = (96, 232, 242)
H_DARK = (40, 24, 46)
H_MID = (110, 72, 100)
H_LIGHT = (156, 112, 140)
GOLD = (222, 176, 76)
GOLD_D = (150, 108, 40)
BONE = (200, 192, 226)


class Canvas:
    def __init__(self):
        self.p = {}

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[(x, y)] = c

    def line(self, a, b, c, w=1):
        (x0, y0), (x1, y1) = a, b
        n = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
        for i in range(n + 1):
            t = i / float(n)
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            for dx in range(w):
                for dy in range(w):
                    self.set(int(round(x)) + dx - (w - 1) // 2, int(round(y)) + dy - (w - 1) // 2, c)

    def poly(self, pts, c):
        for y in range(16):
            for x in range(16):
                if inside(x + 0.5, y + 0.5, pts):
                    self.set(x, y, c)

    def shade(self, cells, dark, mid, light):
        """Paint the given cells with a shading rule: up-left edge light, down-right edge dark."""
        cells = set(cells)
        for (x, y) in cells:
            up = (x, y - 1) in cells
            left = (x - 1, y) in cells
            down = (x, y + 1) in cells
            right = (x + 1, y) in cells
            col = mid
            if not up or not left:
                col = light
            if (not down or not right) and (up and left):
                col = dark
            if (not down and not right):
                col = dark
            self.set(x, y, col)

    def render(self):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for (x, y), c in self.p.items():
            img.putpixel((x, y), tuple(c) + (255,))
        solid = set(self.p)
        for (x, y) in solid:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in solid and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    img.putpixel(q, OUT + (255,))
        return img


def inside(x, y, pts):
    ok = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
            ok = not ok
        j = i
    return ok


def cells_line(a, b, w=1):
    cv = Canvas()
    cv.line(a, b, (1, 1, 1), w)
    return set(cv.p)


def cells_poly(pts):
    cv = Canvas()
    cv.poly(pts, (1, 1, 1))
    return set(cv.p)


def handle(cv, a=(1, 14), b=(8, 7)):
    cv.shade(cells_line(a, b, 1), H_DARK, H_MID, H_LIGHT)
    cv.set(a[0], a[1], GOLD_D)
    cv.set(a[0] + 1, a[1] - 1, GOLD)


def sparkle(cv, pts):
    for (x, y) in pts:
        cv.set(x, y, HOT)


# ------------------------------------------------------------------ weapons and tools
def sword():
    cv = Canvas()
    handle(cv, (2, 13), (5, 10))
    cv.shade(cells_line((6, 9), (14, 1), 2), M_DARK, M_MID, M_LIGHT)
    cv.line((7, 8), (13, 2), M_HI)                                 # fuller highlight
    cv.shade(cells_line((3, 8), (8, 13), 1) | cells_line((4, 8), (8, 12), 1), GOLD_D, GOLD, (255, 226, 140))   # guard
    cv.set(14, 1, HOT)
    cv.set(10, 5, GLOW)
    cv.set(9, 6, GLOW)
    return cv


def pickaxe():
    """Head perpendicular to the handle (upper left to lower right), bulging towards the top right, like vanilla."""
    cv = Canvas()
    handle(cv, (1, 14), (9, 6))
    P0, C, P2 = (3.0, 3.0), (14.0, 0.0), (14.0, 12.0)
    head = set()
    for i in range(0, 61):
        t = i / 60.0
        x = (1 - t) ** 2 * P0[0] + 2 * (1 - t) * t * C[0] + t * t * P2[0]
        y = (1 - t) ** 2 * P0[1] + 2 * (1 - t) * t * C[1] + t * t * P2[1]
        head.add((int(round(x)), int(round(y))))
        if 0.12 < t < 0.88:                                  # thicker in the middle, pointed at the ends
            head.add((int(round(x)) - 1, int(round(y)) + 1))
            if 0.3 < t < 0.7:
                head.add((int(round(x)) - 1, int(round(y))))
                head.add((int(round(x)), int(round(y)) + 1))
    cv.shade(head, M_DARK, M_MID, M_LIGHT)
    cv.set(9, 4, M_HI)
    cv.set(10, 4, GLOW)
    cv.set(11, 5, GLOW)
    cv.set(5, 3, M_HI)
    cv.set(14, 11, M_LIGHT)
    return cv


def axe():
    """Vanilla orientation: the blade sits to the upper LEFT of the handle's tip, edge facing left."""
    cv = Canvas()
    handle(cv, (1, 14), (10, 5))
    head = cells_poly([(10.5, 1.5), (5, 0.5), (1.5, 3), (1.5, 7), (4, 8.5), (6.5, 6.5), (9.5, 4.5)])
    cv.shade(head, M_DARK, M_MID, M_LIGHT)
    cv.line((7, 2), (3, 4), M_HI)
    cv.set(5, 5, GLOW)
    cv.set(4, 6, GLOW)
    cv.set(2, 4, M_HI)
    return cv


def shovel():
    cv = Canvas()
    handle(cv, (1, 14), (10, 5))
    head = set()
    for y in range(16):
        for x in range(16):
            if ((x - 11.5) ** 2) / 9.0 + ((y - 3.5) ** 2) / 12.0 <= 1.0 and (x + y) >= 12:
                head.add((x, y))
    cv.shade(head, M_DARK, M_MID, M_LIGHT)
    cv.set(12, 3, GLOW)
    cv.set(11, 4, M_HI)
    return cv


def hoe():
    """Vanilla orientation: a bar from the handle's tip going LEFT, with the blade hanging down on the left."""
    cv = Canvas()
    handle(cv, (1, 14), (10, 5))
    head = cells_line((4, 2), (10, 2), 2) | {(3, 3), (3, 4), (4, 4), (4, 5), (3, 5), (4, 6), (5, 3), (5, 4)}
    cv.shade(head, M_DARK, M_MID, M_LIGHT)
    cv.set(8, 2, GLOW)
    cv.set(7, 2, M_HI)
    cv.set(4, 4, GLOW)
    return cv


def bow(pull):
    """Vanilla orientation: the limbs bulge to the upper right, the string runs on the lower left side."""
    cv = Canvas()
    P0, P2, C = (2.0, 2.0), (13.0, 13.0), (14.0, 1.0)
    arc = set()
    for i in range(0, 101):
        t = i / 100.0
        x = (1 - t) ** 2 * P0[0] + 2 * (1 - t) * t * C[0] + t * t * P2[0]
        y = (1 - t) ** 2 * P0[1] + 2 * (1 - t) * t * C[1] + t * t * P2[1]
        arc.add((int(round(x)), int(round(y))))
        arc.add((int(round(x)) - 1, int(round(y))))
    cv.shade(arc, M_DARK, M_MID, M_LIGHT)
    for (x, y) in sorted(arc):
        if (x, y) in ((9, 3), (10, 4), (11, 6), (7, 2)):
            cv.set(x, y, GLOW)
    cv.set(2, 2, GOLD)
    cv.set(13, 13, GOLD)
    cv.set(10, 7, H_MID)                                              # grip wrap
    cv.set(9, 8, H_LIGHT)
    cv.set(11, 6, H_MID)
    mid = (7.5 - pull * 1.7, 7.5 + pull * 1.7)                        # pulled towards the lower left
    m = (round(mid[0]), round(mid[1]))
    cv.line((2, 2), m, (236, 228, 250))
    cv.line(m, (13, 13), (236, 228, 250))
    if pull > 0:                                                       # nocked arrow pointing up right
        cv.line(m, (12, 3), H_LIGHT)
        cv.set(13, 2, TEAL)
        cv.set(14, 1, HOT)
        cv.set(m[0] - 1, m[1] + 1, GLOW)
    return cv


def mirror_blade():
    """One-use dagger: a shard of mirror, cracked and bright, with a simple hilt."""
    cv = Canvas()
    handle(cv, (2, 13), (4, 11))
    cv.shade(cells_line((5, 10), (13, 2), 3), (140, 120, 210), (214, 204, 250), (255, 255, 255))
    cv.line((6, 10), (12, 4), M_LIGHT)
    for (x, y) in ((8, 7), (9, 6), (11, 4)):
        cv.set(x, y, GLOW)
    cv.set(10, 6, TEAL)
    cv.shade(cells_line((3, 9), (7, 13), 1), GOLD_D, GOLD, (255, 226, 140))
    sparkle(cv, [(14, 1), (13, 1)])
    return cv


def rift_blade():
    """The reusable blade: long dark blade with a glowing tear running down its length, gold crystal guard."""
    cv = Canvas()
    handle(cv, (1, 14), (4, 11))
    cv.shade(cells_line((5, 10), (14, 1), 3), (24, 10, 44), (52, 30, 96), M_MID)
    for i in range(0, 9):
        x, y = 13 - i, 2 + i
        if i % 3 == 1:
            x -= 1
        cv.set(x, y, GLOW if i % 2 == 0 else HOT)
    cv.set(14, 1, HOT)
    cv.shade(cells_line((2, 9), (7, 14), 1) | cells_line((3, 9), (7, 13), 1), GOLD_D, GOLD, (255, 226, 140))
    cv.set(4, 10, TEAL)
    cv.set(15, 0, GLOW)
    sparkle(cv, [(11, 0), (15, 4)])
    return cv


# ------------------------------------------------------------------ materials
def ingot():
    cv = Canvas()
    top = cells_poly([(5, 5), (13, 5), (14, 8), (6, 8)])
    front = cells_poly([(6, 8), (14, 8), (14, 11), (6, 11)])
    side = cells_poly([(2, 8), (6, 8), (6, 11), (2, 11), (1, 9.5)])
    for (x, y) in top:
        cv.set(x, y, M_LIGHT if (y == 5 or x == 5) else M_MID)
    for (x, y) in front:
        cv.set(x, y, M_MID if y < 10 else M_DARK)
    for (x, y) in side:
        cv.set(x, y, M_DARK if x > 2 else (60, 36, 120))
    for x in range(6, 14):
        cv.set(x, 5, M_HI if x % 2 == 0 else M_LIGHT)
    cv.set(9, 9, GLOW)
    cv.set(10, 9, HOT)
    cv.set(11, 9, GLOW)
    return cv


def raw():
    cv = Canvas()
    rock = cells_poly([(3, 6), (6, 3), (11, 3), (14, 6), (13, 11), (9, 13), (4, 12)])
    cv.shade(rock, (30, 22, 40), (64, 50, 78), (104, 90, 124))
    for (x, y, c) in ((6, 6, GLOW), (7, 7, HOT), (8, 8, GLOW), (9, 9, GLOW), (10, 5, GLOW), (11, 6, HOT), (6, 10, GLOW),
                      (5, 9, M_LIGHT), (9, 5, M_LIGHT), (10, 10, M_LIGHT), (12, 8, GLOW)):
        cv.set(x, y, c)
    return cv


HEART = [
    "................",
    "................",
    "..XXXXX..XXXXX..",
    ".XXXXXXX.XXXXXXX",
    ".XXXXXXXXXXXXXXX",
    ".XXXXXXXXXXXXXXX",
    "..XXXXXXXXXXXXX.",
    "..XXXXXXXXXXXXX.",
    "...XXXXXXXXXXX..",
    "....XXXXXXXXX...",
    ".....XXXXXXX....",
    "......XXXXX.....",
    ".......XXX......",
    "........X.......",
]


def heart():
    cv = Canvas()
    cells = {(x, y) for y, row in enumerate(HEART) for x, ch in enumerate(row) if ch == "X"}
    for (x, y) in cells:
        up = (x, y - 1) in cells
        left = (x - 1, y) in cells
        down = (x, y + 1) in cells
        right = (x + 1, y) in cells
        t = (y - 2) / 11.0
        col = (int(170 - 90 * t), int(24 - 12 * t), int(70 - 26 * t))
        if not up or not left:
            col = (255, 128, 176)
        elif not down or not right:
            col = (84, 8, 40)
        cv.set(x, y, col)
    for (x, y) in ((3, 4), (4, 4), (3, 5)):
        cv.set(x, y, (255, 238, 246))                                  # specular on the left lobe
    for (x, y, c) in ((8, 5, GLOW), (8, 6, HOT), (9, 7, GLOW), (8, 8, GLOW), (8, 9, GLOW), (7, 10, HOT), (11, 4, GLOW),
                      (12, 5, GLOW), (5, 7, GLOW), (6, 6, GLOW)):
        if (x, y) in cells:
            cv.set(x, y, c)
    return cv


def bucket():
    cv = Canvas()
    body = cells_poly([(3, 5), (13, 5), (11.5, 14), (4.5, 14)])
    cv.shade(body, M_DARK, M_MID, M_LIGHT)
    for x in range(4, 13):                                              # dark water
        for y in (5, 6):
            cv.set(x, y, (12, 6, 30))
    for (x, y, c) in ((6, 5, M_LIGHT), (8, 6, GLOW), (10, 5, TEAL), (9, 5, (70, 40, 140)), (5, 6, (40, 24, 90))):
        cv.set(x, y, c)
    for x in range(3, 14):
        cv.set(x, 4, M_HI if x < 8 else M_LIGHT)                        # rim
    cv.line((3, 4), (4, 2), M_MID)                                      # handle ring
    cv.line((13, 4), (12, 2), M_MID)
    cv.line((4, 2), (12, 2), M_MID)
    cv.set(8, 9, GLOW)
    cv.set(8, 10, HOT)
    return cv


ICONS = {
    "mirrorite_sword": sword, "mirrorite_pickaxe": pickaxe, "mirrorite_axe": axe, "mirrorite_shovel": shovel,
    "mirrorite_hoe": hoe, "mirror_blade": mirror_blade, "rift_blade": rift_blade, "mirrorite_ingot": ingot,
    "raw_mirrorite": raw, "reaver_heart": heart, "dark_water_bucket": bucket,
    "mirror_bow": lambda: bow(0), "mirror_bow_pulling_0": lambda: bow(1), "mirror_bow_pulling_1": lambda: bow(2),
    "mirror_bow_pulling_2": lambda: bow(3),
}


def build(RES, MOD):
    d = os.path.join(RES, "assets", MOD, "textures", "item")
    os.makedirs(d, exist_ok=True)
    for name, fn in ICONS.items():
        fn().render().save(os.path.join(d, name + ".png"))


if __name__ == "__main__":
    print("run via generate_assets.py")
