#!/usr/bin/env python3
"""Рисует все текстуры мода: блоки и предметы, 16x16.

Зачем кодом, а не мышью: текстура из ровных форм — рама, кладка, солома,
монета — описывается примитивами точнее, чем рисуется вручную, а главное
переписывается. Палитра одна на весь мод, и это видно: норманнская
штукатурка и штукатурка майя — один материал разного цвета, а не две
разные картинки.

Три правила, по которым всё здесь нарисовано (прежние текстуры нарушали
все три, и заказчик справедливо назвал их некрасивыми):

1. **Форма важнее шума.** Пиксельный шум по всей плитке читается как грязь.
   Штукатурка почти ровная, и вся её жизнь — в двух трещинах и паре пятен.
2. **Три тона, а не тридцать.** У каждого материала тень, основа и свет.
   Свет всегда сверху слева, тень снизу справа — во всём моде одинаково.
3. **Плитка обязана сходиться.** Верх стыкуется с низом, лево с правом:
   иначе стена из блоков рассыпается на квадраты.

    python tools/make-textures.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/villagepax/textures"

N = 16

# --- палитра мода -----------------------------------------------------------
#
# Названия по материалу, а не по цвету: «дерево тёмное» переживёт смену
# оттенка, а «коричневый» — нет.

CLEAR = (0, 0, 0, 0)


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, 255)


# Дерево балок: почти чёрное в тени, тёплое на свету.
BEAM_DARK = rgb(0x33241A)
BEAM = rgb(0x4A3524)
BEAM_LIT = rgb(0x5E4531)

# Доска: пол ратуши, ящики.
PLANK_DARK = rgb(0x6E5330)
PLANK = rgb(0x8F6E3E)
PLANK_LIT = rgb(0xA8854E)

# Штукатурка норманнов.
PLASTER_DARK = rgb(0xC3B79B)
PLASTER = rgb(0xDED4BB)
PLASTER_LIT = rgb(0xEFE7D2)
PLASTER_CRACK = rgb(0xAFA184)

# Штукатурка майя: та же стена, другая земля в замесе.
OCHRE_DARK = rgb(0xA24A32)
OCHRE = rgb(0xBE5B3C)
OCHRE_LIT = rgb(0xD1734E)
OCHRE_CRACK = rgb(0x8B3E2A)

# Знак на алтаре: тёплый отсвет по камню. Не красный и не золотой —
# алтарь не сокровище и не кровь, он тёплый.
EMBER = rgb(0xC98A3C)
EMBER_DARK = rgb(0x8E5C22)

# Камень кладки.
STONE_DARK = rgb(0x6B6B66)
STONE = rgb(0x8A8A84)
STONE_LIT = rgb(0xA3A39B)
MORTAR = rgb(0x585853)

# Известняк майя: светлее и желтее северного камня.
LIME_DARK = rgb(0xB0A98F)
LIME = rgb(0xCFC6A8)
LIME_LIT = rgb(0xE6DDC0)
LIME_SHADOW = rgb(0x8E8871)
JADE = rgb(0x3E8C6A)

# Солома.
STRAW_DARK = rgb(0x8A6E2E)
STRAW = rgb(0xB8933F)
STRAW_LIT = rgb(0xD4B057)

# Кора и срез бревна.
BARK_DARK = rgb(0x3E3022)
BARK = rgb(0x574330)
BARK_LIT = rgb(0x6B5440)
WOOD_CUT = rgb(0xC0995A)
WOOD_RING = rgb(0x9E7A44)

# Мешковина.
BURLAP_DARK = rgb(0x8E7648)
BURLAP = rgb(0xB49A63)
BURLAP_LIT = rgb(0xCBB27C)
ROPE = rgb(0x6E5B38)

# Ткань.
LINEN_DARK = rgb(0xC6C0B2)
LINEN = rgb(0xE4DFD2)
LINEN_LIT = rgb(0xF2EFE6)
DYED = rgb(0x6E88A6)
DYED_DARK = rgb(0x56708C)

# Монета.
COPPER_DARK = rgb(0x8A4A22)
COPPER = rgb(0xC4703A)
COPPER_LIT = rgb(0xE0995C)
SILVER_DARK = rgb(0x8E949A)
SILVER = rgb(0xC3C9CE)
SILVER_LIT = rgb(0xE8EDF1)
GOLD_DARK = rgb(0xA07818)
GOLD = rgb(0xE0B02C)
GOLD_LIT = rgb(0xF6D96A)

# Кожа кошеля.
LEATHER_DARK = rgb(0x5E3E24)
LEATHER = rgb(0x8A5C33)
LEATHER_LIT = rgb(0xA87844)

# Пергамент чертежа.
PAPER_DARK = rgb(0xCFC2A0)
PAPER = rgb(0xE9DFC2)
PAPER_LIT = rgb(0xF6EFDA)
INK = rgb(0x3A4A6B)
WAX = rgb(0xB03A32)

# Метки разметки: тёмная плашка и цветной знак.
SLATE_DARK = rgb(0x1B1F24)
SLATE = rgb(0x2B3138)
SLATE_LIT = rgb(0x3C444D)
MARK_BED = rgb(0xD25B5B)
MARK_DOOR = rgb(0x3FB0A6)
MARK_STORE = rgb(0xE0A030)
MARK_WORK = rgb(0xC8912E)
MARK_DECOR = rgb(0x9B6BC4)
IRON = rgb(0x8A8F96)
IRON_LIT = rgb(0xB4B9C0)


class Tex:
    """Холст 16x16 с примитивами. Координаты с нуля, x вправо, y вниз."""

    def __init__(self, base=CLEAR):
        self.px = [[base for _ in range(N)] for _ in range(N)]

    def set(self, x, y, colour):
        if 0 <= x < N and 0 <= y < N:
            self.px[y][x] = colour

    def fill(self, colour):
        for y in range(N):
            for x in range(N):
                self.px[y][x] = colour

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, colour)

    def dots(self, colour, *points):
        for x, y in points:
            self.set(x, y, colour)

    def line(self, x0, y0, x1, y1, colour):
        """Отрезок Брезенхэма: диагонали раскосов и трещин."""
        dx, dy = abs(x1 - x0), abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx - dy
        while True:
            self.set(x0, y0, colour)
            if x0 == x1 and y0 == y1:
                return
            err2 = 2 * err
            if err2 > -dy:
                err -= dy
                x0 += sx
            if err2 < dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, radius, colour):
        """Круг: монеты. Полушаг даёт ровный край без зубцов."""
        for y in range(N):
            for x in range(N):
                if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    self.set(x, y, colour)

    def ring(self, cx, cy, outer, inner, colour):
        for y in range(N):
            for x in range(N):
                away = (x - cx) ** 2 + (y - cy) ** 2
                if inner * inner < away <= outer * outer:
                    self.set(x, y, colour)

    def sprite(self, rows, palette, ox=0, oy=0):
        """Мелкий знак поверх фона: пробел — не трогать."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != " ":
                    self.set(ox + x, oy + y, palette[ch])

    def save(self, folder, name):
        image = Image.new("RGBA", (N, N), CLEAR)
        image.putdata([self.px[y][x] for y in range(N) for x in range(N)])
        path = OUT / folder / (name + ".png")
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        return path


