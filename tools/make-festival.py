#!/usr/bin/env python3
"""Праздник на ярмарке: сердца праздника, мишень, флажки, пирог, кубок,
вещицы поиска, ленты, праздничный лук, мячики жонглёра и шапки народов.

Заказчик: «добавь веселья». Раз в лунный месяц деревня гуляет на ярмарке:
хоровод вокруг сердца праздника (у каждого народа своего), пироги на столе,
состязания и призы. Всё, что для этого нужно увидеть глазами, — отсюда:
текстуры, модели, блокстейты, добыча, рецепты, теги и названия.

Шапки — объёмные модели предмета, которые игра надевает на голову
по ванильному виду «head», как тыкву. Голова в пространстве модели шапки
занимает 1.6…14.4 по всем осям: лицо смотрит на север, восточный бок
модели — правый бок головы.

    python tools/make-festival.py
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


def _load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


tx = _load("textures", "make-textures.py")
wd = _load("wonders", "make-wonders.py")
Tex, rgb, CLEAR = tx.Tex, tx.rgb, tx.CLEAR
box, model, write_json, ingredient, merge_tag = (wd.box, wd.model, wd.write_json,
                                                 wd.ingredient, wd.merge_tag)

# --- краски -------------------------------------------------------------------

WHITE, CREAM = rgb(0xF2EEE6), rgb(0xE6DCC4)
INK = rgb(0x2A2622)
GOLD, GOLD_L, GOLD_D = rgb(0xE0B040), rgb(0xF6D774), rgb(0xA87A1E)
RED, RED_D = rgb(0xC0392B), rgb(0x8A2419)
BLUE = rgb(0x2E5A9E)
GREEN, GREEN_L, GREEN_D = rgb(0x4E9A3E), rgb(0x7BC96F), rgb(0x2F6A26)
STRAW, STRAW_L, STRAW_D = rgb(0xE0C46C), rgb(0xF0DA8E), rgb(0xB0923E)
WOOD, WOOD_L, WOOD_D = rgb(0x8A5A32), rgb(0xA8744A), rgb(0x5E3A1E)
STONE, STONE_L, STONE_D = rgb(0x8A8A84), rgb(0xA3A39B), rgb(0x5E5E59)
STRING = rgb(0x5A4632)
GLOW, GLOW_L = rgb(0xFFE36B), rgb(0xFFF7C2)
RAINBOW = [rgb(0xE84A5F), rgb(0xF7A541), rgb(0xF5E663), rgb(0x7BC96F), rgb(0x4FA3E0),
           rgb(0x9B6BD3)]

# Цвета народов: флажки, ленты столбов, вещицы.
PEOPLE_COLOURS = {
    "norman": [RED, WHITE, BLUE],
    "maya": [rgb(0x2BB3A8), rgb(0xC23B22), rgb(0xE8B830)],
    "pony": [rgb(0xF7A8C8), rgb(0xF5E663), rgb(0x8FD0F5)],
    "nord": [rgb(0x2D4F8A), rgb(0xEDEFF2), rgb(0xB5382F)],
    "yamato": [rgb(0xC8102E), rgb(0xF4F1EA), rgb(0xC8102E)],
    "dwarf": [rgb(0xB86B3A), rgb(0x7A2A20), rgb(0xD8A83A)],
    "elf": [rgb(0x4C9A5B), rgb(0xCFD6DA), rgb(0xE8D98A)],
}

# Порода флажков — та, из которой народ строит: доски своего леса.
BUNTING_PLANKS = {
    "norman": "minecraft:dark_oak_planks", "maya": "minecraft:jungle_planks",
    "pony": "minecraft:acacia_planks", "nord": "minecraft:spruce_planks",
    "yamato": "minecraft:cherry_planks", "dwarf": "minecraft:cobblestone",
    "elf": "minecraft:birch_planks",
}
PEOPLE_RU = {"norman": "норманнов", "maya": "майя", "pony": "пони", "nord": "северян",
             "yamato": "ямато", "dwarf": "гномов", "elf": "эльфов"}
PEOPLE_EN = {"norman": "Norman", "maya": "Maya", "pony": "Pony", "nord": "Nord",
             "yamato": "Yamato", "dwarf": "Dwarven", "elf": "Elven"}


# --- текстуры: столбы ----------------------------------------------------------


def spiral(colours, base):
    """Столб в спиральных лентах: полосы наискось, через одну — сам столб."""
    t = Tex(base)
    period = 2 * len(colours) * 2
    for y in range(16):
        for x in range(16):
            band = (x + y) % period
            if band % 4 < 2:
                t.set(x, y, colours[(band // 4) % len(colours)])
    # Тень по краю — столб круглый, а не плоский.
    for y in range(16):
        t.set(0, y, _shade(t.px[y][0]))
        t.set(15, y, _shade(t.px[y][15]))
    return t


def _shade(colour, k=0.78):
    return tuple(int(c * k) for c in colour[:3]) + (colour[3] if len(colour) > 3 else 255,)


def banded(colours, base):
    """Крашеный шест воладоров: пояса краски и чёрная кайма между ними."""
    t = Tex(base)
    for y in range(16):
        colour = colours[(y // 4) % len(colours)]
        for x in range(16):
            t.set(x, y, colour if y % 4 else INK)
    for x in range(0, 16, 4):
        t.set(x + 1, 2, GOLD)
        t.set(x + 3, 10, GOLD)
    return t


def wreath():
    """Венок: зелень листвы и цветы — красные, белые, золотые."""
    t = Tex(GREEN)
    for y in range(16):
        for x in range(16):
            if (x * 7 + y * 3) % 5 == 0:
                t.set(x, y, GREEN_D)
            elif (x * 3 + y * 5) % 11 == 0:
                t.set(x, y, GREEN_L)
    for i, (x, y) in enumerate([(2, 2), (7, 4), (12, 2), (4, 9), (10, 8), (14, 12), (6, 13),
                                (1, 12), (11, 14)]):
        colour = [RED, WHITE, GOLD][i % 3]
        t.dots(colour, (x, y), (x + 1, y), (x, y + 1))
    return t


def streamers(colours):
    """Ленты, свисающие с венца: полосы с прорезями между ними."""
    t = Tex(CLEAR)
    for i, x in enumerate(range(1, 16, 3)):
        colour = colours[i % len(colours)]
        length = 16 - (i * 3) % 7
        for y in range(length):
            t.set(x, y, colour)
            if y % 5 == 4:
                t.set(x, y, _shade(colour))
    return t


def frame_wood():
    """Рама воладоров: тёмное дерево джунглей, обмотанное верёвкой."""
    t = Tex(rgb(0x6B4A2A))
    for x in range(16):
        t.set(x, 0, rgb(0x4A301A))
        t.set(x, 15, rgb(0x4A301A))
    for x in range(0, 16, 3):
        t.line(x, 1, x + 2, 14, rgb(0xC9A76A))
    return t


def star():
    """Звезда радужного столба: золото с бликом."""
    t = Tex(GOLD)
    t.rect(0, 0, 15, 1, GOLD_L)
    t.rect(0, 0, 1, 15, GOLD_L)
    t.rect(0, 14, 15, 15, GOLD_D)
    t.rect(14, 0, 15, 15, GOLD_D)
    t.dots(WHITE, (4, 4), (5, 4), (4, 5))
    return t


# --- текстуры: барабан, горн, деревце ------------------------------------------


def taiko_head():
    """Кожа тайко с тремя запятыми томоэ в круге."""
    t = Tex(rgb(0xE9DDC0))
    t.ring(7.5, 7.5, 7.6, 6.4, rgb(0x3A2A1C))
    for x, y in [(1, 7), (14, 7), (7, 1), (7, 14), (3, 3), (12, 3), (3, 12), (12, 12)]:
        t.set(x, y, GOLD)
    t.disc(7.5, 7.5, 3.4, RED)
    t.dots(INK, (6, 6), (9, 7), (7, 9))
    t.dots(INK, (6, 7), (8, 6), (8, 9))
    return t


def taiko_side():
    """Бок тайко: красный лак и два ряда медных гвоздей."""
    t = Tex(rgb(0xB0281E))
    for y in range(16):
        for x in range(16):
            if (x + 3 * y) % 7 == 0:
                t.set(x, y, rgb(0x98221A))
    for x in range(1, 16, 3):
        t.set(x, 1, GOLD_L)
        t.set(x, 14, GOLD_L)
    t.rect(0, 0, 15, 0, rgb(0x6E1610))
    t.rect(0, 15, 15, 15, rgb(0x6E1610))
    return t


def forge_coals():
    """Угли горна: чёрное, тёмно-красное и золото жара."""
    t = Tex(rgb(0x2B2220))
    for y in range(16):
        for x in range(16):
            v = (x * 13 + y * 7) % 9
            if v == 0:
                t.set(x, y, rgb(0xFFB030))
            elif v in (1, 5):
                t.set(x, y, rgb(0xC0401C))
            elif v == 3:
                t.set(x, y, rgb(0x5A2A1E))
    t.rect(0, 0, 15, 0, STONE_D)
    t.rect(0, 15, 15, 15, STONE_D)
    t.rect(0, 0, 0, 15, STONE_D)
    t.rect(15, 0, 15, 15, STONE_D)
    return t


def forge_side():
    """Бок горна: кладка тёмного камня и зев с огнём."""
    t = Tex(rgb(0x4A4A50))
    for y in (3, 7, 11, 15):
        t.rect(0, y, 15, y, rgb(0x2E2E33))
    for y0, off in ((0, 0), (4, 4), (8, 0), (12, 4)):
        for x in range(off, 16, 8):
            t.rect(x, y0, x, y0 + 2, rgb(0x2E2E33))
    t.rect(5, 7, 10, 12, rgb(0x2B1A12))
    t.rect(6, 9, 9, 12, rgb(0xE06020))
    t.dots(rgb(0xFFC040), (7, 10), (8, 11), (6, 12), (9, 12))
    return t


def glow_leaves():
    """Крона деревца: листва с огоньками светлячков, с прорезями по краю."""
    t = Tex(GREEN)
    for y in range(16):
        for x in range(16):
            v = (x * 5 + y * 11) % 13
            if v in (0, 7):
                t.set(x, y, GREEN_D)
            elif v == 3:
                t.set(x, y, GREEN_L)
            elif v == 9 and (x + y) % 3 == 0:
                t.set(x, y, CLEAR)
    for x, y in [(3, 2), (11, 4), (6, 8), (13, 11), (2, 13), (9, 14), (7, 3)]:
        t.set(x, y, GLOW)
        t.set(x + 1, y, GLOW_L)
    return t


# --- текстуры: мишень, флажки, пирог, вещицы -----------------------------------


def target_face():
    """Мишень: солома по краю, белое поле в красной кайме, красная середина с золотом.

    Кольца квадратные и ровно такие, какими их считает ArcheryScore: середина —
    клетки 6…9 (четверть грани), среднее кольцо — 3…12 (пять восьмых).
    """
    t = Tex(STRAW)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 7 == 0:
                t.set(x, y, STRAW_D)
            elif (x + y * 2) % 9 == 0:
                t.set(x, y, STRAW_L)
    t.rect(3, 3, 12, 12, RED)
    t.rect(4, 4, 11, 11, WHITE)
    t.rect(6, 6, 9, 9, RED)
    t.rect(7, 7, 8, 8, GOLD)
    return t


def pennants(colours):
    """Гирлянда: бечева поверху и три флажка остриём вниз, между ними прорезь."""
    t = Tex(CLEAR)
    t.rect(0, 3, 15, 3, STRING)
    for i, left in enumerate((0, 5, 10)):
        colour = colours[i % len(colours)]
        centre = left + 2.5
        for row in range(8):
            half = 2.6 * (1 - row / 8)
            for x in range(left, left + 6):
                if abs(x + 0.5 - (centre + 0.5)) <= half:
                    t.set(x, 4 + row, colour)
        t.set(left + 2, 5, _shade(colour, 1.15) if colour != WHITE else CREAM)
    return t


def pie_top():
    """Корочка пирога решёткой, в прорезях — ягода."""
    t = Tex(rgb(0xD69A52))
    for y in range(16):
        for x in range(16):
            if x % 4 == 1 or y % 4 == 1:
                t.set(x, y, rgb(0xE8B470))
            elif (x + y) % 3 == 0:
                t.set(x, y, rgb(0x9A2A3A))
            else:
                t.set(x, y, rgb(0xB23A48))
    t.rect(0, 0, 15, 0, rgb(0xB07A3A))
    t.rect(0, 15, 15, 15, rgb(0xB07A3A))
    t.rect(0, 0, 0, 15, rgb(0xB07A3A))
    t.rect(15, 0, 15, 15, rgb(0xB07A3A))
    return t


def pie_side():
    """Бок пирога: корочка, полоска ягодной начинки, корочка."""
    t = Tex(CLEAR)
    t.rect(0, 8, 15, 15, rgb(0xD69A52))
    t.rect(0, 8, 15, 9, rgb(0xE8B470))
    t.rect(0, 11, 15, 12, rgb(0xB23A48))
    for x in range(0, 16, 3):
        t.set(x, 13, rgb(0xB07A3A))
    return t


def pie_bottom():
    t = Tex(rgb(0xB07A3A))
    for x, y in [(3, 4), (9, 2), (12, 10), (5, 12)]:
        t.set(x, y, rgb(0xC98A48))
    return t


def pie_inner():
    """Срез пирога: корочка сверху и снизу, между ними начинка."""
    t = Tex(CLEAR)
    t.rect(0, 8, 15, 15, rgb(0xB23A48))
    t.rect(0, 8, 15, 9, rgb(0xE8B470))
    t.rect(0, 14, 15, 15, rgb(0xD69A52))
    for x in range(1, 16, 4):
        t.set(x, 11, rgb(0x7A1A2A))
    return t


def token_egg():
    """Крашеное яйцо: зигзаг и точки в цветах норманнов."""
    t = Tex(rgb(0xF4E6C8))
    for y in range(16):
        for x in range(16):
            if y in (5, 6) and (x + y) % 4 < 2:
                t.set(x, y, RED)
            if y in (10, 11) and (x - y) % 4 < 2:
                t.set(x, y, BLUE)
    for x in range(1, 16, 4):
        t.set(x, 8, GOLD)
    return t


def token_jade():
    t = Tex(rgb(0x2FA67A))
    for y in range(16):
        for x in range(16):
            if (x * 3 + y) % 5 == 0:
                t.set(x, y, rgb(0x7FE0B2))
            elif (x + y * 3) % 7 == 0:
                t.set(x, y, rgb(0x1E6E50))
    t.dots(INK, (5, 4), (10, 4))
    return t


def token_omamori():
    """Омамори: красная парча в золотую клетку, узкая полоска надписи и шнур."""
    t = Tex(rgb(0xC8102E))
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                t.set(x, y, rgb(0xA00C24))
            elif (x * 3 + y) % 8 == 0:
                t.set(x, y, GOLD)
    t.rect(7, 10, 8, 15, rgb(0xF4F1EA))
    t.dots(INK, (7, 11), (8, 12), (7, 14))
    t.rect(0, 7, 15, 8, GOLD)
    return t


def token_rune():
    t = Tex(STONE)
    for y in range(16):
        for x in range(16):
            if (x * 5 + y * 3) % 11 == 0:
                t.set(x, y, STONE_L)
            elif (x * 3 + y * 7) % 13 == 0:
                t.set(x, y, STONE_D)
    # Руна «ансуз»: ствол и два сучка — и светится.
    t.line(7, 3, 7, 12, rgb(0x5FB8FF))
    t.line(7, 3, 11, 6, rgb(0x5FB8FF))
    t.line(7, 7, 11, 10, rgb(0x5FB8FF))
    return t


def token_horseshoe():
    """Подкова с прорезью: толстая дуга железа, гвозди и золотые шипы на концах."""
    t = Tex(CLEAR)
    iron, dark = rgb(0xA6A9AD), rgb(0x6E7176)
    t.ring(7.5, 7.0, 6.9, 3.6, iron)
    t.rect(0, 8, 15, 15, CLEAR)
    t.rect(1, 7, 4, 14, iron)
    t.rect(11, 7, 14, 14, iron)
    t.ring(7.5, 7.0, 6.9, 6.0, dark)
    # Ниже середины остаются только два конца — остальное прорезь.
    for y in range(8, 16):
        for x in range(16):
            if not (1 <= x <= 4 or 11 <= x <= 14):
                t.set(x, y, CLEAR)
    for x, y in [(2, 9), (2, 12), (13, 9), (13, 12), (4, 3), (11, 3), (7, 1)]:
        t.set(x, y, rgb(0x3A3C40))
    t.rect(1, 14, 4, 15, GOLD)
    t.rect(11, 14, 14, 15, GOLD)
    return t


def token_gem():
    """Самоцветы гномов: три кристалла с гранями, с прорезью вокруг."""
    t = Tex(CLEAR)
    for cx, height, colour, lit in ((4, 9, rgb(0x6A4FD8), rgb(0xB9A8FF)),
                                   (8, 13, rgb(0x2FB7C8), rgb(0x9CF0F7)),
                                   (12, 8, rgb(0xC84FA0), rgb(0xF4A8DA))):
        for row in range(height):
            y = 15 - row
            half = 1 if row < height - 2 else 0
            for x in range(cx - half - 1, cx + half + 1):
                t.set(x, y, colour)
            t.set(cx - 1, y, lit)
    return t


def token_firefly():
    """Светлячок: огонёк в ореоле, по краю пусто."""
    t = Tex(CLEAR)
    t.disc(7.5, 7.5, 5.0, (255, 227, 107, 90))
    t.disc(7.5, 7.5, 3.2, GLOW)
    t.disc(7.5, 7.5, 1.6, GLOW_L)
    return t


# --- текстуры предметов --------------------------------------------------------


def ribbon():
    """Праздничная лента: розетка с золотой серединой и два хвоста."""
    t = Tex(CLEAR)
    t.line(6, 9, 4, 15, BLUE)
    t.line(7, 9, 5, 15, BLUE)
    t.line(9, 9, 11, 15, RED)
    t.line(10, 9, 12, 15, RED)
    t.disc(7.5, 6.5, 6.2, RED)
    for i in range(8):
        a = i * math.pi / 4
        t.set(int(round(7.5 + 5.6 * math.cos(a))), int(round(6.5 + 5.6 * math.sin(a))), RED_D)
    t.disc(7.5, 6.5, 3.6, WHITE)
    t.disc(7.5, 6.5, 2.4, GOLD)
    t.dots(GOLD_L, (6, 5), (7, 5))
    return t


def bow(stage):
    """Праздничный лук: светлое дерево, красная лента на рукояти, три кадра натяжения.

    Кадр −1 — лук в покое; 0…2 — тетива оттянута на 1…3 точки и на ней стрела.
    """
    t = Tex(CLEAR)
    pull = stage + 1 if stage >= 0 else 0
    stave = [(3, 1), (4, 1), (5, 2), (6, 3), (7, 4), (8, 5), (9, 6), (10, 7), (11, 8),
             (12, 9), (13, 10), (14, 11), (14, 12)]
    for x, y in stave:
        t.set(x, y, WOOD_L)
        t.set(x - 1, y + 1, WOOD)
    nock = (5 - pull, 11 + pull)
    t.line(3, 2, nock[0], nock[1], rgb(0xDDDDD6))
    t.line(nock[0], nock[1], 13, 12, rgb(0xDDDDD6))
    if stage >= 0:
        t.line(nock[0], nock[1], 11 - pull, 5 + pull, rgb(0x9A7650))
        t.dots(STONE_L, (12 - pull, 4 + pull), (11 - pull, 4 + pull))
        t.dots(RED, (nock[0], nock[1] - 1), (nock[0] + 1, nock[1]))
    t.dots(RED, (8, 6), (9, 7), (9, 5), (10, 6))
    t.dots(RED_D, (10, 5), (11, 4))
    return t


def juggling_balls():
    t = Tex(CLEAR)
    for cx, cy, colour in ((7.5, 3.5, RED), (4.0, 10.5, BLUE), (11.0, 10.5, GOLD)):
        t.disc(cx, cy, 3.2, colour)
        t.set(int(cx) - 1, int(cy) - 1, WHITE)
    return t


# --- текстуры шапок ------------------------------------------------------------


def feathers():
    """Перья майя: бирюза, красный и золото полосами вдоль пера."""
    t = Tex(CLEAR)
    colours = [rgb(0x2BB3A8), rgb(0xC23B22), rgb(0xE8B830), rgb(0x2BB3A8)]
    for i, x in enumerate(range(0, 16, 4)):
        for y in range(16):
            t.set(x + 1, y, colours[i])
            t.set(x + 2, y, colours[i] if y % 3 else GOLD)
        t.set(x + 1, 0, CLEAR)
    return t


def headband():
    """Налобная лента майя: красная с бирюзовыми ромбами."""
    t = Tex(rgb(0xC23B22))
    for x in range(0, 16, 4):
        t.dots(rgb(0x2BB3A8), (x + 1, 7), (x + 2, 6), (x + 2, 8), (x + 3, 7))
    t.rect(0, 0, 15, 1, GOLD)
    t.rect(0, 14, 15, 15, GOLD)
    return t


def kitsune():
    """Маска лисы: белая морда, красные брови и узоры, чёрные прорези глаз."""
    t = Tex(rgb(0xF6F2EA))
    t.rect(3, 5, 5, 6, INK)
    t.rect(10, 5, 12, 6, INK)
    t.line(2, 3, 5, 4, RED)
    t.line(13, 3, 10, 4, RED)
    t.line(7, 1, 7, 4, RED)
    t.line(8, 1, 8, 4, RED)
    t.rect(6, 11, 9, 12, INK)
    t.line(3, 9, 5, 12, RED)
    t.line(12, 9, 10, 12, RED)
    t.dots(GOLD, (7, 8), (8, 8))
    return t


def straw():
    t = Tex(STRAW)
    for y in range(16):
        for x in range(16):
            if (x + y * 3) % 4 == 0:
                t.set(x, y, STRAW_D)
            elif (x * 2 + y) % 5 == 0:
                t.set(x, y, STRAW_L)
    t.rect(0, 6, 15, 7, rgb(0xB5382F))
    return t


def party():
    """Колпак пони: розовый в разноцветный горошек."""
    t = Tex(rgb(0xF7A8C8))
    spots = [(2, 2), (8, 1), (13, 4), (5, 7), (11, 9), (2, 12), (8, 13), (14, 14)]
    for i, (x, y) in enumerate(spots):
        c = RAINBOW[i % len(RAINBOW)]
        t.dots(c, (x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1))
    return t


def pompom():
    t = Tex(RAINBOW[2])
    for y in range(16):
        for x in range(16):
            if (x + y) % 3 == 0:
                t.set(x, y, RAINBOW[(x + y) % len(RAINBOW)])
    return t


def leather_cap():
    t = Tex(rgb(0x6E4A2C))
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 7 == 0:
                t.set(x, y, rgb(0x5A3A22))
    t.rect(0, 14, 15, 15, rgb(0xD8A83A))
    return t


def candle():
    """Свеча колпака: белый воск, огонёк сверху."""
    t = Tex(rgb(0xF2EEE0))
    t.rect(0, 0, 15, 3, CLEAR)
    t.rect(6, 0, 9, 3, GLOW)
    t.rect(7, 0, 8, 1, GLOW_L)
    for y in range(5, 16, 3):
        t.set(3, y, rgb(0xD8D0C0))
    return t


def circlet():
    t = Tex(rgb(0xCFD6DA))
    for x in range(16):
        t.set(x, 7, rgb(0xE8F0F4))
        if x % 4 == 1:
            t.set(x, 8, rgb(0x4C9A5B))
    return t


# --- модели ----------------------------------------------------------------------

BLOCK = "villagepax:block/"
ITEM = "villagepax:item/"


def pole(texture):
    return model({"pole": BLOCK + texture, "particle": BLOCK + texture}, [
        box((6, 0, 6), (10, 16, 10), {"*": "#pole", "up": None, "down": None}),
    ])


def cross(start, end, texture):
    """Две перекрещённые плоскости, как у ванильных цветов и костра."""
    return [
        box(start, end, {"*": None, "north": texture, "south": texture},
            rotation={"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}),
        box(start, end, {"*": None, "north": texture, "south": texture},
            rotation={"origin": [8, 8, 8], "axis": "y", "angle": -45, "rescale": True}),
    ]


def maypole_top():
    return model({"pole": BLOCK + "maypole", "wreath": BLOCK + "maypole_wreath",
                  "ribbons": BLOCK + "maypole_ribbons", "particle": BLOCK + "maypole"}, [
        box((6, 0, 6), (10, 13, 10), {"*": "#pole", "down": None}),
        box((3, 11, 3), (13, 13, 4.5), "#wreath"),
        box((3, 11, 11.5), (13, 13, 13), "#wreath"),
        box((3, 11, 4.5), (4.5, 13, 11.5), "#wreath"),
        box((11.5, 11, 4.5), (13, 13, 11.5), "#wreath"),
        box((6.5, 13, 6.5), (9.5, 15, 9.5), "#wreath"),
        box((7.5, 15, 7.5), (8.5, 16, 8.5), "#wreath"),
    ] + cross((2, 0, 8), (14, 11, 8), "#ribbons"), ao=False)


def volador_top():
    return model({"pole": BLOCK + "volador_pole", "frame": BLOCK + "volador_frame",
                  "particle": BLOCK + "volador_pole"}, [
        box((6, 0, 6), (10, 13, 10), {"*": "#pole", "down": None}),
        box((1, 12, 1), (15, 13.5, 2.5), "#frame"),
        box((1, 12, 13.5), (15, 13.5, 15), "#frame"),
        box((1, 12, 2.5), (2.5, 13.5, 13.5), "#frame"),
        box((13.5, 12, 2.5), (15, 13.5, 13.5), "#frame"),
        box((5.5, 13, 5.5), (10.5, 15, 10.5), "#frame"),
    ], ao=False)


def rainbow_top():
    return model({"pole": BLOCK + "rainbow_pole", "star": BLOCK + "rainbow_star",
                  "ribbons": BLOCK + "rainbow_ribbons", "particle": BLOCK + "rainbow_pole"}, [
        box((6, 0, 6), (10, 12, 10), {"*": "#pole", "down": None}),
        box((6.5, 11.5, 6.5), (9.5, 15.5, 9.5), "#star"),
        box((4, 12.5, 7.25), (12, 14.5, 8.75), "#star"),
        box((7.25, 12.5, 4), (8.75, 14.5, 12), "#star"),
        box((7.25, 15.5, 7.25), (8.75, 16, 8.75), "#star"),
    ] + cross((2, 0, 8), (14, 11, 8), "#ribbons"), ao=False)


def taiko():
    return model({"head": BLOCK + "taiko_head", "side": BLOCK + "taiko_side",
                  "stand": "minecraft:block/dark_oak_planks", "particle": BLOCK + "taiko_side"}, [
        box((2, 0, 2), (4, 6, 14), "#stand"),
        box((12, 0, 2), (14, 6, 14), "#stand"),
        box((1, 3, 3), (15, 14, 13), {"*": "#side", "east": "#head", "west": "#head"}),
        box((0.5, 3.5, 3.5), (1, 13.5, 12.5), {"*": "#side", "west": "#head"}),
        box((15, 3.5, 3.5), (15.5, 13.5, 12.5), {"*": "#side", "east": "#head"}),
    ])


def yule_fire():
    log, top = "minecraft:block/spruce_log", "minecraft:block/spruce_log_top"
    return model({"log": log, "end": top, "fire": "minecraft:block/campfire_fire",
                  "ribbon": BLOCK + "nord_bunting", "particle": log}, [
        box((1, 0, 3), (15, 3, 6), {"*": "#log", "east": "#end", "west": "#end"}),
        box((1, 0, 10), (15, 3, 13), {"*": "#log", "east": "#end", "west": "#end"}),
        box((3, 3, 1), (6, 6, 15), {"*": "#log", "north": "#end", "south": "#end"}),
        box((10, 3, 1), (13, 6, 15), {"*": "#log", "north": "#end", "south": "#end"}),
    ] + cross((1, 1, 8), (15, 16, 8), "#fire"), ao=False)


def forge():
    return model({"top": BLOCK + "festival_forge_coals", "side": BLOCK + "festival_forge_side",
                  "rim": "minecraft:block/polished_deepslate",
                  "particle": BLOCK + "festival_forge_side"}, [
        box((1, 0, 1), (15, 9, 15), {"*": "#side", "up": "#top", "down": "#rim"}),
        box((0.5, 9, 0.5), (15.5, 10, 15.5), {"*": "#rim", "up": None}),
        box((2, 9, 2), (14, 10.5, 14), {"*": None, "up": "#top"}),
    ])


def glow_tree():
    return model({"log": "minecraft:block/birch_log", "leaves": BLOCK + "glow_tree_leaves",
                  "particle": BLOCK + "glow_tree_leaves"}, [
        box((7, 0, 7), (9, 8, 9), "#log"),
        box((3.5, 6, 3.5), (12.5, 13, 12.5), "#leaves"),
        box((5, 13, 5), (11, 16, 11), "#leaves"),
    ], ao=False)


def target():
    out = model({"face": BLOCK + "archery_target", "top": "minecraft:block/hay_block_top",
                 "particle": BLOCK + "archery_target"}, [
        box((0, 0, 0), (16, 16, 16), {"*": "#face", "up": "#top", "down": "#top"}),
    ])
    return out


def bunting(people):
    out = model({"flags": BLOCK + people + "_bunting", "particle": BLOCK + people + "_bunting"}, [
        box((0, 3, 8), (16, 13, 8), {"*": None, "north": "#flags", "south": "#flags"},
            uv={"north": [0, 3, 16, 13], "south": [0, 3, 16, 13]}),
    ], ao=False)
    return out


def pie(bites):
    left = 1 + 2 * bites
    textures = {"top": BLOCK + "feast_pie_top", "side": BLOCK + "feast_pie_side",
                "bottom": BLOCK + "feast_pie_bottom", "inside": BLOCK + "feast_pie_inner",
                "particle": BLOCK + "feast_pie_side"}
    faces = {"*": "#side", "up": "#top", "down": "#bottom",
             "west": "#inside" if bites else "#side"}
    uv = {"north": [1, 8, 15, 16], "south": [1, 8, 15, 16], "east": [1, 8, 15, 16],
          "west": [1, 8, 15, 16], "up": [left, 1, 15, 15], "down": [left, 1, 15, 15]}
    return model(textures, [box((left, 0, 1), (15, 8, 15), faces, uv=uv)])


def trophy():
    gold = "#gold"
    return model({"gold": "minecraft:block/gold_block",
                  "base": "minecraft:block/polished_blackstone",
                  "particle": "minecraft:block/gold_block"}, [
        box((4, 0, 4), (12, 2, 12), "#base"),
        box((6.5, 2, 6.5), (9.5, 3, 9.5), gold),
        box((7.25, 3, 7.25), (8.75, 6, 8.75), gold),
        box((5, 6, 5), (11, 12, 11), gold),
        box((4.5, 11.5, 4.5), (11.5, 12.5, 11.5), gold),
        box((3.5, 7.5, 7.25), (5, 10.5, 8.75), gold),
        box((11, 7.5, 7.25), (12.5, 10.5, 8.75), gold),
    ])


def token(kind):
    tex = {"t": BLOCK + "token_" + kind, "particle": BLOCK + "token_" + kind}
    shapes = {
        "egg": [box((6, 0, 6), (10, 1, 10), "#t"), box((5, 1, 5), (11, 6, 11), "#t"),
                box((6, 6, 6), (10, 8, 10), "#t")],
        "jade": [box((5, 0, 5), (11, 1, 11), "#t"), box((6, 1, 6), (10, 6, 10), "#t"),
                 box((6.5, 6, 6.5), (9.5, 8, 9.5), "#t")],
        "omamori": [box((5.5, 0, 7), (10.5, 7, 9), "#t"), box((7, 7, 7.5), (9, 9, 8.5), "#t")],
        "rune": [box((4, 0, 4), (12, 2, 12), "#t"), box((5, 2, 5), (11, 3, 11), "#t")],
        "horseshoe": [box((3, 0.1, 3), (13, 0.1, 13), {"*": None, "up": "#t"},
                          uv={"up": [0, 0, 16, 16]})],
        "gem": cross((4, 0, 8), (12, 12, 8), "#t"),
        "firefly": [box((7, 5, 7), (9, 7, 9), "#t")] + cross((4.5, 2.5, 8), (11.5, 9.5, 8), "#t"),
    }
    return model(tex, shapes[kind], ao=kind not in ("gem", "firefly", "horseshoe"))


# --- шапки --------------------------------------------------------------------------
#
# Голова в пространстве модели шапки — 1.6…14.4; макушка — 14.4.

HAT_DISPLAY = {
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, -4, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -3, 0], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, -1, 0],
                              "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0],
                              "scale": [0.4, 0.4, 0.4]},
}


def ring(y0, y1, texture, inset=1.2, width=1.4):
    """Обруч вокруг головы: четыре бруска по краю макушки."""
    lo, hi = inset, 16 - inset
    return [
        box((lo, y0, lo), (hi, y1, lo + width), texture),
        box((lo, y0, hi - width), (hi, y1, hi), texture),
        box((lo, y0, lo + width), (lo + width, y1, hi - width), texture),
        box((hi - width, y0, lo + width), (hi, y1, hi - width), texture),
    ]


def hat(textures, elements):
    """Модель шапки: развёртка каждой грани задана явно.

    Шапка поднимается выше блока (колпак — до 25), а развёртка по умолчанию
    считается от координат элемента: для всего, что выше 16, она уходит
    в минус, и игра взяла бы для грани текстуру соседа по атласу. Узор
    у шапок сплошной, поэтому грани берут окно своего размера из угла.
    """
    for element in elements:
        (x0, y0, z0), (x1, y1, z1) = element["from"], element["to"]
        sizes = {"north": (x1 - x0, y1 - y0), "south": (x1 - x0, y1 - y0),
                 "east": (z1 - z0, y1 - y0), "west": (z1 - z0, y1 - y0),
                 "up": (x1 - x0, z1 - z0), "down": (x1 - x0, z1 - z0)}
        for face, spec in element["faces"].items():
            if "uv" not in spec:
                width, height = sizes[face]
                spec["uv"] = [0, 0, round(min(16, max(width, 0.5)), 3),
                              round(min(16, max(height, 0.5)), 3)]
    return {"textures": dict(textures, particle=list(textures.values())[0]),
            "elements": elements, "display": HAT_DISPLAY}


def norman_wreath():
    elements = ring(13.4, 15.6, "#w")
    for x, z in ((3, 1), (8, 0.8), (13, 1), (15, 8), (1, 8), (4, 15), (11, 15)):
        elements.append(box((x - 1, 15.2, z - 0.4), (x + 1, 16.6, z + 0.8), "#f"))
    return hat({"w": ITEM + "hat_wreath", "f": ITEM + "hat_wreath_flowers"}, elements)


def maya_feather_crown():
    elements = ring(12.6, 15.0, "#band")
    # Четыре пера врозь, у каждого свой цвет: перо берёт свою полосу текстуры.
    for i, x in enumerate((3.0, 6.3, 9.7, 13.0)):
        tall = 25 if i in (1, 2) else 22
        window = [4 * i + 1, 0, 4 * i + 3, 16]
        elements.append(box((x - 1.2, 14.6, 1.0), (x + 1.2, tall, 1.0),
                            {"*": None, "north": "#f", "south": "#f"},
                            uv={"north": window, "south": window},
                            rotation={"origin": [x, 14.6, 1], "axis": "x", "angle": -22.5}))
    return hat({"band": ITEM + "hat_headband", "f": ITEM + "hat_feathers"}, elements)


def kitsune_mask():
    # Маска сдвинута на правый бок головы, как носят на празднике: восток модели —
    # правый бок головы. Уши торчат над краем маски.
    return hat({"m": ITEM + "hat_kitsune", "e": ITEM + "hat_kitsune_ear"}, [
        box((14.4, 4, 3), (15.4, 14, 13), {"*": "#m"},
            uv={"east": [0, 0, 16, 16], "west": [0, 0, 16, 16]}),
        box((14.4, 14, 3.5), (15.2, 17, 6), "#e"),
        box((14.4, 14, 10), (15.2, 17, 12.5), "#e"),
        box((15.4, 7, 7), (16.6, 9.5, 9), "#m"),
    ])


def nord_straw_crown():
    elements = ring(13.4, 15.4, "#s")
    for x, z in ((2, 2), (8, 1.2), (14, 2), (14.8, 8), (14, 14), (8, 14.8), (2, 14), (1.2, 8)):
        elements.append(box((x - 0.6, 15.4, z - 0.6), (x + 0.6, 18.2, z + 0.6), "#s"))
    return hat({"s": ITEM + "hat_straw"}, elements)


def pony_party_hat():
    elements = []
    for step, (half, y) in enumerate(((3.6, 14.2), (2.8, 16.2), (2.0, 18.2), (1.2, 20.2))):
        elements.append(box((8 - half, y, 8 - half), (8 + half, y + 2, 8 + half), "#p"))
    elements.append(box((6.8, 22.2, 6.8), (9.2, 24.6, 9.2), "#pom"))
    return hat({"p": ITEM + "hat_party", "pom": ITEM + "hat_pompom"}, elements)


def dwarf_candle_cap():
    return hat({"c": ITEM + "hat_cap", "k": ITEM + "hat_candle"}, [
        box((1.0, 12.6, 1.0), (15.0, 15.6, 15.0), "#c"),
        box((3.0, 15.6, 3.0), (13.0, 17.0, 13.0), "#c"),
        box((0.6, 12.6, -0.6), (15.4, 13.4, 1.0), "#c"),
        box((6.8, 14.0, -0.4), (9.2, 19.5, 1.6), "#k",
            uv={face: [6, 0, 9, 16] for face in ("north", "south", "east", "west")}
            | {"up": [6, 0, 9, 3], "down": [6, 12, 9, 15]}),
    ])


def elf_firefly_wreath():
    elements = ring(13.8, 14.8, "#c", width=0.9)
    for x, y, z in ((3, 17, 2), (13, 16.5, 3), (8, 18, 1), (14.5, 17.5, 11), (2, 16, 13),
                    (9, 17, 15)):
        elements.append(box((x - 0.6, y - 0.6, z - 0.6), (x + 0.6, y + 0.6, z + 0.6), "#g"))
    return hat({"c": ITEM + "hat_circlet", "g": ITEM + "hat_firefly"}, elements)


def glow_texture():
    t = Tex(GLOW)
    t.rect(6, 6, 9, 9, GLOW_L)
    return t


def wreath_flowers():
    t = Tex(WHITE)
    t.disc(7.5, 7.5, 4.5, RED)
    t.disc(7.5, 7.5, 2.0, GOLD)
    return t


def kitsune_ear():
    t = Tex(rgb(0xF6F2EA))
    t.rect(4, 4, 11, 15, RED)
    return t


# --- что пишется -------------------------------------------------------------------

TEXTURES_BLOCK = {
    "maypole": lambda: spiral(PEOPLE_COLOURS["norman"], WHITE),
    "maypole_wreath": wreath,
    "maypole_ribbons": lambda: streamers(PEOPLE_COLOURS["norman"] + [GOLD]),
    "volador_pole": lambda: banded(PEOPLE_COLOURS["maya"], WOOD),
    "volador_frame": frame_wood,
    "rainbow_pole": lambda: spiral(RAINBOW, WHITE),
    "rainbow_star": star,
    "rainbow_ribbons": lambda: streamers(RAINBOW),
    "taiko_head": taiko_head,
    "taiko_side": taiko_side,
    "festival_forge_coals": forge_coals,
    "festival_forge_side": forge_side,
    "glow_tree_leaves": glow_leaves,
    "archery_target": target_face,
    "feast_pie_top": pie_top,
    "feast_pie_side": pie_side,
    "feast_pie_bottom": pie_bottom,
    "feast_pie_inner": pie_inner,
    "token_egg": token_egg,
    "token_jade": token_jade,
    "token_omamori": token_omamori,
    "token_rune": token_rune,
    "token_horseshoe": token_horseshoe,
    "token_gem": token_gem,
    "token_firefly": token_firefly,
}
for _people, _colours in PEOPLE_COLOURS.items():
    TEXTURES_BLOCK[_people + "_bunting"] = (lambda colours: lambda: pennants(colours))(_colours)

TEXTURES_ITEM = {
    "festival_ribbon": ribbon,
    "festival_bow": lambda: bow(-1),
    "festival_bow_pulling_0": lambda: bow(0),
    "festival_bow_pulling_1": lambda: bow(1),
    "festival_bow_pulling_2": lambda: bow(2),
    "juggling_balls": juggling_balls,
    "hat_wreath": wreath,
    "hat_wreath_flowers": wreath_flowers,
    "hat_feathers": feathers,
    "hat_headband": headband,
    "hat_kitsune": kitsune,
    "hat_kitsune_ear": kitsune_ear,
    "hat_straw": straw,
    "hat_party": party,
    "hat_pompom": pompom,
    "hat_cap": leather_cap,
    "hat_candle": candle,
    "hat_circlet": circlet,
    "hat_firefly": glow_texture,
}

POLES = {"maypole": maypole_top, "volador_pole": volador_top, "rainbow_pole": rainbow_top}

HEARTS = {
    "taiko_drum": dict(model=taiko, facing=True),
    "yule_fire": dict(model=yule_fire, facing=False),
    "festival_forge": dict(model=forge, facing=False),
    "glow_tree": dict(model=glow_tree, facing=False),
}

HATS = {
    "norman_wreath": (norman_wreath, "Праздничный венок", "Festival Wreath"),
    "maya_feather_crown": (maya_feather_crown, "Корона из перьев", "Feather Crown"),
    "kitsune_mask": (kitsune_mask, "Маска кицунэ", "Kitsune Mask"),
    "nord_straw_crown": (nord_straw_crown, "Соломенная корона", "Straw Crown"),
    "pony_party_hat": (pony_party_hat, "Праздничный колпак", "Party Hat"),
    "dwarf_candle_cap": (dwarf_candle_cap, "Колпак со свечой", "Candle Cap"),
    "elf_firefly_wreath": (elf_firefly_wreath, "Венок светлячков", "Firefly Circlet"),
}

NAMES = {
    "block.villagepax.maypole": ("Майское дерево", "Maypole"),
    "block.villagepax.volador_pole": ("Шест воладоров", "Volador Pole"),
    "block.villagepax.rainbow_pole": ("Радужный столб", "Rainbow Pole"),
    "block.villagepax.taiko_drum": ("Барабан тайко", "Taiko Drum"),
    "block.villagepax.yule_fire": ("Йольский костёр", "Yule Fire"),
    "block.villagepax.festival_forge": ("Праздничный горн", "Festival Forge"),
    "block.villagepax.glow_tree": ("Светящееся деревце", "Glow Tree"),
    "block.villagepax.archery_target": ("Соломенная мишень", "Straw Archery Target"),
    "block.villagepax.feast_pie": ("Праздничный пирог", "Feast Pie"),
    "block.villagepax.trophy": ("Кубок", "Trophy"),
    "block.villagepax.festival_token": ("Праздничная вещица", "Festival Trinket"),
    "item.villagepax.festival_ribbon": ("Праздничная лента", "Festival Ribbon"),
    "item.villagepax.festival_bow": ("Праздничный лук", "Festival Bow"),
    "item.villagepax.juggling_balls": ("Мячики жонглёра", "Juggling Balls"),
}

RECIPES = {
    "maypole": (["S", "P", "S"], {"S": "minecraft:stick", "P": "minecraft:dark_oak_planks"}, 2),
    "volador_pole": (["S", "P", "S"], {"S": "minecraft:stick", "P": "minecraft:jungle_planks"}, 2),
    "rainbow_pole": (["S", "P", "S"], {"S": "minecraft:stick", "P": "minecraft:acacia_planks"}, 2),
    "taiko_drum": (["PPP", "S S"], {"S": "minecraft:stick", "P": "minecraft:cherry_planks"}, 1),
    "yule_fire": ([" L ", "LSL", " L "], {"S": "minecraft:stick", "L": "minecraft:spruce_log"}, 1),
    "festival_forge": (["CCC", "CSC"], {"S": "minecraft:stick", "C": "minecraft:cobblestone"}, 1),
    "glow_tree": (["SLS"], {"S": "minecraft:stick", "L": "minecraft:birch_log"}, 1),
    "archery_target": ([" P ", "PSP", " P "], {"S": "minecraft:stick",
                                                "P": {"tag": "minecraft:planks"}}, 1),
}
for _people, _planks in BUNTING_PLANKS.items():
    RECIPES[_people + "_bunting"] = (["SPS"], {"S": "minecraft:stick", "P": _planks}, 3)


def variants_facing(name):
    return {"facing=north": {"model": BLOCK + name},
            "facing=east": {"model": BLOCK + name, "y": 90},
            "facing=south": {"model": BLOCK + name, "y": 180},
            "facing=west": {"model": BLOCK + name, "y": 270}}


def drop_self(name, nbt=False):
    entry = {"type": "minecraft:item", "name": "villagepax:" + name}
    if nbt:
        entry["functions"] = [{"function": "minecraft:copy_nbt", "source": "block_entity",
                               "ops": [{"source": "Engraving", "target": "BlockEntityTag.Engraving",
                                        "op": "replace"}]}]
    return {"type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [entry],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


NOTHING = {"type": "minecraft:block", "pools": []}


def main():
    for name, paint in TEXTURES_BLOCK.items():
        paint().save("block", name)
    for name, paint in TEXTURES_ITEM.items():
        paint().save("item", name)

    names_ru, names_en = {}, {}
    for key, (ru, en) in NAMES.items():
        names_ru[key], names_en[key] = ru, en

    # Столбы: звено и верх.
    for name, top in POLES.items():
        write_json(ASSETS / "models/block" / (name + ".json"), pole(name))
        write_json(ASSETS / "models/block" / (name + "_top.json"), top())
        write_json(ASSETS / "blockstates" / (name + ".json"), {"variants": {
            "top=false": {"model": BLOCK + name}, "top=true": {"model": BLOCK + name + "_top"}}})
        write_json(ASSETS / "models/item" / (name + ".json"), {"parent": BLOCK + name + "_top"})
        write_json(DATA / "loot_tables/blocks" / (name + ".json"), drop_self(name))

    for name, spec in HEARTS.items():
        write_json(ASSETS / "models/block" / (name + ".json"), spec["model"]())
        variants = variants_facing(name) if spec["facing"] else {"": {"model": BLOCK + name}}
        write_json(ASSETS / "blockstates" / (name + ".json"), {"variants": variants})
        write_json(ASSETS / "models/item" / (name + ".json"), {"parent": BLOCK + name})
        write_json(DATA / "loot_tables/blocks" / (name + ".json"), drop_self(name))

    write_json(ASSETS / "models/block/archery_target.json", target())
    write_json(ASSETS / "blockstates/archery_target.json",
               {"variants": {"": {"model": BLOCK + "archery_target"}}})
    write_json(ASSETS / "models/item/archery_target.json", {"parent": BLOCK + "archery_target"})
    write_json(DATA / "loot_tables/blocks/archery_target.json", drop_self("archery_target"))

    for people in PEOPLE_COLOURS:
        name = people + "_bunting"
        write_json(ASSETS / "models/block" / (name + ".json"), bunting(people))
        write_json(ASSETS / "blockstates" / (name + ".json"), {"variants": {
            "axis=x": {"model": BLOCK + name}, "axis=z": {"model": BLOCK + name, "y": 90}}})
        write_json(ASSETS / "models/item" / (name + ".json"),
                   {"parent": "minecraft:item/generated",
                    "textures": {"layer0": BLOCK + name}})
        write_json(DATA / "loot_tables/blocks" / (name + ".json"), drop_self(name))
        names_ru["block.villagepax." + name] = "Флажки " + PEOPLE_RU[people]
        names_en["block.villagepax." + name] = PEOPLE_EN[people] + " Bunting"

    for bites in range(7):
        suffix = "" if bites == 0 else "_slice%d" % bites
        write_json(ASSETS / "models/block" / ("feast_pie" + suffix + ".json"), pie(bites))
    write_json(ASSETS / "blockstates/feast_pie.json", {"variants": {
        "bites=%d" % b: {"model": BLOCK + "feast_pie" + ("" if b == 0 else "_slice%d" % b)}
        for b in range(7)}})
    write_json(ASSETS / "models/item/feast_pie.json", {"parent": BLOCK + "feast_pie"})
    write_json(DATA / "loot_tables/blocks/feast_pie.json", NOTHING)

    write_json(ASSETS / "models/block/trophy.json", trophy())
    write_json(ASSETS / "blockstates/trophy.json", {"variants": {"": {"model": BLOCK + "trophy"}}})
    write_json(ASSETS / "models/item/trophy.json", {"parent": BLOCK + "trophy"})
    write_json(DATA / "loot_tables/blocks/trophy.json", drop_self("trophy", nbt=True))

    kinds = ("egg", "jade", "omamori", "rune", "horseshoe", "gem", "firefly")
    for kind in kinds:
        write_json(ASSETS / "models/block" / ("token_" + kind + ".json"), token(kind))
    write_json(ASSETS / "blockstates/festival_token.json", {"variants": {
        "kind=" + kind: {"model": BLOCK + "token_" + kind} for kind in kinds}})
    write_json(ASSETS / "models/item/festival_token.json", {"parent": BLOCK + "token_egg"})
    write_json(DATA / "loot_tables/blocks/festival_token.json", NOTHING)

    for name in ("festival_ribbon", "juggling_balls"):
        write_json(ASSETS / "models/item" / (name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": ITEM + name}})
    write_json(ASSETS / "models/item/festival_bow.json", {
        "parent": "minecraft:item/bow", "textures": {"layer0": ITEM + "festival_bow"},
        "overrides": [
            {"predicate": {"pulling": 1}, "model": ITEM + "festival_bow_pulling_0"},
            {"predicate": {"pulling": 1, "pull": 0.65}, "model": ITEM + "festival_bow_pulling_1"},
            {"predicate": {"pulling": 1, "pull": 0.9}, "model": ITEM + "festival_bow_pulling_2"}]})
    for stage in range(3):
        write_json(ASSETS / "models/item" / ("festival_bow_pulling_%d.json" % stage),
                   {"parent": "minecraft:item/bow",
                    "textures": {"layer0": ITEM + "festival_bow_pulling_%d" % stage}})

    for name, (build, ru, en) in HATS.items():
        write_json(ASSETS / "models/item" / (name + ".json"), build())
        names_ru["item.villagepax." + name] = ru
        names_en["item.villagepax." + name] = en

    for name, (pattern, key, count) in RECIPES.items():
        write_json(DATA / "recipes" / (name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: ingredient(v) for k, v in key.items()},
            "result": {"item": "villagepax:" + name, "count": count}})

    hearts = ["villagepax:" + name for name in list(POLES) + list(HEARTS)]
    buntings = ["villagepax:%s_bunting" % people for people in PEOPLE_COLOURS]
    merge_tag(TAGS / "mineable/axe.json",
              ["villagepax:" + n for n in list(POLES) + ["taiko_drum", "yule_fire", "glow_tree",
                                                          "archery_target", "festival_token"]]
              + buntings)
    merge_tag(TAGS / "mineable/pickaxe.json", ["villagepax:festival_forge", "villagepax:trophy"])
    merge_tag(TAGS / "mineable/hoe.json", ["villagepax:feast_pie"])
    merge_tag(DATA / "tags/blocks/build_decor.json", hearts + buntings + ["villagepax:archery_target"])
    merge_tag(DATA / "tags/blocks/guest_usable.json",
              ["villagepax:festival_token", "villagepax:feast_pie", "villagepax:trophy",
               "villagepax:taiko_drum"])
    write_json(DATA / "tags/blocks/festival_hearts.json", {"replace": False, "values": hearts})

    for code, names in (("ru_ru", names_ru), ("en_us", names_en)):
        path = ASSETS / "lang" / (code + ".json")
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(names)
        write_json(path, data)
    print("праздник: %d текстур блоков, %d предметов, %d шапок"
          % (len(TEXTURES_BLOCK), len(TEXTURES_ITEM), len(HATS)))


if __name__ == "__main__":
    main()
