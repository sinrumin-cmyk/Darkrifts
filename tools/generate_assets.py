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
    """Calm near-black void rock with a few twinkling stars (16x128, 8 frames).
    In the prison the real look is drawn on top with the End-portal shader (client/VoidMatterRenderer)."""
    import math, random
    F = 8
    img = Image.new("RGBA", (16, 16 * F))
    rnd = random.Random(11)
    base = [[rnd.random() for _ in range(16)] for _ in range(16)]
    stars = [(rnd.randrange(16), rnd.randrange(16), rnd.random() * 6.28,
              rnd.choice([(150, 255, 255), (220, 160, 255), (255, 255, 255)])) for _ in range(6)]
    for f in range(F):
        ph = f * 2 * math.pi / F
        for y in range(16):
            for x in range(16):
                k = base[y][x]
                img.putpixel((x, f * 16 + y), (int(3 + 5 * k), int(4 + 7 * k), int(9 + 14 * k), 255))
        for (sx, sy, sp, col) in stars:
            a = 0.15 + 0.85 * max(0.0, math.sin(ph + sp)) ** 3
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
    en["entity.%s.rift_spike" % MOD] = "Rift Spike"
    ru["entity.%s.rift_spike" % MOD] = "Шип разлома"
    en.update(ABILITY_EN)
    ru.update(ABILITY_RU)
    en.update({'ability.darkrifts.wave': 'Spike Wave', 'ability.darkrifts.wave.desc': 'A line of rifts races forward, spikes bursting out', 'ability.darkrifts.slash': 'Void Slash', 'ability.darkrifts.slash.desc': 'A long cut through the air that wounds everything on it', 'ability.darkrifts.strike': 'Void Strike', 'ability.darkrifts.strike.desc': 'A rift opens on your target and a bolt falls through', 'ability.darkrifts.drain': 'Drain', 'ability.darkrifts.drain.desc': 'Steals the life of nearby enemies', 'ability.darkrifts.shield': 'Rift Barrier', 'ability.darkrifts.shield.desc': 'Throws back and hurts everything that comes close', 'ability.darkrifts.spikes.desc': 'Rifts tear open around you and spikes burst out'})
    ru.update({'ability.darkrifts.wave': 'Волна шипов', 'ability.darkrifts.wave.desc': 'Линия разломов мчится вперёд, из них бьют шипы', 'ability.darkrifts.slash': 'Разрез пустоты', 'ability.darkrifts.slash.desc': 'Длинный разрез воздуха, ранит всё на линии', 'ability.darkrifts.strike': 'Удар пустоты', 'ability.darkrifts.strike.desc': 'На цели открывается разлом, из него бьёт молния', 'ability.darkrifts.drain': 'Поглощение', 'ability.darkrifts.drain.desc': 'Крадёт жизнь у врагов рядом', 'ability.darkrifts.shield': 'Барьер разломов', 'ability.darkrifts.shield.desc': 'Отбрасывает и ранит всех, кто подошёл близко', 'ability.darkrifts.spikes.desc': 'Вокруг раскрываются разломы, из них бьют шипы'})
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


# --------------------------------------------------------------------------- rift armor
ARMOR_PIECES = ["helmet", "chestplate", "leggings", "boots"]
ARMOR_EN = {"helmet": "Rift Helmet", "chestplate": "Rift Chestplate", "leggings": "Rift Leggings", "boots": "Rift Boots"}
ARMOR_RU = {"helmet": "Шлем разлома", "chestplate": "Нагрудник разлома", "leggings": "Поножи разлома", "boots": "Ботинки разлома"}