# --- материалы стен ---------------------------------------------------------


def plaster(base, dark, lit, crack):
    """Штукатурка: почти ровная стена, вся жизнь — в трещинах.

    Пятна и трещины расставлены руками и подобраны так, чтобы плитка
    сходилась сама с собой: трещина, уходящая за правый край, продолжается
    у левого.
    """
    t = Tex(base)
    # Пятна кладутся парами и тройками: одиночные точки читаются сором,
    # а пара соседних — мазком мастерка.
    for x, y in [(2, 3), (3, 3), (9, 2), (10, 2), (9, 3), (5, 11), (6, 11), (6, 12)]:
        t.set(x, y, lit)
    for x, y in [(12, 5), (13, 5), (13, 6), (2, 8), (3, 8), (8, 13), (9, 13)]:
        t.set(x, y, dark)
    # Две трещины: одна сверху вниз, вторая через правый край на левый.
    t.line(4, 0, 6, 5, crack)
    t.line(6, 5, 5, 7, crack)
    t.line(12, 9, 15, 11, crack)
    t.line(0, 11, 2, 12, crack)
    return t


def timber_frame():
    """Фахверк: дубовая рама, раскос и штукатурка между балками.

    Рама по всем четырём краям: в стене из таких блоков балки сходятся
    в решётку, и стена читается фахверком, а не пятном.
    """
    t = plaster(PLASTER, PLASTER_DARK, PLASTER_LIT, PLASTER_CRACK)
    t.rect(0, 0, 15, 1, BEAM)
    t.rect(0, 14, 15, 15, BEAM)
    t.rect(0, 0, 1, 15, BEAM)
    t.rect(14, 0, 15, 15, BEAM)
    # Свет сверху слева: у балки светлая верхняя грань и тёмная нижняя.
    t.rect(0, 0, 15, 0, BEAM_LIT)
    t.rect(0, 0, 0, 15, BEAM_LIT)
    t.rect(0, 15, 15, 15, BEAM_DARK)
    t.rect(15, 0, 15, 15, BEAM_DARK)
    # Стойка посередине и два подкоса от неё — настоящая вязка фахверка.
    # Одной тонкой диагонали мало: панель читалась перечёркнутой, а не
    # собранной из брёвен.
    t.rect(7, 2, 8, 13, BEAM)
    t.rect(7, 2, 7, 13, BEAM_LIT)
    t.rect(8, 2, 8, 13, BEAM_DARK)
    for step in range(5):
        # Подкосы в две точки толщиной: тонкая диагональ на 16 пикселях
        # рассыпается в лесенку и выглядит случайной царапиной.
        t.set(2 + step, 12 - step, BEAM)
        t.set(2 + step, 11 - step, BEAM_LIT)
        t.set(3 + step, 12 - step, BEAM_DARK)
        t.set(13 - step, 12 - step, BEAM)
        t.set(13 - step, 11 - step, BEAM_LIT)
        t.set(12 - step, 12 - step, BEAM_DARK)
    return t


def thatch():
    """Солома: пучки разной длины, срез внизу.

    Полосы не ровные — у каждой свой тон и свой обрыв, иначе крыша
    читается забором. Нижний ряд темнее: это срез, он в тени.
    """
    t = Tex(STRAW)
    tones = [STRAW_LIT, STRAW, STRAW_DARK, STRAW, STRAW_LIT, STRAW_DARK,
             STRAW, STRAW_LIT, STRAW_DARK, STRAW, STRAW, STRAW_DARK,
             STRAW_LIT, STRAW, STRAW_DARK, STRAW]
    breaks = [11, 6, 13, 4, 9, 2, 14, 7, 12, 5, 10, 3, 8, 13, 6, 10]
    for x in range(N):
        t.rect(x, 0, x, 15, tones[x])
        # Обрыв пучка: короткая перемычка другого тона поперёк полосы.
        y = breaks[x]
        t.set(x, y, STRAW_DARK if tones[x] is not STRAW_DARK else STRAW_LIT)
        t.set(x, (y + 8) % 16, STRAW_DARK if tones[x] is not STRAW_DARK else STRAW_LIT)
    t.rect(0, 15, 15, 15, STRAW_DARK)
    return t


