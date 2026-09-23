#!/usr/bin/env python3
"""Рисует жителя по той же geo-модели, что читает игра, — чтобы смотреть без игры.

Зачем: у соседнего preview-models.py тот же довод, и он оправдался дважды.
Модель жителя теперь приходит из geo-файла, сложение народа считается
из одного числа, и ошибиться в обоих можно молча: игра не скажет ничего,
просто гном выйдет жердью.

Здесь — вид спереди, собранный из настоящих кубов модели и настоящих
текстур. Ни света, ни поз, ни анимации: проверяется одно — силуэт,
и именно в нём ошибаются. Вид сбоку пробовался и выброшен: у двуногого
он показывает столбик и ничего не говорит.

    python tools/preview-citizens.py

Правило сложения повторено здесь нарочно и сверяется проверкой
StatureTest на стороне Java: это не копия ради удобства, а вторая пара
глаз на то же правило.
"""

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/villagepax"
GEO = ASSETS / "geo/entity/citizen.geo.json"
SKINS = ASSETS / "textures/entity/citizen"
CULTURES = ROOT / "src/main/resources/data/villagepax/villagepax/cultures"
OUT = ROOT / "build/citizen-preview.png"

SCALE = 9          # пикселей на блок модели
PAD = 8


def stature_of(height):
    """Рост, ширина и голова — тем же правилом, что в Stature.java."""
    if not height or height <= 0:
        height = 1.0
    shortfall = 1.0 - height
    return height, 1.0 + shortfall * 0.8, 1.0 + shortfall * 0.5


def faces(u, v, width, height, depth):
    """Шесть граней куба в раскладке Minecraft — та же, что у генератора кож."""
    return {
        "up": (u + depth, v, width, depth),
        "front": (u + depth, v + depth, width, height),
        "left": (u + depth + width, v + depth, depth, height),
        "right": (u, v + depth, depth, height),
    }


def cubes():
    """Кубы модели в мировых мерах, с пометкой, чья это кость."""
    model = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    out = []
    for bone in model["bones"]:
        for cube in bone.get("cubes", []):
            out.append((bone["name"], cube["origin"], cube["size"],
                        faces(cube["uv"][0], cube["uv"][1], *cube["size"])))
    return out


def paint(canvas, skin, box, uv, flip=False):
    """Положить грань текстуры в прямоугольник холста, пиксель в пиксель."""
    x, y, width, height = box
    u, v, uw, uh = uv
    if width <= 0 or height <= 0 or uw <= 0 or uh <= 0:
        return
    part = skin.crop((u, v, u + uw, v + uh))
    if flip:
        part = part.transpose(Image.FLIP_LEFT_RIGHT)
    part = part.resize((int(width), int(height)), Image.NEAREST)
    canvas.paste(part, (int(x), int(y)), part)


def figure(skin, height, girth, head, side=False):
    """Один житель: вид спереди или сбоку, в пикселях модели."""
    tall, wide = 32, 16
    canvas = Image.new("RGBA", (int(wide * 2 * SCALE), int(tall * SCALE + 2 * SCALE)),
                       (0, 0, 0, 0))
    middle = wide * SCALE

    # Дальние кубы первыми: рисуем по глубине, ближний перекрывает дальний.
    order = 0 if side else 2
    for name, origin, size, grain in sorted(cubes(), key=lambda c: -c[1][order]):
        scale = head if name == "head" else 1.0
        # Голова тянется от шеи вверх, а не от пола: иначе крупная голова
        # уезжала бы вместе с туловищем.
        lift = 24 * height if name == "head" else 0.0
        ox, oy, oz = origin
        sx, sy, sz = size

        px = (oz if side else ox) * girth * scale
        pw = (sz if side else sx) * girth * scale
        py = oy * height * scale + (lift * (1 - scale) if name == "head" else 0)
        ph = sy * height * scale

        box = (middle + px * SCALE, (tall - py - ph) * SCALE + SCALE,
               pw * SCALE, ph * SCALE)
        paint(canvas, skin, box, grain["left" if side else "front"])
    return canvas


def main():
    peoples = []
    for path in sorted(CULTURES.glob("*.json")):
        people = path.stem
        declared = json.loads(path.read_text(encoding="utf-8")).get("stature", 1.0)
        peoples.append((people, declared))

    cell_w, cell_h = 34 * SCALE + PAD, 34 * SCALE + PAD
    sheet = Image.new("RGBA", (cell_w * len(peoples), cell_h), (24, 24, 28, 255))

    for column, (people, declared) in enumerate(peoples):
        skin_path = SKINS / people / "male.png"
        if not skin_path.exists():
            continue
        skin = Image.open(skin_path).convert("RGBA")
        height, girth, head = stature_of(declared)
        drawn = figure(skin, height, girth, head)
        sheet.paste(drawn, (column * cell_w + PAD // 2, PAD // 2), drawn)
        print("%-8s рост %.2f, ширина %.2f, голова %.2f" % (people, height, girth, head))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("лист:", OUT)


if __name__ == "__main__":
    main()