ARMOR_ICONS = {
    "helmet": [
        "................",
        "................",
        "................",
        "....OOOOOOOO....",
        "...OHHHHHHHHO...",
        "..OHAAAAAAAAHO..",
        "..OHAAAAAAAAHO..",
        "..OAAAGAAGAAAO..",
        "..OAAGAAAAGAAO..",
        "..OAAAAAAAAAAO..",
        "..OAAOOAAOOAAO..",
        "..OAO..OO..OAO..",
        "..OO........OO..",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "................",
        "..OOO......OOO..",
        ".OHHAO....OAHHO.",
        ".OHAAOOOOOOAAHO.",
        ".OAAAAAAAAAAAAO.",
        ".OAAAAAGGAAAAAO.",
        ".OOAAAGAAGAAAOO.",
        "..OAAAAGGAAAAO..",
        "..OAAAAAAAAAAO..",
        "..OAAAGAAGAAAO..",
        "..OAAAAAAAAAAO..",
        "..OAAAAAAAAAAO..",
        "...OOOOOOOOOO...",
        "................",
        "................",
    ],
    "leggings": [
        "................",
        "................",
        "...OOOOOOOOOO...",
        "..OHHHHHHHHHHO..",
        "..OAAAAGGAAAAO..",
        "..OAAAAAAAAAAO..",
        "..OAAAOOOOAAAO..",
        "..OAAAO..OAAAO..",
        "..OAAGO..OGAAO..",
        "..OAAAO..OAAAO..",
        "..OAAAO..OAAAO..",
        "..OAAAO..OAAAO..",
        "..OOOOO..OOOOO..",
        "................",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "...OOOO..OOOO...",
        "...OHAAO.OAAHO..",
        "...OAAAO.OAAAO..",
        "...OAGAO.OAGAO..",
        "..OAAAAO.OAAAAO.",
        ".OAAAAAO.OAAAAAO",
        ".OHAAAAO.OAAAAHO",
        ".OOOOOOO.OOOOOOO",
        "................",
        "................",
        "................",
    ],
}


def make_armor_icons():
    cmap = {"O": (10, 5, 20, 255), "A": (46, 30, 84, 255), "H": (112, 76, 196, 255), "G": (222, 110, 255, 255)}
    for name, rows in ARMOR_ICONS.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in cmap:
                    img.putpixel((x, y), cmap[ch])
        path = os.path.join(RES, "assets", MOD, "textures", "item", "rift_%s.png" % name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)


