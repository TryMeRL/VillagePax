#!/usr/bin/env python3
"""
Генерирует ступени, плиты и стены из материалов Village Pax.

Зачем скрипт, а не руки: на каждый производный блок приходится
блокстейт, три-четыре модели, модель предмета, таблица добычи, рецепт
и две строки словаря. Одиннадцать блоков — это под сотню файлов, и все
они отличаются одним словом. Руками их набирают ровно до первой опечатки,
которую потом ищут в игре по отсутствующей текстуре.

    python tools/make-decor.py

Формы взяты у ванили: блокстейт ступеней — те самые сорок вариантов
с поворотами и uvlock, стена — мультичасть с post/side/side_tall. Копия
ванильной формы важнее краткости: любой мод, который умеет ванильные
ступени, умеет и наши.
"""

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/villagepax"
DATA = ROOT / "src/main/resources/data/villagepax"

# Материал: из чего делаем, как называется по-русски и по-английски,
# какие формы у него есть и режется ли он камнерезом.
MATERIALS = [
    {
        "base": "timber_frame",
        "ru": "Фахверк",
        "en": "Timber Frame",
        "forms": ["stairs", "slab"],
        "cut": False,
    },
    {
        "base": "plaster",
        "ru": "Штукатурка",
        "en": "Plaster",
        "forms": ["stairs", "slab"],
        "cut": True,
    },
    {
        "base": "ochre_plaster",
        "ru": "Охряная штукатурка",
        "en": "Ochre Plaster",
        "forms": ["stairs", "slab"],
        "cut": True,
    },
    {
        "base": "carved_stone",
        "ru": "Резной камень",
        "en": "Carved Stone",
        "forms": ["stairs", "slab", "wall"],
        "cut": True,
    },
    {
        "base": "thatch",
        "ru": "Пальмовая кровля",
        "en": "Thatch",
        "forms": ["stairs", "slab"],
        "cut": False,
    },
]

FORM_RU = {"stairs": "ступени", "slab": "плита", "wall": "стена"}
FORM_EN = {"stairs": "Stairs", "slab": "Slab", "wall": "Wall"}


def write(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


# --- модели ---

def stair_models(name, texture):
    """Три модели ступеней: прямая, внутренний угол, внешний."""
    sides = {"bottom": texture, "top": texture, "side": texture}
    return {
        name: {"parent": "minecraft:block/stairs", "textures": sides},
        name + "_inner": {"parent": "minecraft:block/inner_stairs", "textures": sides},
        name + "_outer": {"parent": "minecraft:block/outer_stairs", "textures": sides},
    }


def slab_models(name, texture, base):
    sides = {"bottom": texture, "top": texture, "side": texture}
    return {
        name: {"parent": "minecraft:block/slab", "textures": sides},
        name + "_top": {"parent": "minecraft:block/slab_top", "textures": sides},
    }


def wall_models(name, texture):
    wall = {"wall": texture}
    return {
        name + "_post": {"parent": "minecraft:block/template_wall_post", "textures": wall},
        name + "_side": {"parent": "minecraft:block/template_wall_side", "textures": wall},
        name + "_side_tall": {"parent": "minecraft:block/template_wall_side_tall",
                              "textures": wall},
        name + "_inventory": {"parent": "minecraft:block/wall_inventory", "textures": wall},
    }


# --- блокстейты ---

def stair_state(name):
    """Сорок вариантов ванильных ступеней: поворот решает всё."""
    model = "villagepax:block/" + name
    inner = model + "_inner"
    outer = model + "_outer"

    # (facing, shape) -> (модель, поворот вокруг Y)
    layout = {
        ("east", "straight"): (model, 0),
        ("west", "straight"): (model, 180),
        ("south", "straight"): (model, 90),
        ("north", "straight"): (model, 270),
        ("east", "outer_right"): (outer, 0),
        ("east", "outer_left"): (outer, 270),
        ("west", "outer_right"): (outer, 180),
        ("west", "outer_left"): (outer, 90),
        ("south", "outer_right"): (outer, 90),
        ("south", "outer_left"): (outer, 0),
        ("north", "outer_right"): (outer, 270),
        ("north", "outer_left"): (outer, 180),
        ("east", "inner_right"): (inner, 0),
        ("east", "inner_left"): (inner, 270),
        ("west", "inner_right"): (inner, 180),
        ("west", "inner_left"): (inner, 90),
        ("south", "inner_right"): (inner, 90),
        ("south", "inner_left"): (inner, 0),
        ("north", "inner_right"): (inner, 270),
        ("north", "inner_left"): (inner, 180),
    }

    variants = {}
    for (facing, shape), (which, turn) in layout.items():
        for half in ("bottom", "top"):
            key = "facing=%s,half=%s,shape=%s" % (facing, half, shape)
            variant = {"model": which}
            if half == "top":
                variant["x"] = 180
                # Перевёрнутая ступень поворачивается на четверть дальше:
                # так делает ваниль, и так углы сходятся друг с другом.
                extra = {"straight": 0, "outer_right": 90, "outer_left": 0,
                         "inner_right": 90, "inner_left": 0}[shape]
                turned = (turn + extra) % 360
            else:
                turned = turn
            if turned:
                variant["y"] = turned
            if half == "top" or turned:
                variant["uvlock"] = True
            variants[key] = variant
    return {"variants": variants}


def slab_state(name, base):
    return {"variants": {
        "type=bottom": {"model": "villagepax:block/" + name},
        "type=top": {"model": "villagepax:block/" + name + "_top"},
        "type=double": {"model": "villagepax:block/" + base},
    }}


def wall_state(name):
    model = "villagepax:block/" + name
    parts = [{"when": {"up": "true"}, "apply": {"model": model + "_post"}}]
    for side, turn in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for tall, suffix in (("low", "_side"), ("tall", "_side_tall")):
            apply = {"model": model + suffix, "uvlock": True}
            if turn:
                apply["y"] = turn
            parts.append({"when": {side: tall}, "apply": apply})
    return {"multipart": parts}


# --- добыча и рецепты ---

def simple_drop(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "villagepax:" + name}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


def slab_drop(name):
    """Двойная плита роняет две: иначе половина блока пропадает."""
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{
                "type": "minecraft:item",
                "name": "villagepax:" + name,
                "functions": [{
                    "function": "minecraft:set_count",
                    "count": 2,
                    "conditions": [{
                        "condition": "minecraft:block_state_property",
                        "block": "villagepax:" + name,
                        "properties": {"type": "double"},
                    }],
                }, {"function": "minecraft:explosion_decay"}],
            }],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