def chimney_side():
    """Труба: кирпич в перевязку, закопчённый кверху.

    Копоть не украшение: по ней труба отличается от кирпичной стены
    с одного взгляда, а дом с трубой — от дома без неё.
    """
    t = brick(rgb(0x7E3A2C), rgb(0x9C4A38), rgb(0xB35C46), rgb(0x6B5048))
    # Копоть: чем выше, тем гуще. Верхние два ряда почти чёрные.
    soot = rgb(0x3A2A24)
    for x in range(N):
        t.set(x, 0, soot)
        if (x * 5) % 7 < 4:
            t.set(x, 1, soot)
        if (x * 3) % 5 < 2:
            t.set(x, 2, soot)
    return t


def chimney_top():
    """Верх трубы: устье в копоти."""
    t = brick(rgb(0x7E3A2C), rgb(0x9C4A38), rgb(0xB35C46), rgb(0x6B5048))
    t.rect(4, 4, 11, 11, rgb(0x2B211C))
    t.rect(5, 5, 10, 10, rgb(0x17110E))
    t.rect(4, 4, 11, 4, rgb(0x3A2A24))
    t.rect(4, 11, 11, 11, rgb(0x120D0B))
    return t


def brick(dark, base, lit, mortar):
    """Кладка: два ряда со смещением и раствор между ними."""
    t = Tex(base)
    t.rect(0, 0, 15, 0, mortar)
    t.rect(0, 7, 15, 7, mortar)
    t.rect(0, 15, 15, 15, mortar)
    t.rect(7, 1, 7, 6, mortar)
    t.rect(3, 8, 3, 14, mortar)
    t.rect(11, 8, 11, 14, mortar)
    # Свет на верхнем ряду каждого камня, тень на нижнем.
    for y0, y1 in ((1, 6), (8, 14)):
        t.rect(0, y0, 15, y0, lit)
        t.rect(0, y1, 15, y1, dark)
    for x, y in [(2, 3), (10, 4), (5, 10), (13, 12)]:
        t.set(x, y, dark)
    for x, y in [(4, 4), (12, 2), (7, 11), (1, 9)]:
        t.set(x, y, lit)
    return t


def carved_stone():
    """Резной камень майя: ступенчатый знак в рельефе.

    Рельеф делается двумя линиями — светлой сверху слева и тёмной снизу
    справа. Это тот же приём, что у балки, и он один на весь мод.
    """
    t = Tex(LIME)
    for x, y in [(1, 2), (6, 1), (12, 3), (3, 9), (9, 12), (14, 8)]:
        t.set(x, y, LIME_LIT)
    for x, y in [(2, 5), (8, 4), (13, 11), (5, 14), (11, 6)]:
        t.set(x, y, LIME_DARK)

    # Ступенчатая спираль — та самая, что вырезали майя. Штрих в два
    # пикселя и поля по краю: мелкий узор на 16 пикселях превращается
    # в лабиринт, из которого глаз не выбирается.
    glyph = [
        "................",
        "................",
        "..############..",
        "..############..",
        "..##........##..",
        "..##.######.##..",
        "..##.######.##..",
        "..##.##.........",
        "..##.##.........",
        "..##.######.##..",
        "..##.######.##..",
        "..##........##..",
        "..############..",
        "..############..",
        "................",
        "................",
    ]
    for y, row in enumerate(glyph):
        for x, ch in enumerate(row):
            if ch == "#":
                t.set(x, y, LIME_SHADOW)
    # Резьба заглублена: свет ложится на верхний край борозды.
    for y, row in enumerate(glyph):
        for x, ch in enumerate(row):
            if ch == "#" and y + 1 < N and glyph[y + 1][x] != "#":
                t.set(x, y + 1, LIME_LIT)
    t.rect(0, 15, 15, 15, LIME_DARK)
    return t


# --- ратуша -----------------------------------------------------------------


def town_hall_side():
    """Бок ратуши: обшивка, железные углы и знак дома.

    Это первый блок мода, который видит игрок, и по нему он судит обо всём
    остальном. Поэтому здесь не просто доски: знак дома в круге читается
    с десяти шагов и говорит, что это здание — главное.
    """
    t = Tex(PLANK)
    for y in range(N):
        if y % 4 == 0:
            t.rect(0, y, 15, y, PLANK_DARK)
        elif y % 4 == 1:
            t.rect(0, y, 15, y, PLANK_LIT)
    # Стойки по краям: блок читается как рубленая стена, а не как доска.
    t.rect(0, 0, 1, 15, BEAM)
    t.rect(14, 0, 15, 15, BEAM)
    t.rect(0, 0, 0, 15, BEAM_LIT)
    t.rect(15, 0, 15, 15, BEAM_DARK)
    # Железные накладки сверху и снизу.
    t.rect(2, 0, 13, 0, IRON)
    t.rect(2, 15, 13, 15, IRON)
    t.dots(IRON_LIT, (3, 0), (7, 0), (12, 0))

    # Гербовый щит, а не домик: щит читается «здесь власть» с десяти
    # шагов, а домик на восьми пикселях — просто светлое пятно.
    sign = {
        "o": BEAM_DARK,
        "p": PLASTER_LIT,
        "s": PLASTER_DARK,
        "r": rgb(0xA8452F),
        "R": rgb(0xC45A42),
    }
    t.sprite([
        "oooooooo",
        "oppppppo",
        "oppRRpso",
        "oRRRRRRo",
        "oRRRRRRo",
        "oppRRpso",
        "osppppso",
        "ossppsso",
        " osppso ",
        "  osso  ",
        "   oo   ",
    ], sign, ox=4, oy=2)
    return t


