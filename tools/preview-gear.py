#!/usr/bin/env python3
"""Рисует снаряжение народов на человеке — тем же отрисовщиком, что и жителей.

Броня — модель GeckoLib поверх тела игрока, и ошибиться в ней легко
молча: рог уйдёт в затылок, перья — в плечо. Лист показывает каждый
набор спереди и со спины, надетым на человека, без запуска игры.

    python tools/preview-gear.py
"""

import importlib.util
import json
import math
from pathlib import Path

import numpy
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("preview_citizens", HERE / "preview-citizens.py")
pc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(pc)

ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/villagepax"
PEOPLES = ("norman", "maya", "pony", "dwarf", "elf", "nord", "yamato")
OUT = ROOT / "build/gear-preview.png"


def raster(quads, px):
    size = int(46 * px)
    colour = numpy.zeros((size, size, 4), dtype=numpy.uint8)
    depth = numpy.full((size, size), numpy.inf)
    middle_x, floor_y = size / 2, size - 6 * px
    for _, points, shade in quads:
        xs = numpy.array([middle_x - p[0] * px for p in points])
        ys = numpy.array([floor_y - p[1] * px for p in points])
        zs = numpy.array([p[2] for p in points])
        left, right = max(0, int(xs.min())), min(size - 1, int(math.ceil(xs.max())))
        top, bottom = max(0, int(ys.min())), min(size - 1, int(math.ceil(ys.max())))
        if left > right or top > bottom:
            continue
        gx, gy = numpy.meshgrid(numpy.arange(left, right + 1) + 0.5,
                                numpy.arange(top, bottom + 1) + 0.5)
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            x0, y0, x1, y1, x2, y2 = xs[a], ys[a], xs[b], ys[b], xs[c], ys[c]
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            w0 = ((x1 - gx) * (y2 - gy) - (x2 - gx) * (y1 - gy)) / area
            w1 = ((x2 - gx) * (y0 - gy) - (x0 - gx) * (y2 - gy)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * zs[a] + w1 * zs[b] + w2 * zs[c]
            window = depth[top:bottom + 1, left:right + 1]
            nearer = inside & (z < window - 1e-4)
            window[nearer] = z[nearer]
            colour[top:bottom + 1, left:right + 1][nearer] = shade + (255,)
    return Image.fromarray(colour, "RGBA")


def main():
    body = json.loads((ASSETS / "geo/entity/citizen.geo.json").read_text(encoding="utf-8"))
    skin_path = ASSETS / "textures/entity/citizen/norman/male.png"
    skin = Image.open(skin_path).convert("RGBA")
    words = pc.words_of(skin_path)
    px = 5
    views = (-28, 152)
    cell = int(46 * px)
    sheet = Image.new("RGBA", (cell * len(views) * 3, (cell + 14) * 3), (30, 32, 38, 255))
    label = ImageDraw.Draw(sheet)
    for index, people in enumerate(PEOPLES):
        armour = json.loads((ASSETS / "geo/armor" / (people + ".geo.json")).read_text(
            encoding="utf-8"))
        texture = Image.open(ASSETS / "textures/armor" / (people + ".png")).convert("RGBA")
        for v, yaw in enumerate(views):
            view = pc.mul(pc.rot_x(math.radians(12)), pc.rot_y(math.radians(yaw)))
            quads = pc.quads_of(body, skin, {}, words, (1.0, 1.0, 1.0), view)
            quads += pc.quads_of(armour, texture, {}, set(), (1.0, 1.0, 1.0), view)
            x = ((index % 3) * len(views) + v) * cell
            y = (index // 3) * (cell + 14)
            sheet.alpha_composite(raster(quads, px), (x, y))
            label.text((x + 4, y + cell), people, fill=(210, 210, 210, 255))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("лист:", OUT)


if __name__ == "__main__":
    main()