def _paint_rect(img, x0, y0, w, h, seed):
    """Dark void-crystal plating with a glowing violet crack across it."""
    import random
    rnd = random.Random(seed)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            k = rnd.random()
            band = 0.5 + 0.5 * math.sin((x + y * 0.7) * 0.9)
            r = 26 + 22 * band + 10 * k
            g = 16 + 14 * band + 6 * k
            b = 52 + 40 * band + 16 * k
            img.putpixel((x, y), (int(r), int(g), int(b), 255))
    for x in range(x0, x0 + w):            # lighter top edge, darker bottom edge
        img.putpixel((x, y0), (96, 64, 168, 255))
        img.putpixel((x, y0 + h - 1), (14, 8, 28, 255))
    cy = y0 + rnd.randrange(max(1, h // 3), max(2, h - h // 3))
    for x in range(x0 + 1, x0 + w - 1):    # jagged glowing crack
        cy = min(y0 + h - 2, max(y0 + 1, cy + rnd.choice([-1, 0, 0, 1])))
        img.putpixel((x, cy), (222, 120, 255, 255))
        if rnd.random() < 0.35 and cy + 1 < y0 + h - 1:
            img.putpixel((x, cy + 1), (120, 60, 220, 255))


def make_armor_textures():
    """Worn armor: textures/models/armor/rift_layer_1.png (helmet, chest, boots) and _layer_2.png (leggings), 64x32."""
    d = os.path.join(RES, "assets", MOD, "textures", "models", "armor")
    os.makedirs(d, exist_ok=True)
    # layer 1
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    _paint_rect(img, 0, 0, 32, 16, 1)          # helmet (all head faces)
    _paint_rect(img, 16, 16, 24, 16, 2)        # chestplate body
    _paint_rect(img, 40, 16, 16, 16, 3)        # sleeves
    _paint_rect(img, 0, 28, 16, 4, 4)          # boots (lower part of the legs)
    _paint_rect(img, 8, 16, 4, 4, 5)           # sole
    img.save(os.path.join(d, "rift_layer_1.png"))
    # layer 2
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    _paint_rect(img, 16, 16, 24, 16, 6)        # belt
    _paint_rect(img, 0, 16, 16, 16, 7)         # legs
    img.save(os.path.join(d, "rift_layer_2.png"))


def make_armor():
    make_armor_icons()
    make_armor_textures()
    for name in ARMOR_PIECES:
        write_json("assets/%s/models/item/rift_%s.json" % (MOD, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/rift_%s" % (MOD, name)},
        })
        write_json("data/%s/recipe/rift_%s.json" % (MOD, name), {
            "type": "minecraft:crafting_shapeless",
            "category": "equipment",
            "ingredients": [
                {"item": "minecraft:netherite_%s" % name},
                {"item": "minecraft:echo_shard"},
                {"item": "minecraft:echo_shard"},
                {"item": "minecraft:echo_shard"},
                {"item": "minecraft:ender_eye"},
            ],
            "result": {"id": "%s:rift_%s" % (MOD, name), "count": 1},
        })
    for lang, names, extra in (
        ("en_us", ARMOR_EN, {"tooltip.%s.rift_armor" % MOD: "Full set: no fall damage, halves magic damage, Speed I.",
                              "tooltip.%s.rift_armor.set" % MOD: "Set bonus active"}),
        ("ru_ru", ARMOR_RU, {"tooltip.%s.rift_armor" % MOD: "Полный сет: нет урона от падения, магический урон вдвое меньше, Скорость I.",
                              "tooltip.%s.rift_armor.set" % MOD: "Бонус комплекта активен"}),
    ):
        path = os.path.join(RES, "assets", MOD, "lang", lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for n in ARMOR_PIECES:
            data["item.%s.rift_%s" % (MOD, n)] = names[n]
        data.update(extra)
        write_json("assets/%s/lang/%s.json" % (MOD, lang), data)


# --------------------------------------------------------------------------- boss: Rift Reaver (GeckoLib)
REAVER_CUBES = {
    # name: (origin, size, uv)
    "body": [((-8, 24, -4), (16, 20, 8), (0, 0)),
             ((-1, 38, 4), (2, 8, 3), (0, 96)), ((-1, 28, 4), (2, 8, 3), (0, 96)), ((-1, 18, 4), (2, 6, 3), (0, 96))],
    "head": [((-5, 44, -5), (10, 10, 10), (0, 32)),
             ((-6, 53, -1), (2, 9, 2), (48, 32)), ((4, 53, -1), (2, 9, 2), (48, 32)),
             ((-4, 57, -6), (8, 2, 2), (48, 32))],
    "arm_r": [((-14, 24, -3), (6, 20, 6), (64, 0)), ((-15, 16, -4), (8, 8, 8), (64, 28)),
              ((-14, 4, -1), (2, 12, 2), (96, 28)), ((-11, 4, -1), (2, 12, 2), (96, 28))],
    "arm_l": [((8, 24, -3), (6, 20, 6), (96, 0)), ((7, 16, -4), (8, 8, 8), (64, 28)),
              ((8, 4, -1), (2, 12, 2), (96, 28)), ((11, 4, -1), (2, 12, 2), (96, 28))],
    "leg_r": [((-7, 4, -3), (6, 20, 6), (0, 64)), ((-8, 0, -6), (8, 4, 10), (64, 64))],
    "leg_l": [((1, 4, -3), (6, 20, 6), (32, 64)), ((0, 0, -6), (8, 4, 10), (64, 64))],
}
REAVER_PIVOTS = {"body": (0, 24, 0), "head": (0, 44, 0), "arm_r": (-10, 42, 0), "arm_l": (10, 42, 0),
                 "leg_r": (-4, 24, 0), "leg_l": (4, 24, 0)}


def make_reaver_assets():
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    for name, cubes in REAVER_CUBES.items():
        parent = "root" if name in ("body", "leg_r", "leg_l") else "body"
        bones.append({
            "name": name, "parent": parent, "pivot": list(REAVER_PIVOTS[name]),
            "cubes": [{"origin": list(o), "size": list(sz), "uv": list(uv)} for (o, sz, uv) in cubes],
        })
    write_json("assets/%s/geo/entity/rift_reaver.geo.json" % MOD, {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.rift_reaver", "texture_width": 128, "texture_height": 128,
                            "visible_bounds_width": 6, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2, 0]},
            "bones": bones}],
    })

    def kf(d):
        return {("%.4f" % k).rstrip("0").rstrip("."): v for k, v in d.items()}

    P = "animation.rift_reaver."
    anims = {
        P + "idle": {"loop": True, "animation_length": 4.0, "bones": {
            "body": {"position": kf({0: [0, 0, 0], 2.0: [0, -0.6, 0], 4.0: [0, 0, 0]}),
                     "rotation": kf({0: [0, 0, 0], 2.0: [2.5, 0, 0], 4.0: [0, 0, 0]})},
            "head": {"rotation": kf({0: [0, 0, 0], 1.0: [0, 6, 0], 3.0: [0, -6, 0], 4.0: [0, 0, 0]})},
            "arm_r": {"rotation": kf({0: [0, 0, 4], 2.0: [6, 0, 9], 4.0: [0, 0, 4]})},
            "arm_l": {"rotation": kf({0: [0, 0, -4], 2.0: [6, 0, -9], 4.0: [0, 0, -4]})}}},
        P + "walk": {"loop": True, "animation_length": 1.0, "bones": {
            "leg_r": {"rotation": kf({0: [32, 0, 0], 0.5: [-32, 0, 0], 1.0: [32, 0, 0]})},
            "leg_l": {"rotation": kf({0: [-32, 0, 0], 0.5: [32, 0, 0], 1.0: [-32, 0, 0]})},
            "arm_r": {"rotation": kf({0: [-26, 0, 6], 0.5: [26, 0, 6], 1.0: [-26, 0, 6]})},
            "arm_l": {"rotation": kf({0: [26, 0, -6], 0.5: [-26, 0, -6], 1.0: [26, 0, -6]})},
            "body": {"position": kf({0: [0, 0, 0], 0.25: [0, -1.2, 0], 0.5: [0, 0, 0], 0.75: [0, -1.2, 0], 1.0: [0, 0, 0]}),
                     "rotation": kf({0: [6, 3, 0], 0.5: [6, -3, 0], 1.0: [6, 3, 0]})},
            "head": {"rotation": kf({0: [-4, -3, 0], 0.5: [-4, 3, 0], 1.0: [-4, -3, 0]})}}},
        P + "attack": {"loop": "play_once", "animation_length": 0.7, "bones": {
            "arm_r": {"rotation": kf({0: [0, 0, 6], 0.25: [-130, 0, 10], 0.4: [35, 0, 0], 0.7: [0, 0, 6]})},
            "body": {"rotation": kf({0: [0, 0, 0], 0.25: [-8, 14, 0], 0.4: [18, -12, 0], 0.7: [0, 0, 0]})},
            "head": {"rotation": kf({0: [0, 0, 0], 0.25: [-12, 0, 0], 0.4: [14, 0, 0], 0.7: [0, 0, 0]})}}},
        P + "slam": {"loop": "play_once", "animation_length": 1.2, "bones": {
            "arm_r": {"rotation": kf({0: [0, 0, 6], 0.55: [-170, 0, 14], 0.7: [-170, 0, 14], 0.82: [25, 0, 0], 1.2: [0, 0, 6]})},
            "arm_l": {"rotation": kf({0: [0, 0, -6], 0.55: [-170, 0, -14], 0.7: [-170, 0, -14], 0.82: [25, 0, 0], 1.2: [0, 0, -6]})},
            "body": {"position": kf({0: [0, 0, 0], 0.55: [0, 1.5, 0], 0.82: [0, -3, 0], 1.2: [0, 0, 0]}),
                     "rotation": kf({0: [0, 0, 0], 0.55: [-14, 0, 0], 0.82: [24, 0, 0], 1.2: [0, 0, 0]})},
            "head": {"rotation": kf({0: [0, 0, 0], 0.55: [-20, 0, 0], 0.82: [12, 0, 0], 1.2: [0, 0, 0]})}}},
        P + "emerge": {"loop": "hold_on_last_frame", "animation_length": 4.0, "bones": {
            "arm_r": {"rotation": kf({0: [-175, 0, 20], 2.4: [-175, 0, 20], 3.4: [-20, 0, 8], 4.0: [0, 0, 4]})},
            "arm_l": {"rotation": kf({0: [-175, 0, -20], 2.4: [-175, 0, -20], 3.4: [-20, 0, -8], 4.0: [0, 0, -4]})},
            "head": {"rotation": kf({0: [-35, 0, 0], 2.6: [-35, 0, 0], 3.2: [-40, 0, 0], 3.6: [10, 0, 0], 4.0: [0, 0, 0]})},
            "body": {"rotation": kf({0: [-10, 0, 0], 2.6: [-10, 0, 0], 3.4: [12, 0, 0], 4.0: [0, 0, 0]})}}},
    }
    write_json("assets/%s/animations/entity/rift_reaver.animation.json" % MOD,
               {"format_version": "1.8.0", "animations": anims})

    # textures
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    glow = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    seed = 100
    for name, cubes in REAVER_CUBES.items():
        for (o, sz, uv) in cubes:
            w, h, d = sz
            seed += 1
            _paint_rect(img, uv[0], uv[1], 2 * (w + d), h + d, seed)
    # glowing eyes (front of the head: uv (0,32), d=10 -> front starts at x=10, y=42)
    for ex in (12, 16):
        for ey in (45, 46):
            for dx in range(2):
                img.putpixel((ex + dx, ey), (255, 230, 255, 255))
                glow.putpixel((ex + dx, ey), (255, 230, 255, 255))
    # glowing rift in the chest (front of the body: uv (0,0), d=8 -> x 8..24, y 8..28)
    import random
    rnd = random.Random(5)
    cx = 16
    for y in range(11, 26):
        cx = min(21, max(11, cx + rnd.choice([-1, 0, 0, 1])))
        for dx in (0, 1):
            img.putpixel((cx + dx, y), (222, 120, 255, 255))
            glow.putpixel((cx + dx, y), (222, 120, 255, 255))
        img.putpixel((cx - 1, y), (120, 60, 220, 255))
        glow.putpixel((cx - 1, y), (120, 60, 220, 255))
    # glowing veins on the arms (front faces of the upper arms)
    for (ux, uy) in ((64, 0), (96, 0)):
        for y in range(uy + 8, uy + 26, 2):
            x = ux + 6 + (y % 3)
            img.putpixel((x, y), (205, 110, 255, 255))
            glow.putpixel((x, y), (205, 110, 255, 255))
    path = os.path.join(RES, "assets", MOD, "textures", "entity")
    os.makedirs(path, exist_ok=True)
    img.save(os.path.join(path, "rift_reaver.png"))
    glow.save(os.path.join(path, "rift_reaver_glowmask.png"))

    write_json("data/%s/loot_table/entities/rift_reaver.json" % MOD, {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:nether_star"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 8},
             "entries": [{"type": "minecraft:item", "name": "minecraft:echo_shard"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 3},
             "entries": [{"type": "minecraft:item", "name": "minecraft:ender_pearl"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4},
             "entries": [{"type": "minecraft:item", "name": "minecraft:netherite_scrap"}]},
        ],
    })
    write_json("assets/%s/models/item/rift_reaver_spawn_egg.json" % MOD, {"parent": "minecraft:item/template_spawn_egg"})


