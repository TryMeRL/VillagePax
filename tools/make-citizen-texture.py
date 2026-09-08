#!/usr/bin/env python3
"""Рисует текстуру жителя в раскладке двуногой модели.

Зачем кодом, а не рисованием: тем же приёмом, что и схемы зданий со
блочными текстурами, — арт целиком на мне, а текстура из плоских
прямоугольников описывается точнее словами, чем мышью.

Раскладка — классическая (та, которую ждёт ванильная BipedEntityModel):
левая рука и нога зеркалят правые, поэтому в текстуре их нет вовсе.
Из-за этого занят только левый верх картинки, а всё остальное прозрачно.

    python tools/make-citizen-texture.py
"""

from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / (
    "src/main/resources/assets/villagepax/textures/entity/citizen/norman.png")

SIZE = 64

# Норманнский крестьянин: некрашеная шерсть, кожаный ремень, серые штаны.
SKIN = (200, 149, 108, 255)
SKIN_DARK = (168, 122, 86, 255)
HAIR = (74, 55, 40, 255)
TUNIC = (125, 98, 66, 255)
TUNIC_DARK = (102, 79, 53, 255)
BELT = (74, 55, 40, 255)
TROUSERS = (90, 90, 102, 255)
BOOTS = (58, 46, 36, 255)
EYE = (43, 43, 51, 255)
EYE_WHITE = (222, 222, 214, 255)


def faces(u, v, width, height, depth):
    """Шесть граней куба в раскладке Minecraft: (x, y, ширина, высота)."""
    return {
        "top": (u + depth, v, width, depth),
        "bottom": (u + depth + width, v, width, depth),
        "right": (u, v + depth, depth, height),
        "front": (u + depth, v + depth, width, height),
        "left": (u + depth + width, v + depth, depth, height),
        "back": (u + depth + width + depth, v + depth, width, height),
    }


def fill(image, rect, colour):
    x, y, width, height = rect
    for dx in range(width):
        for dy in range(height):
            image.putpixel((x + dx, y + dy), colour)


def band(image, rect, colour, top=0, rows=None):
    """Полоса внутри грани: рукав на плече, сапог на голени, ремень."""
    x, y, width, height = rect
    rows = height - top if rows is None else rows
    fill(image, (x, y + top, width, min(rows, height - top)), colour)


def head(image):
    cube = faces(0, 0, 8, 8, 8)

    for name, rect in cube.items():
        fill(image, rect, SKIN)

    # Волосы: вся макушка и два верхних ряда по кругу, затылок глубже.
    fill(image, cube["top"], HAIR)
    for name in ("front", "back", "left", "right"):
        band(image, cube[name], HAIR, rows=2)
    band(image, cube["back"], HAIR, rows=5)

    # Подбородок в тени: снизу лицо темнее, иначе голова выглядит плоской.
    fill(image, cube["bottom"], SKIN_DARK)

    # Глаза на лице: третий ряд сверху, по два пикселя с каждой стороны.
    x, y = cube["front"][0], cube["front"][1]
    for dx in (1, 5):
        image.putpixel((x + dx, y + 3), EYE_WHITE)
        image.putpixel((x + dx + 1, y + 3), EYE)


def body(image):
    cube = faces(16, 16, 8, 12, 4)

    for rect in cube.values():
        fill(image, rect, TUNIC)
    fill(image, cube["right"], TUNIC_DARK)
    fill(image, cube["left"], TUNIC_DARK)
    fill(image, cube["bottom"], TROUSERS)

    # Ремень на поясе: два ряда снизу у всех боковых граней.
    for name in ("front", "back", "left", "right"):
        band(image, cube[name], BELT, top=cube[name][3] - 3, rows=2)


def arm(image):
    cube = faces(40, 16, 4, 12, 4)

    for rect in cube.values():
        fill(image, rect, TUNIC)
    fill(image, cube["bottom"], SKIN_DARK)

    # Рукав до локтя, дальше рука.
    for name in ("front", "back", "left", "right"):
        band(image, cube[name], SKIN, top=7)


def leg(image):
    cube = faces(0, 16, 4, 12, 4)

    for rect in cube.values():
        fill(image, rect, TROUSERS)
    fill(image, cube["bottom"], BOOTS)

    for name in ("front", "back", "left", "right"):
        band(image, cube[name], BOOTS, top=9)


def main():
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    head(image)
    body(image)
    arm(image)
    leg(image)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT, optimize=True)

    used = sum(1 for pixel in image.getdata() if pixel[3] > 0)
    print(f"{OUT.name}: {SIZE}x{SIZE}, закрашено {used} пикселей, "
          f"{OUT.stat().st_size} байт")


if __name__ == "__main__":
    main()
