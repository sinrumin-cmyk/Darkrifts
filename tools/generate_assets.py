#!/usr/bin/env python3
"""
Generates every JSON / PNG resource of the Dark Rifts mod into src/main/resources.

    python3 tools/generate_assets.py

Needs Pillow (pip install pillow). Keep DIMENSIONS in sync with DarkDimensions.COUNT in the Java code.
"""
import json
import math
import os
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
        "tooltip.%s.rift_blade.1" % MOD: "Tears holes in the sky to the dark realms.",
        "tooltip.%s.rift_blade.2" % MOD: "Hold Right Click, look up and drag your view to draw a rift.",
        "message.%s.stroke_too_short" % MOD: "The rift fizzles out... drag further.",
        "message.%s.no_room" % MOD: "There is no room for a rift here.",
        "message.%s.dimension_missing" % MOD: "The rift leads nowhere (%s is missing).",
    }
    ru = {
        "block.%s.rift" % MOD: "Разлом",
        "item.%s.rift_blade" % MOD: "Клинок разломов",
        "itemGroup.%s" % MOD: "Тёмные разломы",
        "tooltip.%s.rift_blade.1" % MOD: "Рвёт небо, открывая путь в тёмные миры.",
        "tooltip.%s.rift_blade.2" % MOD: "Зажми ПКМ, посмотри вверх и веди взглядом, чтобы нарисовать разлом.",
        "message.%s.stroke_too_short" % MOD: "Разлом угас... веди дальше.",
        "message.%s.no_room" % MOD: "Здесь нет места для разлома.",
        "message.%s.dimension_missing" % MOD: "Разлом ведёт в никуда (%s отсутствует).",
    }
    for b in BIOMES:
        en["biome.%s.%s" % (MOD, b["name"])] = b["title_en"]
        ru["biome.%s.%s" % (MOD, b["name"])] = b["title_ru"]
    write_json("assets/%s/lang/en_us.json" % MOD, en)
    write_json("assets/%s/lang/ru_ru.json" % MOD, ru)


# --------------------------------------------------------------------------- biomes
MONSTERS = [
    {"type": "minecraft:spider", "weight": 100, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:zombie", "weight": 95, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:skeleton", "weight": 100, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:creeper", "weight": 100, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:enderman", "weight": 12, "minCount": 1, "maxCount": 4},
    {"type": "minecraft:witch", "weight": 5, "minCount": 1, "maxCount": 1},
]

ORES = [
    "minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_tuff",
    "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
    "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small",
    "minecraft:ore_gold", "minecraft:ore_redstone", "minecraft:ore_diamond",
    "minecraft:ore_lapis", "minecraft:ore_copper",
]

BIOMES = [
    dict(name="umbral_plains", title_en="Umbral Plains", title_ru="Сумрачные равнины",
         sky=rgb(5, 4, 12), fog=rgb(10, 8, 20), water=rgb(14, 20, 40), water_fog=rgb(6, 8, 18),
         grass=rgb(38, 56, 44), foliage=rgb(28, 44, 38), particle=None,
         vegetation=["minecraft:trees_plains", "minecraft:patch_grass_plain", "minecraft:patch_dead_bush"]),
    dict(name="gloomwood", title_en="Gloomwood", title_ru="Мрачная чаща",
         sky=rgb(3, 5, 8), fog=rgb(6, 10, 12), water=rgb(10, 28, 30), water_fog=rgb(4, 14, 16),
         grass=rgb(24, 44, 34), foliage=rgb(18, 34, 28), particle=None,
         vegetation=["minecraft:dark_forest_vegetation", "minecraft:brown_mushroom_normal",
                     "minecraft:red_mushroom_normal"]),
    dict(name="ashen_wastes", title_en="Ashen Wastes", title_ru="Пепельная пустошь",
         sky=rgb(10, 10, 12), fog=rgb(22, 22, 26), water=rgb(30, 30, 38), water_fog=rgb(14, 14, 18),
         grass=rgb(58, 58, 62), foliage=rgb(46, 46, 52), particle=("minecraft:ash", 0.02),
         vegetation=["minecraft:patch_dead_bush"]),
    dict(name="void_marsh", title_en="Void Marsh", title_ru="Бездонное болото",
         sky=rgb(8, 3, 16), fog=rgb(16, 7, 32), water=rgb(34, 14, 72), water_fog=rgb(18, 8, 40),
         grass=rgb(46, 30, 62), foliage=rgb(36, 24, 52), particle=("minecraft:white_ash", 0.006),
         vegetation=["minecraft:trees_swamp", "minecraft:brown_mushroom_normal",
                     "minecraft:red_mushroom_normal"]),
    dict(name="nightfall_peaks", title_en="Nightfall Peaks", title_ru="Пики Сумерек",
         sky=rgb(2, 4, 14), fog=rgb(8, 12, 28), water=rgb(12, 24, 58), water_fog=rgb(6, 12, 30),
         grass=rgb(30, 46, 58), foliage=rgb(22, 38, 52), particle=None,
         vegetation=["minecraft:trees_taiga", "minecraft:patch_grass_plain"]),
]


