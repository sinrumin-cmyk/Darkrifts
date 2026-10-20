#!/usr/bin/env python3
"""
Assets of the endgame: Heart Pedestal + Egg Altar (block models/textures), the Mirror Sovereign skin (a crimson recolour
of the Reaver skin, same UVs), its loot table and all the text. Called from generate_assets.py (after everything else):
    sanctum_assets.build(RES, MOD, write_json)
"""
import colorsys
import json
import os
import random

from PIL import Image

OUT = (9, 4, 18)
DARK = (22, 11, 38)
BASE = (40, 24, 70)
HI = (104, 76, 180)
GLOW = (230, 110, 255)
GOLD = (214, 168, 70)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def block_texture(path, seed, accent):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd.random()
            img.putpixel((x, y), mix(DARK, BASE, n * 0.9) + (255,))
    for i in range(16):                                   # bevel
        for (x, y) in ((i, 0), (0, i)):
            img.putpixel((x, y), mix(HI, BASE, 0.3) + (255,))
        for (x, y) in ((i, 15), (15, i)):
            img.putpixel((x, y), OUT + (255,))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):   # rivets
        img.putpixel((x, y), HI + (255,))
    y = 8                                                 # glowing crack
    for x in range(1, 15):
        y = max(3, min(12, y + rnd.choice([-1, 0, 0, 1])))
        img.putpixel((x, y), accent + (255,))
        if rnd.random() < 0.4:
            img.putpixel((x, y + 1), mix(accent, BASE, 0.6) + (255,))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def recolor(src, dst, hue_shift, sat, val):
    img = Image.open(src).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            h = (h + hue_shift) % 1.0
            s = min(1.0, s * sat)
            v = min(1.0, v * val)
            nr, ng, nb = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    img.save(dst)