def shaped(pattern, base, result, count):
    return {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": pattern,
        "key": {"#": {"item": "villagepax:" + base}},
        "result": {"item": "villagepax:" + result, "count": count},
    }


def stonecut(base, result, count):
    return {
        "type": "minecraft:stonecutting",
        "ingredient": {"item": "villagepax:" + base},
        "result": "villagepax:" + result,
        "count": count,
    }


def main():
    lang_ru = {}
    lang_en = {}
    made = []

    for material in MATERIALS:
        base = material["base"]
        texture = "villagepax:block/" + base

        for form in material["forms"]:
            name = base + "_" + form
            made.append(name)

            if form == "stairs":
                models = stair_models(name, texture)
                write(ASSETS / "blockstates" / (name + ".json"), stair_state(name))
                write(ASSETS / "models/item" / (name + ".json"),
                      {"parent": "villagepax:block/" + name})
                write(DATA / "loot_tables/blocks" / (name + ".json"), simple_drop(name))
                write(DATA / "recipes" / (name + ".json"),
                      shaped(["#  ", "## ", "###"], base, name, 4))
            elif form == "slab":
                models = slab_models(name, texture, base)
                write(ASSETS / "blockstates" / (name + ".json"), slab_state(name, base))
                write(ASSETS / "models/item" / (name + ".json"),
                      {"parent": "villagepax:block/" + name})
                write(DATA / "loot_tables/blocks" / (name + ".json"), slab_drop(name))
                write(DATA / "recipes" / (name + ".json"),
                      shaped(["###"], base, name, 6))
            else:
                models = wall_models(name, texture)
                write(ASSETS / "blockstates" / (name + ".json"), wall_state(name))
                write(ASSETS / "models/item" / (name + ".json"),
                      {"parent": "villagepax:block/" + name + "_inventory"})
                write(DATA / "loot_tables/blocks" / (name + ".json"), simple_drop(name))
                write(DATA / "recipes" / (name + ".json"),
                      shaped(["###", "###"], base, name, 6))

            for model_name, payload in models.items():
                write(ASSETS / "models/block" / (model_name + ".json"), payload)

            if material["cut"]:
                write(DATA / "recipes" / (name + "_from_stonecutting.json"),
                      stonecut(base, name, 2 if form == "slab" else 1))

            lang_ru["block.villagepax." + name] = "%s: %s" % (material["ru"], FORM_RU[form])
            lang_en["block.villagepax." + name] = "%s %s" % (material["en"], FORM_EN[form])

    print("Сделано блоков: %d" % len(made))
    for name in made:
        print("  " + name)

    print("\nСтроки словаря (вставить в ru_ru.json и en_us.json):")
    print(json.dumps(lang_ru, ensure_ascii=False, indent=2))
    print(json.dumps(lang_en, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
