"""Mirrorite arsenal: textures, models, recipes, loot, worldgen and lang for the progression items."""
import json
import os
import random

from PIL import Image

LIGHT = (222, 196, 255, 255)
MID = (160, 116, 230, 255)
DARK = (92, 56, 160, 255)
HI = (250, 240, 255, 255)
OUT = (20, 10, 40, 255)
STK = (96, 62, 40, 255)
STK2 = (136, 92, 58, 255)
PAL = {"h": HI, "a": LIGHT, "b": MID, "c": DARK, "k": STK, "l": STK2}


def _save(RES, MOD, kind, name, img):
    d = os.path.join(RES, "assets", MOD, "textures", kind)
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name + ".png"))


def _outline(img):
    px = img.load()
    cells = [(x, y) for x in range(16) for y in range(16) if px[x, y][3] > 0]
    for x, y in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] == 0:
                    px[nx, ny] = OUT
    return img


def _ascii(rows, handle=True):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    if handle:
        for i in range(10):
            x, y = 2 + i, 13 - i
            px[x, y] = STK2 if i % 2 else STK
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in PAL:
                px[x, y] = PAL[ch]
    return _outline(img)


TOOLS = {
    "pickaxe": [
        "................",
        "...bbbbbbb......",
        "..baaaaaaabb....",
        ".bah.....bbb....",
        ".bc.......bab...",
        ".c.........bab..",
        "............bb..",
        "............bc..",
        "................", "................", "................", "................",
        "................", "................", "................", "................"],
    "axe": [
        "................",
        "....bb..........",
        "...baabbbb......",
        "...baaaaaabb....",
        "...baaaaaaab....",
        "...baaaaabc.....",
        "....bbbbbc......",
        "................", "................", "................", "................",
        "................", "................", "................", "................", "................"],
    "shovel": [
        "..........bb....",
        ".........baab...",
        ".........baab...",
        ".........baab...",
        "..........bab...",
        "..........bcb...",
        "...........b....",
        "................", "................", "................", "................",
        "................", "................", "................", "................", "................"],
    "hoe": [
        "................",
        "......bbbbb.....",
        ".....baaaaab....",
        "....bc.bbbbc....",
        "....c...........",
        "................", "................", "................", "................",
        "................", "................", "................", "................", "................",
        "................", "................"],
}