def build(RES, MOD, write_json):
    tex = os.path.join(RES, "assets", MOD, "textures")
    block_texture(os.path.join(tex, "block", "sanctum_pedestal.png"), 7, GLOW)
    block_texture(os.path.join(tex, "block", "sanctum_altar.png"), 11, GOLD)

    # ---- block models / states / items
    def model(name, elements, tex_name):
        write_json("assets/%s/models/block/%s.json" % (MOD, name), {
            "textures": {"all": "%s:block/%s" % (MOD, tex_name), "particle": "%s:block/%s" % (MOD, tex_name)},
            "elements": [{"from": f, "to": t, "faces": {d: {"texture": "#all"} for d in
                                                           ("down", "up", "north", "south", "east", "west")}}
                         for (f, t) in elements]})
        write_json("assets/%s/blockstates/%s.json" % (MOD, name),
                   {"variants": {"": {"model": "%s:block/%s" % (MOD, name)}}})
        write_json("assets/%s/models/item/%s.json" % (MOD, name), {"parent": "%s:block/%s" % (MOD, name)})

    model("sanctum_tower", [([2, 0, 2], [14, 3, 14]), ([5, 3, 5], [11, 10, 11]), ([3, 10, 3], [13, 13, 13]),
                            ([2, 13, 2], [4, 14, 14]), ([12, 13, 2], [14, 14, 14]),
                            ([4, 13, 2], [12, 14, 4]), ([4, 13, 12], [12, 14, 14])], "sanctum_pedestal")
    model("sanctum_core", [([0, 0, 0], [16, 4, 16]), ([2, 4, 2], [14, 8, 14]), ([4, 8, 4], [12, 11, 12]),
                           ([1, 4, 1], [3, 12, 3]), ([13, 4, 1], [15, 12, 3]),
                           ([1, 4, 13], [3, 12, 15]), ([13, 4, 13], [15, 12, 15])], "sanctum_altar")

    write_json("data/%s/loot_table/entities/mirror_sovereign.json" % MOD, {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:dragon_egg"}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:elytra"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 3},
             "entries": [{"type": "minecraft:item", "name": "minecraft:nether_star"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 6, "max": 10},
             "entries": [{"type": "minecraft:item", "name": "minecraft:netherite_ingot"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5},
             "entries": [{"type": "minecraft:item", "name": "minecraft:enchanted_golden_apple"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 24, "max": 40},
             "entries": [{"type": "minecraft:item", "name": "minecraft:echo_shard"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 6, "max": 12},
             "entries": [{"type": "minecraft:item", "name": "%s:mirrorite_block" % MOD}]},
        ],
    })

    # ---- text
    en = {
        "entity.%s.mirror_sovereign" % MOD: "Mirror Sovereign",
        "block.%s.sanctum_tower" % MOD: "Heart Pedestal",
        "block.%s.sanctum_core" % MOD: "Egg Altar",
        "tooltip.%s.sanctum_tower" % MOD: "Right click with a Reaver Heart. One for each tower of the Sanctum.",
        "tooltip.%s.sanctum_core" % MOD: "Right click with the Dragon Egg once all four towers hold a heart.",
        "message.%s.tower_empty" % MOD: "The pedestal waits for a Reaver Heart.",
        "message.%s.tower_full" % MOD: "The heart beats in the dark.",
        "message.%s.tower_filled" % MOD: "A heart beats in the tower. %s / 4",
        "message.%s.towers_ready" % MOD: "All four towers burn. The altar waits for the egg.",
        "message.%s.core_need_egg" % MOD: "The altar hungers for the Dragon Egg.",
        "message.%s.core_silent" % MOD: "The towers are silent. Hearts: %s / 4",
        "message.%s.sovereign_awake" % MOD: "The Sovereign is already awake.",
        "message.%s.sanctum_begin" % MOD: "THE MIRROR WAKES",
        "message.%s.sanctum_begin.sub" % MOD: "Something old is coming through.",
        "message.%s.sovereign_sub" % MOD: "Ruler of the Dark Mirror",
        "message.%s.sovereign_phase2" % MOD: "THE SOVEREIGN RISES",
        "message.%s.sovereign_phase3" % MOD: "THE MIRROR SHATTERS",
        "message.%s.sovereign_dead" % MOD: "THE SOVEREIGN FALLS",
    }
    ru = {
        "entity.%s.mirror_sovereign" % MOD: "Властитель зеркала",
        "block.%s.sanctum_tower" % MOD: "Постамент сердца",
        "block.%s.sanctum_core" % MOD: "Алтарь яйца",
        "tooltip.%s.sanctum_tower" % MOD: "ПКМ с Сердцем жнеца. По одному на каждую башню Святилища.",
        "tooltip.%s.sanctum_core" % MOD: "ПКМ с Яйцом дракона, когда во всех четырёх башнях лежит сердце.",
        "message.%s.tower_empty" % MOD: "Постамент ждёт Сердце жнеца.",
        "message.%s.tower_full" % MOD: "Сердце бьётся во тьме.",
        "message.%s.tower_filled" % MOD: "В башне бьётся сердце. %s / 4",
        "message.%s.towers_ready" % MOD: "Все четыре башни горят. Алтарь ждёт яйцо.",
        "message.%s.core_need_egg" % MOD: "Алтарь жаждет Яйцо дракона.",
        "message.%s.core_silent" % MOD: "Башни молчат. Сердец: %s / 4",
        "message.%s.sovereign_awake" % MOD: "Властитель уже проснулся.",
        "message.%s.sanctum_begin" % MOD: "ЗЕРКАЛО ПРОБУЖДАЕТСЯ",
        "message.%s.sanctum_begin.sub" % MOD: "Что-то древнее идёт сквозь разлом.",
        "message.%s.sovereign_sub" % MOD: "Правитель Тёмного зеркала",
        "message.%s.sovereign_phase2" % MOD: "ВЛАСТИТЕЛЬ ВОССТАЁТ",
        "message.%s.sovereign_phase3" % MOD: "ЗЕРКАЛО РАЗБИВАЕТСЯ",
        "message.%s.sovereign_dead" % MOD: "ВЛАСТИТЕЛЬ ПАЛ",
    }
    for lang, extra in (("en_us", en), ("ru_ru", ru)):
        path = os.path.join(RES, "assets", MOD, "lang", lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data.update(extra)
        write_json("assets/%s/lang/%s.json" % (MOD, lang), data)


if __name__ == "__main__":
    print("run via generate_assets.py")
