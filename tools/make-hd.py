#!/usr/bin/env python3
"""Текстуры блоков 32x32: стройматериалы — заново, остальное — вдвое с фактурой.

Заказчик: «сделай все блоки более реалистичными, не 16x16, а 32x32 хотя
бы, чтоб приятно было». Модели мерятся шестнадцатыми долями блока, а не
пикселями, поэтому текстура вдвое крупнее ложится на ту же модель без
единой правки JSON.

Исходники — 16x16 из прежних генераторов. При первом запуске они
переносятся в `tools/hd-source/`, и дальше читаются оттуда: повторный
запуск даёт те же пиксели, а не увеличивает увеличенное. Прежний
генератор, запущенный снова, перепишет блок в 16x16 — тогда этот
запуск подхватит новый исходник сам.

Два способа:

* **Заново** — штукатурка, фахверк, солома, кирпич, поленья, мешковина,
  верёвка, листва, кладка, вода. Это то, чем выложены стены и крыши, —
  их видно больше всего. Рисуются процедурно, палитра берётся из
  исходника, чтобы народ остался узнаваем.
* **Вдвое с фактурой** — всё остальное: флажки, жетоны, маркеры, эмблемы.
  Scale2x скругляет диагонали, затем каждая область получает фактуру
  своего материала (волокно у дерева, крапину у камня, пятна у листвы)
  и мягкую светотень по краю: верх-лево светлее, низ-право темнее.

    python tools/make-hd.py
"""

import colorsys
import math
import random
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCKS = ROOT / "src/main/resources/assets/villagepax/textures/block"
SOURCE = ROOT / "tools/hd-source"
N = 32


# --- цвет --------------------------------------------------------------------------

def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, d):
    return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d)) + tuple(c[3:])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def palette(img):
    """Цвета исходника от тёмного к светлому, по частоте: [(цвет, доля)]."""
    counts = {}
    for c in img.getdata():
        if c[3] < 128:
            continue
        counts[c[:3]] = counts.get(c[:3], 0) + 1
    total = sum(counts.values()) or 1
    return sorted(((c + (255,), n / total) for c, n in counts.items()), key=lambda p: lum(p[0]))


def dominant(img):
    pal = palette(img)
    return max(pal, key=lambda p: p[1])[0]


def darkest(img):
    return palette(img)[0][0]


def lightest(img):
    return palette(img)[-1][0]


# --- шум ---------------------------------------------------------------------------

class Noise:
    """Зашитая в клетку (тайлящаяся) значимая шумовая сетка."""

    def __init__(self, seed, cells):
        rng = random.Random(seed)
        self.cells = cells
        self.grid = [[rng.random() * 2 - 1 for _ in range(cells)] for _ in range(cells)]

    def at(self, x, y, size=N):
        fx = x / size * self.cells
        fy = y / size * self.cells
        x0, y0 = int(math.floor(fx)), int(math.floor(fy))
        tx, ty = fx - x0, fy - y0
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        g = self.grid
        c = self.cells
        a = g[y0 % c][x0 % c]
        b = g[y0 % c][(x0 + 1) % c]
        d = g[(y0 + 1) % c][x0 % c]
        e = g[(y0 + 1) % c][(x0 + 1) % c]
        return (a * (1 - tx) + b * tx) * (1 - ty) + (d * (1 - tx) + e * tx) * ty


def canvas(colour=(0, 0, 0, 0)):
    return Image.new("RGBA", (N, N), colour)


# --- стройматериалы заново ---------------------------------------------------------

