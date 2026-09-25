#!/usr/bin/env python3
"""Диковинки народов: блоки с характером и обереги.

Заказчик: «создавай свои блоки, не стесняйся, всю фантазию; больше
амулетов и всяких таких штук». Каждая диковинка — вещь с делом,
а не просто модель: торо светит в саду, фурин звенит на ветру, рунный
камень рассказывает сагу, барабан поднимает на марш, идол ягуара ночью
подсвечивает врагов, фонтан пони даёт воду, флюгер предсказывает дождь,
календарь майя знает фазу луны, кадильница делает жертву весомее,
пугало гоняет кроликов с грядок. Обереги носят в левой руке.

Всё — текстуры, модели, блокстейты, добыча, рецепты, названия и теги —
пишется отсюда, как и остальной арт мода.

    python tools/make-wonders.py
"""

import importlib.util
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/villagepax"
DATA = ROOT / "src/main/resources/data/villagepax"
TAGS = ROOT / "src/main/resources/data/minecraft/tags/blocks"

_spec = importlib.util.spec_from_file_location("textures", HERE / "make-textures.py")
tx = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(tx)
Tex, rgb, CLEAR = tx.Tex, tx.rgb, tx.CLEAR

FACES = ("north", "south", "east", "west", "up", "down")


# --- бруски ---------------------------------------------------------------------


def box(start, end, texture, faces=FACES, uv=None, rotation=None, tint=None):
    element = {"from": list(start), "to": list(end), "faces": {}}
    for face in faces:
        tex = texture if isinstance(texture, str) else texture.get(face, texture["*"])
        if tex is None:
            continue
        spec = {"texture": tex}
        if uv and face in uv:
            spec["uv"] = uv[face]
        element["faces"][face] = spec
    if rotation:
        element["rotation"] = rotation
    return element


def model(textures, elements, ao=True):
    out = {"parent": "minecraft:block/block", "textures": textures}
    if not ao:
        out["ambientocclusion"] = False
    out["elements"] = elements
    # Держится в руке и в рамке так же, как ванильный блок.
    out["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                  "scale": [0.375] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                  "scale": [0.4] * 3},
    }
    return out


# --- краски -----------------------------------------------------------------------

PAPER = rgb(0xF3E7C4)
PAPER_WARM = rgb(0xFFD98A)
PAPER_GLOW = rgb(0xFFF1C4)
STONE_D, STONE, STONE_L = rgb(0x6B6B66), rgb(0x8A8A84), rgb(0xA3A39B)
RUNE, RUNE_LIT = rgb(0x3F8FE0), rgb(0x9FD8FF)
JADE, JADE_LIT, JADE_D = rgb(0x2FA67A), rgb(0x7FE0B2), rgb(0x1E6E50)
GOLD, GOLD_L, GOLD_D = rgb(0xE0B02C), rgb(0xF6D96A), rgb(0xA07818)
SILVER, SILVER_L, SILVER_D = rgb(0xC3C9CE), rgb(0xE8EDF1), rgb(0x8E949A)
LEATHER, LEATHER_L, LEATHER_D = rgb(0xC9A57A), rgb(0xE0C497), rgb(0x9C7A52)
ROPE = rgb(0x8A6A3A)
RED = rgb(0xC0392B)
CORD = rgb(0x6E4A2A)
WATER, WATER_L, WATER_D = rgb(0x4FA8E8), rgb(0xA8E0FF), rgb(0x2E78C0)
CYAN, CYAN_L, CYAN_D = rgb(0x57E1E6), rgb(0xC8FAFF), rgb(0x2E9AA8)
RAINBOW = [rgb(v) for v in (0xE8413C, 0xF39A2B, 0xF6D83A, 0x5CC85A, 0x3AA0E8, 0x8D5AD8)]


def toro_light():
    """Окно торо: тёплая бумага в каменной раме, сквозь неё — огонь."""
    t = Tex(PAPER_WARM)
    t.rect(0, 0, 15, 1, STONE)
    t.rect(0, 14, 15, 15, STONE_D)
    t.rect(0, 0, 1, 15, STONE)
    t.rect(14, 0, 15, 15, STONE_D)
    t.rect(7, 2, 8, 13, STONE)
    for y in range(3, 13):
        for x in (3, 4, 10, 11):
            t.set(x, y, PAPER_GLOW)
    t.dots(rgb(0xFFB84A), (5, 9), (6, 10), (9, 8), (12, 10))
    return t


