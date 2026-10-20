#!/usr/bin/env python3
"""
Mirror Sovereign: its own model (geo), animations, texture and glow mask. Called from generate_assets.py:
    sovereign_model.build(RES, MOD, write_json)

A levitating, legless lord: broad armoured torso, a tapering robe that ends in a bone spike, a horned skull with a
crown, huge pauldrons, clawed hands, two fans of crystal blades for wings, a ring of orbiting shards and a halo.
Re-uses the packing and painting code of the Reaver model with a crimson palette. Cubes are axis-aligned only.
Units: model pixels (16 = 1 block). About 5 blocks tall; the renderer scales it by 1.6.
"""
import colorsys
import os

import reaver_model as rm

CRIMSON = {
    "armor": ((58, 14, 28), (130, 42, 62), (20, 4, 10)),
    "plate": ((96, 22, 40), (196, 62, 86), (34, 8, 16)),
    "bone": ((214, 196, 190), (252, 240, 232), (122, 102, 102)),
    "bonedark": ((150, 126, 122), (208, 186, 180), (82, 62, 64)),
    "tooth": ((240, 230, 224), (255, 255, 255), (160, 140, 140)),
    "core": ((24, 6, 12), (86, 18, 32), (8, 2, 6)),
    "cloth": ((44, 10, 22), (100, 28, 48), (18, 4, 8)),
    "crystal": ((224, 62, 92), (255, 204, 214), (142, 24, 50)),
}