def sword(key=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(5, 15):
        y = 15 - x
        px[x, y] = HI if key else LIGHT
        if y + 1 < 16:
            px[x, y + 1] = LIGHT if key else MID
        if x + 1 < 16 and x < 14:
            px[x + 1, y + 1] = DARK
    for k in range(-2, 3):
        px[4 + k, 11 + k] = MID if abs(k) < 2 else DARK
    px[4, 11] = HI
    px[3, 12] = STK2
    px[2, 13] = STK
    px[1, 14] = MID
    return _outline(img)


def ingot():
    rows = [
        "................",
        "................",
        "................",
        "......hhhhhh....",
        "....hhaaaaaab...",
        "...haaaaaaabbb..",
        "..bbbbbbbbbbc...",
        "..bcccccccbc....",
        "...cccccccc.....",
        "................", "................", "................", "................",
        "................", "................", "................"]
    return _ascii(rows, handle=False)


def raw():
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(4, 13):
        for x in range(3, 13):
            d = ((x - 7.5) ** 2) / 24 + ((y - 8) ** 2) / 16
            if d + rnd.random() * 0.25 < 1:
                px[x, y] = rnd.choice([(88, 80, 100, 255), (110, 100, 124, 255), (70, 62, 86, 255)])
    for _ in range(9):
        x, y = rnd.randrange(4, 12), rnd.randrange(5, 12)
        if px[x, y][3]:
            px[x, y] = rnd.choice([MID, LIGHT, HI])
    return _outline(img)


def heart():
    rows = [
        "................",
        "................",
        "...bbb..bbb.....",
        "..baaab baaab...",
        ".baahaabaahab...",
        ".baaaaaaaaaab...",
        ".bcaaaaaaaabc...",
        "..bcaaaaaabc....",
        "...bcaaaabc.....",
        "....bcaabc......",
        ".....bcbc.......",
        "......bc........",
        "................", "................", "................", "................"]
    rows = [r.replace(" ", ".") for r in rows]
    return _ascii(rows, handle=False)


def bow(pull):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    arc = []
    for k in range(-6, 7):
        b = 5 - round(k * k / 8)
        arc.append((8 + k + b // 2, 8 + k - b // 2 - (1 if b > 2 else 0) * 0))
    for i, (x, y) in enumerate(arc):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = STK2 if i % 2 else STK
    a, b = arc[0], arc[-1]
    mx, my = (a[0] + b[0]) // 2 - pull, (a[1] + b[1]) // 2 + pull
    for p, q in ((a, (mx, my)), ((mx, my), b)):
        n = max(abs(q[0] - p[0]), abs(q[1] - p[1]), 1)
        for t in range(n + 1):
            x = round(p[0] + (q[0] - p[0]) * t / n)
            y = round(p[1] + (q[1] - p[1]) * t / n)
            if 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] == 0:
                px[x, y] = LIGHT
    for (x, y) in (arc[3], arc[-4]):
        px[x, y] = MID
    return _outline(img)


def ore_texture():
    rnd = random.Random(11)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = rnd.randrange(-9, 10)
            px[x, y] = (46 + v, 42 + v, 58 + v, 255)
    for cx, cy in ((4, 4), (11, 5), (7, 11), (13, 12), (3, 12)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            x, y = cx + dx, cy + dy
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = MID if (dx, dy) != (0, 0) else HI
        if cx + 1 < 16:
            px[cx + 1, cy + 1] = DARK
    return img


def block_texture():
    rnd = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            band = 0.5 + 0.5 * __import__("math").sin((x + y) * 0.7)
            v = rnd.randrange(-6, 7)
            px[x, y] = (int(120 + 50 * band + v), int(84 + 40 * band + v), int(190 + 40 * band + v), 255)
    for i in range(16):
        px[i, 0] = px[0, i] = LIGHT
        px[i, 15] = px[15, i] = DARK
    for i in range(3, 13):
        px[i, 7] = DARK
        px[7, i] = DARK
    return img


def textures(RES, MOD):
    for n, rows in TOOLS.items():
        _save(RES, MOD, "item", "mirrorite_" + n, _ascii(rows))
    _save(RES, MOD, "item", "mirrorite_sword", sword())
    _save(RES, MOD, "item", "mirror_blade", sword(True))
    _save(RES, MOD, "item", "mirrorite_ingot", ingot())
    _save(RES, MOD, "item", "raw_mirrorite", raw())
    _save(RES, MOD, "item", "reaver_heart", heart())
    _save(RES, MOD, "item", "mirror_bow", bow(0))
    for i, p in enumerate((1, 2, 3)):
        _save(RES, MOD, "item", "mirror_bow_pulling_%d" % i, bow(p))
    _save(RES, MOD, "block", "mirror_ore", ore_texture())
    _save(RES, MOD, "block", "mirrorite_block", block_texture())


def recipes(MOD, w):
    def shaped(name, pattern, key, result, count=1, cat="equipment"):
        w("data/%s/recipe/%s.json" % (MOD, name), {
            "type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()},
            "result": {"id": result if ":" in result else "%s:%s" % (MOD, result), "count": count}})
    ing = "%s:mirrorite_ingot" % MOD
    st = "minecraft:stick"
    shaped("mirrorite_sword", ["I", "I", "S"], {"I": ing, "S": st}, "mirrorite_sword")
    shaped("mirrorite_pickaxe", ["III", " S ", " S "], {"I": ing, "S": st}, "mirrorite_pickaxe")
    shaped("mirrorite_axe", ["II", "IS", " S"], {"I": ing, "S": st}, "mirrorite_axe")
    shaped("mirrorite_shovel", ["I", "S", "S"], {"I": ing, "S": st}, "mirrorite_shovel")
    shaped("mirrorite_hoe", ["II", " S", " S"], {"I": ing, "S": st}, "mirrorite_hoe")
    shaped("mirror_bow", [" IS", "I S", " IS"], {"I": ing, "S": "minecraft:string"}, "mirror_bow")
    shaped("mirrorite_block", ["III", "III", "III"], {"I": ing}, "mirrorite_block", cat="building")
    w("data/%s/recipe/mirrorite_ingot_from_block.json" % MOD, {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "%s:mirrorite_block" % MOD}],
        "result": {"id": ing, "count": 9}})
    w("data/%s/recipe/mirror_blade.json" % MOD, {
        "type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": [{"item": "%s:reaver_heart" % MOD}, {"item": "minecraft:iron_sword"}] +
                       [{"item": "minecraft:echo_shard"}] * 3,
        "result": {"id": "%s:mirror_blade" % MOD, "count": 1}})
    for kind, time in (("smelting", 200), ("blasting", 100)):
        for src in ("raw_mirrorite", "mirror_ore"):
            w("data/%s/recipe/mirrorite_ingot_%s_%s.json" % (MOD, kind, src), {
                "type": "minecraft:" + kind, "category": "misc",
                "ingredient": {"item": "%s:%s" % (MOD, src)},
                "result": {"id": ing}, "experience": 1.0, "cookingtime": time})


def data(MOD, w):
    w("data/%s/loot_table/blocks/mirror_ore.json" % MOD, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{
            "type": "minecraft:alternatives",
            "children": [
                {"type": "minecraft:item", "name": "%s:mirror_ore" % MOD, "conditions": [{
                    "condition": "minecraft:match_tool",
                    "predicate": {"predicates": {"minecraft:enchantments": [
                        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
                {"type": "minecraft:item", "name": "%s:raw_mirrorite" % MOD, "functions": [
                    {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                     "formula": "minecraft:ore_drops"},
                    {"function": "minecraft:explosion_decay"}]}]}]}]})
    w("data/%s/loot_table/blocks/mirrorite_block.json" % MOD, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:mirrorite_block" % MOD}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    w("data/minecraft/tags/block/needs_diamond_tool.json", {
        "values": ["%s:mirror_ore" % MOD, "%s:mirrorite_block" % MOD]})
    w("data/%s/worldgen/configured_feature/mirror_ore.json" % MOD, {"type": "%s:mirror_ore" % MOD, "config": {}})
    w("data/%s/worldgen/placed_feature/mirror_ore.json" % MOD, {
        "feature": "%s:mirror_ore" % MOD,
        "placement": [
            {"type": "minecraft:count", "count": 9},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {
                "type": "minecraft:uniform",
                "min_inclusive": {"absolute": -60}, "max_inclusive": {"absolute": 40}}},
            {"type": "minecraft:biome"}]})
    w("data/%s/neoforge/biome_modifier/mirror_ore.json" % MOD, {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": "%s:mirror_ore" % MOD, "step": "underground_ores"})


def models(MOD, w):
    gen, hand = "minecraft:item/generated", "minecraft:item/handheld"
    for n in ("reaver_heart", "raw_mirrorite", "mirrorite_ingot"):
        w("assets/%s/models/item/%s.json" % (MOD, n), {"parent": gen, "textures": {"layer0": "%s:item/%s" % (MOD, n)}})
    for n in ("mirror_blade", "mirrorite_sword", "mirrorite_pickaxe", "mirrorite_axe", "mirrorite_shovel", "mirrorite_hoe"):
        w("assets/%s/models/item/%s.json" % (MOD, n), {"parent": hand, "textures": {"layer0": "%s:item/%s" % (MOD, n)}})
    for b in ("mirror_ore", "mirrorite_block"):
        w("assets/%s/blockstates/%s.json" % (MOD, b), {"variants": {"": {"model": "%s:block/%s" % (MOD, b)}}})
        w("assets/%s/models/block/%s.json" % (MOD, b), {"parent": "minecraft:block/cube_all",
                                                        "textures": {"all": "%s:block/%s" % (MOD, b)}})
        w("assets/%s/models/item/%s.json" % (MOD, b), {"parent": "%s:block/%s" % (MOD, b)})
    display = {
        "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
        "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}}
    for i in range(3):
        w("assets/%s/models/item/mirror_bow_pulling_%d.json" % (MOD, i), {
            "parent": gen, "textures": {"layer0": "%s:item/mirror_bow_pulling_%d" % (MOD, i)}, "display": display})
    w("assets/%s/models/item/mirror_bow.json" % MOD, {
        "parent": gen, "textures": {"layer0": "%s:item/mirror_bow" % MOD}, "display": display,
        "overrides": [
            {"predicate": {"pulling": 1}, "model": "%s:item/mirror_bow_pulling_0" % MOD},
            {"predicate": {"pulling": 1, "pull": 0.65}, "model": "%s:item/mirror_bow_pulling_1" % MOD},
            {"predicate": {"pulling": 1, "pull": 0.9}, "model": "%s:item/mirror_bow_pulling_2" % MOD}]})


EN = {
    "item.darkrifts.reaver_heart": "Reaver Heart",
    "item.darkrifts.mirror_blade": "Mirror Blade",
    "block.darkrifts.mirror_ore": "Mirror Ore",
    "item.darkrifts.raw_mirrorite": "Raw Mirrorite",
    "item.darkrifts.mirrorite_ingot": "Mirrorite Ingot",
    "block.darkrifts.mirrorite_block": "Block of Mirrorite",
    "item.darkrifts.mirrorite_sword": "Mirrorite Sword",
    "item.darkrifts.mirrorite_pickaxe": "Mirrorite Pickaxe",
    "item.darkrifts.mirrorite_axe": "Mirrorite Axe",
    "item.darkrifts.mirrorite_shovel": "Mirrorite Shovel",
    "item.darkrifts.mirrorite_hoe": "Mirrorite Hoe",
    "item.darkrifts.mirror_bow": "Mirror Bow",
    "tooltip.darkrifts.mirror_blade.1": "One use. Draws a single rift into the Dark Mirror.",
    "tooltip.darkrifts.mirror_blade.2": "Hold Right Click, look up and drag your view. The blade shatters when the rift is finished.",
    "tooltip.darkrifts.mirror_bow": "Arrows hit harder and pierce two targets.",
    "message.darkrifts.blade_shattered": "The Mirror Blade shatters",
}
RU = {
    "item.darkrifts.reaver_heart": "Сердце жнеца",
    "item.darkrifts.mirror_blade": "Зеркальный клинок",
    "block.darkrifts.mirror_ore": "Зеркальная руда",
    "item.darkrifts.raw_mirrorite": "Сырой зеркалит",
    "item.darkrifts.mirrorite_ingot": "Слиток зеркалита",
    "block.darkrifts.mirrorite_block": "Блок зеркалита",
    "item.darkrifts.mirrorite_sword": "Зеркалитовый меч",
    "item.darkrifts.mirrorite_pickaxe": "Зеркалитовая кирка",
    "item.darkrifts.mirrorite_axe": "Зеркалитовый топор",
    "item.darkrifts.mirrorite_shovel": "Зеркалитовая лопата",
    "item.darkrifts.mirrorite_hoe": "Зеркалитовая мотыга",
    "item.darkrifts.mirror_bow": "Зеркальный лук",
    "tooltip.darkrifts.mirror_blade.1": "Одноразовый. Рисует один разлом в Тёмное зеркало.",
    "tooltip.darkrifts.mirror_blade.2": "Зажми ПКМ, посмотри вверх и веди взглядом. Когда разлом готов, клинок разбивается.",
    "tooltip.darkrifts.mirror_bow": "Стрелы бьют сильнее и пробивают две цели.",
    "message.darkrifts.blade_shattered": "Зеркальный клинок разбился",
}


def build(RES, MOD, write_json):
    textures(RES, MOD)
    recipes(MOD, write_json)
    data(MOD, write_json)
    models(MOD, write_json)
    for lang, extra in (("en_us", EN), ("ru_ru", RU)):
        path = os.path.join(RES, "assets", MOD, "lang", lang + ".json")
        with open(path, encoding="utf-8") as f:
            d = json.load(f)
        d.update(extra)
        write_json("assets/%s/lang/%s.json" % (MOD, lang), d)
