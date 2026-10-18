#!/usr/bin/env python3
"""
Generates every JSON / PNG resource of the Dark Rifts mod into src/main/resources.

    python3 tools/generate_assets.py

Needs Pillow (pip install pillow). The single mirror dimension is named in DarkDimensions.MIRROR.
"""
import json
import math
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
MOD = "darkrifts"
ABILITY_EN = {'ability.darkrifts.rift': 'Rift', 'ability.darkrifts.rift.desc': 'Hold Right Click and drag your view to tear a rift', 'ability.darkrifts.spikes': 'Rift Spikes', 'ability.darkrifts.spikes.desc': 'Rifts open around you and spikes burst out', 'ability.darkrifts.step': 'Rift Step', 'ability.darkrifts.step.desc': 'Dash forward through a tear', 'ability.darkrifts.pull': 'Collapse', 'ability.darkrifts.pull.desc': 'Pulls enemies into a point in front of you', 'ability.darkrifts.cloak': 'Void Cloak', 'ability.darkrifts.cloak.desc': 'Invisible, shielded, nearby mobs go blind', 'message.darkrifts.ability_selected': 'Ability: %s', 'tooltip.darkrifts.rift_blade.3': 'Press R to choose an ability.', 'key.darkrifts.abilities': 'Rift Blade abilities', 'key.categories.darkrifts': 'Dark Rifts'}
ABILITY_RU = {'ability.darkrifts.rift': 'Разлом', 'ability.darkrifts.rift.desc': 'Зажми ПКМ и веди взглядом, чтобы разорвать пространство', 'ability.darkrifts.spikes': 'Шипы разлома', 'ability.darkrifts.spikes.desc': 'Вокруг открываются разломы, из них бьют шипы', 'ability.darkrifts.step': 'Шаг сквозь разлом', 'ability.darkrifts.step.desc': 'Мгновенный рывок вперёд', 'ability.darkrifts.pull': 'Коллапс', 'ability.darkrifts.pull.desc': 'Втягивает врагов в точку перед тобой', 'ability.darkrifts.cloak': 'Покров тьмы', 'ability.darkrifts.cloak.desc': 'Невидимость, щит, ближайшие мобы слепнут', 'message.darkrifts.ability_selected': 'Способность: %s', 'tooltip.darkrifts.rift_blade.3': 'Нажми R, чтобы выбрать способность.', 'key.darkrifts.abilities': 'Способности клинка разломов', 'key.categories.darkrifts': 'Тёмные разломы'}