def define():
    C = rm.cube
    bones = []

    body = [
        C((-7, 30, -4), (14, 4, 8), "plate"),                       # waist ring
        C((-5, 34, -3), (10, 8, 6), "core", "abdomen"),            # glowing abdomen
        C((-12, 42, -5), (24, 14, 10), "armor", "chest"),          # colossal chest
        C((-8, 43, -6), (16, 11, 1), "plate", "chestplate"),       # front plate with the core
        C((-8, 43, 5), (16, 11, 1), "plate"),                       # back plate
        C((-7, 56, -3), (14, 2, 6), "plate"),                       # collar
        C((-1, 47, 6), (2, 10, 2), "bone"),                         # spine spikes
        C((-1, 40, 6), (2, 8, 2), "bone"),
        C((-1, 53, 6), (2, 8, 2), "bone"),
        C((-1, 34, 6), (2, 6, 2), "bone"),
    ]
    bones.append(("body", "root", (0, 40, 0), body))

    robe = [  # no legs: the body tapers into a hanging robe that ends in a spike
        C((-9, 22, -5), (18, 8, 10), "armor"),
        C((-7, 14, -4), (14, 8, 8), "armor"),
        C((-5, 7, -3), (10, 7, 6), "cloth"),
        C((-3, 0, -2), (6, 7, 4), "cloth"),
        C((-2, -8, -1), (4, 8, 2), "bone"),
        C((-1, -13, -0.5), (2, 5, 1), "bone"),
        C((-11, 12, -2), (3, 14, 4), "cloth"),                      # torn strips
        C((8, 12, -2), (3, 14, 4), "cloth"),
        C((-14, 6, -1), (3, 12, 2), "cloth"),
        C((11, 6, -1), (3, 12, 2), "cloth"),
        C((-5, 14, 4), (10, 14, 1), "cloth"),
    ]
    bones.append(("robe", "body", (0, 30, 0), robe))

    head = [
        C((-5, 58, -5), (10, 9, 10), "bone", "skull"),
        C((-6, 64, -6), (12, 2, 2), "bonedark"),                    # heavy brow
        C((-1, 67, -2), (2, 9, 2), "bone"),                          # crown
        C((-4, 67, -3), (2, 6, 2), "bone"),
        C((2, 67, -3), (2, 6, 2), "bone"),
        C((-7, 66, -3), (2, 4, 2), "bone"),
        C((5, 66, -3), (2, 4, 2), "bone"),
    ]
    for sgn in (1, -1):  # horns, climbing out and up
        for (x, y, w, h) in ((-9, 61, 4, 5), (-12, 65, 4, 5), (-15, 69, 3, 6), (-17, 74, 2, 6), (-17, 80, 1, 4)):
            ox = x if sgn == 1 else -(x + w)
            head.append(C((ox, y, -3 if w >= 3 else -2), (w, h, w if w >= 3 else 2), "bone"))
    bones.append(("head", "body", (0, 58, -1), head))
    jaw = [
        C((-4, 54, -8), (8, 4, 9), "bone"),
        C((-3, 58, -8), (1, 2, 1), "tooth"),
        C((2, 58, -8), (1, 2, 1), "tooth"),
        C((-1, 58, -8), (2, 1, 1), "tooth"),
    ]
    bones.append(("jaw", "head", (0, 58, -4), jaw))

    arm_r = [
        C((-26, 56, -6), (13, 5, 12), "plate"),                      # pauldron
        C((-25, 61, -3), (2, 8, 2), "bone"),
        C((-21, 61, -5), (2, 6, 2), "bone"),
        C((-21, 61, 2), (2, 6, 2), "bone"),
        C((-29, 57, -1), (3, 3, 2), "bone"),
        C((-23, 42, -4), (9, 14, 8), "armor"),                       # upper arm
        C((-25, 44, -1), (2, 4, 2), "bone"),                         # elbow spike
    ]
    fore_r = [
        C((-23, 26, -4), (9, 16, 8), "armor"),
        C((-24, 30, -5), (11, 6, 10), "plate"),                      # bracer
        C((-23, 19, -4), (9, 8, 8), "armor", "hand"),
        C((-23, 8, -4), (2, 11, 2), "bone", "claw"),
        C((-19, 8, -4), (2, 11, 2), "bone", "claw"),
        C((-21, 8, 1), (2, 11, 2), "bone", "claw"),
    ]
    bones.append(("arm_r", "body", (-18, 56, 0), arm_r))
    bones.append(("fore_r", "arm_r", (-19, 42, 0), fore_r))
    bones.append(("arm_l", "body", rm.mirror_pivot((-18, 56, 0)), [rm.mirror(c) for c in arm_r]))
    bones.append(("fore_l", "arm_l", rm.mirror_pivot((-19, 42, 0)), [rm.mirror(c) for c in fore_r]))

    # wings: fans of crystal blades behind the shoulders
    wing_r = []
    for i, (x, y, h) in enumerate(((-14, 54, 22), (-19, 53, 26), (-24, 51, 26), (-29, 49, 22), (-33, 47, 17))):
        wing_r.append(C((x, y, 8 + i), (2, h, 1), "crystal"))
        wing_r.append(C((x, y + h, 8 + i), (2, 4, 1), "bone"))
    bones.append(("wing_r", "body", (-9, 54, 7), wing_r))
    bones.append(("wing_l", "body", rm.mirror_pivot((-9, 54, 7)), [rm.mirror(c) for c in wing_r]))

    shards = []
    import math
    for k in range(8):
        a = k * math.pi / 4.0
        x = int(round(math.cos(a) * 34))
        z = int(round(math.sin(a) * 34))
        shards.append(C((x - 1, 44 + (4 if k % 2 else -4), z - 1), (3, 9, 3), "crystal"))
    bones.append(("shards", "root", (0, 46, 0), shards))
    halo = []
    for k in range(6):
        a = k * math.pi / 3.0
        x = int(round(math.cos(a) * 13))
        z = int(round(math.sin(a) * 13))
        halo.append(C((x - 1, 88, z - 1), (2, 6, 2), "crystal"))
    bones.append(("halo", "root", (0, 90, 0), halo))
    return bones


def kf(d):
    return rm.kf(d)