def rune_face():
    """Камень с рунами: борозды светятся синим, как иней на лунном свету."""
    t = Tex(STONE)
    for (x, y) in [(2, 3), (11, 5), (5, 12), (13, 13), (3, 9)]:
        t.set(x, y, STONE_L)
    for (x, y) in [(9, 2), (4, 6), (12, 10), (7, 14)]:
        t.set(x, y, STONE_D)
    # Руны столбцом, как на рунических камнях: ᚱ, ᛉ, ᛟ.
    glyphs = [
        ["XX.", "X.X", "XX.", "X.X", "X.X"],
        ["X.X.X", ".XXX.", "..X..", "..X..", "..X.."],
        [".X.", "X.X", ".X.", "X.X", "X.X"],
    ]
    oy = 1
    for glyph in glyphs:
        ox = 8 - len(glyph[0]) // 2
        for dy, row in enumerate(glyph):
            for dx, ch in enumerate(row):
                if ch == "X":
                    t.set(ox + dx, oy + dy, RUNE)
        oy += 5
    t.dots(RUNE_LIT, (7, 1), (8, 7), (7, 12))
    return t


def drum_head():
    """Кожа барабана: натянута, по краю — шнуровка."""
    t = Tex(LEATHER)
    t.ring(7.5, 7.5, 7.6, 6.2, LEATHER_D)
    t.disc(7.5, 7.5, 2.2, LEATHER_L)
    for (x, y) in [(3, 5), (11, 4), (5, 11), (10, 12)]:
        t.set(x, y, LEATHER_L)
    return t


def drum_side():
    """Бок барабана: тёмное дерево и косая шнуровка между обручами."""
    t = Tex(rgb(0x5A3A24))
    t.rect(0, 0, 15, 1, rgb(0x3E2616))
    t.rect(0, 14, 15, 15, rgb(0x3E2616))
    for x in range(0, 16, 4):
        t.line(x, 2, x + 3, 13, ROPE)
        t.line(x + 3, 2, x, 13, ROPE)
    return t


def jaguar_face():
    """Морда ягуара из резного камня: нефритовые глаза и клыки."""
    base, dark, lit = rgb(0xB9A780), rgb(0x8C7B58), rgb(0xD8C8A0)
    t = Tex(base)
    t.rect(0, 0, 15, 0, dark)
    t.rect(1, 3, 6, 6, dark)
    t.rect(9, 3, 14, 6, dark)
    t.rect(2, 4, 5, 5, JADE)
    t.rect(10, 4, 13, 5, JADE)
    t.dots(JADE_LIT, (3, 4), (11, 4))
    t.rect(6, 7, 9, 9, dark)
    t.rect(3, 11, 12, 12, dark)
    t.dots(rgb(0xF2ECDC), (4, 12), (5, 13), (10, 13), (11, 12))
    for (x, y) in [(2, 9), (13, 9), (1, 14), (14, 14)]:
        t.set(x, y, lit)
    return t


def fountain_water():
    """Вода в чаше: голубая рябь с бликами."""
    t = Tex(WATER)
    for y in range(16):
        for x in range(16):
            if (x + y * 2) % 7 == 0:
                t.set(x, y, WATER_L)
            elif (x * 3 + y) % 11 == 0:
                t.set(x, y, WATER_D)
    return t