def town_hall_top():
    """Верх ратуши: пол из плах и врезанный круг с розой ветров."""
    t = Tex(PLANK)
    for y in range(N):
        if y % 5 == 0:
            t.rect(0, y, 15, y, PLANK_DARK)
    t.rect(0, 0, 15, 0, BEAM)
    t.rect(0, 15, 15, 15, BEAM)
    t.rect(0, 0, 0, 15, BEAM)
    t.rect(15, 0, 15, 15, BEAM)
    t.ring(7.5, 7.5, 6.2, 5.2, BEAM_DARK)
    t.ring(7.5, 7.5, 5.2, 4.4, PLANK_LIT)
    t.line(7, 3, 7, 12, BEAM_DARK)
    t.line(8, 3, 8, 12, BEAM_DARK)
    t.line(3, 7, 12, 7, BEAM_DARK)
    t.line(3, 8, 12, 8, BEAM_DARK)
    t.dots(GOLD, (7, 7), (8, 7), (7, 8), (8, 8))
    t.dots(GOLD_LIT, (7, 7))
    return t


# --- прочие блоки -----------------------------------------------------------


def firewood_end():
    """Торцы поленницы: срезы с годовыми кольцами."""
    t = Tex(BARK_DARK)
    for cx, cy, r in ((4, 4, 3.4), (11, 4, 3.0), (4, 11, 3.0), (11, 11, 3.4)):
        t.disc(cx, cy, r, BARK)
        t.disc(cx, cy, r - 0.9, WOOD_CUT)
        t.ring(cx, cy, r - 1.7, r - 2.4, WOOD_RING)
        t.set(int(cx), int(cy), WOOD_RING)
    for x, y in [(7, 7), (8, 8), (0, 8), (15, 7), (8, 0), (7, 15)]:
        t.set(x, y, BARK_LIT)
    return t


def log_end():
    """Торец одного полена: кора кольцом, срез с годовыми кольцами.

    Отдельной картинкой, потому что в модели поленница сложена из брёвен,
    и каждому бревну нужен СВОЙ торец целиком. Общая плитка на четыре
    среза давала каждому полену по четверти чужого — рябь вместо дров.
    """
    t = Tex(BARK)
    t.disc(7.5, 7.5, 7.6, BARK_DARK)
    t.disc(7.5, 7.5, 7.0, BARK)
    t.disc(7.5, 7.5, 6.0, WOOD_CUT)
    t.ring(7.5, 7.5, 4.6, 3.9, WOOD_RING)
    t.ring(7.5, 7.5, 2.4, 1.7, WOOD_RING)
    t.dots(WOOD_RING, (7, 7), (8, 8))
    # Свет сверху слева, как у всего прочего.
    t.dots(BARK_LIT, (4, 2), (5, 1), (10, 2))
    t.dots(BARK_DARK, (11, 13), (10, 14), (5, 14))
    return t


def firewood_side():
    """Бок поленницы: лежащие брёвна с корой."""
    t = Tex(BARK)
    for y0 in (0, 4, 8, 12):
        t.rect(0, y0, 15, y0, BARK_LIT)
        t.rect(0, y0 + 3, 15, y0 + 3, BARK_DARK)
    for x, y in [(2, 1), (9, 2), (5, 5), (13, 6), (3, 9), (11, 10), (7, 13), (14, 14)]:
        t.set(x, y, BARK_DARK)
    for x, y in [(6, 1), (12, 2), (1, 6), (8, 5), (14, 9), (4, 10), (10, 13)]:
        t.set(x, y, BARK_LIT)
    return t


def grain_sack():
    """Мешок зерна: холстина в переплетение и верёвка поперёк."""
    t = Tex(BURLAP)
    for y in range(N):
        for x in range(N):
            if (x + y) % 4 == 0:
                t.set(x, y, BURLAP_DARK)
            elif (x - y) % 4 == 0:
                t.set(x, y, BURLAP_LIT)
    t.rect(0, 0, 15, 0, BURLAP_LIT)
    t.rect(0, 15, 15, 15, BURLAP_DARK)
    t.rect(0, 6, 15, 6, ROPE)
    t.rect(0, 7, 15, 7, BARK_DARK)
    t.dots(BURLAP_LIT, (4, 6), (11, 6))
    return t


def rope_texture():
    """Верёвка: витое волокно, сплошная плитка."""
    t = Tex(ROPE)
    for y in range(N):
        for x in range(N):
            if (x + y) % 4 == 0:
                t.set(x, y, BURLAP_LIT)
            elif (x - y) % 4 == 0:
                t.set(x, y, BARK_DARK)
    return t


def marker(colour, glyph):
    """Метка разметки: тёмная плашка со знаком.

    Метки видит только строитель да игрок в голограмме, но и они должны
    читаться с одного взгляда: рамка, тень внутри и знак в цвет смысла.
    """
    t = Tex(SLATE)
    t.rect(0, 0, 15, 0, SLATE_LIT)
    t.rect(0, 0, 0, 15, SLATE_LIT)
    t.rect(0, 15, 15, 15, SLATE_DARK)
    t.rect(15, 0, 15, 15, SLATE_DARK)
    t.rect(1, 1, 14, 14, SLATE)
    t.sprite(glyph, {"#": colour, "+": SLATE_LIT}, ox=2, oy=3)
    return t


MARK_GLYPHS = {
    "bed": [
        "#          #",
        "#..........#",
        "############",
        "############",
        "#.#......#.#",
        "#.#......#.#",
    ],
    "door": [
        "  ########  ",
        " ########## ",
        " ##......## ",
        " ##......## ",
        " ##...#..## ",
        " ##......## ",
        " ##......## ",
        " ##......## ",
    ],
    "storage": [
        "############",
        "#..........#",
        "#....##....#",
        "############",
        "#....##....#",
        "#..........#",
        "############",
    ],
    "workstation": [
        "############",
        "#..........#",
        "...####.....",
        "...####.....",
        "...####.....",
        "..######....",
        ".########...",
    ],
    "decor": [
        "....####....",
        "...######...",
        "..###..###..",
        ".###....###.",
        "..###..###..",
        "...######...",
        "....####....",
    ],
}


