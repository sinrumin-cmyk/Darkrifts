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
    px = {}
    steel = (46, 42, 66, 255)
    light = (122, 112, 160, 255)
    glow = (160, 70, 230, 255)
    guard = (92, 62, 132, 255)
    guard_hi = (150, 110, 200, 255)
    handle = (50, 32, 70, 255)
    gem = (210, 90, 255, 255)

    for k in range(9):                      # blade, going from the tip (13,2) down-left
        x, y = 13 - k, 2 + k
        px[(x, y)] = steel
        px[(x + 1, y)] = light              # upper-right edge catches the light
        px[(x, y + 1)] = glow if k % 2 == 0 else (120, 50, 190, 255)  # lower-left purple glow
    px[(14, 1)] = light                     # tip
    px[(14, 2)] = light
    for t in range(-2, 3):                  # cross guard
        px[(5 + t, 10 + t)] = guard
        px[(6 + t, 10 + t)] = guard_hi if t < 0 else guard
    px[(4, 11)] = handle                    # grip
    px[(3, 12)] = handle
    px[(2, 13)] = handle
    px[(1, 14)] = gem                       # pommel gem

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), c in px.items():
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), c)
    outline = (12, 6, 20, 255)
    for (x, y) in list(px.keys()):          # 1px dark outline
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in px:
                    img.putpixel((nx, ny), outline)
    path = os.path.join(RES, "assets", MOD, "textures", "item", "rift_blade.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
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
    }
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
    "effects": "minecraft:overworld",
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
    write_json("data/%s/neoforge/biome_modifier/natural_rifts.json" % MOD, {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "%s:natural_rift" % MOD,
        "step": "surface_structures",
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
    make_client_assets()
    make_dimension()
    make_worldgen_and_recipe()
    print("Generated resources into", RES)
    sys.exit(0)