def write_json(rel_path, data):
    path = os.path.join(RES, rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(r, g, b):
    return (r << 16) | (g << 8) | b


# --------------------------------------------------------------------------- textures
def make_rift_texture():
    frames = 8
    img = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        t = f / frames * 2 * math.pi
        for y in range(16):
            for x in range(16):
                v = (math.sin(x * 0.9 + y * 0.6 + t)
                     + math.sin(y * 1.3 - x * 0.4 - 2 * t)
                     + math.sin(math.hypot(x - 8, y - 8) * 0.8 - t)) / 3.0
                v = (v + 1) / 2
                glow = max(0.0, v - 0.6) / 0.4
                r = int(8 + glow * 150)
                g = int(3 + glow * 40)
                b = int(18 + glow * 210)
                edge = min(x, y, 15 - x, 15 - y)
                a = 235 if edge >= 2 else 235 - (2 - edge) * 55
                img.putpixel((x, y + 16 * f), (r, g, b, a))
    path = os.path.join(RES, "assets", MOD, "textures", "block", "rift.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    write_json("assets/%s/textures/block/rift.png.mcmeta" % MOD,
               {"animation": {"frametime": 3, "interpolate": True}})


def make_sword_texture():
    """16x16 obsidian blade with a glowing violet rift running through it."""
    px = {}
    D = (22, 14, 38, 255)       # blade shadow side
    M = (44, 27, 82, 255)       # blade body
    H = (112, 76, 196, 255)     # blade light edge
    C = (205, 120, 255, 255)    # glowing crack
    W = (248, 228, 255, 255)    # white-hot crack core
    G = (38, 30, 58, 255)       # guard dark
    g = (92, 76, 128, 255)      # guard light
    P = (232, 70, 252, 255)     # gem
    p = (150, 40, 190, 255)     # gem shade
    B = (52, 32, 24, 255)       # grip
    b = (92, 60, 40, 255)       # grip wrap

    # blade: centre line x+y=15 from the guard (x=6) to the tip (x=15)
    for x in range(6, 16):
        y = 15 - x
        k = x - 6
        crack = [C, M, W, M, C, M, C, W, M, C][k]
        px[(x, y)] = crack
        if x <= 13:
            px[(x - 1, y)] = D
            px[(x, y + 1)] = D if k % 3 else M
            px[(x + 1, y)] = H if x <= 13 else H
        elif x == 14:
            px[(x - 1, y)] = D
            px[(x, y + 1)] = D
        # x == 15: the very tip, a single pixel
    for x in (12, 13):                      # light edge highlight on the upper-right side
        px[(x, 15 - x - 1)] = H
    px[(14, 0)] = H

    # cross guard along (1,1), centred on (5,10), swept at the ends
    for t in range(-3, 4):
        x, y = 5 + t, 10 + t
        px[(x, y)] = G if abs(t) != 3 else g
        px[(x + 1, y)] = g if t < 0 else G
    px[(5, 10)] = P
    px[(6, 11)] = p
    px[(4, 9)] = g

    # grip + pommel
    px[(4, 11)] = B
    px[(3, 12)] = b
    px[(3, 11)] = B
    px[(2, 13)] = B
    px[(2, 12)] = b
    px[(1, 14)] = P
    px[(0, 14)] = p
    px[(1, 15)] = p

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), c in px.items():
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), c)
    outline = (8, 4, 14, 255)
    for (x, y) in list(px.keys()):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in px:
                    img.putpixel((nx, ny), outline)
    path = os.path.join(RES, "assets", MOD, "textures", "item", "rift_blade.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def make_void_texture():
    """Animated starry 'end portal' matter (16x128, 8 frames, seamless)."""
    import math, random
    F = 8
    img = Image.new("RGBA", (16, 16 * F))
    rnd = random.Random(7)
    stars = [(rnd.randrange(16), rnd.randrange(16), rnd.random() * 6.28,
              rnd.choice([(120, 255, 255), (230, 150, 255), (255, 255, 255)])) for _ in range(14)]
    tau = 2 * math.pi / 16
    for f in range(F):
        ph = f * 2 * math.pi / F
        for y in range(16):
            for x in range(16):
                v = (math.sin(tau * x + ph) + math.sin(tau * (y * 2) - ph)
                     + math.sin(tau * (x + y) + 2 * ph) + math.sin(tau * (x * 2 - y) - ph)) / 4.0
                k = (v + 1) / 2
                img.putpixel((x, f * 16 + y), (int(6 + 14 * k), int(8 + 34 * k), int(20 + 52 * k), 255))
        for (sx, sy, sp, col) in stars:
            a = 0.25 + 0.75 * (0.5 + 0.5 * math.sin(ph + sp))
            img.putpixel((sx, f * 16 + sy), (int(col[0] * a), int(col[1] * a), int(col[2] * a), 255))
    path = os.path.join(RES, "assets", MOD, "textures", "block", "void_matter.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def make_bucket_texture():
    """Bucket of dark water (16x16)."""
    O = (8, 6, 16, 255)
    S = (120, 120, 134, 255)
    s = (70, 70, 84, 255)
    L = (200, 200, 214, 255)
    Wt = (14, 22, 48, 255)
    Wh = (40, 58, 110, 255)
    rows = {
        2: "....OOOOOOOO....",
        3: "...OLSSSSSSsO...",
        4: "...OSOOOOOOsO...",
        5: "..OOWhWtWhWtOO..",
        6: "..OSWtWtWtWtsO..",
        7: "...OSWtWtWtsO...",
        8: "...OSWtWtWtsO...",
        9: "...OSWtWtWtsO...",
        10: "...OSWtWtWtsO...",
        11: "...OSWtWtWtsO...",
        12: "....OSWtWtsO....",
        13: "....OOssssOO....",
        14: ".....OOOOOO.....",
    }
    cmap = {"O": O, "S": S, "s": s, "L": L, "W": Wt, "h": Wh, "t": Wt}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in rows.items():
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            img.putpixel((x, y), cmap[ch] if ch != "W" else Wt)
    for y, row in rows.items():          # lighter water speckles
        for x, ch in enumerate(row):
            if ch == "h":
                img.putpixel((x, y), Wh)
    path = os.path.join(RES, "assets", MOD, "textures", "item", "dark_water_bucket.png")
    img.save(path)


# --------------------------------------------------------------------------- client assets
def make_client_assets():
    write_json("assets/%s/blockstates/rift.json" % MOD,
               {"variants": {"": {"model": "%s:block/rift" % MOD}}})
    write_json("assets/%s/models/block/rift.json" % MOD, {
        "parent": "minecraft:block/cube_all",
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": {"all": "%s:block/rift" % MOD},
    })
    write_json("assets/%s/models/item/rift.json" % MOD, {"parent": "%s:block/rift" % MOD})
    write_json("assets/%s/models/item/rift_blade.json" % MOD, {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": "%s:item/rift_blade" % MOD},
    })

    en = {
        "block.%s.rift" % MOD: "Rift",
        "item.%s.rift_blade" % MOD: "Rift Blade",
        "itemGroup.%s" % MOD: "Dark Rifts",
        "tooltip.%s.rift_blade.1" % MOD: "Tears rifts in the sky into the dark mirror of the world.",
        "tooltip.%s.rift_blade.2" % MOD: "Hold Right Click, look up and drag your view to draw a rift. Sneak + Right Click on a rift closes it.",
        "message.%s.stroke_too_short" % MOD: "The rift fizzles out... drag further.",
        "message.%s.no_room" % MOD: "There is no room for a rift here.",
        "message.%s.cant_close" % MOD: "This return rift cannot be closed.",
        "message.%s.dimension_missing" % MOD: "The rift leads nowhere (%s is missing).",
        "block.%s.dark_water" % MOD: "Dark Water",
        "item.%s.dark_water_bucket" % MOD: "Dark Water Bucket",
    }
    ru = {
        "block.%s.rift" % MOD: "Разлом",
        "item.%s.rift_blade" % MOD: "Клинок разломов",
        "itemGroup.%s" % MOD: "Тёмные разломы",
        "tooltip.%s.rift_blade.1" % MOD: "Рвёт небо, открывая путь в тёмное зеркало мира.",
        "tooltip.%s.rift_blade.2" % MOD: "Зажми ПКМ, посмотри вверх и веди взглядом, чтобы нарисовать разлом. Shift + ПКМ по разлому закрывает его.",
        "message.%s.stroke_too_short" % MOD: "Разлом угас... веди дальше.",
        "message.%s.no_room" % MOD: "Здесь нет места для разлома.",
        "message.%s.cant_close" % MOD: "Этот обратный разлом закрыть нельзя.",
        "message.%s.dimension_missing" % MOD: "Разлом ведёт в никуда (%s отсутствует).",
        "block.%s.dark_water" % MOD: "Тёмная вода",
        "item.%s.dark_water_bucket" % MOD: "Ведро тёмной воды",
    }
    write_json("data/%s/dimension_type/void_prison.json" % MOD, {
        "ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "has_skylight": False,
        "has_ceiling": False, "ambient_light": 0.12, "fixed_time": 18000,
        "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        "piglin_safe": False, "bed_works": False, "respawn_anchor_works": False, "has_raids": False,
        "logical_height": 64, "min_y": 0, "height": 64,
        "infiniburn": "#minecraft:infiniburn_overworld", "effects": "%s:void_sky" % MOD,
    })
    write_json("data/%s/dimension/void_prison.json" % MOD, {
        "type": "%s:void_prison" % MOD,
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": "minecraft:the_void", "features": False, "lakes": False,
            "layers": [{"block": "%s:void_matter" % MOD, "height": 4}],
            "structure_overrides": []}},
    })
    write_json("assets/%s/blockstates/void_matter.json" % MOD,
               {"variants": {"": {"model": "%s:block/void_matter" % MOD}}})
    write_json("assets/%s/models/block/void_matter.json" % MOD,
               {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/void_matter" % MOD}})
    write_json("assets/%s/models/item/void_matter.json" % MOD, {"parent": "%s:block/void_matter" % MOD})
    write_json("assets/%s/textures/block/void_matter.png.mcmeta" % MOD,
               {"animation": {"frametime": 5, "interpolate": True}})
    en.update({'message.darkrifts.not_your_seal': 'Only the one who sealed this player can lift the seal', 'message.darkrifts.not_sealed': 'This player is not sealed'})
    ru.update({'message.darkrifts.not_your_seal': 'Печать может снять только тот, кто её наложил', 'message.darkrifts.not_sealed': 'Этот игрок не запечатан'})
    en.update(ABILITY_EN)
    ru.update(ABILITY_RU)
    en.update({'ability.darkrifts.seal': 'Void Seal', 'ability.darkrifts.seal.desc': 'Seals the target in the void. A sealed player respawns there forever', 'message.darkrifts.no_target': 'No target in sight', 'message.darkrifts.sealed': '%s is sealed in the void', 'message.darkrifts.you_are_sealed': 'The void seal holds you', 'message.darkrifts.seal_removed': 'The seal is gone', 'block.darkrifts.void_matter': 'Void Matter', 'dimension.darkrifts.void_prison': 'Void Prison'})
    ru.update({'ability.darkrifts.seal': 'Печать пустоты', 'ability.darkrifts.seal.desc': 'Запечатывает цель в пустоте. Игрок возрождается там вечно', 'message.darkrifts.no_target': 'Нет цели', 'message.darkrifts.sealed': '%s запечатан в пустоте', 'message.darkrifts.you_are_sealed': 'Печать пустоты держит тебя', 'message.darkrifts.seal_removed': 'Печать снята', 'block.darkrifts.void_matter': 'Материя пустоты', 'dimension.darkrifts.void_prison': 'Тюрьма пустоты'})
    write_json("assets/%s/blockstates/dark_water.json" % MOD,
               {"variants": {"": {"model": "%s:block/dark_water" % MOD}}})
    write_json("assets/%s/models/block/dark_water.json" % MOD,
               {"textures": {"particle": "minecraft:block/water_still"}})
    write_json("assets/%s/models/item/dark_water_bucket.json" % MOD, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/dark_water_bucket" % MOD},
    })
    write_json("data/minecraft/tags/fluid/water.json", {
        "replace": False,
        "values": ["%s:dark_water" % MOD, "%s:flowing_dark_water" % MOD],
    })
    write_json("assets/%s/lang/en_us.json" % MOD, en)
    write_json("assets/%s/lang/ru_ru.json" % MOD, ru)


# --------------------------------------------------------------------------- mirror dimension
DIM_TYPE = {
    "ultrawarm": False,
    "natural": True,
    "coordinate_scale": 1.0,
    "has_skylight": True,
    "has_ceiling": False,
    "ambient_light": 0.0,
    "fixed_time": 18000,                      # eternal midnight
    "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
    "monster_spawn_block_light_limit": 0,
    "piglin_safe": False,
    "bed_works": True,
    "respawn_anchor_works": False,
    "has_raids": False,
    "logical_height": 384,
    "min_y": -64,
    "height": 384,
    "infiniburn": "#minecraft:infiniburn_overworld",
    "effects": "darkrifts:dark_sky",
}


def make_dimension():
    write_json("data/%s/dimension_type/dark_tall.json" % MOD, DIM_TYPE)
    # Same noise settings + same biome layout + (automatically) the same world seed as the overworld
    # => identical terrain, caves, ores, trees and structure positions. The mod darkens it while it generates.
    write_json("data/%s/dimension/mirror.json" % MOD, {
        "type": "%s:dark_tall" % MOD,
        "generator": {
            "type": "minecraft:noise",
            "settings": "minecraft:overworld",
            "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:overworld"},
        },
    })


