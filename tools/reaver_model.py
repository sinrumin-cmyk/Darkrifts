#!/usr/bin/env python3
"""
Rift Reaver: model (geo), animations, texture and glow mask. Called from generate_assets.py:
    reaver_model.build(RES, MOD, write_json)
All cubes are axis-aligned (no cube rotations) so nothing depends on rotation sign conventions.
Units are model pixels: 16 px = 1 block. The boss is about 4.3 blocks tall.
"""
import math
import os
import random

from PIL import Image

ATLAS = 256


def cube(o, s, style, tag=None):
    return {"o": o, "s": s, "style": style, "tag": tag}


def mirror(c):
    o, s = c["o"], c["s"]
    return {"o": (-(o[0] + s[0]), o[1], o[2]), "s": s, "style": c["style"], "tag": c["tag"]}


def mirror_pivot(p):
    return (-p[0], p[1], p[2])


# --------------------------------------------------------------------------- the model
def define():
    bones = []  # (name, parent, pivot, cubes)

    # ---- body (torso, collar, back plate, spine spikes, loincloth)
    body = [
        cube((-7, 25, -3), (14, 5, 6), "armor"),                      # pelvis
        cube((-4, 30, -2), (8, 8, 4), "core", "abdomen"),            # glowing abdomen
        cube((-10, 38, -4), (20, 12, 8), "armor", "chest"),          # chest (broad shoulders)
        cube((-7, 39, -5), (14, 9, 1), "plate", "chestplate"),       # front plate with the core
        cube((-6, 50, -3), (12, 2, 6), "plate"),                      # collar
        cube((-7, 39, 4), (14, 9, 1), "plate"),                       # back plate
        cube((-1, 43, 5), (2, 8, 2), "bone"),                         # spine spikes
        cube((-1, 34, 4), (2, 6, 2), "bone"),
        cube((-1, 48, 5), (2, 6, 2), "bone"),
        cube((-3, 18, -5), (6, 12, 1), "cloth"),                      # loincloth
        cube((-5, 22, -4), (2, 8, 1), "cloth"),
        cube((3, 22, -4), (2, 8, 1), "cloth"),
    ]
    bones.append(("body", "root", (0, 28, 0), body))

    # ---- head and jaw
    head = [
        cube((-4, 51, -5), (8, 7, 8), "bone", "skull"),
        cube((-5, 55, -6), (10, 2, 2), "bonedark"),                   # heavy brow
        cube((-1, 58, -2), (2, 6, 2), "bone"),                         # crown
        cube((-3, 58, -3), (2, 4, 2), "bone"),
        cube((1, 58, -3), (2, 4, 2), "bone"),
        # horns: stairs going out and up
        cube((-7, 54, -2), (3, 4, 3), "bone"),
        cube((-9, 57, -2), (3, 4, 3), "bone"),
        cube((-11, 60, -2), (2, 4, 2), "bone"),
        cube((-12, 63, -1), (1, 3, 1), "bone"),
        cube((4, 54, -2), (3, 4, 3), "bone"),
        cube((6, 57, -2), (3, 4, 3), "bone"),
        cube((9, 60, -2), (2, 4, 2), "bone"),
        cube((11, 63, -1), (1, 3, 1), "bone"),
    ]
    bones.append(("head", "body", (0, 51, -1), head))
    jaw = [
        cube((-3, 48, -6), (6, 3, 7), "bone"),
        cube((-3, 51, -6), (1, 2, 1), "tooth"),
        cube((2, 51, -6), (1, 2, 1), "tooth"),
        cube((-1, 51, -6), (2, 1, 1), "tooth"),
    ]
    bones.append(("jaw", "head", (0, 52, -4), jaw))

    # ---- arms (right side, then mirrored)
    arm_r = [
        cube((-17, 47, -5), (9, 4, 10), "plate"),                      # pauldron
        cube((-16, 51, -2), (2, 6, 2), "bone"),
        cube((-13, 51, -4), (2, 4, 2), "bone"),
        cube((-13, 51, 1), (2, 4, 2), "bone"),
        cube((-15, 36, -3), (6, 12, 6), "armor"),                      # upper arm
        cube((-17, 38, -1), (2, 3, 2), "bone"),                        # elbow spike
    ]
    fore_r = [
        cube((-15, 21, -3), (6, 15, 6), "armor"),                      # forearm
        cube((-16, 24, -4), (8, 5, 8), "plate"),                       # bracer
        cube((-15, 14, -3), (6, 7, 6), "armor", "hand"),
        cube((-15, 6, -3), (2, 8, 2), "bone", "claw"),
        cube((-12, 6, -3), (2, 8, 2), "bone", "claw"),
        cube((-14, 6, 0), (2, 8, 2), "bone", "claw"),
    ]
    def shift(cs, dx):
        return [cube((c["o"][0] + dx, c["o"][1], c["o"][2]), c["s"], c["style"], c["tag"]) for c in cs]

    arm_r = shift(arm_r, -2)
    fore_r = shift(fore_r, -2)
    bones.append(("arm_r", "body", (-13, 48, 0), arm_r))
    bones.append(("fore_r", "arm_r", (-14, 36, 0), fore_r))
    bones.append(("arm_l", "body", mirror_pivot((-13, 48, 0)), [mirror(c) for c in arm_r]))
    bones.append(("fore_l", "arm_l", mirror_pivot((-14, 36, 0)), [mirror(c) for c in fore_r]))

    # ---- legs
    leg_r = [
        cube((-7, 17, -4), (7, 12, 8), "armor"),                       # thigh
        cube((-9, 22, -2), (2, 4, 3), "bone"),
    ]
    shin_r = [
        cube((-7, 5, -3), (6, 12, 6), "armor"),                        # shin
        cube((-6, 14, -6), (4, 3, 3), "bone"),                         # knee spike
        cube((-7, 0, -6), (7, 5, 10), "plate"),                        # foot
        cube((-7, 0, -9), (2, 2, 3), "bone", "claw"),                  # toes
        cube((-4, 0, -9), (2, 2, 3), "bone", "claw"),
    ]
    bones.append(("leg_r", "root", (-4, 29, 0), leg_r))
    bones.append(("shin_r", "leg_r", (-4, 17, 0), shin_r))
    bones.append(("leg_l", "root", mirror_pivot((-4, 29, 0)), [mirror(c) for c in leg_r]))
    bones.append(("shin_l", "leg_l", mirror_pivot((-4, 17, 0)), [mirror(c) for c in shin_r]))

    # ---- torn cloak on the back (sways)
    cloak = [
        cube((-8, 22, 5), (4, 24, 1), "cloth"),
        cube((-3, 16, 5), (6, 30, 1), "cloth"),
        cube((4, 22, 5), (4, 24, 1), "cloth"),
        cube((-6, 24, 6), (3, 20, 1), "cloth"),
        cube((3, 24, 6), (3, 20, 1), "cloth"),
        cube((-1, 20, 6), (2, 24, 1), "cloth"),
    ]
    bones.append(("cloak", "body", (0, 46, 5), cloak))

    # ---- floating crystals orbiting the boss
    shards = [
        cube((19, 44, -1), (3, 8, 3), "crystal"),
        cube((-23, 34, -1), (3, 8, 3), "crystal"),
        cube((-2, 52, -21), (3, 8, 3), "crystal"),
        cube((-1, 36, 20), (3, 8, 3), "crystal"),
        cube((14, 58, 14), (3, 8, 3), "crystal"),
        cube((-16, 56, -13), (3, 8, 3), "crystal"),
    ]
    bones.append(("shards", "root", (0, 44, 0), shards))
    return bones