def plaster_hd(src, seed):
    """Штукатурка: тёплая неровность, песчинки, волосяные трещины, тайлится."""
    base = dominant(src)
    pal = palette(src)
    crack = pal[0][0]
    rng = random.Random(seed)
    low, mid = Noise(seed, 4), Noise(seed + 1, 8)
    img = canvas()
    px = img.load()
    for y in range(N):
        for x in range(N):
            d = 7 * low.at(x, y) + 3 * mid.at(x, y) + rng.uniform(-2, 2)
            c = shade(base, d)
            r = rng.random()
            if r < 0.03:
                c = shade(c, -16)
            elif r < 0.06:
                c = shade(c, 10)
            px[x, y] = c
    for _ in range(3):
        x, y = rng.randrange(N), rng.randrange(N)
        dx = rng.choice((-1, 1))
        for _ in range(rng.randint(7, 13)):
            px[x % N, y % N] = mix(crack, base, 0.35)
            px[(x + 1) % N, (y + 1) % N] = shade(px[(x + 1) % N, (y + 1) % N], 8)
            x += dx if rng.random() < 0.6 else 0
            y += 1 if rng.random() < 0.7 else 0
    return img


def wood_texel(base, x, y, along_x, rng, grain):
    """Пиксель бруса: волокно вдоль, тёмные края годовых слоёв."""
    t = (y if along_x else x)
    s = (x if along_x else y)
    g = grain.at(s * 0.25, t * 2.0)
    d = 10 * g
    if int(t * 1.7 + g * 3) % 5 == 0:
        d -= 12
    return shade(base, d + rng.uniform(-3, 3))


def timber_frame_hd(src, seed):
    """Фахверк: обвязка, стойка, два подкоса, нагели; между ними — штукатурка."""
    pal = palette(src)
    browns = [col for col, share in pal if 35 < lum(col) < 110 and col[0] >= col[2]]
    beam = max(browns, key=lambda col: dict(pal)[col]) if browns else shade(darkest(src), 30)
    beam = shade(beam, 8)
    edge = shade(beam, -28)
    infill = plaster_hd(Image.new("RGBA", (4, 4), lightest(src)), seed + 7)
    rng = random.Random(seed)
    grain = Noise(seed + 3, 6)
    img = infill.copy()
    px = img.load()
    timber = [[None] * N for _ in range(N)]

    def mark(x, y, along_x):
        if 0 <= x < N and 0 <= y < N:
            timber[y][x] = along_x

    for x in range(N):
        for y in range(N):
            if y < 4 or y >= N - 4:
                mark(x, y, True)
            elif x < 4 or x >= N - 4:
                mark(x, y, False)
    for y in range(4, 16):
        for x in range(14, 18):
            mark(x, y, False)
    for y in range(14, N - 4):
        t = (y - 14) / (N - 4 - 14)
        for side in (-1, 1):
            cx = 16 + side * t * 11.5
            for k in range(-2, 2):
                mark(int(round(cx)) + k, y, False)

    for y in range(N):
        for x in range(N):
            along = timber[y][x]
            if along is None:
                continue
            c = wood_texel(beam, x, y, along, rng, grain)
            # Фаска бруса: край к штукатурке темнее — брус выступает.
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < N and 0 <= ny < N and timber[ny][nx] is None:
                    c = mix(c, edge, 0.45)
            px[x, y] = c
    # Тень от бруса на штукатурку снизу и справа.
    for y in range(1, N):
        for x in range(1, N):
            if timber[y][x] is None and (timber[y - 1][x] is not None or timber[y][x - 1] is not None):
                px[x, y] = shade(px[x, y], -16)
    for (x, y) in ((16, 6), (16, 13), (6, 2), (25, 2), (2, 29), (29, 29), (6, 29), (25, 29)):
        px[x, y] = shade(beam, -40)
        px[x - 1, y - 1] = shade(beam, 22)
    return img