def _glyph(rows):
    return [row.replace(".", " ") for row in rows]


# --- предметы ---------------------------------------------------------------


def coin(dark, base, lit, stamp):
    """Монета: обод, чекан и блик.

    Блик слева сверху, тень справа снизу — те же, что у всего мода.
    Без чекана монета читается пуговицей.
    """
    t = Tex(CLEAR)
    t.disc(7.5, 7.5, 6.6, dark)
    t.disc(7.5, 7.5, 5.7, base)
    t.ring(7.5, 7.5, 5.7, 4.6, dark)
    t.disc(7.5, 7.5, 4.6, base)
    t.sprite(stamp, {"#": dark, "+": lit}, ox=5, oy=5)
    # Блик: короткая дуга сверху слева.
    t.dots(lit, (4, 4), (5, 3), (6, 3), (7, 2), (3, 5), (3, 6))
    t.dots(dark, (11, 11), (12, 10), (12, 9), (10, 12))
    return t


def coin_stacks(dark, base, lit, stacks):
    """Монеты стопками — то, что видно в руке, когда их много.

    Одна монета на значке честна для одной монеты и лжёт для сорока:
    игрок, у которого в сумке целая казна, видит там пуговицу. Стопки
    рисуются наискосок, как лежащие на столе: сверху овал, снизу ребро,
    и каждая следующая монета закрывает верх предыдущей.

    `stacks` — список (центр по x, низ по y, сколько монет).
    """
    t = Tex(CLEAR)
    for cx, bottom, count in stacks:
        for layer in range(count):
            top = bottom - 3 - layer * 2
            # Ребро монеты: тёмная полоса под овалом.
            for x in range(cx - 3, cx + 4):
                t.set(x, top + 2, dark)
            # Верх: овал в три ряда, блик слева.
            for x in range(cx - 2, cx + 3):
                t.set(x, top, base)
            for x in range(cx - 3, cx + 4):
                t.set(x, top + 1, base)
            t.set(cx - 3, top + 1, dark)
            t.set(cx + 3, top + 1, dark)
            t.set(cx - 2, top, lit)
            t.set(cx - 1, top, lit)
            t.set(cx + 2, top, dark)
        # Верхняя монета — с чеканом: точка посередине.
        top = bottom - 3 - (count - 1) * 2
        t.set(cx, top + 1, dark)
    return t


def purse_empty():
    """Пустой кошель: мешочек опал, горловина стянута, монеты не видно.

    По значку в руке игрок должен понимать, есть ли в кошеле что-нибудь,
    не открывая подсказки: за этим кошель и носят.
    """
    t = Tex(CLEAR)
    body = [
        "     ....     ",
        "    .oooo.    ",
        "     .oo.     ",
        "   ..LLLL..   ",
        "  .LLLLLLLL.  ",
        " .LLLLLLLLLL. ",
        " LLLLLLLLLLLL ",
        " .LLLLLLLLLL. ",
        " .dLLLLLLLLd. ",
        "  .dddddddd.  ",
        "   ........   ",
    ]
    palette = {
        "L": LEATHER,
        "d": LEATHER_DARK,
        "o": ROPE,
        ".": LEATHER_DARK,
    }
    t.sprite(body, palette, ox=1, oy=4)
    t.dots(LEATHER_LIT, (4, 9), (5, 8), (4, 10))
    # Складки опавшей кожи.
    t.dots(LEATHER_DARK, (7, 9), (8, 10), (10, 9), (9, 11))
    return t


def purse():
    """Кошель: мешочек кожи с затяжкой и монетой в горловине."""
    t = Tex(CLEAR)
    body = [
        "    ......    ",
        "   .oooooo.   ",
        "  .oo####oo.  ",
        "  .o######o.  ",
        " .LLLLLLLLLL. ",
        ".LLLLLLLLLLLL.",
        "LLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLL",
        ".LLLLLLLLLLLL.",
        ".dLLLLLLLLLLd.",
        " .dddddddddd. ",
        "  .dddddddd.  ",
        "   ........   ",
    ]
    palette = {
        "L": LEATHER,
        "d": LEATHER_DARK,
        "o": ROPE,
        "#": GOLD,
        ".": LEATHER_DARK,
    }
    t.sprite(body, palette, ox=1, oy=1)
    # Свет на левом плече мешка и складка справа.
    t.dots(LEATHER_LIT, (3, 7), (3, 8), (4, 6), (4, 9), (5, 6))
    t.dots(LEATHER_DARK, (11, 8), (11, 9), (10, 10))
    t.dots(GOLD_LIT, (7, 3))
    return t


def cloth():
    """Сукно: свёрнутый отрез с кромкой и складками.

    Первый товар колонии на вывоз, и выглядеть он должен товаром:
    ровный свёрток с видимой кромкой, а не тряпка. Оттенок — небелёный
    лён: крашеное сукно будет отдельной вещью, когда дойдут руки.
    """
    t = Tex(CLEAR)
    base = rgb(0xD9CFB4)
    dark = rgb(0xBCB094)
    lit = rgb(0xEDE5CE)
    edge = rgb(0x8E8368)
    t.rect(1, 3, 14, 12, base)
    t.rect(1, 3, 14, 3, lit)
    t.rect(1, 12, 14, 12, edge)
    t.rect(1, 3, 1, 12, lit)
    t.rect(14, 3, 14, 12, edge)
    # Складки: три валика поперёк свёртка.
    for y in (5, 8, 11):
        t.rect(2, y, 13, y, dark)
        t.rect(2, y - 1, 13, y - 1, lit)
    # Кромка по краю: по ней отрез и узнаётся.
    for x in range(2, 14, 3):
        t.set(x, 12, rgb(0xA8452F))
    return t