# --------------------------------------------------------------------------- ritual altar + codex book
BOOK_EN = [
    "\u00a75\u00a7lDark Rifts Codex\u00a7r\n\nA guide to the tears in the world, the blade that opens them, and what waits on the other side.",
    "\u00a75Rift Blade\u00a7r\n\nNetherite Sword + 4 Echo Shards + Eye of Ender.\n\nHold Right Click, look up and drag your view to draw a rift. Sneak + Right Click on a rift closes it.",
    "\u00a75Abilities\u00a7r\n\nPress R with the blade in hand.\n\nRift, Spikes, Step, Collapse, Cloak, Seal, Spike Wave, Void Slash, Void Strike, Drain and Rift Barrier.",
    "\u00a75Dark Mirror\u00a7r\n\nRifts lead to the Dark Mirror: a ruined copy of the world under an eternal night. Thick fog, black trees, dark water and no sun.",
    "\u00a75Void Prison\u00a7r\n\nThe Seal ability throws a player into the Void Prison. A sealed player respawns there forever. Only the one who sealed them can lift the seal.",
    "\u00a75Rift Armor\u00a7r\n\nNetherite piece + 3 Echo Shards + Eye of Ender.\n\nFull set: no fall damage, magic damage halved, Speed I.",
    "\u00a75Ritual Altar\u00a7r\n\nCraft: 6 Obsidian, 1 Crying Obsidian, 2 Echo Shards (shards on top corners, crying obsidian between them).\nPlace it on open ground.",
    "\u00a75The Ritual\u00a7r\n\nRight Click the altar with a Nether Star. For twelve seconds the world shakes, runes burn and rifts open. Stay back, and keep the sky above the altar clear.",
    "\u00a75Rift Reaver\u00a7r\n\nThe altar is consumed and the Reaver climbs out of the tear. It slams the ground, calls spikes and drags you in. At half health it goes berserk.",
    "\u00a75Spoils\u00a7r\n\nThe Reaver drops a Nether Star, Echo Shards, Ender Pearls and Netherite Scrap.\n\nGood luck, rift-walker.",
]
BOOK_RU = [
    "\u00a75\u00a7lКодекс тёмных разломов\u00a7r\n\nРуководство по трещинам мира, клинку, который их открывает, и тому, что ждёт по ту сторону.",
    "\u00a75Клинок разломов\u00a7r\n\nНезеритовый меч + 4 эхо-осколка + глаз Края.\n\nЗажми ПКМ, посмотри вверх и веди взглядом, чтобы нарисовать разлом. Shift + ПКМ по разлому закрывает его.",
    "\u00a75Способности\u00a7r\n\nНажми R с клинком в руке.\n\nРазлом, Шипы, Шаг, Коллапс, Покров, Печать, Волна шипов, Разрез, Удар пустоты, Поглощение и Барьер.",
    "\u00a75Тёмное зеркало\u00a7r\n\nРазломы ведут в Тёмное зеркало: разрушенную копию мира под вечной ночью. Густой туман, чёрные деревья, тёмная вода и никакого солнца.",
    "\u00a75Тюрьма пустоты\u00a7r\n\nСпособность «Печать» бросает игрока в Тюрьму пустоты. Запечатанный возрождается там вечно. Снять печать может только тот, кто её наложил.",
    "\u00a75Броня разломов\u00a7r\n\nНезеритовая деталь + 3 эхо-осколка + глаз Края.\n\nПолный сет: нет урона от падения, магический урон вдвое меньше, Скорость I.",
    "\u00a75Алтарь ритуала\u00a7r\n\nКрафт: 6 обсидиана, 1 плачущий обсидиан, 2 эхо-осколка (осколки сверху по углам, плачущий обсидиан между ними).\nПоставь на открытой земле.",
    "\u00a75Ритуал\u00a7r\n\nПКМ по алтарю со звездой Нижнего мира. Двенадцать секунд мир дрожит, руны горят, открываются разломы. Отойди и не загораживай небо над алтарём.",
    "\u00a75Жнец разлома\u00a7r\n\nАлтарь поглощается, и Жнец вылезает из трещины. Он бьёт по земле, вызывает шипы и затягивает тебя. На половине здоровья впадает в ярость.",
    "\u00a75Трофеи\u00a7r\n\nС Жнеца падают звезда Нижнего мира, эхо-осколки, жемчуг Края и незеритовый лом.\n\nУдачи, ходящий по разломам.",
]