def make_biomes():
    for b in BIOMES:
        effects = {
            "sky_color": b["sky"],
            "fog_color": b["fog"],
            "water_color": b["water"],
            "water_fog_color": b["water_fog"],
            "grass_color": b["grass"],
            "foliage_color": b["foliage"],
            "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000,
                           "block_search_extent": 8, "offset": 2.0},
        }
        if b["particle"]:
            effects["particle"] = {"options": {"type": b["particle"][0]}, "probability": b["particle"][1]}
        features = [[] for _ in range(11)]
        features[4] = ["%s:natural_rift" % MOD]                      # SURFACE_STRUCTURES
        features[6] = list(ORES)                                      # UNDERGROUND_ORES
        features[8] = ["minecraft:spring_water", "minecraft:spring_lava"]  # FLUID_SPRINGS
        features[9] = list(b["vegetation"])                           # VEGETAL_DECORATION
        write_json("data/%s/worldgen/biome/%s.json" % (MOD, b["name"]), {
            "has_precipitation": False,
            "temperature": 0.5,
            "downfall": 0.0,
            "effects": effects,
            "spawners": {
                "monster": MONSTERS,
                "creature": [],
                "ambient": [{"type": "minecraft:bat", "weight": 10, "minCount": 8, "maxCount": 8}],
                "axolotls": [],
                "underground_water_creature": [],
                "water_creature": [],
                "water_ambient": [],
                "misc": [],
            },
            "spawn_costs": {},
            "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
            "features": features,
        })


# --------------------------------------------------------------------------- dimensions
def dimension_type(min_y, height, has_ceiling, has_skylight, infiniburn):
    return {
        "ultrawarm": False,
        "natural": True,
        "coordinate_scale": 1.0,
        "has_skylight": has_skylight,
        "has_ceiling": has_ceiling,
        "ambient_light": 0.0,
        "fixed_time": 18000,                      # eternal midnight
        "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
        "monster_spawn_block_light_limit": 0,
        "piglin_safe": False,
        "bed_works": True,
        "respawn_anchor_works": False,
        "has_raids": False,
        "logical_height": height,
        "min_y": min_y,
        "height": height,
        "infiniburn": infiniburn,
        "effects": "minecraft:overworld",
    }


DIM_TYPES = {
    "dark_tall": dimension_type(-64, 384, False, True, "#minecraft:infiniburn_overworld"),
    "dark_short": dimension_type(0, 256, False, True, "#minecraft:infiniburn_overworld"),
    "dark_end": dimension_type(0, 128, False, True, "#minecraft:infiniburn_end"),
    "dark_nether": dimension_type(0, 128, True, False, "#minecraft:infiniburn_nether"),
}

# (terrain preset, dimension type, biome names)
B = [b["name"] for b in BIOMES]
DIMENSIONS = [
    ("minecraft:overworld", "dark_tall", [B[0], B[1], B[4]]),
    ("minecraft:overworld", "dark_tall", [B[1], B[3]]),
    ("minecraft:amplified", "dark_tall", [B[4], B[2], B[0]]),
    ("minecraft:large_biomes", "dark_tall", [B[1], B[2], B[3], B[4]]),
    ("minecraft:caves", "dark_tall", [B[2], B[3]]),
    ("minecraft:floating_islands", "dark_short", [B[0], B[1], B[3]]),
    ("minecraft:end", "dark_end", [B[3], B[4]]),
    ("minecraft:nether", "dark_nether", [B[2], B[1]]),
    ("minecraft:amplified", "dark_tall", [B[3], B[1], B[2]]),
    ("minecraft:overworld", "dark_tall", [B[2], B[3], B[0]]),
    ("minecraft:floating_islands", "dark_short", [B[4], B[2]]),
    ("minecraft:end", "dark_end", [B[0], B[1], B[2]]),
]
assert len(DIMENSIONS) == 12, "keep in sync with DarkDimensions.COUNT"


def biome_source(biome_names):
    n = len(biome_names)
    entries = []
    for i, name in enumerate(biome_names):
        lo = round(-1.0 + 2.0 * i / n, 3)
        hi = round(-1.0 + 2.0 * (i + 1) / n, 3)
        entries.append({
            "biome": "%s:%s" % (MOD, name),
            "parameters": {
                "temperature": [lo, hi],
                "humidity": [-2.0, 2.0],
                "continentalness": [-2.0, 2.0],
                "erosion": [-2.0, 2.0],
                "depth": [-2.0, 2.0],
                "weirdness": [-2.0, 2.0],
                "offset": 0.0,
            },
        })
    return {"type": "minecraft:multi_noise", "biomes": entries}


def make_dimensions():
    for name, data in DIM_TYPES.items():
        write_json("data/%s/dimension_type/%s.json" % (MOD, name), data)
    for i, (preset, dtype, biome_names) in enumerate(DIMENSIONS):
        write_json("data/%s/dimension/dark_%02d.json" % (MOD, i + 1), {
            "type": "%s:%s" % (MOD, dtype),
            "generator": {
                "type": "minecraft:noise",
                "settings": preset,
                "biome_source": biome_source(biome_names),
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
    make_rift_texture()
    make_sword_texture()
    make_client_assets()
    make_biomes()
    make_dimensions()
    make_worldgen_and_recipe()
    print("Generated resources into", RES)
    sys.exit(0)