def fountain_rim():
    """Край чаши пони: белый мрамор с радужной каймой."""
    t = Tex(rgb(0xF4EEF2))
    for x in range(16):
        t.set(x, 0, RAINBOW[(x // 3) % len(RAINBOW)])
        t.set(x, 15, rgb(0xD8CCD4))
    for (x, y) in [(3, 5), (11, 8), (6, 12)]:
        t.set(x, y, rgb(0xE2D6DE))
    return t


def dwarf_crystal():
    """Кристалл гномьей лампы: бирюза с внутренним светом."""
    t = Tex(CYAN)
    for y in range(16):
        for x in range(16):
            if (x + y) % 5 == 0:
                t.set(x, y, CYAN_L)
            elif (x - y) % 6 == 0:
                t.set(x, y, CYAN_D)
    return t


def fireflies():
    """Светлячки в банке: жёлто-зелёные огоньки на прозрачном."""
    t = Tex(CLEAR)
    for (x, y) in [(3, 4), (10, 2), (7, 8), (12, 11), (4, 12), (9, 14), (13, 6)]:
        t.set(x, y, rgb(0xE8FF6A))
        t.set(x + 1, y, rgb(0xB8E040))
    return t


def calendar_face():
    """Каменный календарь майя: круг дней вокруг лика солнца."""
    stone, dark, lit = rgb(0xB9A780), rgb(0x7C6C4C), rgb(0xD8C8A0)
    t = Tex(CLEAR)
    t.disc(7.5, 7.5, 7.6, stone)
    t.ring(7.5, 7.5, 7.6, 6.6, dark)
    t.ring(7.5, 7.5, 5.4, 4.6, dark)
    for angle in range(0, 360, 30):
        x = 7.5 + math.cos(math.radians(angle)) * 6.0
        y = 7.5 + math.sin(math.radians(angle)) * 6.0
        t.set(int(round(x)), int(round(y)), JADE)
    t.disc(7.5, 7.5, 2.6, GOLD)
    t.dots(dark, (6, 7), (9, 7), (7, 9), (8, 9))
    t.dots(lit, (6, 6), (7, 5))
    return t


def chime_glass():
    """Колокол фурина: тонкое стекло с нарисованной золотой рыбкой.

    Полупрозрачное: ванильное стекло почти целиком прозрачно, и колокол
    из него в мире пропадал — видно было только нитку и язычок.
    """
    glass = (214, 238, 246, 150)
    t = Tex(glass)
    t.rect(0, 0, 15, 0, (240, 250, 255, 200))
    t.rect(0, 0, 0, 15, (240, 250, 255, 170))
    t.rect(4, 7, 9, 9, (226, 72, 52, 235))
    t.dots((226, 72, 52, 235), (10, 6), (10, 10), (11, 8), (3, 8))
    t.dots((255, 214, 120, 240), (5, 7), (6, 8))
    t.rect(12, 3, 13, 3, (70, 140, 210, 220))
    t.rect(2, 12, 4, 12, (70, 140, 210, 220))
    return t


def jar_glass():
    """Стекло банки: дымка и блики по краю — чтобы банку было видно."""
    t = Tex((228, 240, 236, 64))
    t.rect(0, 0, 15, 0, (245, 250, 248, 170))
    t.rect(0, 0, 0, 15, (245, 250, 248, 150))
    t.rect(15, 0, 15, 15, (200, 214, 210, 120))
    t.rect(2, 2, 2, 9, (255, 255, 255, 190))
    return t


def chime_paper():
    """Бумажный язычок фурина с мазком туши."""
    t = Tex(PAPER)
    t.rect(0, 0, 15, 1, RED)
    t.line(5, 4, 9, 12, rgb(0x2A2A30))
    t.line(6, 4, 10, 12, rgb(0x2A2A30))
    t.line(9, 6, 6, 9, rgb(0x2A2A30))
    return t


def rooster():
    """Петушок флюгера: золотой силуэт на прозрачном."""
    rows = [
        "................",
        "......GG........",
        ".....GGGG.......",
        "....GGGGGR......",
        "....GGGGG.......",
        "......GGG....GG.",
        ".....GGGGG..GGG.",
        "....GGGGGGGGGGG.",
        "...GGGGGGGGGGG..",
        "....GGGGGGGGGG..",
        ".....GGGGGGGG...",
        "........G.G.....",
        "........G.G.....",
        "IIIIIIIIIIIIIIII",
        "................",
        "................",
    ]
    t = Tex(CLEAR)
    t.sprite([row.replace(".", " ") for row in rows],
             {"G": GOLD, "R": RED, "I": rgb(0x3A3A40)})
    for (x, y) in [(5, 2), (6, 4), (7, 7), (9, 8)]:
        t.set(x, y, GOLD_L)
    return t


def scarecrow_shirt():
    """Рубаха пугала: латаная мешковина с заплатой."""
    t = Tex(rgb(0xB49A63))
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                t.set(x, y, rgb(0xA08850))
    t.rect(3, 4, 7, 8, rgb(0x6E88A6))
    t.rect(10, 9, 13, 13, rgb(0xB03A32))
    t.line(3, 4, 7, 4, rgb(0x5A4A2A))
    return t


def incense_smoke():
    """Угли в чаше кадильницы."""
    t = Tex(rgb(0x3A2A20))
    t.dots(rgb(0xE88A3A), (4, 5), (9, 3), (11, 9), (6, 11), (8, 7))
    t.dots(rgb(0xFFD06A), (5, 5), (8, 8))
    return t


# --- обереги: шнурок и подвеска --------------------------------------------------


def charm(pendant, palette, cord=CORD):
    """Иконка оберега: петля шнурка сверху и своя подвеска внизу."""
    t = Tex(CLEAR)
    for (x, y) in [(3, 1), (4, 2), (4, 3), (5, 4), (6, 5), (12, 1), (11, 2), (11, 3),
                   (10, 4), (9, 5)]:
        t.set(x, y, cord)
    t.set(7, 6, cord)
    t.set(8, 6, cord)
    t.sprite([row.replace(".", " ") for row in pendant], palette, 0, 7)
    return t


CHARMS = {
    "pilgrim_reliquary": ([
        "......OO........",
        ".....OSSO.......",
        "....OSRRSO......",
        "....OSRRSO......",
        "....ORRRRO......",
        "....OSRRSO......",
        "....OSSSSO......",
        ".....OOOO.......",
    ], {"O": SILVER_D, "S": SILVER, "R": RED}),
    "jade_jaguar": ([
        ".....O..O.......",
        "....OJOOJO......",
        "....OJJJJO......",
        "....OLJJLO......",
        "....OJJJJO......",
        ".....OWWO.......",
        "......OO........",
        "................",
    ], {"O": JADE_D, "J": JADE, "L": JADE_LIT, "W": rgb(0xF2ECDC)}),
    "lucky_horseshoe": ([
        "....G....G......",
        "....G....G......",
        "....G....G......",
        "....GL..LG......",
        ".....G..G.......",
        ".....GGGG.......",
        "......12........",
        "......34........",
    ], {"G": GOLD, "L": GOLD_L, "1": RAINBOW[0], "2": RAINBOW[2], "3": RAINBOW[3],
        "4": RAINBOW[4]}),
    "ore_gem": ([
        "......GG........",
        ".....GCCG.......",
        "....GCLCCG......",
        "....GCCCDG......",
        ".....GCDG.......",
        "......GG........",
        "................",
        "................",
    ], {"G": GOLD, "C": CYAN, "L": CYAN_L, "D": CYAN_D}),
    "moon_pendant": ([
        ".....SS.........",
        "....SL..........",
        "...SL...........",
        "...SL......L....",
        "...SL...........",
        "....SL..........",
        ".....SS.........",
        "................",
    ], {"S": SILVER, "L": SILVER_L}),
    "thunder_rune": ([
        ".....OOOO.......",
        "....OSSSSO......",
        "....OSSBSO......",
        "....OSBSSO......",
        "....OSSBSO......",
        "....OSBSSO......",
        ".....OOOO.......",
        "................",
    ], {"O": STONE_D, "S": STONE, "B": RUNE_LIT}),
    "kitsune_charm": ([
        "....W....W......",
        "....WW..WW......",
        "....WWWWWW......",
        "....WRWWRW......",
        "....WWWWWW......",
        ".....WRRW.......",
        "......WW........",
        "................",
    ], {"W": rgb(0xF4F0E8), "R": RED}),
    "homeward_charm": ([
        "......RR........",
        ".....RRRR.......",
        "....RRRRRR......",
        ".....WWWW.......",
        ".....WDDW.......",
        ".....WDDW.......",
        "................",
        "................",
    ], {"R": RED, "W": rgb(0xE8DCC0), "D": rgb(0x6E4A2A)}),
    "magnet_charm": ([
        "....R....S......",
        "....R....S......",
        "....R....R......",
        "....RR..RR......",
        ".....RRRR.......",
        "................",
        "................",
        "................",
    ], {"R": rgb(0xD83A32), "S": SILVER_L}),
    "wind_charm": ([
        "......WW........",
        ".....W..B.......",
        "....W.WW.B......",
        "....W.W..B......",
        ".....W..B.......",
        "......BB........",
        "................",
        "................",
    ], {"W": rgb(0xF4F8FF), "B": rgb(0x8FC8F0)}),
    "sea_shell": ([
        "......PP........",
        ".....PLLP.......",
        "....PLPPLP......",
        "....PPLLPP......",
        ".....PPPP.......",
        "......PP........",
        "................",
        "................",
    ], {"P": rgb(0xF0A8B8), "L": rgb(0xFFE0E8)}),
}


# --- модели блоков -----------------------------------------------------------------

STONE_TEX = "minecraft:block/stone_bricks"
SMOOTH = "minecraft:block/smooth_stone"


def toro_lantern():
    s, light = "#stone", "#light"
    return model({"stone": STONE_TEX, "light": "villagepax:block/toro_light",
                  "cap": SMOOTH, "particle": STONE_TEX}, [
        box((3, 0, 3), (13, 2, 13), s),
        box((6, 2, 6), (10, 7, 10), s),
        box((4, 7, 4), (12, 8, 12), s),
        box((5, 8, 5), (11, 12, 11), {"*": light, "up": None, "down": None}),
        box((3, 12, 3), (13, 13.5, 13), "#cap"),
        box((5, 13.5, 5), (11, 14.5, 11), "#cap"),
        box((7, 14.5, 7), (9, 16, 9), s),
    ])


BELL_UV = {face: [0, 0, 16, 16] for face in ("north", "south", "east", "west")}


def wind_chime(hanging):
    """Колокол — на всю картинку с каждой стороны: рыбка видна целиком."""
    glass, paper, cord = "#glass", "#paper", "#cord"
    elements = [
        box((5, 6, 5), (11, 11, 11), glass, uv=BELL_UV),
        box((7.5, 4, 7.5), (8.5, 6, 8.5), cord),
        box((6.5, 0, 7.9), (9.5, 4, 8.1), {"*": paper, "east": None, "west": None,
                                            "up": None, "down": None}),
    ]
    if hanging:
        elements.append(box((7.5, 11, 7.5), (8.5, 16, 8.5), cord))
    else:
        # На своей подставке: две стойки и перекладина из вишни.
        wood = "#wood"
        elements = [
            box((2, 0, 7), (4, 16, 9), wood),
            box((12, 0, 7), (14, 16, 9), wood),
            box((2, 14, 7), (14, 16, 9), wood),
            box((7.5, 11, 7.5), (8.5, 14, 8.5), cord),
            box((5, 6, 5), (11, 11, 11), glass, uv=BELL_UV),
            box((7.5, 4, 7.5), (8.5, 6, 8.5), cord),
            box((6.5, 0.5, 7.9), (9.5, 4, 8.1), {"*": paper, "east": None, "west": None,
                                                  "up": None, "down": None}),
        ]
    return model({"glass": "villagepax:block/wind_chime_glass", "paper": "villagepax:block/wind_chime",
                  "cord": "minecraft:block/white_wool", "wood": "minecraft:block/cherry_planks",
                  "particle": "villagepax:block/wind_chime_glass"}, elements, ao=False)


def bonsai():
    return model({"pot": "minecraft:block/flower_pot", "dirt": "minecraft:block/dirt",
                  "log": "minecraft:block/cherry_log", "leaves": "minecraft:block/cherry_leaves",
                  "particle": "minecraft:block/flower_pot"}, [
        box((5, 0, 5), (11, 6, 11), "#pot"),
        box((5.5, 5, 5.5), (10.5, 5.5, 10.5), "#dirt"),
        box((7.5, 5.5, 7.5), (8.5, 10, 8.5), "#log"),
        box((8.5, 8.5, 7.5), (10.5, 9.5, 8.5), "#log"),
        box((3.5, 9.5, 3.5), (9.5, 12.5, 9.5), "#leaves"),
        box((8.5, 8.5, 7), (12.5, 11.5, 11), "#leaves"),
        box((5.5, 12.5, 5.5), (8.5, 14.5, 8.5), "#leaves"),
    ], ao=False)


def rune_stone():
    return model({"face": "villagepax:block/rune_stone", "stone": "minecraft:block/stone",
                  "particle": "minecraft:block/stone"}, [
        box((3, 0, 6), (13, 14, 10), {"*": "#stone", "north": "#face", "south": "#face"}),
        box((4, 14, 6.5), (12, 16, 9.5), "#stone"),
    ])


def war_drum():
    return model({"head": "villagepax:block/drum_head", "side": "villagepax:block/drum_side",
                  "rim": "minecraft:block/stripped_dark_oak_log",
                  "particle": "villagepax:block/drum_side"}, [
        box((2, 0, 2), (14, 12, 14), {"*": "#side", "up": "#head", "down": "#rim"}),
        box((1.5, 11, 1.5), (14.5, 12.5, 14.5), {"*": "#rim", "up": "#head"}),
        box((1.5, 0, 1.5), (14.5, 1.5, 14.5), "#rim"),
    ])


def jaguar_idol():
    stone = "#stone"
    return model({"stone": "villagepax:block/carved_stone", "face": "villagepax:block/jaguar_idol",
                  "particle": "villagepax:block/carved_stone"}, [
        box((2, 0, 3), (14, 4, 13), stone),
        box((3, 4, 4), (13, 13, 13), {"*": stone, "north": "#face"}),
        box((5, 5, 2), (11, 9, 4), {"*": stone, "north": "#face"}),
        box((3, 13, 7), (5, 15, 9), stone),
        box((11, 13, 7), (13, 15, 9), stone),
    ])


def rainbow_fountain():
    rim, water = "#rim", "#water"
    return model({"rim": "villagepax:block/fountain_rim", "water": "villagepax:block/fountain_water",
                  "marble": "minecraft:block/smooth_quartz",
                  "particle": "minecraft:block/smooth_quartz"}, [
        box((1, 0, 1), (15, 1, 15), "#marble"),
        box((1, 1, 1), (15, 4, 2), rim),
        box((1, 1, 14), (15, 4, 15), rim),
        box((1, 1, 2), (2, 4, 14), rim),
        box((14, 1, 2), (15, 4, 14), rim),
        box((2, 1, 2), (14, 3.5, 14), {"*": water, "down": None}),
        box((7, 3.5, 7), (9, 9, 9), "#marble"),
        box((5, 9, 5), (11, 10, 11), "#marble"),
        box((6, 10, 6), (10, 10.5, 10), {"*": water, "down": None}),
    ])


def crystal_lamp():
    crystal = "#crystal"
    return model({"crystal": "villagepax:block/dwarf_crystal", "base": "minecraft:block/anvil",
                  "particle": "villagepax:block/dwarf_crystal"}, [
        box((4, 0, 4), (12, 2, 12), "#base"),
        box((6.5, 2, 6.5), (9.5, 12, 9.5), crystal),
        box((4, 2, 7), (6, 8, 9), crystal,
            rotation={"origin": [5, 2, 8], "axis": "z", "angle": 22.5}),
        box((10, 2, 7), (12, 9, 9), crystal,
            rotation={"origin": [11, 2, 8], "axis": "z", "angle": -22.5}),
        box((7, 2, 4), (9, 7, 6), crystal,
            rotation={"origin": [8, 2, 5], "axis": "x", "angle": -22.5}),
    ], ao=False)


def firefly_jar():
    return model({"glass": "villagepax:block/jar_glass", "flies": "villagepax:block/fireflies",
                  "lid": "minecraft:block/birch_planks", "particle": "villagepax:block/jar_glass"}, [
        box((5, 1, 5), (11, 9, 11), "#flies"),
        box((4, 0, 4), (12, 10, 12), "#glass"),
        box((4.5, 10, 4.5), (11.5, 11, 11.5), "#lid"),
        box((7, 11, 7), (9, 12, 9), "#lid"),
    ], ao=False)


def incense_burner():
    bronze = "#bronze"
    return model({"bronze": "minecraft:block/exposed_copper",
                  "coals": "villagepax:block/incense_coals",
                  "particle": "minecraft:block/exposed_copper"}, [
        box((4, 0, 4), (5, 3, 5), bronze),
        box((11, 0, 4), (12, 3, 5), bronze),
        box((7.5, 0, 11), (8.5, 3, 12), bronze),
        box((4, 3, 4), (12, 7, 12), {"*": bronze, "up": "#coals"}),
        box((5, 7, 5), (11, 8, 11), {"*": bronze, "up": "#coals"}),
        box((7, 8, 7), (9, 10, 9), bronze),
    ])


def weathervane():
    iron = "#iron"
    return model({"iron": "minecraft:block/anvil", "rooster": "villagepax:block/weathervane",
                  "gold": "minecraft:block/gold_block", "particle": "minecraft:block/anvil"}, [
        box((7.5, 0, 7.5), (8.5, 12, 8.5), iron),
        box((3, 9, 7.75), (13, 9.5, 8.25), iron),
        box((7.75, 9, 3), (8.25, 9.5, 13), iron),
        box((2, 11, 7.9), (14, 16, 8.1), {"*": "#rooster", "east": None, "west": None,
                                           "up": None, "down": None}),
        box((7, 12, 7), (9, 13, 9), "#gold"),
    ], ao=False)


def maya_calendar():
    return model({"stone": "villagepax:block/carved_stone", "face": "villagepax:block/maya_calendar",
                  "particle": "villagepax:block/carved_stone"}, [
        box((2, 0, 5), (14, 2, 11), "#stone"),
        box((6, 2, 7), (10, 4, 9), "#stone"),
        box((1, 2, 7.5), (15, 16, 8.5), {"*": None, "north": "#face", "south": "#face"}),
    ], ao=False)


def scarecrow():
    return model({"post": "minecraft:block/stripped_oak_log", "shirt": "villagepax:block/scarecrow",
                  "face": "minecraft:block/carved_pumpkin", "pumpkin": "minecraft:block/pumpkin_side",
                  "top": "minecraft:block/pumpkin_top", "hat": "minecraft:block/hay_block_top",
                  "particle": "minecraft:block/hay_block_side"}, [
        box((7, 0, 7), (9, 11, 9), "#post"),
        box((1, 9, 7.25), (15, 10.5, 8.75), "#post"),
        box((4.5, 4, 6), (11.5, 10.5, 10), "#shirt"),
        box((5, 10.5, 5.5), (11, 15, 10.5), {"*": "#pumpkin", "north": "#face", "up": "#top"}),
        box((3.5, 15, 4), (12.5, 16, 12), "#hat"),
    ])


# --- что пишется -------------------------------------------------------------------
#
# name: (модель или модели, направленный ли, инструмент, рецепт, ru, en, описание ru, en)

BLOCKS = {
    "toro_lantern": dict(models={"": toro_lantern}, facing=False, tool="pickaxe",
                         recipe=(["BLB", " B ", "BBB"], {"B": "minecraft:stone_bricks",
                                                         "L": "minecraft:lantern"}),
                         ru="Каменный фонарь торо", en="Toro Stone Lantern"),
    "wind_chime": dict(models={"": lambda: wind_chime(False), "_hanging": lambda: wind_chime(True)},
                       facing=False, hanging=True, tool="pickaxe",
                       recipe=([" S ", "GIG", " P "], {"S": "minecraft:string",
                                                       "G": "minecraft:glass_pane",
                                                       "I": "minecraft:iron_nugget",
                                                       "P": "minecraft:paper"}),
                       ru="Фурин", en="Furin Wind Chime"),
    "bonsai": dict(models={"": bonsai}, facing=True, tool="axe",
                   shapeless=["minecraft:flower_pot", "minecraft:cherry_sapling", "minecraft:bone_meal"],
                   ru="Вишнёвый бонсай", en="Cherry Bonsai"),
    "rune_stone": dict(models={"": rune_stone}, facing=True, tool="pickaxe",
                       recipe=([" S ", "SLS", " S "], {"S": "minecraft:stone",
                                                       "L": "minecraft:lapis_lazuli"}),
                       ru="Рунный камень", en="Rune Stone"),
    "war_drum": dict(models={"": war_drum}, facing=False, tool="axe",
                     recipe=(["LLL", "PSP", "PPP"], {"L": "minecraft:leather",
                                                     "P": {"tag": "minecraft:planks"},
                                                     "S": "minecraft:string"}),
                     ru="Боевой барабан", en="War Drum"),
    "jaguar_idol": dict(models={"": jaguar_idol}, facing=True, tool="pickaxe",
                        recipe=(["CEC", "CCC"], {"C": "villagepax:carved_stone",
                                                 "E": "minecraft:emerald"}),
                        ru="Идол ягуара", en="Jaguar Idol"),
    "rainbow_fountain": dict(models={"": rainbow_fountain}, facing=False, tool="pickaxe",
                             recipe=(["RWB", "QQQ"], {"R": "minecraft:red_dye",
                                                      "W": "minecraft:water_bucket",
                                                      "B": "minecraft:blue_dye",
                                                      "Q": "minecraft:smooth_quartz"}),
                             ru="Радужный фонтанчик", en="Rainbow Fountain"),
    "crystal_lamp": dict(models={"": crystal_lamp}, facing=False, tool="pickaxe",
                         recipe=([" A ", "AGA", "III"], {"A": "minecraft:amethyst_shard",
                                                         "G": "minecraft:glowstone_dust",
                                                         "I": "minecraft:iron_ingot"}),
                         ru="Кристальная лампа гномов", en="Dwarven Crystal Lamp"),
    "firefly_jar": dict(models={"": firefly_jar}, facing=False, tool="pickaxe",
                        shapeless=["minecraft:glass", "minecraft:glow_berries",
                                   {"tag": "minecraft:wooden_slabs"}],
                        ru="Банка светлячков", en="Firefly Jar"),
    "incense_burner": dict(models={"": incense_burner}, facing=False, tool="pickaxe",
                           recipe=([" C ", "CAC", "C C"], {"C": "minecraft:copper_ingot",
                                                           "A": "minecraft:charcoal"}),
                           ru="Кадильница", en="Incense Burner"),
    "weathervane": dict(models={"": weathervane}, facing=True, tool="pickaxe",
                        recipe=([" G ", " I ", " I "], {"G": "minecraft:gold_ingot",
                                                        "I": "minecraft:iron_ingot"}),
                        ru="Флюгер-петушок", en="Rooster Weathervane"),
    "maya_calendar": dict(models={"": maya_calendar}, facing=True, tool="pickaxe",
                          recipe=(["CCC", "CGC", "CCC"], {"C": "villagepax:carved_stone",
                                                          "G": "minecraft:gold_ingot"}),
                          ru="Календарный камень майя", en="Maya Calendar Stone"),
    "scarecrow": dict(models={"": scarecrow}, facing=True, tool="axe",
                      recipe=([" P ", "WHW", " W "], {"P": "minecraft:carved_pumpkin",
                                                      "W": "minecraft:stick",
                                                      "H": "minecraft:hay_block"}),
                      ru="Пугало", en="Scarecrow"),
}

TEXTURES = {
    "toro_light": toro_light, "rune_stone": rune_face, "drum_head": drum_head,
    "drum_side": drum_side, "jaguar_idol": jaguar_face, "fountain_water": fountain_water,
    "fountain_rim": fountain_rim, "dwarf_crystal": dwarf_crystal, "fireflies": fireflies,
    "maya_calendar": calendar_face, "wind_chime": chime_paper, "weathervane": rooster,
    "scarecrow": scarecrow_shirt, "incense_coals": incense_smoke,
    "wind_chime_glass": chime_glass, "jar_glass": jar_glass,
}

CHARM_NAMES = {
    "pilgrim_reliquary": ("Ладанка паломника", "Pilgrim's Reliquary"),
    "jade_jaguar": ("Нефритовый ягуар", "Jade Jaguar"),
    "lucky_horseshoe": ("Подкова удачи", "Lucky Horseshoe"),
    "ore_gem": ("Рудный самоцвет", "Ore Gem"),
    "moon_pendant": ("Лунный кулон", "Moon Pendant"),
    "thunder_rune": ("Руна Громовержца", "Thunderer's Rune"),
    "kitsune_charm": ("Лисий оберег", "Kitsune Charm"),
    "homeward_charm": ("Оберег возвращения", "Homeward Charm"),
    "magnet_charm": ("Оберег-магнит", "Magnet Charm"),
    "wind_charm": ("Оберег ветра", "Wind Charm"),
    "sea_shell": ("Раковина прибоя", "Surf Shell"),
}

CHARM_RECIPES = {
    "homeward_charm": ([" S ", "GEG", " C "], {"S": "minecraft:string", "G": "minecraft:gold_ingot",
                                               "E": "minecraft:ender_pearl",
                                               "C": "minecraft:compass"}),
    "magnet_charm": ([" S ", "IRI", " I "], {"S": "minecraft:string", "I": "minecraft:iron_ingot",
                                             "R": "minecraft:redstone"}),
    "wind_charm": ([" S ", "FPF", " F "], {"S": "minecraft:string", "F": "minecraft:feather",
                                           "P": "minecraft:phantom_membrane"}),
    "sea_shell": ([" S ", "PNP", " P "], {"S": "minecraft:string",
                                          "P": "minecraft:prismarine_shard",
                                          "N": "minecraft:nautilus_shell"}),
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8",
                    newline="\n")


def ingredient(value):
    return value if isinstance(value, dict) else {"item": value}


def merge_tag(path, entries):
    """Дописать свои блоки в общий тег, не трогая чужих и порядка."""
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() \
        else {"replace": False, "values": []}
    for entry in entries:
        if entry not in data["values"]:
            data["values"].append(entry)
    write_json(path, data)


def main():
    for name, paint in TEXTURES.items():
        paint().save("block", name)
    for name, (pendant, palette) in CHARMS.items():
        charm(pendant, palette).save("item", name)

    by_tool = {"pickaxe": [], "axe": []}
    names_ru, names_en = {}, {}
    for name, spec in BLOCKS.items():
        for suffix, build in spec["models"].items():
            write_json(ASSETS / "models/block" / (name + suffix + ".json"), build())
        if spec.get("hanging"):
            variants = {"hanging=false": {"model": "villagepax:block/" + name},
                        "hanging=true": {"model": "villagepax:block/" + name + "_hanging"}}
        elif spec["facing"]:
            variants = {"facing=north": {"model": "villagepax:block/" + name},
                        "facing=east": {"model": "villagepax:block/" + name, "y": 90},
                        "facing=south": {"model": "villagepax:block/" + name, "y": 180},
                        "facing=west": {"model": "villagepax:block/" + name, "y": 270}}
        else:
            variants = {"": {"model": "villagepax:block/" + name}}
        write_json(ASSETS / "blockstates" / (name + ".json"), {"variants": variants})
        write_json(ASSETS / "models/item" / (name + ".json"),
                   {"parent": "villagepax:block/" + name})
        write_json(DATA / "loot_tables/blocks" / (name + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0,
                       "entries": [{"type": "minecraft:item", "name": "villagepax:" + name}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        if "recipe" in spec:
            pattern, key = spec["recipe"]
            recipe = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                      "key": {k: ingredient(v) for k, v in key.items()},
                      "result": {"item": "villagepax:" + name}}
        else:
            recipe = {"type": "minecraft:crafting_shapeless", "category": "misc",
                      "ingredients": [ingredient(v) for v in spec["shapeless"]],
                      "result": {"item": "villagepax:" + name}}
        write_json(DATA / "recipes" / (name + ".json"), recipe)
        by_tool[spec["tool"]].append("villagepax:" + name)
        names_ru["block.villagepax." + name] = spec["ru"]
        names_en["block.villagepax." + name] = spec["en"]

    for name, (ru, en) in CHARM_NAMES.items():
        write_json(ASSETS / "models/item" / (name + ".json"),
                   {"parent": "minecraft:item/generated",
                    "textures": {"layer0": "villagepax:item/" + name}})
        names_ru["item.villagepax." + name] = ru
        names_en["item.villagepax." + name] = en
    for name, (pattern, key) in CHARM_RECIPES.items():
        write_json(DATA / "recipes" / (name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: ingredient(v) for k, v in key.items()},
            "result": {"item": "villagepax:" + name}})

    merge_tag(TAGS / "mineable/pickaxe.json", by_tool["pickaxe"])
    merge_tag(TAGS / "mineable/axe.json", by_tool["axe"])
    merge_tag(DATA / "tags/blocks/build_decor.json",
              ["villagepax:" + name for name in BLOCKS if name != "scarecrow"] + ["villagepax:scarecrow"])

    for code, names in (("ru_ru", names_ru), ("en_us", names_en)):
        path = ASSETS / "lang" / (code + ".json")
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(names)
        write_json(path, data)
    print("диковинок: %d блоков, %d оберегов" % (len(BLOCKS), len(CHARMS)))


if __name__ == "__main__":
    main()
