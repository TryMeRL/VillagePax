#!/usr/bin/env python3
"""Пишет модели мебели и алтаря: скамья, стол, игорный стол, полка, алтарь.

Зачем кодом: модель мебели — это два десятка брусков, и у каждого есть
смысл (ножка, царга, проножка, спинка). Числами в JSON этот смысл
теряется, и через месяц уже не скажешь, какой брусок — перекладина.
Здесь бруски названы, а развёртка по умолчанию берётся по положению,
как у ванили, — поэтому доска на доске выглядит доской, а не мозаикой.

Лицо у всей мебели — север: блокстейт поворачивает модель по взгляду
поставившего, и полка висит на южной стене клетки, глядя на север.

Смотреть результат — `python tools/preview-models.py bench table shelf altar`.

    python tools/make-furniture.py
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "src/main/resources/assets/villagepax/models/block"

FACES = ("north", "south", "east", "west", "up", "down")


def box(name, start, end, texture, faces=FACES, uv=None, cull=None, rotation=None):
    """Брусок: грани по умолчанию все, развёртка — по положению (ваниль сама).

    `uv` — развёртка для отдельных граней {грань: [u1, v1, u2, v2]},
    `cull` — грани, которые прячутся за соседним полным блоком.
    """
    element = {"__comment": name, "from": list(start), "to": list(end), "faces": {}}
    for face in faces:
        spec = {"texture": texture if isinstance(texture, str) else texture.get(face, texture["*"])}
        if uv and face in uv:
            spec["uv"] = uv[face]
        if cull and face in cull:
            spec["cullface"] = face
        element["faces"][face] = spec
    if rotation:
        element["rotation"] = rotation
    return element


def model(textures, elements, parent="minecraft:block/block", ao=True):
    out = {"parent": parent, "textures": textures}
    if not ao:
        out["ambientocclusion"] = False
    out["elements"] = elements
    return out


# --- скамья ---------------------------------------------------------------------
#
# Сиденье из доски, четыре ножки, спинка на задних ножках с тремя
# балясинами. След прежний (z от 4 до 12): по нему ходит поиск пути.

def bench():
    planks, post = "#planks", "#post"
    legs = []
    for x in (1, 13.5):
        legs.append(box("передняя ножка", (x, 0, 5), (x + 1.5, 7, 6.5), post, cull=("down",)))
        legs.append(box("задняя ножка и стойка спинки", (x, 0, 9.5), (x + 1.5, 15.5, 11),
                        {"*": post, "up": "#post_top"}, cull=("down",)))
        legs.append(box("проножка", (x + 0.25, 2, 6.5), (x + 1.25, 3, 9.5), post,
                        faces=("east", "west", "up", "down")))
    spindles = [box("балясина спинки", (x, 11.5, 10), (x + 1, 13, 10.5), post,
                    faces=("north", "south", "east", "west"))
                for x in (4.5, 7.5, 10.5)]
    return model(
        {"planks": "minecraft:block/oak_planks", "post": "minecraft:block/stripped_oak_log",
         "post_top": "minecraft:block/stripped_oak_log_top",
         "particle": "minecraft:block/oak_planks"},
        [box("сиденье", (0, 7, 4.5), (16, 8.5, 11.5), planks),
         box("царга под сиденьем", (0.5, 6, 4.75), (15.5, 7, 5.75), planks,
             faces=("north", "south", "down", "east", "west")),
         *legs,
         box("перекладина спинки", (2.5, 13, 9.75), (13.5, 15, 10.75), planks),
         box("нижняя перекладина спинки", (2.5, 10, 9.75), (13.5, 11.5, 10.75), planks),
         *spindles])


# --- стол -----------------------------------------------------------------------
#
# Столешница в два пальца, царга по кругу, точёные ножки и проножка
# буквой Н у пола: без неё стол читается табуретом-переростком.

def table():
    planks, post = "#planks", "#post"
    legs = [box("ножка", (x, 0, z), (x + 2, 14, z + 2), {"*": post, "down": "#post_top"},
                faces=("north", "south", "east", "west", "down"), cull=("down",))
            for x in (1.5, 12.5) for z in (1.5, 12.5)]
    apron = [
        box("царга", (3.5, 12, 1.75), (12.5, 14, 2.75), planks, faces=("north", "south", "down")),
        box("царга", (3.5, 12, 13.25), (12.5, 14, 14.25), planks, faces=("north", "south", "down")),
        box("царга", (1.75, 12, 3.5), (2.75, 14, 12.5), planks, faces=("east", "west", "down")),
        box("царга", (13.25, 12, 3.5), (14.25, 14, 12.5), planks, faces=("east", "west", "down")),
    ]
    stretchers = [
        box("проножка", (2, 3, 3.5), (3, 4, 12.5), post, faces=("east", "west", "up", "down")),
        box("проножка", (13, 3, 3.5), (14, 4, 12.5), post, faces=("east", "west", "up", "down")),
        box("средник", (3, 3, 7.5), (13, 4, 8.5), post, faces=("north", "south", "up", "down")),
    ]
    return model(
        {"planks": "minecraft:block/oak_planks", "post": "minecraft:block/stripped_oak_log",
         "post_top": "minecraft:block/stripped_oak_log_top",
         "particle": "minecraft:block/oak_planks"},
        [box("столешница", (0, 14, 0), (16, 16, 16), planks, cull=("up",)),
         *apron, *legs, *stretchers])


# --- игорный стол ---------------------------------------------------------------
#
# Тот же стол, а на столешнице — две кости и две кружки: за ним по вечерам
# играют, и это видно раньше, чем кто-нибудь бросит кость. Кости — диорит:
# светлый камень в тёмную крапину, и на двух текселях крапина читается
# очком. Кружки — бочарная клёпка с обручем, как у пивовара. Вторая кость
# повёрнута: две кости ровно в ряд лежат не брошенными, а выставленными.

def game_table():
    table_model = table()
    die, mug, mug_top = "#die", "#mug", "#mug_top"
    table_model["textures"].update({"die": "minecraft:block/diorite",
                                    "mug": "minecraft:block/barrel_side",
                                    "mug_top": "minecraft:block/barrel_top"})
    thrown = box("кость, брошенная наискось", (8.5, 16, 8.5), (10.5, 18, 10.5), die,
                 faces=("north", "south", "east", "west", "up"))
    thrown["rotation"] = {"origin": [9.5, 16, 9.5], "axis": "y", "angle": 22.5}
    table_model["elements"] += [
        box("кость", (5.5, 16, 6), (7.5, 18, 8), die, faces=("north", "south", "east", "west", "up")),
        thrown,
        box("кружка", (2.5, 16, 11), (5.5, 20, 14), {"*": mug, "up": mug_top},
            faces=("north", "south", "east", "west", "up")),
        box("кружка", (11, 16, 2.5), (14, 20, 5.5), {"*": mug, "up": mug_top},
            faces=("north", "south", "east", "west", "up")),
    ]
    return table_model


# --- полка ----------------------------------------------------------------------
#
# Открытая полка на южной стене: боковины, две доски и верх. На досках —
# то, что в доме держат на виду: корешки книг, горшки, миска. Вещи —
# часть модели, а не предметы: полка в схеме должна выглядеть обжитой
# сразу, а не когда игрок её обставит.

def shelf():
    planks = "#planks"
    books = "#books"
    cull_south = ("south",)
    return model(
        {"planks": "minecraft:block/oak_planks", "books": "minecraft:block/bookshelf",
         "pot": "minecraft:block/terracotta", "pot_rim": "minecraft:block/brown_terracotta",
         "particle": "minecraft:block/oak_planks"},
        [box("боковина", (0, 0, 11), (1, 16, 16), planks, cull=("south", "down", "up", "west")),
         box("боковина", (15, 0, 11), (16, 16, 16), planks, cull=("south", "down", "up", "east")),
         box("верх", (1, 15, 11), (15, 16, 16), planks, faces=("north", "up", "down", "south"),
             cull=("up", "south")),
         box("нижняя доска", (1, 3, 11), (15, 4, 16), planks,
             faces=("north", "up", "down", "south"), cull=cull_south),
         box("верхняя доска", (1, 9, 11), (15, 10, 16), planks,
             faces=("north", "up", "down", "south"), cull=cull_south),
         # Внизу — ряд книг: корешки с ванильного шкафа, лицом наружу.
         box("книги", (1.5, 4, 12), (8.5, 9, 15.5), books,
             faces=("north", "east", "up"),
             uv={"north": [1, 1, 8, 6], "east": [1, 1, 4.5, 6], "up": [1, 1, 8, 4.5]}),
         box("книга наискось", (8.6, 4, 12.5), (9.6, 8.5, 15.5), books,
             faces=("north", "east", "west", "up"),
             uv={"north": [9, 1, 10, 5.5], "east": [9, 1, 12, 5.5], "west": [9, 1, 12, 5.5],
                 "up": [9, 1, 10, 4]},
             rotation={"origin": [9.1, 4, 14], "axis": "z", "angle": -22.5}),
         # Справа внизу горшок, вверху два горшка и миска.
         box("горшок", (11, 4, 12.5), (14, 7.5, 15.5), {"*": "#pot", "up": "#pot_rim"},
             faces=("north", "east", "west", "up")),
         box("горшок", (2, 10, 12.5), (4.5, 13, 15), {"*": "#pot", "up": "#pot_rim"},
             faces=("north", "east", "west", "up")),
         box("горшок", (5.5, 10, 13), (7.5, 12, 15), {"*": "#pot", "up": "#pot_rim"},
             faces=("north", "east", "west", "up")),
         box("миска", (9.5, 10, 12.5), (13.5, 11, 15.5), "#planks",
             faces=("north", "east", "west", "up"),
             uv={"up": [2, 2, 6, 5]})])


# --- алтарь ---------------------------------------------------------------------
#
# Цоколь, тумба, карниз и стол с рогами по углам — силуэт жертвенника,
# который узнают по любой книжке о древности. На столе — чаша с углём
# и две свечи: алтарь светится, и свет должен откуда-то идти.
# Свечи ванильные, их огонёк — частица, как у ванильной свечи
# (см. AltarBlock#randomDisplayTick): места фитилей — постоянные ниже.

# Центры свечей: два угла стола вместо рогов, накрест.
CANDLES = ((2, 2), (14, 14))


def altar():
    side, top = "#side", "#top"
    stone = "#stone"
    horns = [box("рог", (x - 1, 14.5, z - 1), (x + 1, 16, z + 1), stone,
                 faces=("north", "south", "east", "west", "up"))
             for x, z in ((2, 2), (14, 2), (2, 14), (14, 14)) if (x, z) not in CANDLES]
    bowl = [
        box("стенка чаши", (5, 14.5, 5), (11, 15.75, 6), stone,
            faces=("north", "south", "east", "west", "up")),
        box("стенка чаши", (5, 14.5, 10), (11, 15.75, 11), stone,
            faces=("north", "south", "east", "west", "up")),
        box("стенка чаши", (5, 14.5, 6), (6, 15.75, 10), stone, faces=("east", "west", "up")),
        box("стенка чаши", (10, 14.5, 6), (11, 15.75, 10), stone, faces=("east", "west", "up")),
        box("угли", (6, 14.5, 6), (10, 15.25, 10), "#embers", faces=("up",),
            uv={"up": [6, 6, 10, 10]}),
    ]
    candles = []
    for cx, cz in CANDLES:
        sides = {face: [0, 8, 2, 11] for face in ("north", "south", "east", "west")}
        candles.append(box("свеча", (cx - 1, 14.5, cz - 1), (cx + 1, 17.5, cz + 1), "#candle",
                           faces=("north", "south", "east", "west", "up"),
                           uv=sides | {"up": [0, 6, 2, 8]}))
        # Фитиль — две скрещённые плоскости, как у ванильной свечи.
        for angle in (45, -45):
            candles.append(box("фитиль", (cx - 0.5, 17.5, cz), (cx + 0.5, 18.5, cz), "#candle",
                               faces=("north", "south"),
                               uv={"north": [0, 5, 1, 6], "south": [0, 5, 1, 6]},
                               rotation={"origin": [cx, 17.5, cz], "axis": "y", "angle": angle}))
    return model(
        {"side": "villagepax:block/altar_side", "top": "villagepax:block/altar_top",
         "stone": "minecraft:block/polished_andesite", "embers": "villagepax:block/altar_top",
         "candle": "minecraft:block/candle_lit", "particle": "villagepax:block/altar_side"},
        [box("цоколь", (1, 0, 1), (15, 2, 15), stone, cull=("down",)),
         box("тумба", (2.5, 2, 2.5), (13.5, 11, 13.5), side, faces=("north", "south", "east", "west")),
         box("карниз", (1.5, 11, 1.5), (14.5, 12.5, 14.5), stone),
         box("стол", (0.5, 12.5, 0.5), (15.5, 14.5, 15.5), {"*": stone, "up": top}),
         *horns, *bowl, *candles])


# --- бумажный фонарик -----------------------------------------------------------
#
# Бочонок красной бумаги между двумя золотыми донцами; висячий поднят
# под потолок и держится на шнурке. Развёртка — один лист
# (block/paper_lantern): бок, донце и шнурок рядом.

def paper_lantern(hanging):
    lift = 5 if hanging else 0
    paper = {"*": "#paper"}
    side = {"north": [0, 0, 6, 7], "south": [0, 0, 6, 7], "east": [0, 0, 6, 7],
            "west": [0, 0, 6, 7], "up": [6, 0, 10, 4], "down": [6, 0, 10, 4]}
    cap = {face: [6, 0, 10, 4] for face in FACES}
    elements = [
        box("бумажный бочонок", (5, 1 + lift, 5), (11, 8 + lift, 11), paper, uv=side),
        box("нижнее донце", (6, lift, 6), (10, 1 + lift, 10), paper, uv=cap),
        box("верхнее донце", (6, 8 + lift, 6), (10, 9 + lift, 10), paper, uv=cap),
    ]
    if hanging:
        cord = {face: [10, 0, 11, 5] for face in FACES}
        elements.append(box("шнурок", (7.5, 14, 7.5), (8.5, 16, 8.5), paper, uv=cord,
                            faces=("north", "south", "east", "west")))
    return model({"paper": "villagepax:block/paper_lantern",
                  "particle": "villagepax:block/paper_lantern"}, elements, ao=False)


# --- цветочный ящик -----------------------------------------------------------
#
# Ящик из еловой доски, земля вровень с краем и три цветка крест-накрест:
# мак, одуванчик, василёк. Ящик прижат к дальней стороне клетки — к стене,
# под окно.

def flower_box():
    planks = "#planks"
    elements = [box("ящик", (0, 0, 9), (16, 6, 16), planks, cull=("south", "down")),
                box("земля", (1, 6, 10), (15, 6.5, 15), "#soil", faces=("up",))]
    for x, texture in ((3.5, "#poppy"), (8, "#dandelion"), (12.5, "#cornflower")):
        for angle in (45, -45):
            # Развёртка — сам цветок без пустых полей ванильной текстуры:
            # иначе на плоскость в пять текселей ложилась вся клетка 16x16,
            # и цветок выходил с ноготь.
            element = box("цветок", (x - 2.5, 6, 12.5), (x + 2.5, 14, 12.5), texture,
                          faces=("north", "south"),
                          uv={"north": [4, 4, 12, 16], "south": [4, 4, 12, 16]})
            element["rotation"] = {"origin": [x, 6, 12.5], "axis": "y", "angle": angle}
            element["shade"] = False
            elements.append(element)
    return model({"planks": "minecraft:block/spruce_planks",
                  "soil": "minecraft:block/rooted_dirt",
                  "poppy": "minecraft:block/poppy",
                  "dandelion": "minecraft:block/dandelion",
                  "cornflower": "minecraft:block/cornflower",
                  "particle": "minecraft:block/spruce_planks"}, elements)


# --- верстовой столб ------------------------------------------------------------
#
# Еловый столб в рост и три указателя на разной высоте, повёрнутые в
# разные стороны: так столб читается развилкой, а не забором.

def signpost():
    post = {"*": "#post", "up": "#post_top", "down": "#post_top"}
    elements = [box("столб", (7, 0, 7), (9, 16, 9), post, cull=("down",))]
    for low, angle, reach in ((12, 0, 15), (8, 45, 14), (4, -45, 14)):
        # Доска в три текселя шириной: уже — и указатель читается сучком.
        board = box("указатель", (8, low, 7.5), (reach, low + 3, 8.5), "#board")
        board["rotation"] = {"origin": [8, low, 8], "axis": "y", "angle": angle}
        tip = box("остриё", (reach, low + 0.75, 7.5), (reach + 1, low + 2.25, 8.5), "#board")
        tip["rotation"] = {"origin": [8, low, 8], "axis": "y", "angle": angle}
        elements += [board, tip]
    return model({"post": "minecraft:block/stripped_spruce_log",
                  "post_top": "minecraft:block/stripped_spruce_log_top",
                  "board": "minecraft:block/spruce_planks",
                  "particle": "minecraft:block/spruce_planks"}, elements)


def main():
    for name, build in (("bench", bench), ("table", table), ("game_table", game_table),
                        ("shelf", shelf), ("altar", altar),
                        ("paper_lantern", lambda: paper_lantern(False)),
                        ("paper_lantern_hanging", lambda: paper_lantern(True)),
                        ("flower_box", flower_box), ("signpost", signpost)):
        path = MODELS / (name + ".json")
        path.write_text(json.dumps(build(), indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8", newline="\n")
        print("модель:", path.name)


if __name__ == "__main__":
    main()