def animations():
    P = "animation.mirror_sovereign."
    A = {}
    A[P + "ambient"] = {"loop": True, "animation_length": 8.0, "bones": {
        "shards": {"rotation": kf({0: [0, 0, 0], 2: [0, 90, 0], 4: [0, 180, 0], 6: [0, 270, 0], 8: [0, 360, 0]}),
                   "position": kf({0: [0, 0, 0], 2: [0, 3, 0], 4: [0, 0, 0], 6: [0, -3, 0], 8: [0, 0, 0]})},
        "halo": {"rotation": kf({0: [0, 360, 0], 4: [0, 180, 0], 8: [0, 0, 0]})},
        "robe": {"rotation": kf({0: [3, 0, 2], 2: [-4, 0, -2], 4: [3, 0, 2], 6: [-4, 0, 3], 8: [3, 0, 2]})},
        "wing_r": {"rotation": kf({0: [0, 0, -4], 2: [0, 0, 10], 4: [0, 0, -4], 6: [0, 0, 10], 8: [0, 0, -4]})},
        "wing_l": {"rotation": kf({0: [0, 0, 4], 2: [0, 0, -10], 4: [0, 0, 4], 6: [0, 0, -10], 8: [0, 0, 4]})}}}
    A[P + "idle"] = {"loop": True, "animation_length": 4.0, "bones": {
        "body": {"position": kf({0: [0, 0, 0], 2: [0, 3.5, 0], 4: [0, 0, 0]}),
                 "rotation": kf({0: [4, 0, 0], 2: [1, 0, 0], 4: [4, 0, 0]})},
        "head": {"rotation": kf({0: [-3, 0, 0], 1: [-3, 9, 0], 3: [-3, -9, 0], 4: [-3, 0, 0]})},
        "jaw": {"rotation": kf({0: [2, 0, 0], 2: [8, 0, 0], 4: [2, 0, 0]})},
        "arm_r": {"rotation": kf({0: [-6, 0, 6], 2: [2, 0, 10], 4: [-6, 0, 6]})},
        "fore_r": {"rotation": kf({0: [-16, 0, 0], 2: [-26, 0, 0], 4: [-16, 0, 0]})},
        "arm_l": {"rotation": kf({0: [2, 0, -10], 2: [-6, 0, -6], 4: [2, 0, -10]})},
        "fore_l": {"rotation": kf({0: [-26, 0, 0], 2: [-16, 0, 0], 4: [-26, 0, 0]})}}}
    A[P + "attack"] = {"loop": "play_once", "animation_length": 0.8, "bones": {
        "arm_r": {"rotation": kf({0: [-6, 0, 6], 0.25: [-150, 0, 6], 0.42: [-30, 0, 6], 0.8: [-6, 0, 6]})},
        "fore_r": {"rotation": kf({0: [-16, 0, 0], 0.25: [-60, 0, 0], 0.42: [-6, 0, 0], 0.8: [-16, 0, 0]})},
        "arm_l": {"rotation": kf({0: [2, 0, -10], 0.25: [-120, 0, -20], 0.42: [-20, 0, -10], 0.8: [2, 0, -10]})},
        "body": {"rotation": kf({0: [4, 0, 0], 0.25: [-8, 12, 0], 0.42: [22, -10, 0], 0.8: [4, 0, 0]})},
        "head": {"rotation": kf({0: [-3, 0, 0], 0.25: [-20, 0, 0], 0.42: [12, 0, 0], 0.8: [-3, 0, 0]})},
        "jaw": {"rotation": kf({0: [2, 0, 0], 0.25: [12, 0, 0], 0.42: [38, 0, 0], 0.8: [2, 0, 0]})},
        "wing_r": {"rotation": kf({0: [0, 0, -4], 0.25: [0, 0, 24], 0.8: [0, 0, -4]})},
        "wing_l": {"rotation": kf({0: [0, 0, 4], 0.25: [0, 0, -24], 0.8: [0, 0, 4]})}}}
    A[P + "slam"] = {"loop": "play_once", "animation_length": 1.4, "bones": {
        "arm_r": {"rotation": kf({0: [-6, 0, 6], 0.6: [-175, 0, 12], 0.8: [-175, 0, 12], 0.95: [-25, 0, 6], 1.4: [-6, 0, 6]})},
        "arm_l": {"rotation": kf({0: [2, 0, -10], 0.6: [-175, 0, -12], 0.8: [-175, 0, -12], 0.95: [-25, 0, -6], 1.4: [2, 0, -10]})},
        "fore_r": {"rotation": kf({0: [-16, 0, 0], 0.6: [-40, 0, 0], 0.95: [-4, 0, 0], 1.4: [-16, 0, 0]})},
        "fore_l": {"rotation": kf({0: [-26, 0, 0], 0.6: [-40, 0, 0], 0.95: [-4, 0, 0], 1.4: [-26, 0, 0]})},
        "body": {"position": kf({0: [0, 0, 0], 0.6: [0, 6, 0], 0.95: [0, -6, 0], 1.4: [0, 0, 0]}),
                 "rotation": kf({0: [4, 0, 0], 0.6: [-16, 0, 0], 0.95: [34, 0, 0], 1.4: [4, 0, 0]})},
        "head": {"rotation": kf({0: [-3, 0, 0], 0.6: [-26, 0, 0], 0.95: [14, 0, 0], 1.4: [-3, 0, 0]})},
        "jaw": {"rotation": kf({0: [2, 0, 0], 0.55: [44, 0, 0], 1.0: [40, 0, 0], 1.4: [2, 0, 0]})},
        "robe": {"rotation": kf({0: [3, 0, 0], 0.95: [-22, 0, 0], 1.4: [3, 0, 0]})},
        "wing_r": {"rotation": kf({0: [0, 0, -4], 0.6: [0, 0, 30], 0.95: [0, 0, -14], 1.4: [0, 0, -4]})},
        "wing_l": {"rotation": kf({0: [0, 0, 4], 0.6: [0, 0, -30], 0.95: [0, 0, 14], 1.4: [0, 0, 4]})}}}
    A[P + "emerge"] = {"loop": "hold_on_last_frame", "animation_length": 8.5, "bones": {
        "arm_r": {"rotation": kf({0: [-30, 0, 40], 3: [-45, 0, 50], 6: [-20, 0, 24], 8.5: [-6, 0, 6]})},
        "arm_l": {"rotation": kf({0: [-30, 0, -40], 3: [-40, 0, -50], 6: [-24, 0, -24], 8.5: [2, 0, -10]})},
        "fore_r": {"rotation": kf({0: [-50, 0, 0], 3: [-30, 0, 0], 8.5: [-16, 0, 0]})},
        "fore_l": {"rotation": kf({0: [-50, 0, 0], 3: [-36, 0, 0], 8.5: [-26, 0, 0]})},
        "head": {"rotation": kf({0: [-24, 0, 0], 4: [-28, 6, 0], 6: [-10, -6, 0], 8.5: [-3, 0, 0]})},
        "jaw": {"rotation": kf({0: [36, 0, 0], 2.5: [42, 0, 0], 5: [14, 0, 0], 8.5: [2, 0, 0]})},
        "body": {"rotation": kf({0: [14, 0, 0], 5: [10, 0, 0], 8.5: [4, 0, 0]})},
        "robe": {"rotation": kf({0: [-14, 0, 0], 4: [-8, 0, 0], 8.5: [3, 0, 0]})},
        "wing_r": {"rotation": kf({0: [0, 0, 34], 4: [0, 0, 26], 8.5: [0, 0, -4]})},
        "wing_l": {"rotation": kf({0: [0, 0, -34], 4: [0, 0, -26], 8.5: [0, 0, 4]})}}}
    return A