# --------------------------------------------------------------------------- worldgen, recipe
def make_worldgen_and_recipe():
    write_json("data/%s/worldgen/configured_feature/natural_rift.json" % MOD,
               {"type": "%s:rift" % MOD, "config": {}})
    write_json("data/%s/worldgen/placed_feature/natural_rift.json" % MOD, {
        "feature": "%s:natural_rift" % MOD,
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 25},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"},
        ],
    })
    write_json("data/%s/worldgen/configured_feature/mirror_darken.json" % MOD,
               {"type": "%s:darken" % MOD, "config": {}})
    write_json("data/%s/worldgen/placed_feature/mirror_darken.json" % MOD,
               {"feature": "%s:mirror_darken" % MOD, "placement": []})
    write_json("data/%s/neoforge/biome_modifier/mirror_darken.json" % MOD, {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "%s:mirror_darken" % MOD,
        "step": "top_layer_modification",
    })
    write_json("data/%s/recipe/rift_blade.json" % MOD, {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": [" E ", "ESE", " P "],
        "key": {
            "E": {"item": "minecraft:echo_shard"},
            "S": {"item": "minecraft:netherite_sword"},
            "P": {"item": "minecraft:ender_eye"},
        },
        "result": {"id": "%s:rift_blade" % MOD, "count": 1},
    })


if __name__ == "__main__":
    shutil.rmtree(os.path.join(RES, "data", MOD), ignore_errors=True)
    make_rift_texture()
    make_sword_texture()
    make_bucket_texture()
    make_void_texture()
    make_client_assets()
    make_dimension()
    make_worldgen_and_recipe()
    print("Generated resources into", RES)
    sys.exit(0)
