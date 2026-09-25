#!/usr/bin/env python3
"""Рисует схемы зданий в изометрии — чтобы увидеть силуэт без игры.

Не отрисовщик блоков, а макет: у каждого блока свой цвет, у граней
свой свет, плиты вдвое ниже, воздух и маркеры не рисуются. Этого
хватает, чтобы увидеть главное — пропорции, кровлю, свес и вход, —
а ошибаются в схемах именно в них.

    python tools/preview-schematics.py yamato
    python tools/preview-schematics.py nord/house_lvl1 yamato/shrine_lvl1
"""

import importlib.util
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("schematics", HERE / "make-schematics.py")
ms = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ms)

OUT = HERE.parent / "build/schematic-preview.png"

# Цвет блока по слову в его имени: первое совпадение побеждает.
COLOURS = (
    ("deepslate_tile", (58, 58, 66)), ("deepslate", (70, 70, 78)), ("blackstone", (40, 36, 40)),
    ("stripped_mangrove", (196, 70, 56)), ("mangrove", (120, 50, 45)),
    ("cherry_leaves", (238, 168, 196)), ("pink_petals", (240, 150, 190)),
    ("cherry", (226, 178, 172)), ("dark_oak", (74, 50, 30)), ("spruce", (96, 70, 44)),
    ("acacia", (176, 92, 50)), ("jungle", (150, 110, 70)), ("birch", (212, 200, 150)),
    ("oak", (160, 128, 80)), ("plaster", (236, 232, 222)), ("ochre", (212, 150, 70)),
    ("timber_frame", (120, 86, 52)), ("thatch", (206, 176, 90)),
    ("white_stained_glass", (242, 242, 246)), ("glass", (190, 220, 235)),
    ("stone_brick", (128, 128, 128)), ("cobble", (112, 112, 112)), ("stone", (125, 125, 125)),
    ("gravel", (140, 134, 128)), ("farmland", (92, 60, 34)), ("dirt", (120, 86, 56)),
    ("grass", (96, 150, 60)), ("water", (60, 100, 210)), ("carrot", (70, 170, 60)),
    ("wheat", (200, 180, 80)), ("hay", (210, 180, 60)), ("sapling", (70, 150, 60)),
    ("lantern", (255, 206, 90)), ("torch", (255, 210, 110)), ("campfire", (240, 120, 40)),
    ("bed", (230, 230, 236)), ("banner", (60, 90, 170)), ("wool", (240, 240, 240)),
    ("brick", (150, 70, 55)), ("bell", (230, 190, 70)), ("barrel", (120, 84, 50)),
    ("bench", (140, 100, 60)), ("table", (140, 100, 60)), ("shelf", (130, 94, 58)),
    ("carved_stone", (170, 160, 140)), ("altar", (190, 180, 160)), ("chimney", (110, 70, 60)),
)

SKIP = ("marker_", "ladder")


def colour_of(block):
    name = block.split(":")[1]
    for word, rgb in COLOURS:
        if word in name:
            return rgb
    return (200, 0, 200)


def shade(rgb, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


def draw(name, px=10):
    layers = ms.SCHEMATICS[name]
    height, depth, width = len(layers), len(layers[0]), len(layers[0][0])
    # Изометрия: x вправо-вниз, z влево-вниз, y вверх.
    w = (width + depth) * px + px * 2
    h = (width + depth) * px // 2 + height * px + px * 3
    image = Image.new("RGBA", (w, h), (34, 36, 42, 255))
    pen = ImageDraw.Draw(image)
    ox, oy = depth * px + px, height * px + px

    def point(x, y, z):
        return (ox + (x - z) * px, oy + (x + z) * px // 2 - y * px)

    cells = []
    for y, layer in enumerate(layers):
        for z0, row in enumerate(layer):
            # Север — к зрителю: входы у зданий мода с северной стороны.
            z = depth - 1 - z0
            for x, symbol in enumerate(row):
                block, props = ms.LEGEND[symbol]
                # Воздух — по имени целиком: «stairs» тоже содержит «air».
                if block == "minecraft:air" or any(word in block for word in SKIP):
                    continue
                top = 0.5 if props.get("type") == "bottom" or "carpet" in block \
                    or "pink_petals" in block else 1.0
                cells.append((x + z + y, x, y, z, colour_of(block), top))
    # Глубина в изометрии — сумма трёх координат: ближе к зрителю тот,
    # у кого она больше, и рисуется он позже.
    cells.sort(key=lambda c: (c[0], c[2]))
    for _, x, y, z, rgb, top in cells:
        yt = y + top
        a, b = point(x, yt, z), point(x + 1, yt, z)
        c, d = point(x + 1, yt, z + 1), point(x, yt, z + 1)
        pen.polygon([a, b, c, d], fill=shade(rgb, 1.08))
        pen.polygon([d, c, point(x + 1, y, z + 1), point(x, y, z + 1)], fill=shade(rgb, 0.78))
        pen.polygon([b, c, point(x + 1, y, z + 1), point(x + 1, y, z)], fill=shade(rgb, 0.62))
    pen.text((4, 2), name, fill=(220, 220, 220, 255))
    return image


def main():
    wanted = sys.argv[1:] or ["yamato"]
    names = [n for n in ms.SCHEMATICS
             if any(n == w or n.startswith(w.rstrip("/") + "/") for w in wanted)]
    images = [draw(n) for n in names]
    columns = 4
    cell_w = max(i.width for i in images)
    cell_h = max(i.height for i in images)
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new("RGBA", (cell_w * columns, cell_h * rows), (34, 36, 42, 255))
    for i, image in enumerate(images):
        sheet.alpha_composite(image, ((i % columns) * cell_w, (i // columns) * cell_h))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("лист:", OUT, len(images), "схем")


if __name__ == "__main__":
    main()