def blueprint():
    """Чертёж: свиток с планом дома и восковой печатью."""
    t = Tex(CLEAR)
    t.rect(2, 1, 13, 14, PAPER)
    t.rect(2, 1, 13, 1, PAPER_LIT)
    t.rect(2, 14, 13, 14, PAPER_DARK)
    t.rect(2, 1, 2, 14, PAPER_LIT)
    t.rect(13, 1, 13, 14, PAPER_DARK)
    # План: дом в разрезе, а не абстрактные полоски.
    t.rect(4, 4, 11, 4, INK)
    t.rect(4, 10, 11, 10, INK)
    t.rect(4, 4, 4, 10, INK)
    t.rect(11, 4, 11, 10, INK)
    t.rect(7, 5, 7, 9, INK)
    t.dots(INK, (5, 3), (6, 2), (7, 2), (8, 2), (9, 3), (10, 3))
    t.rect(8, 8, 10, 9, INK)
    # Печать: единственное красное пятно, держит взгляд.
    t.disc(12, 12, 2.2, WAX)
    t.dots(rgb(0xD25B4E), (11, 11))
    return t


def ale():
    """Кружка эля: дерево, обручи, шапка пены.

    Пена — то, по чему кружка узнаётся мгновенно; без неё это просто
    ведро. Ручка справа, свет слева — как у всего прочего.
    """
    t = Tex(CLEAR)
    mug = {
        "o": BEAM_DARK,
        "w": rgb(0x8A6034),
        "W": rgb(0xA87844),
        "d": rgb(0x6B4A28),
        "i": IRON,
        "f": rgb(0xF2EEE0),
        "F": rgb(0xD8D2BE),
        "a": rgb(0xC98A2E),
    }
    t.sprite([
        "ooooooooo",
        "offfffffo",
        "ofFffffFo",
        "oWaaaaado",
        "oWaaaaado",
        "oiiiiiiio",
        "oWaaaaado",
        "oWaaaaado",
        "oiiiiiiio",
        "oWaaaaado",
        "oWaaaaado",
        "ooooooooo",
    ], mug, ox=2, oy=2)
    # Ручка прирастает к боку кружки: отдельно висящая дужка читается
    # вторым предметом, а не частью этого.
    t.dots(BEAM_DARK, (11, 6), (12, 7), (12, 8), (12, 9), (11, 10))
    t.dots(rgb(0x8A6034), (11, 7), (11, 8), (11, 9))
    return t


def cacao():
    """Чаша какао: высокий расписной сосуд майя и шапка пены.

    Пена — как у эля, главное, по чему питьё узнаётся: майя взбивали какао,
    переливая его с высоты, и подавали с пеной выше края. Без неё чаша
    читалась горшком. Роспись — поясом по плечу, красным и охрой, как
    на настоящих сосудах; ниже пояса глина чистая, свет слева.
    """
    t = Tex(CLEAR)
    cup = {
        "o": rgb(0x4A2616),
        "c": rgb(0xA85A38),
        "C": rgb(0xC47048),
        "s": rgb(0x86452A),
        "r": rgb(0x8E2E22),
        "R": rgb(0xA83A2A),
        "q": rgb(0x6E231A),
        "y": rgb(0xD8A03C),
        "Y": rgb(0xECC05A),
        "z": rgb(0xB0802C),
        "f": rgb(0xC99A6C),
        "F": rgb(0xE6C89C),
        "d": rgb(0x7A4A2A),
    }
    t.sprite([
        "   oooo   ",
        "  oFFffo  ",
        " oFFfffdo ",
        "oooooooooo",
        "oCccccccso",
        "oRrrrrrrqo",
        "oYyryyryzo",
        "oRrrrrrrqo",
        "oCccccccso",
        "oCccccccso",
        "oCccccccso",
        " oooooooo ",
    ], cup, ox=3, oy=2)
    return t


def laundry_item():
    """Верёвка в руке: провисшая бечева и полотно на двух прищепках.

    Как блок она — нитка поперёк клетки, и в сумке от неё оставался
    косой волосок, который не узнать. Значок рисует вещь плашмя, как
    ванильную цепь: бечеву с провисом и то, ради чего её натягивают.
    Полотно здесь — подпись, а не обещание: на поставленной верёвке
    висит только то, что повесили.
    """
    t = Tex(CLEAR)
    sag = [3, 3, 4, 4, 4, 5, 5, 5, 5, 5, 5, 4, 4, 4, 3, 3]
    for x in range(4, 12):
        for y in range(sag[x] + 1, 13):
            shade = LINEN
            if x == 4:
                shade = LINEN_LIT
            elif x == 11 or y == 12:
                shade = LINEN_DARK
            t.set(x, y, shade)
    # Крашеная кайма, как у сукна ткача: без неё белый лоскут
    # читается бумагой.
    t.rect(4, 10, 11, 10, DYED)
    t.set(11, 10, DYED_DARK)
    for x, y in enumerate(sag):
        t.set(x, y, BURLAP_LIT if x % 3 == 0 else ROPE)
        if not 4 <= x <= 11:
            t.set(x, y + 1, BARK_DARK)
    for x in (5, 10):
        t.set(x, sag[x] - 1, PLANK_LIT)
        t.set(x, sag[x], PLANK)
        t.set(x, sag[x] + 1, BEAM_DARK)
    return t


COIN_STAMP = [
    " #### ",
    "#+  +#",
    "#   ##",
    "##   #",
    "#+  +#",
    " #### ",
]