def recolor_hot(img):
    """Anything violet / teal that the shared painter left in becomes red / gold."""
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s > 0.25 and 0.45 <= h <= 0.56:
                h = 0.11                       # teal -> gold
            elif s > 0.2 and 0.56 < h <= 0.9:
                h = 0.985 + (h - 0.7) * 0.05   # violet -> red
            nr, ng, nb = colorsys.hsv_to_rgb(h % 1.0, s, v)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)


def build(RES, MOD, write_json):
    saved = (dict(rm.PAL), rm.GLOW_V, rm.GLOW_W)
    rm.PAL.update(CRIMSON)
    rm.GLOW_V = (255, 70, 90)
    rm.GLOW_W = (255, 232, 220)
    try:
        bones = define()
        rm.pack(bones)
        img, glow = rm.paint(bones)
    finally:
        rm.PAL.clear()
        rm.PAL.update(saved[0])
        rm.GLOW_V, rm.GLOW_W = saved[1], saved[2]
    recolor_hot(img)
    recolor_hot(glow)
    out = [{"name": "root", "pivot": [0, 0, 0]}]
    for (name, parent, pivot, cubes) in bones:
        out.append({"name": name, "parent": parent, "pivot": list(pivot),
                    "cubes": [{"origin": list(c["o"]), "size": list(c["s"]), "uv": list(c["uv"])} for c in cubes]})
    write_json("assets/%s/geo/entity/mirror_sovereign.geo.json" % MOD, {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.mirror_sovereign", "texture_width": rm.ATLAS,
                            "texture_height": rm.ATLAS, "visible_bounds_width": 10, "visible_bounds_height": 10,
                            "visible_bounds_offset": [0, 3, 0]},
            "bones": out}],
    })
    write_json("assets/%s/animations/entity/mirror_sovereign.animation.json" % MOD,
               {"format_version": "1.8.0", "animations": animations()})
    d = os.path.join(RES, "assets", MOD, "textures", "entity")
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, "mirror_sovereign.png"))
    glow.save(os.path.join(d, "mirror_sovereign_glowmask.png"))


if __name__ == "__main__":
    print("run via generate_assets.py")