# --------------------------------------------------------------------------- UV packing
def pack(bones):
    items = []
    for bi, (_, _, _, cubes) in enumerate(bones):
        for ci, c in enumerate(cubes):
            w, h, d = c["s"]
            items.append((h + d, 2 * (w + d), bi, ci))
    items.sort(reverse=True)
    x = y = shelf = 0
    for (H, W, bi, ci) in items:
        if x + W > ATLAS:
            x = 0
            y += shelf + 1
            shelf = 0
        bones[bi][3][ci]["uv"] = (x, y)
        x += W + 1
        shelf = max(shelf, H)
    if y + shelf > ATLAS:
        raise SystemExit("atlas too small: need %d" % (y + shelf))


# --------------------------------------------------------------------------- painting
PAL = {
    "armor": ((40, 25, 74), (92, 62, 150), (15, 8, 30)),
    "plate": ((62, 40, 110), (130, 88, 200), (24, 13, 48)),
    "bone": ((186, 174, 206), (234, 224, 246), (98, 86, 124)),
    "bonedark": ((122, 108, 146), (180, 166, 204), (60, 48, 84)),
    "tooth": ((236, 230, 244), (255, 255, 255), (150, 140, 170)),
    "core": ((14, 8, 28), (46, 28, 90), (6, 3, 14)),
    "cloth": ((28, 17, 52), (66, 42, 108), (12, 7, 24)),
    "crystal": ((132, 82, 232), (240, 206, 255), (72, 32, 156)),
}
GLOW_V = (222, 120, 255)
GLOW_W = (255, 236, 255)


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def put(img, glow, x, y, col, glowing=False, alpha=255):
    if 0 <= x < ATLAS and 0 <= y < ATLAS:
        c = tuple(int(max(0, min(255, v))) for v in col)
        img.putpixel((x, y), c + (alpha,))
        if glowing:
            glow.putpixel((x, y), c + (255,))