CROSS_STAMP = [
    "  ##  ",
    "  ##  ",
    "######",
    "######",
    "  ##  ",
    "  ##  ",
]

SUN_STAMP = [
    "# ## #",
    " #### ",
    "######",
    "######",
    " #### ",
    "# ## #",
]


# --- подложки интерфейса ---------------------------------------------------
#
# Сюда я пришёл не от красоты, а от жалобы: «вырвиглазное меню, ничего
# не разобрать». Причина оказалась ровно одна и очень простая. Карточки
# разделов рисовались вдавленной плашкой owo — а она ТЁМНО-СЕРАЯ, —
# и по ней шёл мой тёмно-коричневый текст. Два тёмных слоя друг на друге
# читаются никак.
#
# Чинить перекраской текста в светлый значило бы вернуть «чисто тёмное
# меню», от которого заказчик отказался раньше. Поэтому подложки свои:
# лён окна и пергамент карточек. Тёмные чернила по светлой бумаге —
# то, как выглядят и ванильные книги, и интерфейс MineColonies, на
# который заказчик просил равняться.


def altar_side():
    """Бок алтаря: тёсаный камень с выжженным знаком.

    Не кладка: алтарь вытесан из одного камня, и шов на нём был бы
    ложью о том, как его делали. Знак — простая насечка, одинаковая
    у обоих народов: боги у них разные, а камень один.
    """
    t = Tex(STONE)
    t.rect(0, 0, N - 1, 0, STONE_LIT)
    t.rect(0, N - 1, N - 1, N - 1, STONE_DARK)
    t.rect(0, 1, 0, N - 2, STONE_LIT)
    t.rect(N - 1, 1, N - 1, N - 2, STONE_DARK)

    # Выемка посередине: по ней бок читается боком, а не куском стены.
    t.rect(3, 4, 12, 11, STONE_DARK)
    t.rect(4, 5, 11, 10, STONE)

    # Знак: круг над чертой — небо над землёй, и ничего больше.
    t.ring(8, 7, 2.6, 1.6, EMBER)
    t.rect(5, 10, 10, 10, EMBER_DARK)
    return t


def altar_top():
    """Верх алтаря: плита с ложбиной, куда кладут.

    Ложбина обязательна. Плоская крышка читается как ступень,
    и игрок не понимает, что сюда что-то кладут: место для жертвы
    должно быть видно сверху с первого взгляда.
    """
    t = Tex(STONE_LIT)
    t.rect(1, 1, 14, 14, STONE)
    t.rect(3, 3, 12, 12, STONE_DARK)
    t.rect(4, 4, 11, 11, STONE)
    t.ring(7.5, 7.5, 3.2, 2.2, EMBER_DARK)
    t.disc(7.5, 7.5, 1.6, EMBER)
    t.dots(STONE_DARK, (2, 13), (13, 2), (2, 2), (13, 13))
    return t


def sickle():
    """Серп изобилия: лезвие дугой и колос у рукояти.

    Артефакт обязан читаться артефактом: у всех трёх один приём —
    золотой отблеск на обычном материале. Мерцания и рамок нет,
    их даёт эпическая редкость самой подписью.
    """
    t = Tex(CLEAR)
    # Лезвие дугой, в два пикселя толщиной.
    #
    # В один оно рассыпалось на точки: дуга из отдельных пикселей
    # читается пунктиром, а не сталью. Два слоя — тело и обух — дают
    # сплошную линию, и серп узнаётся с первого взгляда в инвентаре.
    t.ring(7.5, 9.0, 6.6, 5.0, SILVER)
    t.ring(7.5, 9.0, 6.6, 6.0, SILVER_DARK)
    t.ring(7.5, 9.0, 5.6, 5.0, SILVER_LIT)
    # Нижняя половина кольца — не лезвие, а рукоять: её стирают.
    t.rect(0, 9, N - 1, N - 1, CLEAR)
    # Рукоять снизу справа — поверх стёртого низа дуги.
    t.line(10, 15, 13, 9, PLANK_DARK)
    t.line(9, 15, 12, 9, PLANK)
    t.dots(PLANK_LIT, (10, 13), (11, 11))
    # Колос у рукояти: то, ради чего серп.
    t.dots(GOLD, (13, 12), (14, 13), (14, 11))
    t.dots(GOLD_LIT, (13, 11))
    return t


def plumb():
    """Отвес зодчего: шнур, груз и золотая метка.

    Отвес, а не молот: бог камня в этом моде не бьёт, а выверяет.
    Стена, которую он поднимает, встаёт ровной — об этом и предмет.
    """
    t = Tex(CLEAR)
    # Перекладина сверху.
    t.rect(3, 1, 12, 2, BEAM)
    t.rect(3, 1, 12, 1, BEAM_LIT)
    t.dots(GOLD, (3, 2), (12, 2))
    # Шнур строго по середине: в этом весь смысл вещи.
    t.rect(8, 3, 8, 9, PLANK_DARK)
    # Груз: гранёная капля.
    t.rect(6, 9, 10, 10, STONE_LIT)
    t.rect(6, 10, 10, 12, STONE)
    t.rect(7, 12, 9, 13, STONE_DARK)
    t.set(8, 14, STONE_DARK)
    t.dots(GOLD_LIT, (7, 10), (8, 10))
    t.dots(GOLD, (9, 11))
    return t


