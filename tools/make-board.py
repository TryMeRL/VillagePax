#!/usr/bin/env python3
"""Рисует доску заданий: лицо блока 32x32, доску окна и листок на гвоздике.

Доска — настоящая: еловые доски с волокном, щелями и сучками, тёмная
обвязка по краю; листки — желтоватая бумага с загнутым уголком и
неровным краем, приколотые кованым гвоздём. Всё рисуется детерминированно
(свой сид), поэтому повторный запуск даёт те же пиксели.

    python tools/make-board.py
"""

import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/villagepax/textures"

# Ель: светлее дуба, с красноватым волокном.
PLANK = [(122, 86, 52), (131, 93, 57), (114, 80, 48), (139, 100, 62)]
GRAIN_DARK = (92, 63, 37)
GAP = (48, 32, 20)
FRAME = (74, 50, 30)
FRAME_LIGHT = (96, 66, 40)
PAPER = (236, 224, 192)
PAPER_SHADE = (214, 199, 162)
PAPER_EDGE = (190, 172, 132)
INK = (60, 48, 36)
NAIL = (70, 70, 74)
NAIL_LIGHT = (150, 150, 156)


def shade(colour, delta):
    return tuple(max(0, min(255, c + delta)) for c in colour)


def planks(width, height, board, rng, frame=0):
    """Горизонтальные доски шириной `board` с волокном, щелями и сучками."""
    img = Image.new("RGBA", (width, height))
    px = img.load()
    for top in range(0, height, board):
        base = rng.choice(PLANK)
        phase = rng.random() * 6.28
        for y in range(top, min(top + board, height)):
            for x in range(width):
                # Волокно: длинные полосы вдоль доски с лёгкой волной.
                wave = int(2.5 * __import__("math").sin(x / 7.0 + phase + y * 0.9))
                grain = ((y - top) * 3 + wave) % 7
                colour = shade(base, -10 if grain == 0 else (6 if grain == 3 else 0))
                colour = shade(colour, rng.randint(-4, 4))
                px[x, y] = colour + (255,)
        # Щель между досками.
        for x in range(width):
            px[x, top] = GAP + (255,)
        # Сучок — тёмный овал с кольцом.
        if rng.random() < 0.7 and board >= 6:
            kx = rng.randint(3, width - 4)
            ky = top + board // 2
            for dx in range(-2, 3):
                for dy in range(-1, 2):
                    if 0 <= kx + dx < width and top < ky + dy < min(top + board, height):
                        ring = abs(dx) == 2 or abs(dy) == 1
                        px[kx + dx, ky + dy] = (shade(GRAIN_DARK, 12) if ring else GRAIN_DARK) + (255,)
    if frame:
        draw = ImageDraw.Draw(img)
        for i in range(frame):
            colour = FRAME if i < frame - 1 else FRAME_LIGHT
            draw.rectangle([i, i, width - 1 - i, height - 1 - i], outline=colour + (255,))
    return img


def nail(px, cx, cy, big=False):
    """Кованый гвоздь: шляпка с бликом и тенью вниз-вправо."""
    r = 3 if big else 1
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            if dx * dx + dy * dy <= r * r + (1 if big else 0):
                px[cx + dx, cy + dy] = NAIL + (255,)
    px[cx - (1 if big else 0), cy - (1 if big else 0)] = NAIL_LIGHT + (255,)
    shadow_at = (cx + r + 1, cy + r) if big else (cx + 1, cy + 1)
    if shadow_at[0] < px_size[0] and shadow_at[1] < px_size[1]:
        old = px[shadow_at]
        px[shadow_at] = shade(old[:3], -30) + (255,)


def sheet(width, height, rng, lines, dog_ear=True, lines_from=None):
    """Листок: бумага с неровным краем, загнутым уголком и строками «письма»."""
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = img.load()
    for y in range(height):
        for x in range(width):
            # Неровный край: отщипы по периметру.
            edge = x in (0, width - 1) or y in (0, height - 1)
            if edge and rng.random() < 0.07:
                continue
            colour = PAPER
            if (x * 7 + y * 13) % 17 == 0:
                colour = PAPER_SHADE
            colour = shade(colour, rng.randint(-5, 3))
            # Лёгкое потемнение к краю — бумага старая.
            if x < 2 or y < 2 or x > width - 3 or y > height - 3:
                colour = shade(colour, -14)
            px[x, y] = colour + (255,)
    if dog_ear:
        # Загнутый уголок: срезанный треугольник и отогнутый клапан на бумаге.
        ear = max(4, width // 8)
        for i in range(ear):
            for j in range(ear - i):
                px[width - 1 - j, height - 1 - i] = (0, 0, 0, 0)
        for i in range(ear):
            for j in range(i + 1):
                x, y = width - ear + j - 1, height - ear + i - 1 - j + j
                px[width - 1 - (ear - 1 - j) - 1, height - 1 - i] = PAPER_SHADE + (255,)
        for i in range(ear):
            px[width - 1 - (ear - i), height - 1 - i] = PAPER_EDGE + (255,)
    if lines:
        start = lines_from if lines_from is not None else max(3, height // 5)
        step = lines
        for y in range(start, height - 3, step):
            length = rng.randint(width // 2, max(width // 2, width - 6))
            for x in range(2, min(width - 2, 2 + length)):
                if rng.random() < 0.8:
                    px[x, y] = shade(INK, rng.randint(-10, 30)) + (255,)
    return img


def front(rng):
    """Лицо блока 32x32: доски, три листка вразнобой, гвозди."""
    img = planks(32, 32, 6, rng, frame=2)
    global px_size
    px_size = img.size
    for (x, y, w, h) in ((4, 5, 10, 12), (17, 4, 11, 14), (9, 18, 12, 10)):
        paper = sheet(w, h, rng, lines=3, dog_ear=False, lines_from=4)
        img.alpha_composite(paper, (x, y))
        nail(img.load(), x + w // 2, y + 1)
    return img


def board_gui(rng):
    """Доска окна: большая, плитка 128x128, без рамки — рамку рисует экран."""
    img = planks(128, 128, 16, rng)
    return img


def sheet_gui(rng):
    """Листок окна 96x128 с гвоздём наверху."""
    img = sheet(96, 128, rng, lines=0)
    global px_size
    px_size = img.size
    nail(img.load(), 48, 5, big=True)
    return img


def main():
    rng = random.Random(20261007)
    out = {
        TEXTURES / "block/notice_board_front.png": front(rng),
        TEXTURES / "gui/notice_board.png": board_gui(rng),
        TEXTURES / "gui/notice_sheet.png": sheet_gui(rng),
    }
    for path, img in out.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print("текстура:", path.relative_to(ROOT), img.size)


px_size = (0, 0)

if __name__ == "__main__":
    main()
