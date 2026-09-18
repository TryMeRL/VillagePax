#!/usr/bin/env python3
"""Рисует блочные модели мода в изометрии — чтобы смотреть на них без игры.

Зачем: заказчик дважды поймал кривизну, которой я не видел, — плоское
бельё и вещи, налезающие друг на друга. Тексты и тесты такого не ловят,
а запускать клиент ради каждой правки модели нельзя.

Здесь — маленький отрисовщик: он читает ту же JSON-модель, что и игра,
и складывает её грани в изометрию, беря цвета из настоящих текстур.
Это не игра и на неё не похоже: нет ни света, ни теней, ни поворота
блока. Но геометрию он показывает честно — а ошибался я именно в ней.

    python tools/preview-models.py                # все модели мода
    python tools/preview-models.py laundry grain_sack
"""

import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets"
OUT = ROOT / "build/model-preview.png"

SCALE = 14           # пикселей на один шаг модели (модель — 16 шагов)
COS30 = math.cos(math.radians(30))
SIN30 = math.sin(math.radians(30))

# Грани разной яркости: в ванили свет падает сверху, и без этого
# изометрия читается плоской мозаикой.
SHADE = {"up": 1.0, "north": 0.82, "south": 0.82, "east": 0.66, "west": 0.66, "down": 0.5}


def texture_of(model, key):
    """Развернуть ссылку #ключ в настоящий путь к картинке."""
    seen = 0
    while key.startswith("#") and seen < 8:
        key = model.get("textures", {}).get(key[1:], "")
        seen += 1
    if not key or ":" not in key:
        return None
    namespace, path = key.split(":", 1)
    return ASSETS / namespace / "textures" / (path + ".png")


def load(path):
    if path is None or not path.exists():
        return None
    image = Image.open(path).convert("RGBA")
    # Анимированные картинки — столбик кадров: берём первый.
    if image.height > image.width:
        image = image.crop((0, 0, image.width, image.width))
    return image


def project(x, y, z):
    """Модельные координаты (0..16) в экранные."""
    return ((x - z) * COS30 * SCALE, ((x + z) * SIN30 - y) * SCALE)


# Для каждой грани: четыре угла куба (в долях from/to) в порядке обхода
# и два направления, вдоль которых идёт текстура.
FACES = {
    "up":    (lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]),
                            (b[0], b[1], b[2]), (a[0], b[1], b[2])]),
    "down":  (lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]),
                            (b[0], a[1], a[2]), (a[0], a[1], a[2])]),
    "north": (lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]),
                            (a[0], a[1], a[2]), (b[0], a[1], a[2])]),
    "south": (lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]),
                            (b[0], a[1], b[2]), (a[0], a[1], b[2])]),
    "west":  (lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]),
                            (a[0], a[1], b[2]), (a[0], a[1], a[2])]),
    "east":  (lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]),
                            (b[0], a[1], a[2]), (b[0], a[1], b[2])]),
}

# Видимые в этой изометрии грани. Остальные рисовать незачем.
DRAWN = ("up", "south", "east")


def shade(colour, factor):
    r, g, b, a = colour
    return (int(r * factor), int(g * factor), int(b * factor), a)


def draw_face(canvas, corners, texture, uv, factor, samples=12):
    """Грань клетками: цвет каждой берётся из своего места текстуры."""
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = corners
    u0, v0, u1, v1 = uv

    for row in range(samples):
        for col in range(samples):
            s0, s1 = col / samples, (col + 1) / samples
            t0, t1 = row / samples, (row + 1) / samples

            def lerp(s, t):
                # Билинейно по четырём углам грани.
                top = (x0 + (x1 - x0) * s, y0 + (y1 - y0) * s)
                bottom = (x3 + (x2 - x3) * s, y3 + (y2 - y3) * s)
                return (top[0] + (bottom[0] - top[0]) * t,
                        top[1] + (bottom[1] - top[1]) * t)

            quad = [lerp(s0, t0), lerp(s1, t0), lerp(s1, t1), lerp(s0, t1)]
            if texture is None:
                colour = (150, 150, 150, 255)
            else:
                tx = min(texture.width - 1,
                         int((u0 + (u1 - u0) * (s0 + s1) / 2) / 16 * texture.width))
                ty = min(texture.height - 1,
                         int((v0 + (v1 - v0) * (t0 + t1) / 2) / 16 * texture.height))
                colour = texture.getpixel((max(0, tx), max(0, ty)))
            if colour[3] == 0:
                continue
            canvas.polygon(quad, fill=shade(colour, factor))


def render(model_path):
    model = json.loads(model_path.read_text(encoding="utf-8"))
    elements = model.get("elements")
    if not elements:
        return None

    size = 20 * SCALE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas = ImageDraw.Draw(image)
    origin = (size / 2, size * 0.72)

    # Дальние тела рисуются первыми: художник кладёт краску слоями.
    def depth(element):
        a, b = element["from"], element["to"]
        return (a[0] + b[0]) / 2 + (a[2] + b[2]) / 2 + (a[1] + b[1]) / 2

    for element in sorted(elements, key=depth):
        a, b = element["from"], element["to"]
        for name in DRAWN:
            face = element.get("faces", {}).get(name)
            if face is None:
                continue
            corners = FACES[name](a, b)
            flat = []
            for x, y, z in corners:
                px, py = project(x, y, z)
                flat.append((origin[0] + px, origin[1] + py))
            texture = load(texture_of(model, face.get("texture", "")))
            uv = face.get("uv", [0, 0, 16, 16])
            draw_face(canvas, flat, texture, uv, SHADE[name])

    return image


def main():
    wanted = sys.argv[1:]
    folder = ASSETS / "villagepax/models/block"
    names = wanted or sorted(p.stem for p in folder.glob("*.json"))

    drawn = []
    for name in names:
        path = folder / (name + ".json")
        if not path.exists():
            print("нет модели:", name)
            continue
        image = render(path)
        if image is not None:
            drawn.append((name, image))

    if not drawn:
        print("рисовать нечего: у этих моделей нет своих тел")
        return

    columns = min(4, len(drawn))
    rows = (len(drawn) + columns - 1) // columns
    cell = drawn[0][1].width
    sheet = Image.new("RGB", (columns * cell, rows * (cell + 18)), (38, 42, 50))
    pen = ImageDraw.Draw(sheet)
    for index, (name, image) in enumerate(drawn):
        x = (index % columns) * cell
        y = (index // columns) * (cell + 18)
        sheet.paste(image, (x, y), image)
        pen.text((x + 6, y + cell + 3), name, fill=(210, 210, 210))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("просмотр:", OUT, "моделей:", len(drawn))


if __name__ == "__main__":
    main()
