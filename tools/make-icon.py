#!/usr/bin/env python3
"""Рисует значок мода: деревня двух народов, 64x64, увеличенная до 128x128.

Значок — первое, что видит игрок в списке модов, и теперь по нему же
открываются настройки. Прежний был одним домиком: верно, но так мог бы
выглядеть любой строительный мод. Здесь то, чем Village Pax отличается:
живая деревня (дым из трубы, житель у двери, тропа к порогу) и второй
народ на горизонте — ступенчатая пирамида майя.

Палитра — та же, что у блоков (tools/make-textures.py): солома, фахверк,
штукатурка и охра значка должны быть солома, фахверк, штукатурка и охра
мира. Увеличение — ближайшим соседом: сглаженный значок выглядел бы
значком другого мода.

    python tools/make-icon.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/villagepax/icon.png"

N = 64


def rgb(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(4))


SKY_TOP = rgb(0x4F7AA6)
SKY_LOW = rgb(0xA9C4D6)
CLOUD = rgb(0xE8EEF2)
HAZE = rgb(0x8FA9BC)

GRASS_DARK = rgb(0x4B6B30)
GRASS = rgb(0x5E8038)
GRASS_LIT = rgb(0x749645)
PATH = rgb(0x9C8358)
PATH_DARK = rgb(0x7E6843)

STONE_DARK = rgb(0x6B6B66)
STONE = rgb(0x8A8A84)
STONE_LIT = rgb(0xA3A39B)

BEAM_DARK = rgb(0x33241A)
BEAM = rgb(0x4A3524)
PLASTER_DARK = rgb(0xC3B79B)
PLASTER = rgb(0xDED4BB)
PLASTER_LIT = rgb(0xEFE7D2)

STRAW_DARK = rgb(0x8A6E2E)
STRAW = rgb(0xB8933F)
STRAW_LIT = rgb(0xD4B057)

BRICK = rgb(0x9A4A36)
BRICK_DARK = rgb(0x6E3324)
SMOKE = rgb(0xD8DCDF, 200)
SMOKE_THIN = rgb(0xD8DCDF, 120)

LIGHT = rgb(0xF0B840)
LIGHT_DEEP = rgb(0xC98A2E)
DOOR = rgb(0x5A3F24)

LIME = rgb(0xCFC6A8)
LIME_SHADE = rgb(0xA9A088)
OCHRE = rgb(0xBE5B3C)

SKIN = rgb(0xC8906A)
HAIR = rgb(0x4A3020)
TUNIC = rgb(0x56708C)
TUNIC_DARK = rgb(0x44596F)
LEGS = rgb(0x5A4A36)


class Canvas:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * N for _ in range(N)]

    def set(self, x, y, colour):
        if 0 <= x < N and 0 <= y < N:
            if colour[3] < 255:
                colour = mix(self.px[y][x], colour[:3] + (255,), colour[3] / 255)
            self.px[y][x] = colour

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, colour)

    def image(self):
        image = Image.new("RGBA", (N, N))
        image.putdata([self.px[y][x] for y in range(N) for x in range(N)])
        return image


def sky(c):
    for y in range(N):
        colour = mix(SKY_TOP, SKY_LOW, min(1.0, y / 44))
        for x in range(N):
            c.set(x, y, colour)
    # Облако — тремя пузырями, не прямоугольником.
    for x0, y0, w in ((6, 9, 9), (10, 7, 7), (13, 10, 8)):
        c.rect(x0, y0, x0 + w, y0 + 2, CLOUD)
    c.rect(44, 5, 52, 6, CLOUD)
    c.rect(47, 4, 50, 4, CLOUD)


def pyramid(c):
    """Пирамида майя вдали: ступени известняка, храм наверху, дымка."""
    base, left, right = 44, 38, 63
    for step in range(5):
        y0 = base - step * 3 - 2
        x0, x1 = left + step * 2, right - step * 2
        c.rect(x0, y0, x1, y0 + 2, LIME)
        c.rect(x0, y0 + 2, x1, y0 + 2, LIME_SHADE)
        c.rect(x1 - 1, y0, x1, y0 + 2, LIME_SHADE)
    # Лестница посередине и храм с красной полосой.
    mid = (left + right) // 2
    c.rect(mid - 1, base - 14, mid + 1, base, LIME_SHADE)
    c.rect(mid - 4, base - 20, mid + 4, base - 15, LIME)
    c.rect(mid - 4, base - 19, mid + 4, base - 19, OCHRE)
    c.rect(mid - 1, base - 17, mid + 1, base - 15, BEAM_DARK)
    # Дымка расстояния: пирамида далеко, и выглядеть она должна далёкой.
    for y in range(base - 21, base + 1):
        for x in range(left, right + 1):
            if c.px[y][x] not in (mix(SKY_TOP, SKY_LOW, min(1.0, y / 44)),):
                c.set(x, y, HAZE[:3] + (70,))


def ground(c):
    for y in range(44, N):
        for x in range(N):
            shade = GRASS if (x * 7 + y * 3) % 11 else GRASS_DARK
            if y == 44:
                shade = GRASS_LIT
            c.set(x, y, shade)
    # Кочки травы — пара светлых пикселей, а не шум по всему полю.
    for x, y in ((3, 49), (12, 57), (48, 52), (57, 60), (40, 58), (6, 61)):
        c.set(x, y, GRASS_LIT)
        c.set(x, y - 1, GRASS_LIT)
    # Тропа от порога вниз, шире к зрителю.
    for y in range(49, N):
        half = 1 + (y - 49) // 4
        for x in range(23 - half, 25 + half):
            c.set(x, y, PATH if (x + y) % 5 else PATH_DARK)


def house(c):
    """Норманнский дом: цоколь, фахверк по штукатурке, соломенная крыша."""
    x0, x1 = 8, 40
    # Цоколь.
    c.rect(x0, 45, x1, 48, STONE)
    for x in range(x0, x1 + 1, 4):
        c.set(x, 46, STONE_DARK)
        c.set(x + 2, 48, STONE_DARK)
    c.rect(x0, 45, x1, 45, STONE_LIT)
    # Стены.
    c.rect(x0 + 1, 29, x1 - 1, 44, PLASTER)
    c.rect(x0 + 1, 29, x0 + 2, 44, PLASTER_LIT)
    c.rect(x1 - 3, 29, x1 - 1, 44, PLASTER_DARK)
    # Балки: углы, пояс, стойки и раскосы.
    for x in (x0, x0 + 11, x1 - 11, x1):
        c.rect(x, 29, x, 44, BEAM)
    c.rect(x0, 36, x1, 36, BEAM)
    c.rect(x0, 29, x1, 29, BEAM_DARK)
    for i in range(7):
        c.set(x0 + 1 + i, 43 - i, BEAM)
        c.set(x1 - 1 - i, 43 - i, BEAM)
    # Дверь и окна с тёплым светом.
    c.rect(22, 38, 26, 44, DOOR)
    c.rect(22, 38, 26, 38, BEAM_DARK)
    c.set(25, 41, LIGHT_DEEP)
    for wx in (13, 31):
        c.rect(wx, 31, wx + 3, 34, LIGHT)
        c.rect(wx, 33, wx + 3, 34, LIGHT_DEEP)
        c.rect(wx + 1, 31, wx + 2, 34, BEAM)
        c.rect(wx, 32, wx + 3, 32, BEAM)
    # Крыша: солома свесом за стены, светлая слева, тёмная справа.
    for row in range(15):
        y = 14 + row
        left = 24 - row - 2
        right = 24 + row + 2
        for x in range(left, right + 1):
            if x < 24 - row // 2:
                shade = STRAW_LIT
            elif x > 24 + row:
                shade = STRAW_DARK
            else:
                shade = STRAW
            if (x + y * 2) % 7 == 0:
                shade = STRAW_DARK
            c.set(x, y, shade)
    c.rect(24 - 16, 28, 24 + 16, 28, STRAW_DARK)
    # Труба и дым.
    c.rect(32, 13, 35, 21, BRICK)
    c.rect(35, 13, 35, 21, BRICK_DARK)
    c.rect(31, 12, 36, 12, BRICK_DARK)
    for x, y, colour in ((33, 10, SMOKE), (34, 9, SMOKE), (35, 7, SMOKE), (36, 6, SMOKE),
                         (38, 4, SMOKE_THIN), (39, 3, SMOKE_THIN), (41, 2, SMOKE_THIN)):
        c.rect(x, y, x + 1, y + 1, colour)


def citizen(c):
    """Житель у тропы: голова, волосы, рубаха, ноги — одиннадцать пикселей роста."""
    x, top = 29, 44
    c.rect(x, top, x + 2, top + 2, SKIN)
    c.rect(x, top, x + 2, top, HAIR)
    c.set(x + 2, top + 1, HAIR)
    c.rect(x - 1, top + 3, x + 3, top + 7, TUNIC)
    c.rect(x + 2, top + 3, x + 3, top + 7, TUNIC_DARK)
    c.set(x - 1, top + 7, SKIN)
    c.set(x + 3, top + 7, SKIN)
    c.rect(x, top + 8, x, top + 10, LEGS)
    c.rect(x + 2, top + 8, x + 2, top + 10, LEGS)


def main():
    c = Canvas()
    sky(c)
    pyramid(c)
    ground(c)
    house(c)
    citizen(c)
    c.image().resize((N * 2, N * 2), Image.NEAREST).save(OUT)
    print("значок: %s" % OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