def thatch_hd(src, seed):
    """Солома: ярусы по восемь, у каждого — тень от верхнего и светлые концы стеблей."""
    pal = palette(src)
    dark, base, lit = pal[0][0], dominant(src), pal[-1][0]
    rng = random.Random(seed)
    img = canvas()
    px = img.load()
    for course in range(N // 8):
        tones = [rng.choice((base, base, shade(base, 9), shade(base, -9), mix(base, lit, 0.5)))
                 for _ in range(N)]
        tips = [rng.randint(0, 2) for _ in range(N)]
        for x in range(N):
            for k in range(8):
                y = course * 8 + k
                c = tones[x]
                if k < 2:
                    # Под краем верхнего яруса — тень.
                    c = mix(c, dark, 0.55 - 0.2 * k)
                if k >= 7 - tips[x]:
                    # Концы стеблей светлее: их обрезали и они выгорели.
                    c = mix(c, lit, 0.45)
                if x % 3 == 0 and 2 <= k < 6:
                    c = shade(c, -7)
                px[x, y] = shade(c, rng.uniform(-4, 4))
    return img


def bricks(src, seed, soot=False, hole=False):
    """Кирпич перевязкой: раствор с фаской, кирпичи разного обжига."""
    pal = palette(src)
    reds = [c for c, share in pal if c[0] > c[2] + 25 and share > 0.03] or [dominant(src)]
    brick = max(reds, key=lambda c: sum(1 for p in pal if p[0] == c))
    mortar = (138, 128, 118, 255)
    for c, share in pal:
        if abs(c[0] - c[2]) < 25 and 90 < lum(c) < 200 and share > 0.03:
            mortar = c
            break
    rng = random.Random(seed)
    speck = Noise(seed, 8)
    img = canvas()
    px = img.load()
    for y in range(N):
        row = y // 8
        offset = 8 if row % 2 else 0
        for x in range(N):
            bx = (x + offset) % 16
            by = y % 8
            if by == 7 or bx == 15:
                px[x, y] = shade(mortar, rng.uniform(-8, 8))
                continue
            key = (row, (x + offset) // 16)
            fire = random.Random(hash((seed, key))).uniform(-18, 14)
            c = shade(brick, fire + 6 * speck.at(x, y) + rng.uniform(-5, 5))
            if by == 0:
                c = shade(c, 12)
            if by == 6 or bx == 14:
                c = shade(c, -14)
            if rng.random() < 0.04:
                c = shade(c, -22)
            px[x, y] = c
    if soot:
        for y in range(10):
            for x in range(N):
                t = (10 - y) / 10 * (0.85 + 0.3 * speck.at(x, y))
                px[x, y] = mix(px[x, y], (28, 22, 20, 255), max(0, min(1, t)))
        for _ in range(5):
            x = rng.randrange(N)
            for y in range(10, 10 + rng.randint(2, 7)):
                px[x, y] = mix(px[x, y], (30, 24, 22, 255), 0.6)
    if hole:
        for y in range(8, 24):
            for x in range(8, 24):
                depth = min(x - 8, 23 - x, y - 8, 23 - y)
                px[x, y] = shade((22, 16, 14, 255), -depth * 2 + rng.uniform(-3, 3))
        for x in range(7, 25):
            px[x, 7] = shade(px[x, 7], -25)
            px[x, 24] = shade(px[x, 24], 10)
    return img


def stone_courses(src, seed):
    """Тёсаная кладка: ряды по восемь, швы с тенью, крапина и сколы."""
    pal = palette(src)
    stone = dominant(src)
    joint = pal[0][0]
    rng = random.Random(seed)
    speck = Noise(seed, 8)
    img = canvas()
    px = img.load()
    for y in range(N):
        row = y // 16
        offset = 8 if row % 2 else 0
        for x in range(N):
            bx = (x + offset) % 32
            by = y % 16
            if by == 15 or bx in (15, 31):
                px[x, y] = shade(joint, rng.uniform(-6, 6))
                continue
            tone = random.Random(hash((seed, row, (x + offset) // 16))).uniform(-10, 10)
            c = shade(stone, tone + 7 * speck.at(x, y) + rng.uniform(-6, 6))
            if by == 0 or bx in (0, 16):
                c = shade(c, 14)
            if by == 14 or bx in (14, 30):
                c = shade(c, -16)
            if rng.random() < 0.05:
                c = shade(c, rng.choice((-20, 14)))
            px[x, y] = c
    return img


def log_side(src, seed):
    """Поленница сбоку: горизонтальные поленья с корой и трещинами коры."""
    pal = palette(src)
    bark = dominant(src)
    dark = pal[0][0]
    rng = random.Random(seed)
    grain = Noise(seed, 6)
    img = canvas()
    px = img.load()
    for y in range(N):
        band = y % 8
        for x in range(N):
            c = shade(bark, 8 * grain.at(x * 0.3, y * 3) + rng.uniform(-6, 6))
            if band in (0, 7):
                c = mix(c, dark, 0.6)
            elif band == 1:
                c = shade(c, 12)
            if rng.random() < 0.05:
                c = shade(c, -24)
            px[x, y] = c
    return img


def log_ends(src, seed, count):
    """Торцы поленьев: годовые кольца, сердцевина, тёмный зазор между."""
    pal = palette(src)
    gap = pal[0][0]
    wood = max((c for c, s in pal if lum(c) > 90), key=lum, default=dominant(src))
    ring_dark = mix(wood, gap, 0.35)
    bark = mix(gap, wood, 0.35)
    rng = random.Random(seed)
    img = canvas(shade(gap, 0))
    px = img.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = shade(gap, rng.uniform(-5, 5))
    centres = [(16, 16, 14)] if count == 1 else [(8, 8, 7), (24, 8, 7), (8, 24, 7), (24, 24, 7)]
    for cx, cy, r in centres:
        cx += 0.5 * rng.choice((-1, 0, 1))
        for y in range(N):
            for x in range(N):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d > r:
                    continue
                if d > r - 1.3:
                    c = bark
                else:
                    ring = (d + 0.6 * math.sin(math.atan2(y - cy, x - cx) * 3)) % 2.4
                    c = ring_dark if ring < 0.8 else wood
                    if d < 1.2:
                        c = ring_dark
                px[x, y] = shade(c, rng.uniform(-4, 4))
        # Радиальная трещина усушки.
        ang = rng.random() * 6.28
        for k in range(int(r * 0.4), int(r * 0.9)):
            x = int(cx + math.cos(ang) * k)
            y = int(cy + math.sin(ang) * k)
            if 0 <= x < N and 0 <= y < N:
                px[x, y] = shade(gap, 10)
    return img


def burlap(src, seed, seam=True):
    """Мешковина: переплетение нитей утка и основы, шов посередине."""
    base = dominant(src)
    dark = palette(src)[0][0]
    rng = random.Random(seed)
    img = canvas()
    px = img.load()
    for y in range(N):
        for x in range(N):
            over = (x // 2 + y // 2) % 2 == 0
            c = shade(base, 10 if over else -10)
            if (x % 2 == 1) != (y % 2 == 1):
                c = shade(c, -6)
            px[x, y] = shade(c, rng.uniform(-4, 4))
    if seam:
        for x in range(N):
            px[x, 15] = mix(px[x, 15], dark, 0.6)
            px[x, 16] = shade(px[x, 16], 8)
            if x % 4 == 1:
                px[x, 14] = mix(px[x, 14], dark, 0.5)
                px[x, 17] = mix(px[x, 17], dark, 0.5)
    return img


def rope_weave(src, seed):
    """Плетёная верёвка: косые пряди в два цвета с тенью витка."""
    pal = palette(src)
    dark, lit = pal[0][0], pal[-1][0]
    rng = random.Random(seed)
    img = canvas()
    px = img.load()
    for y in range(N):
        for x in range(N):
            t = ((x + y) % 8) / 8
            strand = ((x - y) // 8) % 2
            base = lit if strand else mix(lit, dark, 0.35)
            c = mix(base, dark, 0.55 * abs(math.sin(t * math.pi)) ** 0.5 if t > 0.7 else 0)
            if (x + y) % 8 == 7:
                c = mix(c, dark, 0.7)
            px[x, y] = shade(c, rng.uniform(-5, 5))
    return img


def leaves(src, seed):
    """Листва: мелкие листья в три тона и просветы; светляки исходника сохранены."""
    pal = palette(src)
    greens = [c for c, s in pal if c[1] > c[0] and c[1] > c[2]]
    dark = greens[0] if greens else pal[0][0]
    lit = greens[-1] if greens else pal[-1][0]
    mid = mix(dark, lit, 0.5)
    glow = [c for c, s in pal if c[0] > 180 and c[1] > 160 and c[2] < 140]
    rng = random.Random(seed)
    img = canvas()
    px = img.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = shade(dark, rng.uniform(-8, 4))
    for _ in range(170):
        x, y = rng.randrange(N), rng.randrange(N)
        tone = rng.choice((mid, mid, lit))
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            px[(x + dx) % N, (y + dy) % N] = shade(tone, rng.uniform(-6, 6))
        px[(x + 1) % N, (y + 1) % N] = shade(dark, -8)
    for _ in range(6 if glow else 0):
        x, y = rng.randrange(1, N - 1), rng.randrange(1, N - 1)
        px[x, y] = glow[-1]
        px[x + 1, y] = shade(glow[-1], -20)
    return img


def water(src, seed):
    """Вода родника: рябь бликов на голубом, тайлится."""
    base = dominant(src)
    lit = lightest(src)
    rng = random.Random(seed)
    wave = Noise(seed, 4)
    img = canvas()
    px = img.load()
    for y in range(N):
        for x in range(N):
            w = wave.at(x, y)
            c = shade(base, 14 * w)
            if math.sin((x + y * 0.5) * 0.9 + w * 4) > 0.86:
                c = mix(c, lit, 0.7)
            px[x, y] = shade(c, rng.uniform(-3, 3))
    return img


# --- вдвое с фактурой --------------------------------------------------------------

def scale2x(img):
    """EPX/Scale2x: вдвое, со скруглением ступенчатых диагоналей."""
    w, h = img.size
    src = img.load()
    out = Image.new("RGBA", (w * 2, h * 2))
    dst = out.load()

    def p(x, y):
        return src[max(0, min(w - 1, x)), max(0, min(h - 1, y))]

    for y in range(h):
        for x in range(w):
            P = p(x, y)
            A, B, C, D = p(x, y - 1), p(x + 1, y), p(x - 1, y), p(x, y + 1)
            # Тонкую черту — крест на щите, руну, стрелку маркера — не
            # скругляем: у неё не больше одного соседа своего цвета, и
            # скругление съело бы её. Сглаживается фон вокруг неё.
            if sum(1 for q in (A, B, C, D) if q == P) <= 1:
                for dx in (0, 1):
                    for dy in (0, 1):
                        dst[2 * x + dx, 2 * y + dy] = P
                continue
            e0 = A if (C == A and C != D and A != B) else P
            e1 = B if (A == B and A != C and B != D) else P
            e2 = C if (D == C and D != B and C != A) else P
            e3 = D if (B == D and B != A and D != C) else P
            dst[2 * x, 2 * y] = e0
            dst[2 * x + 1, 2 * y] = e1
            dst[2 * x, 2 * y + 1] = e2
            dst[2 * x + 1, 2 * y + 1] = e3
    return out


def material(c):
    r, g, b = c[0] / 255, c[1] / 255, c[2] / 255
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if s < 0.13:
        return "stone"
    if 0.05 <= h <= 0.13 and s > 0.25 and v < 0.75:
        return "wood"
    if 0.2 <= h <= 0.45 and s > 0.3:
        return "leaf"
    if v > 0.85 and s < 0.3:
        return "paper"
    return "plain"


def detailed(src, seed):
    """Вдвое и фактура: материал по цвету, светотень по краю области."""
    big = scale2x(src)
    out = big.copy()
    px, ref = out.load(), big.load()
    rng = random.Random(seed)
    grain = Noise(seed, 8)
    for y in range(N):
        for x in range(N):
            c = ref[x, y]
            if c[3] == 0:
                continue
            kind = material(c)
            d = 0.0
            if kind == "wood":
                d += 7 * grain.at(x * 0.3, y * 2.5) + rng.uniform(-3, 3)
            elif kind == "stone":
                d += 5 * grain.at(x, y) + (rng.choice((-9, 7)) if rng.random() < 0.08 else rng.uniform(-3, 3))
            elif kind == "leaf":
                d += rng.uniform(-9, 9)
            elif kind == "paper":
                d += rng.uniform(-2, 2)
            else:
                d += rng.uniform(-2.5, 2.5)
            # Светотень: край области сверху/слева светлее, снизу/справа темнее.
            up = ref[x, y - 1] if y > 0 else c
            left = ref[x - 1, y] if x > 0 else c
            down = ref[x, y + 1] if y < N - 1 else c
            right = ref[x + 1, y] if x < N - 1 else c
            if up[:3] != c[:3] and up[3] and lum(up) < lum(c) + 30:
                d += 7
            if left[:3] != c[:3] and left[3] and lum(left) < lum(c) + 30:
                d += 4
            if down[:3] != c[:3] and down[3]:
                d -= 7
            if right[:3] != c[:3] and right[3]:
                d -= 4
            px[x, y] = shade(c, d)
    return out


def carved(src, seed):
    """Резной камень: узор исходника — канавкой, с тенью и бликом по краю."""
    big = src.resize((N, N), Image.NEAREST)
    stone = dominant(src)
    rng = random.Random(seed)
    speck = Noise(seed, 8)
    groove = [[lum(big.getpixel((x, y))) < lum(stone) - 25 for x in range(N)] for y in range(N)]
    img = canvas()
    px = img.load()
    for y in range(N):
        for x in range(N):
            c = big.getpixel((x, y))
            c = shade(c, 5 * speck.at(x, y) + rng.uniform(-4, 4))
            if groove[y][x]:
                if y > 0 and not groove[y - 1][x]:
                    c = shade(c, -18)
                if x > 0 and not groove[y][x - 1]:
                    c = shade(c, -10)
            else:
                if y > 0 and groove[y - 1][x]:
                    c = shade(c, 14)
                if x > 0 and groove[y][x - 1]:
                    c = shade(c, 8)
            px[x, y] = c
    return img


# --- сборка -----------------------------------------------------------------------

REDRAWN = {
    "plaster": plaster_hd,
    "ochre_plaster": plaster_hd,
    "timber_frame": timber_frame_hd,
    "thatch": thatch_hd,
    "chimney": lambda src, seed: bricks(src, seed, soot=True),
    "chimney_top": lambda src, seed: bricks(src, seed, hole=True),
    "town_hall_bottom": stone_courses,
    "firewood_side": log_side,
    "firewood_end": lambda src, seed: log_ends(src, seed, 4),
    "firewood_log_end": lambda src, seed: log_ends(src, seed, 1),
    "grain_sack": burlap,
    "laundry_rope": rope_weave,
    "glow_tree_leaves": leaves,
    "fountain_water": water,
    "carved_stone": carved,
    "rune_stone": carved,
}

# Свои 32x32 уже нарисованы своим генератором (доска заданий).
NATIVE = {"notice_board_front"}


def source_of(name):
    """Исходник 16x16: из папки исходников, а если блок новее — из ассетов."""
    asset = BLOCKS / (name + ".png")
    kept = SOURCE / (name + ".png")
    current = Image.open(asset).convert("RGBA")
    if current.size == (16, 16):
        SOURCE.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(asset, kept)
        return current
    if kept.exists():
        return Image.open(kept).convert("RGBA")
    return None


def main():
    done = 0
    for asset in sorted(BLOCKS.glob("*.png")):
        name = asset.stem
        if name in NATIVE:
            continue
        src = source_of(name)
        if src is None:
            print("нет исходника 16x16, пропущено:", name)
            continue
        seed = sum(ord(ch) * 31 ** i for i, ch in enumerate(name)) % 1_000_003
        maker = REDRAWN.get(name, detailed)
        img = maker(src, seed)
        # Прозрачность исходника — закон: что было дырой, дырой и остаётся.
        if maker is not detailed and any(c[3] < 255 for c in src.getdata()):
            mask = src.resize((N, N), Image.NEAREST).getchannel("A")
            img.putalpha(mask)
        img.save(asset)
        done += 1
    print("текстур 32x32:", done)


if __name__ == "__main__":
    main()