def make_altar_and_book():
    write_json("assets/%s/blockstates/ritual_altar.json" % MOD,
               {"variants": {"": {"model": "%s:block/ritual_altar" % MOD}}})
    write_json("assets/%s/models/block/ritual_altar.json" % MOD, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "minecraft:block/crying_obsidian", "side": "minecraft:block/obsidian",
                     "bottom": "minecraft:block/obsidian"},
    })
    write_json("assets/%s/models/item/ritual_altar.json" % MOD, {"parent": "%s:block/ritual_altar" % MOD})
    write_json("data/%s/loot_table/blocks/ritual_altar.json" % MOD, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:ritual_altar" % MOD}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })
    write_json("data/minecraft/tags/block/mineable/pickaxe.json", {"values": ["%s:ritual_altar" % MOD]})
    write_json("data/%s/recipe/ritual_altar.json" % MOD, {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["ECE", "OOO", "OOO"],
        "key": {"E": {"item": "minecraft:echo_shard"}, "C": {"item": "minecraft:crying_obsidian"},
                "O": {"item": "minecraft:obsidian"}},
        "result": {"id": "%s:ritual_altar" % MOD, "count": 1},
    })
    write_json("data/%s/recipe/codex.json" % MOD, {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:echo_shard"}],
        "result": {"id": "minecraft:written_book", "count": 1, "components": {
            "minecraft:written_book_content": {
                "title": {"raw": "Dark Rifts Codex"},
                "author": "Dark Rifts",
                "resolved": True,
                "pages": [{"raw": {"translate": "book.%s.page%d" % (MOD, i + 1)}} for i in range(len(BOOK_EN))],
            }}},
    })
    en_extra = {
        "block.%s.ritual_altar" % MOD: "Ritual Altar",
        "entity.%s.rift_reaver" % MOD: "Rift Reaver",
        "item.%s.rift_reaver_spawn_egg" % MOD: "Rift Reaver Spawn Egg",
        "message.%s.ritual_need_star" % MOD: "The altar hungers for a Nether Star",
        "message.%s.ritual_no_room" % MOD: "Not enough free space around and above the altar",
        "message.%s.ritual_begin" % MOD: "The ritual begins...",
        "message.%s.ritual_busy" % MOD: "The ritual is already underway",
        "message.%s.ritual_awakens" % MOD: "The Rift Reaver awakens",
        "message.%s.reaver_enraged" % MOD: "The Reaver goes berserk!",
        "tooltip.%s.ritual_altar" % MOD: "Right Click with a Nether Star to begin the ritual.",
    }
    ru_extra = {
        "block.%s.ritual_altar" % MOD: "Алтарь ритуала",
        "entity.%s.rift_reaver" % MOD: "Жнец разлома",
        "item.%s.rift_reaver_spawn_egg" % MOD: "Яйцо призыва: Жнец разлома",
        "message.%s.ritual_need_star" % MOD: "Алтарь жаждет звезду Нижнего мира",
        "message.%s.ritual_no_room" % MOD: "Не хватает свободного места вокруг и над алтарём",
        "message.%s.ritual_begin" % MOD: "Ритуал начинается...",
        "message.%s.ritual_busy" % MOD: "Ритуал уже идёт",
        "message.%s.ritual_awakens" % MOD: "Жнец разлома пробуждается",
        "message.%s.reaver_enraged" % MOD: "Жнец впал в ярость!",
        "tooltip.%s.ritual_altar" % MOD: "ПКМ со звездой Нижнего мира начинает ритуал.",
    }
    for i in range(len(BOOK_EN)):
        en_extra["book.%s.page%d" % (MOD, i + 1)] = BOOK_EN[i]
        ru_extra["book.%s.page%d" % (MOD, i + 1)] = BOOK_RU[i]
    for lang, extra in (("en_us", en_extra), ("ru_ru", ru_extra)):
        path = os.path.join(RES, "assets", MOD, "lang", lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data.update(extra)
        write_json("assets/%s/lang/%s.json" % (MOD, lang), data)


if __name__ == "__main__":
    shutil.rmtree(os.path.join(RES, "data", MOD), ignore_errors=True)
    make_rift_texture()
    make_sword_texture()
    make_bucket_texture()
    make_void_texture()
    make_client_assets()
    make_dimension()
    make_worldgen_and_recipe()
    make_armor()
    make_reaver_assets()
    make_altar_and_book()
    print("Generated resources into", RES)
    sys.exit(0)