def crack(img, glow, rect, rnd, col=GLOW_V, length=None, horizontal=None):
    fx, fy, fw, fh = rect
    if fw < 3 or fh < 3:
        return
    length = length or (fw + fh)
    if horizontal is None:
        horizontal = fw >= fh
    if horizontal:
        x, y = 0, rnd.randrange(1, fh - 1)
        dx, dy = 1, 0
    else:
        x, y = rnd.randrange(1, fw - 1), 0
        dx, dy = 0, 1
    for _ in range(length):
        if not (0 <= x < fw and 0 <= y < fh):
            break
        put(img, glow, fx + x, fy + y, col, True)
        if rnd.random() < 0.25:
            put(img, glow, fx + x + (1 if dx == 0 else 0), fy + y + (1 if dy == 0 else 0), lerp(col, (90, 40, 200), 0.5), True)
        step = rnd.choice([-1, 0, 0, 1])
        if horizontal:
            x += 1
            y = max(0, min(fh - 1, y + step))
        else:
            y += 1
            x = max(0, min(fw - 1, x + step))


def paint_face(img, glow, rect, face, c, rnd):
    fx, fy, fw, fh = rect
    base, hi, lo = PAL[c["style"]]
    style = c["style"]
    light = {"top": 1.2, "bottom": 0.6}.get(face)
    for y in range(fh):
        for x in range(fw):
            if light is None:
                t = y / max(1, fh - 1)
                lt = 1.08 - 0.34 * t + (0.07 if face in ("front", "right") else -0.05)
            else:
                lt = light
            col = tuple(v * lt for v in base)
            col = tuple(v * (1.0 + (rnd.random() - 0.5) * 0.14) for v in col)
            if style in ("armor", "plate", "bone", "bonedark"):
                if x == 0 or y == 0:
                    col = lerp(col, hi, 0.5)
                if x == fw - 1 or y == fh - 1:
                    col = lerp(col, lo, 0.6)
            alpha = 255
            if style == "cloth":
                if y > fh * 0.72 and face not in ("top", "bottom"):
                    if rnd.random() < (y - fh * 0.72) / (fh * 0.28) * 0.85:
                        alpha = 0
                if x in (0, fw - 1) and rnd.random() < 0.3:
                    alpha = 0
            put(img, glow, fx + x, fy + y, col, False, alpha)
            if style == "crystal":
                cx, cy = (fw - 1) / 2.0, (fh - 1) / 2.0
                k = 1.0 - min(1.0, math.hypot((x - cx) / max(1, fw / 2), (y - cy) / max(1, fh / 2)))
                gcol = lerp(base, (255, 245, 255), k)
                put(img, glow, fx + x, fy + y, gcol, True)
    # plate seams + rivets
    if style in ("armor", "plate") and fw >= 6 and fh >= 6:
        for y in range(1, fh - 1):
            put(img, glow, fx + fw // 2, fy + y, lerp(base, lo, 0.75))
        for x in range(1, fw - 1):
            if fh >= 10:
                put(img, glow, fx + x, fy + fh // 2, lerp(base, lo, 0.75))
        for (rx, ry) in ((2, 2), (fw - 3, 2), (2, fh - 3), (fw - 3, fh - 3)):
            put(img, glow, fx + rx, fy + ry, hi)
    # cracks and runes
    if style in ("armor", "plate") and fw >= 5 and fh >= 5 and rnd.random() < 0.55:
        crack(img, glow, rect, rnd, GLOW_V if rnd.random() < 0.7 else (120, 230, 240))
    if style == "plate" and fw >= 8 and fh >= 8 and rnd.random() < 0.6:
        gx, gy = fx + rnd.randrange(2, fw - 4), fy + rnd.randrange(2, fh - 4)
        for (dx, dy) in rnd.choice([[(0, 0), (1, 0), (2, 0), (1, 1), (1, 2)],
                                    [(0, 0), (0, 1), (0, 2), (1, 1), (2, 1)],
                                    [(0, 0), (1, 1), (2, 2), (2, 0), (0, 2)]]):
            put(img, glow, gx + dx, gy + dy, GLOW_V, True)
    if style == "core":
        for _ in range(3):
            crack(img, glow, rect, rnd, GLOW_V, horizontal=rnd.random() < 0.5)
    if style == "cloth":
        for x in range(1, fw - 1):
            if rnd.random() < 0.12:
                put(img, glow, fx + x, fy + int(fh * 0.55), (110, 60, 200), True)
    if c.get("tag") == "claw" and face not in ("top", "bottom") and fh >= 3:
        for x in range(fw):
            put(img, glow, fx + x, fy + fh - 1, (190, 110, 250), True)


def special(img, glow, c, rects):
    tag = c.get("tag")
    if tag == "skull":
        fx, fy, fw, fh = rects["front"]
        for (ex, ey) in ((1, 3), (fw - 3, 3)):
            for dx in range(-1, 3):
                for dy in range(-1, 2):
                    put(img, glow, fx + ex + dx, fy + ey + dy, (8, 4, 18))
        for (ex, ey) in ((1, 3), (fw - 3, 3)):
            for dx in range(2):
                put(img, glow, fx + ex + dx, fy + ey, GLOW_W, True)
                put(img, glow, fx + ex + dx, fy + ey + 1, (190, 110, 250), True)
        for y in range(5, fh):                                  # nose slit
            put(img, glow, fx + fw // 2 - 1, fy + y, (10, 5, 20))
            put(img, glow, fx + fw // 2, fy + y, (10, 5, 20))
    if tag == "chestplate":
        fx, fy, fw, fh = rects["front"]
        cx, cy = fw // 2, fh // 2
        for y in range(fh):
            for x in range(fw):
                dst = math.hypot(x - cx + 0.5, y - cy)
                if dst < 1.6:
                    put(img, glow, fx + x, fy + y, GLOW_W, True)
                elif dst < 2.8:
                    put(img, glow, fx + x, fy + y, GLOW_V, True)
                elif dst < 3.4:
                    put(img, glow, fx + x, fy + y, (50, 20, 110), True)
        for (dx, dy) in ((0, -4), (0, 4), (-5, 0), (5, 0), (-3, -3), (3, 3), (-3, 3), (3, -3)):  # rays
            put(img, glow, fx + cx + dx, fy + cy + dy, GLOW_V, True)


def paint(bones):
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    glow = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    rnd = random.Random(2024)
    for (_, _, _, cubes) in bones:
        for c in cubes:
            w, h, d = c["s"]
            u, v = c["uv"]
            rects = {
                "top": (u + d, v, w, d),
                "bottom": (u + d + w, v, w, d),
                "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h),
                "left": (u + d + w, v + d, d, h),
                "back": (u + 2 * d + w, v + d, w, h),
            }
            for face, rect in rects.items():
                paint_face(img, glow, rect, face, c, rnd)
            special(img, glow, c, rects)
    return img, glow


# --------------------------------------------------------------------------- animations
def kf(d):
    return {("%.4f" % k).rstrip("0").rstrip("."): v for k, v in d.items()}


def animations():
    P = "animation.rift_reaver."
    A = {}
    A[P + "ambient"] = {"loop": True, "animation_length": 8.0, "bones": {
        "shards": {"rotation": kf({0: [0, 0, 0], 2: [0, 90, 0], 4: [0, 180, 0], 6: [0, 270, 0], 8: [0, 360, 0]}),
                   "position": kf({0: [0, 0, 0], 2: [0, 2.5, 0], 4: [0, 0, 0], 6: [0, -2.5, 0], 8: [0, 0, 0]})},
        "cloak": {"rotation": kf({0: [2, 0, 0], 2: [9, 0, 2], 4: [2, 0, 0], 6: [-4, 0, -2], 8: [2, 0, 0]})}}}
    A[P + "idle"] = {"loop": True, "animation_length": 4.0, "bones": {
        "body": {"position": kf({0: [0, 0, 0], 2: [0, -0.7, 0], 4: [0, 0, 0]}),
                 "rotation": kf({0: [6, 0, 0], 2: [9, 0, 0], 4: [6, 0, 0]})},
        "head": {"rotation": kf({0: [-4, 0, 0], 1: [-4, 8, 0], 3: [-4, -8, 0], 4: [-4, 0, 0]})},
        "jaw": {"rotation": kf({0: [2, 0, 0], 2: [9, 0, 0], 4: [2, 0, 0]})},
        "arm_r": {"rotation": kf({0: [-4, 0, 0], 2: [3, 0, 0], 4: [-4, 0, 0]})},
        "fore_r": {"rotation": kf({0: [-12, 0, 0], 2: [-20, 0, 0], 4: [-12, 0, 0]})},
        "arm_l": {"rotation": kf({0: [3, 0, 0], 2: [-4, 0, 0], 4: [3, 0, 0]})},
        "fore_l": {"rotation": kf({0: [-20, 0, 0], 2: [-12, 0, 0], 4: [-20, 0, 0]})},
        "leg_r": {"rotation": kf({0: [-3, 0, 0], 4: [-3, 0, 0]})},
        "leg_l": {"rotation": kf({0: [3, 0, 0], 4: [3, 0, 0]})}}}
    A[P + "walk"] = {"loop": True, "animation_length": 1.0, "bones": {
        "leg_r": {"rotation": kf({0: [-28, 0, 0], 0.5: [28, 0, 0], 1.0: [-28, 0, 0]})},
        "shin_r": {"rotation": kf({0: [10, 0, 0], 0.25: [5, 0, 0], 0.5: [8, 0, 0], 0.75: [48, 0, 0], 1.0: [10, 0, 0]})},
        "leg_l": {"rotation": kf({0: [28, 0, 0], 0.5: [-28, 0, 0], 1.0: [28, 0, 0]})},
        "shin_l": {"rotation": kf({0: [8, 0, 0], 0.25: [48, 0, 0], 0.5: [10, 0, 0], 0.75: [5, 0, 0], 1.0: [8, 0, 0]})},
        "arm_r": {"rotation": kf({0: [24, 0, 0], 0.5: [-24, 0, 0], 1.0: [24, 0, 0]})},
        "fore_r": {"rotation": kf({0: [-14, 0, 0], 0.5: [-34, 0, 0], 1.0: [-14, 0, 0]})},
        "arm_l": {"rotation": kf({0: [-24, 0, 0], 0.5: [24, 0, 0], 1.0: [-24, 0, 0]})},
        "fore_l": {"rotation": kf({0: [-34, 0, 0], 0.5: [-14, 0, 0], 1.0: [-34, 0, 0]})},
        "body": {"position": kf({0: [0, 0, 0], 0.25: [0, -1.4, 0], 0.5: [0, 0, 0], 0.75: [0, -1.4, 0], 1.0: [0, 0, 0]}),
                 "rotation": kf({0: [11, 4, 0], 0.5: [11, -4, 0], 1.0: [11, 4, 0]})},
        "head": {"rotation": kf({0: [-9, -4, 0], 0.5: [-9, 4, 0], 1.0: [-9, -4, 0]})}}}
    A[P + "attack"] = {"loop": "play_once", "animation_length": 0.7, "bones": {
        "arm_r": {"rotation": kf({0: [-4, 0, 0], 0.2: [-145, 0, 0], 0.38: [-38, 0, 0], 0.7: [-4, 0, 0]})},
        "fore_r": {"rotation": kf({0: [-12, 0, 0], 0.2: [-55, 0, 0], 0.38: [-8, 0, 0], 0.7: [-12, 0, 0]})},
        "body": {"rotation": kf({0: [6, 0, 0], 0.2: [-4, 14, 0], 0.38: [24, -12, 0], 0.7: [6, 0, 0]})},
        "head": {"rotation": kf({0: [-4, 0, 0], 0.2: [-18, 0, 0], 0.38: [10, 0, 0], 0.7: [-4, 0, 0]})},
        "jaw": {"rotation": kf({0: [2, 0, 0], 0.2: [10, 0, 0], 0.38: [35, 0, 0], 0.7: [2, 0, 0]})}}}
    A[P + "slam"] = {"loop": "play_once", "animation_length": 1.2, "bones": {
        "arm_r": {"rotation": kf({0: [-4, 0, 0], 0.55: [-170, 0, 0], 0.7: [-170, 0, 0], 0.82: [-30, 0, 0], 1.2: [-4, 0, 0]})},
        "arm_l": {"rotation": kf({0: [3, 0, 0], 0.55: [-170, 0, 0], 0.7: [-170, 0, 0], 0.82: [-30, 0, 0], 1.2: [3, 0, 0]})},
        "fore_r": {"rotation": kf({0: [-12, 0, 0], 0.55: [-40, 0, 0], 0.82: [-6, 0, 0], 1.2: [-12, 0, 0]})},
        "fore_l": {"rotation": kf({0: [-20, 0, 0], 0.55: [-40, 0, 0], 0.82: [-6, 0, 0], 1.2: [-20, 0, 0]})},
        "body": {"position": kf({0: [0, 0, 0], 0.55: [0, 2, 0], 0.82: [0, -4, 0], 1.2: [0, 0, 0]}),
                 "rotation": kf({0: [6, 0, 0], 0.55: [-14, 0, 0], 0.82: [30, 0, 0], 1.2: [6, 0, 0]})},
        "head": {"rotation": kf({0: [-4, 0, 0], 0.55: [-24, 0, 0], 0.82: [12, 0, 0], 1.2: [-4, 0, 0]})},
        "jaw": {"rotation": kf({0: [2, 0, 0], 0.5: [42, 0, 0], 0.85: [38, 0, 0], 1.2: [2, 0, 0]})},
        "leg_r": {"rotation": kf({0: [-3, 0, 0], 0.82: [-14, 0, 0], 1.2: [-3, 0, 0]})},
        "leg_l": {"rotation": kf({0: [3, 0, 0], 0.82: [14, 0, 0], 1.2: [3, 0, 0]})}}}
    # tearing out of the sky rift: it hangs, arms spread, roaring, slowly lowered (8.5 s = 170 ticks)
    A[P + "emerge"] = {"loop": "hold_on_last_frame", "animation_length": 8.5, "bones": {
        "arm_r": {"rotation": kf({0: [-30, 0, 0], 3: [-45, 0, 0], 6: [-20, 0, 0], 8.5: [-4, 0, 0]})},
        "fore_r": {"rotation": kf({0: [-50, 0, 0], 3: [-30, 0, 0], 6: [-40, 0, 0], 8.5: [-12, 0, 0]})},
        "arm_l": {"rotation": kf({0: [-30, 0, 0], 3: [-40, 0, 0], 6: [-24, 0, 0], 8.5: [3, 0, 0]})},
        "fore_l": {"rotation": kf({0: [-50, 0, 0], 3: [-36, 0, 0], 6: [-36, 0, 0], 8.5: [-20, 0, 0]})},
        "head": {"rotation": kf({0: [-22, 0, 0], 4: [-26, 6, 0], 6: [-10, -6, 0], 8.5: [-4, 0, 0]})},
        "jaw": {"rotation": kf({0: [34, 0, 0], 2.5: [40, 0, 0], 5: [14, 0, 0], 8.5: [2, 0, 0]})},
        "body": {"rotation": kf({0: [14, 0, 0], 5: [10, 0, 0], 8.5: [6, 0, 0]})},
        "leg_r": {"rotation": kf({0: [-8, 0, 0], 4: [-4, 0, 0], 8: [-3, 0, 0], 8.5: [-3, 0, 0]})},
        "shin_r": {"rotation": kf({0: [24, 0, 0], 4: [16, 0, 0], 8: [0, 0, 0], 8.5: [0, 0, 0]})},
        "leg_l": {"rotation": kf({0: [6, 0, 0], 4: [4, 0, 0], 8: [3, 0, 0], 8.5: [3, 0, 0]})},
        "shin_l": {"rotation": kf({0: [18, 0, 0], 4: [12, 0, 0], 8: [0, 0, 0], 8.5: [0, 0, 0]})}}}
    return A


# --------------------------------------------------------------------------- output
def build(RES, MOD, write_json):
    bones = define()
    pack(bones)
    out = [{"name": "root", "pivot": [0, 0, 0]}]
    for (name, parent, pivot, cubes) in bones:
        out.append({"name": name, "parent": parent, "pivot": list(pivot),
                    "cubes": [{"origin": list(c["o"]), "size": list(c["s"]), "uv": list(c["uv"])} for c in cubes]})
    write_json("assets/%s/geo/entity/rift_reaver.geo.json" % MOD, {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.rift_reaver", "texture_width": ATLAS, "texture_height": ATLAS,
                            "visible_bounds_width": 8, "visible_bounds_height": 8, "visible_bounds_offset": [0, 2, 0]},
            "bones": out}],
    })
    write_json("assets/%s/animations/entity/rift_reaver.animation.json" % MOD,
               {"format_version": "1.8.0", "animations": animations()})
    img, glow = paint(bones)
    d = os.path.join(RES, "assets", MOD, "textures", "entity")
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, "rift_reaver.png"))
    glow.save(os.path.join(d, "rift_reaver_glowmask.png"))
    write_json("data/%s/loot_table/entities/rift_reaver.json" % MOD, {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:reaver_heart" % MOD}]},
            {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 8},
             "entries": [{"type": "minecraft:item", "name": "minecraft:echo_shard"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 3},
             "entries": [{"type": "minecraft:item", "name": "minecraft:ender_pearl"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4},
             "entries": [{"type": "minecraft:item", "name": "minecraft:netherite_scrap"}]},
        ],
    })
    write_json("assets/%s/models/item/rift_reaver_spawn_egg.json" % MOD, {"parent": "minecraft:item/template_spawn_egg"})
    return bones


if __name__ == "__main__":
    print("run via generate_assets.py")