def watchers_eye():
    """Око дозора: глаз в кольце из камня.

    Зрачок золотой, белок светлый, кольцо — тот же резной камень,
    из которого у майя углы, а у норманнов гребень. Артефакт один
    на оба народа, и материал у него должен быть общий.
    """
    t = Tex(CLEAR)
    t.disc(7.5, 7.5, 7.4, STONE_DARK)
    t.disc(7.5, 7.5, 6.6, STONE)
    t.ring(7.5, 7.5, 7.4, 6.6, STONE_LIT)

    # Белок — миндалём, а не прямоугольником: прямоугольная прорезь
    # читалась щелью почтового ящика. Ширина строк набрана руками,
    # потому что миндаль в шестнадцати пикселях не описывается формулой.
    white = rgb(0xF2ECDC)
    shade = rgb(0xC9BFA6)
    for y, (x0, x1) in ((5, (6, 9)), (6, (4, 11)), (7, (2, 13)),
                        (8, (2, 13)), (9, (4, 11)), (10, (6, 9))):
        t.rect(x0, y, x1, y, white)
    t.rect(6, 5, 9, 5, shade)
    t.rect(4, 6, 11, 6, shade)

    # Радужка и зрачок: золото по тёмному, блик сверху слева — как везде.
    t.disc(7.5, 7.5, 3.1, GOLD)
    t.ring(7.5, 7.5, 3.1, 2.4, GOLD_DARK)
    t.disc(7.5, 7.5, 1.5, rgb(0x2B2110))
    t.dots(GOLD_LIT, (6, 6), (7, 6))
    t.dots(white, (6, 7))
    return t


def linen():
    """Поле окна: некрашеный лён, едва заметная нить."""
    base = rgb(0xC9B695)
    dark = rgb(0xBCA884)
    lit = rgb(0xD6C4A6)
    t = Tex(base)
    for y in range(N):
        for x in range(N):
            if (x + y * 3) % 7 == 0:
                t.set(x, y, lit)
            elif (x * 2 + y) % 11 == 0:
                t.set(x, y, dark)
    return t


def parchment():
    """Поле карточки: бумага светлее окна, чтобы карточка выступала."""
    base = rgb(0xE3D6B4)
    dark = rgb(0xD5C6A0)
    lit = rgb(0xF0E6CA)
    t = Tex(base)
    for x, y in [(3, 2), (4, 2), (11, 5), (2, 9), (13, 12), (8, 14)]:
        t.set(x, y, lit)
    for x, y in [(6, 3), (12, 7), (5, 8), (9, 11), (1, 13)]:
        t.set(x, y, dark)
    return t


def main():
    made = []
    made.append(linen().save("gui", "linen"))
    made.append(parchment().save("gui", "parchment"))

    made.append(plaster(PLASTER, PLASTER_DARK, PLASTER_LIT, PLASTER_CRACK)
                .save("block", "plaster"))
    made.append(plaster(OCHRE, OCHRE_DARK, OCHRE_LIT, OCHRE_CRACK)
                .save("block", "ochre_plaster"))
    made.append(timber_frame().save("block", "timber_frame"))
    made.append(thatch().save("block", "thatch"))
    made.append(carved_stone().save("block", "carved_stone"))
    made.append(town_hall_side().save("block", "town_hall_side"))
    made.append(town_hall_top().save("block", "town_hall_top"))
    made.append(brick(STONE_DARK, STONE, STONE_LIT, MORTAR)
                .save("block", "town_hall_bottom"))
    made.append(chimney_side().save("block", "chimney"))
    made.append(chimney_top().save("block", "chimney_top"))
    made.append(firewood_end().save("block", "firewood_end"))
    made.append(log_end().save("block", "firewood_log_end"))
    made.append(firewood_side().save("block", "firewood_side"))
    made.append(grain_sack().save("block", "grain_sack"))
    # Нарисованного белья больше нет: на верёвке висит то, что повесили,
    # и рисует это клиент по содержимому блока. Остались бечева и прищепки.
    made.append(rope_texture().save("block", "laundry_rope"))

    for name, colour in (("bed", MARK_BED), ("door", MARK_DOOR),
                         ("storage", MARK_STORE), ("workstation", MARK_WORK),
                         ("decor", MARK_DECOR)):
        made.append(marker(colour, _glyph(MARK_GLYPHS[name]))
                    .save("block", "marker_" + name))

    made.append(coin(COPPER_DARK, COPPER, COPPER_LIT, CROSS_STAMP)
                .save("item", "coin"))
    made.append(coin(SILVER_DARK, SILVER, SILVER_LIT, COIN_STAMP)
                .save("item", "silver_coin"))
    made.append(coin(GOLD_DARK, GOLD, GOLD_LIT, SUN_STAMP)
                .save("item", "gold_coin"))
    # Стопки: «несколько» и «груда», по той же палитре, что сама монета.
    for name, colours in (("coin", (COPPER_DARK, COPPER, COPPER_LIT)),
                          ("silver_coin", (SILVER_DARK, SILVER, SILVER_LIT)),
                          ("gold_coin", (GOLD_DARK, GOLD, GOLD_LIT))):
        made.append(coin_stacks(*colours, [(5, 14, 2), (10, 15, 3)])
                    .save("item", name + "_few"))
        made.append(coin_stacks(*colours, [(4, 13, 5), (11, 12, 4), (8, 16, 3)])
                    .save("item", name + "_pile"))
    made.append(ale().save("item", "ale"))
    made.append(cacao().save("item", "cacao"))
    made.append(laundry_item().save("item", "laundry"))
    made.append(cloth().save("item", "cloth"))
    made.append(purse().save("item", "purse"))
    made.append(purse_empty().save("item", "purse_empty"))
    made.append(blueprint().save("item", "town_hall_blueprint"))

    made.append(altar_side().save("block", "altar_side"))
    made.append(altar_top().save("block", "altar_top"))
    made.append(sickle().save("item", "sickle_of_plenty"))
    made.append(plumb().save("item", "builders_plumb"))
    made.append(watchers_eye().save("item", "watchers_eye"))

    print("нарисовано текстур: %d" % len(made))


if __name__ == "__main__":
    main()
