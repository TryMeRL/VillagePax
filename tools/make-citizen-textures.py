#!/usr/bin/env python3
"""Рисует облик жителей: народ, пол и ремесло — каждый своей текстурой.

Зачем: до этого на всех жителей мира была одна картинка. Майя выглядели
норманнами, женщина — мужчиной, пахарь — стражником. К людям, которых
не различить, нельзя привязаться, и колония читалась счётчиком населения,
а не местом, где живут люди. Это и был главный ответ на «скучно».

Три яруса, и каждый виден с десяти шагов:

1. **Народ** — кожа, волосы, крой и цвет одежды, а теперь и тело:
   борода гнома, уши эльфа, перья старейшины майя.
2. **Пол** — причёска, тонкие руки и юбка у женщин.
3. **Ремесло** — то, во что человек одет для работы: фартук каменщика,
   соломенная шляпа пахаря с полями, шлем и наплечники стража, котомка
   курьера, ряса старейшины.

Кожа у человека — современная раскладка игрока (64x64 со вторым слоем:
шляпа, куртка, рукава, штанины), а под ней ещё 64x64 для примет. Где
какая грань, говорит `citizen_body.py` — тот же модуль, из которого
пишется geo-модель, поэтому модель и кожа разойтись не могут.

Второй слой — это объём, а не украшение. Волосы, поля, фартук и кольчуга
на нём стоят над кожей на полтекселя и отбрасывают край; на первом слое
те же пиксели читались наклейкой.

    python tools/make-citizen-textures.py
"""

import zlib
from pathlib import Path

from PIL import Image

import citizen_body
from citizen_body import faces

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/villagepax/textures/entity/citizen"


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, 255)


def mix(a, b, share):
    return tuple(int(round(a[i] + (b[i] - a[i]) * share)) for i in range(3)) + (255,)


def tone(colour, factor):
    """Светлее (больше единицы) или темнее (меньше) того же цвета."""
    if factor >= 1:
        return mix(colour, (255, 255, 255, 255), min(1.0, factor - 1))
    return mix(colour, (0, 0, 0, 255), 1 - factor)


CLEAR = (0, 0, 0, 0)

# --- народы -----------------------------------------------------------------
#
# Палитра народа — это его лицо в прямом смысле. Меняется здесь, а не
# в двадцати местах.

NORMAN = {
    "skin": rgb(0xD8A882),
    "skin_dark": rgb(0xB78A68),
    "hair": rgb(0x6B4A2E),
    "hair_dark": rgb(0x513825),
    "cloth": rgb(0x9C7F55),
    "cloth_dark": rgb(0x7E6543),
    "cloth_lit": rgb(0xB59468),
    "trousers": rgb(0x5A5A66),
    "trousers_dark": rgb(0x474751),
    "boots": rgb(0x3E3226),
    "belt": rgb(0x4A3524),
    "accent": rgb(0xA8452F),
    "helm": rgb(0x9AA0A8),
}

MAYA = {
    "skin": rgb(0xA8724A),
    "skin_dark": rgb(0x8A5B39),
    "hair": rgb(0x241A12),
    "hair_dark": rgb(0x17100B),
    "cloth": rgb(0xE2DAC6),
    "cloth_dark": rgb(0xC6BCA4),
    "cloth_lit": rgb(0xF2ECDC),
    "trousers": rgb(0xD8CFB8),
    "trousers_dark": rgb(0xBAAF94),
    "boots": rgb(0x6E5334),
    "belt": rgb(0xB4503A),
    "accent": rgb(0x3E8C6A),
    # Стёганый хлопок, а не железо: страж майя в железной каске
    # выглядел бы норманном, заблудившимся в джунглях.
    "helm": rgb(0xC9B48A),
}

# Пони — луговые коневоды, третий народ мода. Масть, а не загар:
# пшеничная кожа и льняная грива взяты у их же лошадей, и рядом
# с бурым норманном и смуглым майя народ читается с одного взгляда.
# Зелёная тесьма — луг, на котором они живут.
PONY = {
    "skin": rgb(0xE0C08A),
    "skin_dark": rgb(0xC09E68),
    # Грива тёмная, а не в тон масти. Пока пони были людьми, светлые
    # волосы на светлой коже читались — голова маленькая, и разница
    # видна вблизи. У коня грива идёт по всей шее и холке, и в тон масти
    # она исчезает вовсе: первый же лист показал не пони, а ламу без гривы.
    # Пшеничная масть с тёмной гривой — это буланая, самая узнаваемая
    # лошадиная масть вообще.
    "hair": rgb(0x5B4326),
    "hair_dark": rgb(0x3A2A16),
    "cloth": rgb(0xB8C49A),
    "cloth_dark": rgb(0x94A078),
    "cloth_lit": rgb(0xD2DCB4),
    "trousers": rgb(0x7A5C3A),
    "trousers_dark": rgb(0x5E462C),
    "boots": rgb(0x4A3520),
    "belt": rgb(0x8A6A3C),
    "accent": rgb(0x4E8A3E),
    "helm": rgb(0x9AA0A8),
}

# Гномы — подземный народ. Кожа не бледная, а обветренная: они не сидят
# в норе, они в ней работают у горна. Медь в волосах и бороде — единственное
# яркое, что есть в чертоге; сукно серое, как порода, а тесьма латунная,
# потому что золото гномы не носят, а отдают Каменному Отцу.
DWARF = {
    "skin": rgb(0xC99A78),
    "skin_dark": rgb(0xA87C5C),
    "hair": rgb(0xA24A22),
    "hair_dark": rgb(0x7C3517),
    "cloth": rgb(0x4E4A52),
    "cloth_dark": rgb(0x393640),
    "cloth_lit": rgb(0x676270),
    "trousers": rgb(0x3A3630),
    "trousers_dark": rgb(0x2B2822),
    "boots": rgb(0x2E2620),
    "belt": rgb(0x6A4A2A),
    "accent": rgb(0xC08A2E),
    "helm": rgb(0x7C828A),
}

# Эльфы — зеркало гномов и здесь. Там медь и порода, тут берёста и лист:
# пепельно-русые волосы, белая кора вместо штанов, зелёное сукно полога.
# Рядом с рыжим коренастым гномом народ читается с одного взгляда,
# а это и есть вся задача облика.
ELF = {
    "skin": rgb(0xEBD3BE),
    "skin_dark": rgb(0xCDB29B),
    # Волосы темнее кожи нарочно. Первая проба была пепельно-русой в тон
    # лицу, и на контактном листе голова эльфа читалась пятном без черт:
    # кожа, волосы и борода сливались в одно. Народ узнают по силуэту
    # головы раньше, чем по одежде.
    "hair": rgb(0xB9A97E),
    "hair_dark": rgb(0x93855F),
    "cloth": rgb(0x6E8C5A),
    "cloth_dark": rgb(0x546E44),
    "cloth_lit": rgb(0x8FAE76),
    "trousers": rgb(0xCFC6AE),
    "trousers_dark": rgb(0xB0A78F),
    "boots": rgb(0x5A4A32),
    "belt": rgb(0x7A6A44),
    # Серебро, а не золото, и это не вкусовщина: тесьмой красятся плащ
    # стража, кайма купца и одеяние старейшины. С золотом эльфийский
    # старейшина оказался неотличим от гномьего — оба в латуни, а это
    # два самых непохожих народа мода.
    "accent": rgb(0xA8BFD0),
    "helm": rgb(0xC3D0D8),
}

# Северяне — народ заснеженной тайги. Кожа бледная, как у тех, кто полгода
# не видит солнца; волосы соломенные, у кого-то рыжие; шерсть синяя,
# как вода фьорда подо льдом, и латунная тесьма — единственное, что
# блестит у них зимой.
NORD = {
    "skin": rgb(0xECC8AC),
    "skin_dark": rgb(0xCFA88C),
    "hair": rgb(0xD8B868),
    "hair_dark": rgb(0xB0883E),
    "cloth": rgb(0x4E6E92),
    "cloth_dark": rgb(0x3C5878),
    "cloth_lit": rgb(0x6A8AAE),
    "trousers": rgb(0x6A5A48),
    "trousers_dark": rgb(0x524536),
    "boots": rgb(0x5A5048),
    "belt": rgb(0x3E2E20),
    "accent": rgb(0xC8A040),
    "helm": rgb(0x8A9098),
}

# Ямато — народ цветущей вишни. Волосы чёрные, как тушь; кимоно цвета
# индиго — краска, которой красили одежду веками; пояс-оби и тесьма —
# киноварь, тот же красный, что у тории. Шлем стража — чёрный лак.
YAMATO = {
    "skin": rgb(0xE8C8A6),
    "skin_dark": rgb(0xCBA888),
    "hair": rgb(0x201C22),
    "hair_dark": rgb(0x0F0D12),
    "cloth": rgb(0x2E416E),
    "cloth_dark": rgb(0x223156),
    "cloth_lit": rgb(0x4A5E92),
    "trousers": rgb(0x4A4540),
    "trousers_dark": rgb(0x36322E),
    "boots": rgb(0x6A5238),
    "belt": rgb(0xB8322A),
    "accent": rgb(0xC0392B),
    "helm": rgb(0x2A2A30),
}

CULTURES = {"norman": NORMAN, "maya": MAYA, "pony": PONY,
            "dwarf": DWARF, "elf": ELF, "nord": NORD, "yamato": YAMATO}

# Кто носит бороду. Это про народ, а не про моду: северянин и подгорный
# с бородой, южанин, степняк и лесной без.
BEARDED = ("norman", "dwarf", "nord")

# У кого борода длинная, до пояса, и косы у женщин: подгорный народ
# и северяне — оба живут там, где холодно, и оба этим гордятся.
LONG_HAIRED = ("dwarf", "nord")

EYE = rgb(0x2B2B33)
EYE_WHITE = rgb(0xE6E6DE)
MOUTH = rgb(0x8A5B4A)

IRON = rgb(0x9AA0A8)
IRON_DARK = rgb(0x6E747C)
IRON_LIT = rgb(0xC4C9CE)
LEATHER = rgb(0x8A5C33)
LEATHER_DARK = rgb(0x5E3E24)
LEATHER_LIT = rgb(0xA67646)
STRAW = rgb(0xD4B057)
STRAW_DARK = rgb(0xA8862F)
STRAW_LIT = rgb(0xE8CD7E)
GREY_HAIR = rgb(0xC6C2B8)
GREY_HAIR_DARK = rgb(0x9C978C)
GOLD = rgb(0xD8AA3C)
GOLD_DARK = rgb(0xA67C22)
LINEN = rgb(0xE4DFD2)
LINEN_DARK = rgb(0xC4BEAE)
RED = rgb(0xB0402C)

SIDES = ("front", "back", "left", "right")
ALL = SIDES + ("top", "bottom")

# --- кожа игрока -------------------------------------------------------------
#
# Углы частей — из citizen_body.SKIN, ширина руки — от пола: у мужчины
# четыре текселя, у женщины три, как у Стива и Алекс.


def human_parts(woman):
    at = citizen_body.SKIN
    arm = 3 if woman else 4
    return {
        "head": faces(*at["head"], 8, 8, 8),
        "hat": faces(*at["hat"], 8, 8, 8),
        "body": faces(*at["body"], 8, 12, 4),
        "jacket": faces(*at["jacket"], 8, 12, 4),
        "arm": faces(*at["arm_right"], arm, 12, 4),
        "sleeve": faces(*at["sleeve_right"], arm, 12, 4),
        "arm_left": faces(*at["arm_left"], arm, 12, 4),
        "sleeve_left": faces(*at["sleeve_left"], arm, 12, 4),
        "leg": faces(*at["leg_right"], 4, 12, 4),
        "pants": faces(*at["pants_right"], 4, 12, 4),
        "leg_left": faces(*at["leg_left"], 4, 12, 4),
        "pants_left": faces(*at["pants_left"], 4, 12, 4),
    }


REGIONS = citizen_body.HUMAN.regions
PONY_REGIONS = citizen_body.PONY.regions


class Skin:
    """Холст с гранями кубов. Всё рисование — через грани."""

    def __init__(self, width, height, seed):
        self.image = Image.new("RGBA", (width, height), CLEAR)
        self.seed = seed

    def noise(self, x, y, salt=0):
        """Шум, который не меняется от запуска к запуску.

        Случайность генератора — это правки в каждой картинке при каждом
        запуске, то есть сотня изменённых файлов в истории без единого
        изменения в облике.
        """
        value = zlib.crc32(("%s:%d:%d:%d" % (self.seed, x, y, salt)).encode())
        return (value & 0xFFFF) / 0xFFFF

    def fill(self, rect, colour):
        x, y, width, height = rect
        for dx in range(width):
            for dy in range(height):
                self.image.putpixel((x + dx, y + dy), colour)

    def band(self, rect, colour, top=0, rows=1):
        """Полоса поперёк грани: пояс, обшлаг, край шлема."""
        x, y, width, height = rect
        top = max(0, min(top, height))
        rows = max(0, min(rows, height - top))
        self.fill((x, y + top, width, rows), colour)

    def column(self, rect, colour, left=0, cols=1, top=0, rows=None):
        x, y, width, height = rect
        rows = height - top if rows is None else rows
        left = max(0, min(left, width))
        cols = max(0, min(cols, width - left))
        self.fill((x + left, y + top, cols, max(0, min(rows, height - top))), colour)

    def px(self, rect, dx, dy, colour):
        x, y, width, height = rect
        if 0 <= dx < width and 0 <= dy < height:
            self.image.putpixel((x + dx, y + dy), colour)

    def get(self, rect, dx, dy):
        x, y, _, _ = rect
        return self.image.getpixel((x + dx, y + dy))

    def cube(self, cube, colour, names=ALL):
        for name in names:
            self.fill(cube[name], colour)

    def weave(self, rect, base, dark, lit, density=0.16, salt=0):
        """Ткань: редкие тёмные и светлые ворсинки поверх заливки.

        Плоская заливка на модели читается пластмассой; одна ворсинка
        на шесть клеток уже даёт сукно, а не краску.
        """
        x, y, width, height = rect
        for dx in range(width):
            for dy in range(height):
                roll = self.noise(x + dx, y + dy, salt)
                colour = base
                if roll < density / 2:
                    colour = dark
                elif roll > 1 - density / 2:
                    colour = lit
                self.image.putpixel((x + dx, y + dy), colour)

    def cloth(self, cube, colour, names=ALL, salt=0):
        for name in names:
            self.weave(cube[name], colour, tone(colour, 0.9), tone(colour, 1.06), salt=salt)

    def strands(self, rect, hair, dark, lit=None, top=0, rows=None):
        """Волосы прядями: тёмный столбец через два и светлый блик сверху."""
        x, y, width, height = rect
        rows = height - top if rows is None else rows
        for dx in range(width):
            for dy in range(top, min(height, top + rows)):
                colour = hair
                if (dx + (dy // 3)) % 3 == 2:
                    colour = dark
                elif lit is not None and dy == top and dx % 2 == 0:
                    colour = lit
                self.image.putpixel((x + dx, y + dy), colour)

    def shade(self, cube, dark, names=SIDES):
        """Тень нижним рядом каждой боковой грани.

        Свет у Minecraft падает сверху, и граням он и так даёт разную
        яркость. Нижний ряд темнее — это касание соседней части: рукав
        у запястья, рубаха у пояса, — без него части сливаются.
        """
        for name in names:
            x, y, width, height = cube[name]
            self.fill((x, y + height - 1, width, 1), dark)

    def outline(self, cube, colour, names=SIDES):
        """Кромка второго слоя: самый край непрозрачного темнее.

        Второй слой стоит над кожей на полтекселя, и без кромки его край
        сливается с тем, что под ним; с кромкой фартук — это фартук.
        """
        for name in names:
            x, y, width, height = cube[name]
            edge = []
            for dx in range(width):
                for dy in range(height):
                    here = self.image.getpixel((x + dx, y + dy))
                    if here[3] == 0:
                        continue
                    below = self.image.getpixel((x + dx, y + dy + 1)) if dy + 1 < height else CLEAR
                    if dy == height - 1 or below[3] == 0:
                        edge.append((x + dx, y + dy))
            for point in edge:
                self.image.putpixel(point, colour)

    def mirror(self, source, target):
        """Левая рука и нога — зеркало правых.

        Грани меняются местами (правая сторона правой руки — это левая
        сторона левой) и каждая отражается по горизонтали. Так делает
        и сама игра для зеркальных кубов, и так левая рука выходит
        отражением, а не копией, — у копии пуговицы ушли бы за спину.
        """
        swap = {"right": "left", "left": "right"}
        for name in ALL:
            sx, sy, sw, sh = source[name]
            tx, ty, tw, th = target[swap.get(name, name)]
            part = self.image.crop((sx, sy, sx + sw, sy + sh)).transpose(
                Image.FLIP_LEFT_RIGHT)
            if (tw, th) != (sw, sh):
                part = part.resize((tw, th), Image.NEAREST)
            self.image.paste(part, (tx, ty))

    def save(self, name):
        path = OUT / (name + ".png")
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image.save(path, optimize=True)
        return path


# --- части тела человека --------------------------------------------------------


def draw_head(skin, parts, look, woman):
    head, hat = parts["head"], parts["hat"]
    skin.cube(head, look["skin"])
    skin.fill(head["bottom"], look["skin_dark"])
    skin.shade(head, look["skin_dark"], names=("left", "right", "back"))

    # Волосы на коже — под вторым слоем, чтобы в просвете причёски
    # была голова, а не лысина.
    skin.strands(head["top"], look["hair"], look["hair_dark"])
    for name in SIDES:
        skin.band(head[name], look["hair"], rows=2)
    skin.fill(head["back"], look["hair"])

    face = head["front"]
    # Брови, глаза, нос, рот — лицо читается с двадцати шагов, если у него
    # есть надбровье: без бровей глаза висят на гладком месте.
    brow = tone(look["hair_dark"], 0.9)
    for dx in (1, 5):
        skin.px(face, dx, 3, brow)
        skin.px(face, dx + 1, 3, brow)
        skin.px(face, dx, 4, EYE_WHITE)
        skin.px(face, dx + 1, 4, EYE)
    skin.px(face, 3, 4, look["skin"])
    skin.px(face, 4, 4, look["skin"])
    skin.px(face, 3, 5, look["skin_dark"])
    skin.px(face, 4, 5, tone(look["skin_dark"], 0.92))
    skin.px(face, 3, 6, MOUTH)
    skin.px(face, 4, 6, MOUTH)
    skin.px(face, 1, 7, tone(look["skin"], 0.95))
    skin.px(face, 6, 7, tone(look["skin"], 0.95))
    if woman:
        # Румянец и ресница: женское лицо отличимо и без причёски.
        blush = mix(look["skin"], rgb(0xD06A5A), 0.22)
        skin.px(face, 1, 5, blush)
        skin.px(face, 6, 5, blush)
        skin.px(face, 1, 3, EYE)
        skin.px(face, 6, 3, EYE)

    # Второй слой — объём причёски.
    lit = tone(look["hair"], 1.12)
    skin.strands(hat["top"], look["hair"], look["hair_dark"], lit)
    for name in ("left", "right"):
        skin.strands(hat[name], look["hair"], look["hair_dark"], lit, rows=3 if not woman else 8)
    skin.strands(hat["back"], look["hair"], look["hair_dark"], lit, rows=6 if not woman else 8)
    # Чёлка: неровный край, а не линейка.
    front = hat["front"]
    for dx in range(8):
        rows = 1 + (1 if skin.noise(dx, 0, 7) > 0.5 else 0)
        if woman:
            rows = 2 if dx in (0, 7) else 1
        for dy in range(rows):
            skin.px(front, dx, dy, look["hair"] if dy == 0 else look["hair_dark"])
    if woman:
        # Пряди вдоль лица во всю высоту — лицо в рамке волос.
        for dy in range(8):
            skin.px(front, 0, dy, look["hair"])
            skin.px(front, 7, dy, look["hair_dark"])


def draw_beard(skin, parts, look, colour=None):
    """Борода на втором слое: стоит над лицом, а не нарисована на нём."""
    colour = colour or look["hair"]
    dark = tone(colour, 0.82)
    front = parts["hat"]["front"]
    for dx in range(1, 7):
        skin.px(front, dx, 7, colour)
    for dx in (1, 2, 5, 6):
        skin.px(front, dx, 6, colour)
    # Усы над губой.
    skin.px(front, 2, 5, dark)
    skin.px(front, 5, 5, dark)
    for name in ("left", "right"):
        skin.band(parts["hat"][name], colour, top=5, rows=3)
        skin.band(parts["hat"][name], dark, top=7, rows=1)


def draw_long_beard(skin, look, colour=None):
    """Борода гнома — отдельный куб, свисающий на грудь."""
    colour = colour or look["hair"]
    region = REGIONS["beard"]
    skin.cube(region, colour)
    for name in ("front", "left", "right", "bottom"):
        skin.strands(region[name], colour, tone(colour, 0.8), tone(colour, 1.12))
    # Раздвоенный конец и латунное кольцо — гномья борода заплетена.
    front = region["front"]
    width, height = front[2], front[3]
    for dx in (3, 4):
        skin.px(front, dx, height - 1, CLEAR)
    skin.px(front, 1, height - 3, look["accent"])
    skin.px(front, 6, height - 3, look["accent"])
    # Верхний ряд и спинка скрыты подбородком — там тон тени.
    skin.fill(region["back"], tone(colour, 0.7))


def draw_body(skin, parts, look, woman):
    body, jacket = parts["body"], parts["jacket"]
    skin.cloth(body, look["cloth"], salt=1)
    skin.fill(body["bottom"], look["trousers"])
    skin.band(body["front"], look["cloth_lit"], rows=1)
    for name in ("front", "back"):
        x, y, width, height = body[name]
        for column in (2, 5):
            skin.column(body[name], look["cloth_dark"], left=column, top=2, rows=height - 4)
    skin.shade(body, look["cloth_dark"])

    # Второй слой: ворот и пояс — то, что у рубахи выступает.
    for name in SIDES:
        skin.band(jacket[name], tone(look["cloth"], 0.86), rows=1)
    belt_row = 7 if woman else 9
    for name in SIDES:
        skin.band(jacket[name], look["belt"], top=belt_row, rows=2 if not woman else 1)
    skin.px(jacket["front"], 3, belt_row, look["accent"])
    skin.px(jacket["front"], 4, belt_row, tone(look["accent"], 1.2))


def draw_arm(skin, parts, look):
    arm, sleeve = parts["arm"], parts["sleeve"]
    skin.cloth(arm, look["cloth"], salt=2)
    skin.fill(arm["bottom"], look["skin_dark"])
    for name in SIDES:
        skin.band(arm[name], look["skin"], top=8, rows=4)
        skin.band(arm[name], look["skin_dark"], top=11, rows=1)
    # Обшлаг на втором слое — край рукава стоит над запястьем.
    for name in SIDES:
        skin.band(sleeve[name], look["cloth_dark"], top=7, rows=1)


def draw_leg(skin, parts, look):
    leg, pants = parts["leg"], parts["pants"]
    skin.cloth(leg, look["trousers"], salt=3)
    skin.fill(leg["bottom"], look["boots"])
    for name in SIDES:
        skin.band(leg[name], look["boots"], top=9, rows=3)
    skin.shade(leg, look["trousers_dark"])
    # Голенище на втором слое: сапог шире штанины.
    for name in SIDES:
        skin.band(pants[name], tone(look["boots"], 1.1), top=8, rows=1)
        skin.band(pants[name], look["boots"], top=9, rows=2)
        skin.band(pants[name], tone(look["boots"], 0.8), top=11, rows=1)


def draw_skirt(skin, look, colour=None, trim=None):
    region = REGIONS["skirt"]
    colour = colour or look["cloth"]
    skin.cloth(region, colour, salt=4)
    for name in ("front", "back"):
        x, y, width, height = region[name]
        for column in (1, 4, 6):
            skin.column(region[name], tone(colour, 0.88), left=column, top=1)
    for name in SIDES:
        skin.band(region[name], trim or look["accent"], top=region[name][3] - 1, rows=1)
    skin.fill(region["top"], CLEAR)
    skin.fill(region["bottom"], CLEAR)


def draw_hair(skin, look, colour=None):
    """Волосы до плеч — сзади, на отдельной пластине."""
    region = REGIONS["hair"]
    colour = colour or look["hair"]
    skin.cube(region, colour)
    for name in ("back", "front", "left", "right"):
        skin.strands(region[name], colour, tone(colour, 0.8), tone(colour, 1.1))
    back = region["back"]
    for dx in range(back[2]):
        if skin.noise(dx, 9, 11) > 0.55:
            skin.px(back, dx, back[3] - 1, CLEAR)


def draw_braid(skin, look, colour=None):
    region = REGIONS["braid"]
    colour = colour or look["hair"]
    skin.cube(region, colour)
    for name in SIDES:
        x, y, width, height = region[name]
        for dy in range(height):
            for dx in range(width):
                skin.px(region[name], dx, dy,
                        tone(colour, 0.78) if (dx + dy) % 2 else colour)
        skin.band(region[name], look["accent"], top=height - 3, rows=1)


def draw_topknot(skin, look, colour=None):
    """Тёммагэ: тугой узел вдоль темени, чуть темнее волос."""
    region = REGIONS["topknot"]
    colour = colour or look["hair"]
    skin.cube(region, colour)
    for name in SIDES:
        skin.strands(region[name], colour, tone(colour, 0.75), tone(colour, 1.2))
    skin.fill(region["top"], tone(colour, 1.15))


def draw_bun(skin, look, colour=None):
    """Узел на затылке и шпилька-кандзаси с киноварной бусиной."""
    region = REGIONS["bun"]
    colour = colour or look["hair"]
    skin.cube(region, colour)
    for name in SIDES:
        skin.strands(region[name], colour, tone(colour, 0.75), tone(colour, 1.2))
    pin = REGIONS["kanzashi"]
    skin.cube(pin, GOLD)
    for name in ("left", "right"):
        skin.fill(pin[name], look["accent"])


def draw_ears(skin, look):
    """Острое ухо: кончик светлее, раковина темнее, у корня прядь."""
    region = REGIONS["ear"]
    skin.cube(region, look["skin"])
    for name in ("left", "right"):
        x, y, width, height = region[name]
        skin.column(region[name], look["skin_dark"], left=0, top=1)
        skin.px(region[name], width - 1, 0, tone(look["skin"], 1.06))
        skin.band(region[name], look["hair"], top=height - 1, rows=1)
    skin.fill(region["front"], tone(look["skin"], 0.96))
    skin.fill(region["top"], tone(look["skin"], 1.05))


# --- ремёсла ----------------------------------------------------------------
#
# Каждое ремесло — это одежда для работы, а не цветная нашивка. Игрок
# должен узнавать человека по делу, не наводя на него прицел.


def craft_builder(skin, parts, look, woman, people):
    """Каменщик: кожаный фартук, повязка на лбу и сумка с инструментом."""
    jacket = parts["jacket"]
    x, y, width, height = jacket["front"]
    skin.fill((x + 1, y + 3, width - 2, 9), LEATHER)
    skin.band(jacket["front"], LEATHER_DARK, top=11, rows=1)
    skin.fill((x + 2, y + 5, 3, 2), LEATHER_DARK)
    # Лямки через плечи: со спины видно, что фартук держится.
    for name in ("left", "right"):
        skin.column(jacket[name], LEATHER_DARK, left=1, rows=4)
    skin.column(jacket["back"], LEATHER_DARK, left=1, rows=9)
    skin.column(jacket["back"], LEATHER_DARK, left=6, rows=9)
    for name in SIDES:
        skin.band(parts["hat"][name], LEATHER, top=2, rows=1)
    region = REGIONS["pouch"]
    skin.cube(region, LEATHER)
    skin.band(region["front"], LEATHER_DARK, rows=1)
    skin.px(region["front"], 1, 2, IRON)
    skin.fill(region["top"], LEATHER_DARK)


def craft_farmer(skin, parts, look, woman, people):
    """Пахарь: соломенная шляпа с полями и холщовый передник."""
    hat = parts["hat"]
    skin.weave(hat["top"], STRAW, STRAW_DARK, STRAW_LIT, density=0.3, salt=5)
    for name in SIDES:
        for dy in range(3):
            for dx in range(8):
                skin.px(hat[name], dx, dy, STRAW if (dx + dy) % 2 else STRAW_LIT)
        skin.band(hat[name], look["accent"], top=2, rows=1)
    brim = REGIONS["brim"]
    x, y, width, depth = brim["top"]
    for dx in range(width):
        for dy in range(depth):
            ring = min(dx, dy, width - 1 - dx, depth - 1 - dy)
            colour = STRAW if (dx + dy) % 2 else STRAW_LIT
            if ring == 0:
                colour = STRAW_DARK
            skin.image.putpixel((x + dx, y + dy), colour)
    skin.fill(brim["front"], STRAW_DARK)
    jacket = parts["jacket"]
    x, y, width, height = jacket["front"]
    skin.fill((x + 1, y + 5, width - 2, 6), LINEN)
    skin.band(jacket["front"], LINEN_DARK, top=10, rows=1)
    if woman:
        region = REGIONS["skirt"]
        sx, sy, sw, sh = region["front"]
        skin.fill((sx + 1, sy, sw - 2, sh - 1), LINEN)


def craft_lumberjack(skin, parts, look, woman, people):
    """Лесоруб: рубаха в клетку, закатанные рукава и вязаная шапка."""
    body = parts["body"]
    for name in SIDES:
        x, y, width, height = body[name]
        for dy in range(height - 3):
            for dx in range(width):
                if ((dx // 2) + (dy // 2)) % 2 == 0:
                    skin.px(body[name], dx, dy, look["cloth_dark"])
                if dy % 4 == 0:
                    skin.px(body[name], dx, dy, tone(look["accent"], 0.85))
    arm = parts["arm"]
    for name in SIDES:
        skin.band(arm[name], look["skin"], top=4, rows=8)
        skin.band(arm[name], look["skin_dark"], top=11, rows=1)
        skin.band(parts["sleeve"][name], look["cloth_dark"], top=3, rows=2)
        skin.band(parts["sleeve"][name], CLEAR, top=7, rows=1)
    cap = tone(look["accent"], 0.8)
    skin.weave(parts["hat"]["top"], cap, tone(cap, 0.85), tone(cap, 1.1), density=0.4, salt=6)
    for name in SIDES:
        skin.band(parts["hat"][name], cap, rows=2)
        skin.band(parts["hat"][name], tone(cap, 0.8), top=1, rows=1)


def craft_courier(skin, parts, look, woman, people):
    """Курьер: дорожный капюшон, лямки и котомка за спиной."""
    hat = parts["hat"]
    hood = tone(look["cloth_dark"], 0.95)
    skin.weave(hat["top"], hood, tone(hood, 0.88), tone(hood, 1.06), salt=7)
    skin.weave(hat["back"], hood, tone(hood, 0.88), tone(hood, 1.06), salt=8)
    for name in ("left", "right"):
        skin.weave(hat[name], hood, tone(hood, 0.88), tone(hood, 1.06), salt=9)
        skin.band(hat[name], tone(hood, 0.8), top=7, rows=1)
    front = hat["front"]
    skin.band(front, hood, rows=1)
    for dy in range(8):
        skin.px(front, 0, dy, hood)
        skin.px(front, 7, dy, tone(hood, 0.85))
    jacket = parts["jacket"]
    for name in ("front", "back"):
        skin.column(jacket[name], LEATHER_DARK, left=1, rows=9)
        skin.column(jacket[name], LEATHER_DARK, left=6, rows=9)
    skin.px(jacket["front"], 1, 3, IRON)
    skin.px(jacket["front"], 6, 3, IRON)
    region = REGIONS["knapsack"]
    skin.cube(region, LEATHER)
    for name in SIDES:
        skin.shade(region, LEATHER_DARK, names=(name,))
    skin.band(region["back"], LEATHER_DARK, rows=2)
    skin.px(region["back"], 2, 2, IRON)
    skin.px(region["back"], 3, 2, IRON)
    skin.fill(region["top"], LEATHER_LIT)
    skin.column(region["top"], LEATHER_DARK, left=1)
    skin.column(region["top"], LEATHER_DARK, left=4)


def helmet(skin, parts, look, people):
    hat = parts["hat"]
    metal = look["helm"]
    dark, lit = tone(metal, 0.78), tone(metal, 1.15)
    skin.fill(hat["top"], metal)
    skin.px(hat["top"], 3, 3, lit)
    skin.px(hat["top"], 4, 4, lit)
    for name in SIDES:
        skin.band(hat[name], metal, rows=3)
        skin.band(hat[name], dark, top=3, rows=1)
        skin.px(hat[name], 1, 0, lit)
    # Нащёчники: шлем держит лицо в раме.
    for dy in range(3, 6):
        skin.px(hat["front"], 0, dy, dark)
        skin.px(hat["front"], 7, dy, dark)
    for name in ("left", "right"):
        skin.band(hat[name], metal, top=3, rows=3)
    skin.band(hat["back"], metal, top=3, rows=3)
    return metal, dark, lit


def craft_guard(skin, parts, look, woman, people):
    """Страж: шлем народа, кольчуга, накидка и наплечники."""
    metal, dark, lit = helmet(skin, parts, look, people)
    jacket = parts["jacket"]
    for name in SIDES:
        x, y, width, height = jacket[name]
        for dy in range(9):
            for dx in range(width):
                skin.px(jacket[name], dx, dy, IRON if (dx + dy) % 2 else IRON_DARK)
    x, y, width, height = jacket["front"]
    skin.fill((x + 2, y, 4, 12), look["accent"])
    skin.fill((x + 3, y + 3, 2, 2), GOLD if people != "elf" else IRON_LIT)
    skin.fill(jacket["back"][:2] + (jacket["back"][2], 12), look["accent"])
    skin.band(jacket["back"], tone(look["accent"], 0.8), top=11, rows=1)
    for name in SIDES:
        skin.band(jacket[name], look["belt"], top=9, rows=1)
    for name in SIDES:
        x, y, width, height = parts["sleeve"][name]
        for dy in range(6):
            for dx in range(width):
                skin.px(parts["sleeve"][name], dx, dy, IRON if (dx + dy) % 2 else IRON_DARK)
    region = REGIONS["pauldron"]
    skin.cube(region, metal)
    skin.band(region["front"], dark, top=2, rows=1)
    for name in SIDES:
        skin.band(region[name], dark, top=2, rows=1)
        skin.px(region[name], 1, 1, lit)
    skin.px(region["top"], 2, 2, lit)
    if people == "norman":
        for name in ("helm_base", "helm_cone", "helm_crown", "helm_tip", "nasal"):
            skin.cube(REGIONS[name], metal)
            skin.fill(REGIONS[name]["top"], lit)
        skin.cube(REGIONS["helm_tip"], dark)
    if people == "maya":
        crest = REGIONS["crest"]
        for name in ALL:
            x, y, width, height = crest[name]
            for dx in range(width):
                for dy in range(height):
                    colour = look["accent"] if (dx // 2) % 2 == 0 else RED
                    if dy == 0:
                        colour = GOLD
                    skin.image.putpixel((x + dx, y + dy), colour)
    if people == "yamato":
        # Кабуто: золотые рога-кувагата и чёрный нашейник в киноварной шнуровке.
        crest = REGIONS["kuwagata"]
        skin.cube(crest, GOLD)
        for name in SIDES:
            skin.px(crest[name], 0, 0, tone(GOLD, 1.2))
        shikoro = REGIONS["shikoro"]
        skin.cube(shikoro, metal)
        for name in SIDES:
            skin.band(shikoro[name], look["accent"], top=1, rows=1)
        skin.fill(shikoro["top"], dark)
    if woman:
        draw_skirt(skin, look, colour=look["accent"], trim=GOLD)


def craft_elder(skin, parts, look, woman, people):
    """Старейшина: ряса до пят, седина и золотая кайма."""
    robe_colour = look["accent"]
    jacket = parts["jacket"]
    for name in SIDES:
        skin.weave(jacket[name], robe_colour, tone(robe_colour, 0.9),
                   tone(robe_colour, 1.08), salt=10)
    x, y, width, height = jacket["front"]
    skin.column(jacket["front"], GOLD, left=3, cols=2)
    skin.band(jacket["front"], GOLD, rows=1)
    for name in SIDES:
        skin.band(parts["sleeve"][name], robe_colour, rows=9)
        skin.band(parts["sleeve"][name], GOLD, top=8, rows=1)
    region = REGIONS["robe"]
    skin.cloth(region, robe_colour, salt=11)
    for name in SIDES:
        skin.band(region[name], GOLD, top=region[name][3] - 1, rows=1)
    skin.column(region["front"], GOLD, left=3, cols=2)
    skin.fill(region["top"], CLEAR)
    skin.fill(region["bottom"], CLEAR)

    hat = parts["hat"]
    for name in ("top",) + SIDES:
        x, y, width, height = hat[name]
        for dx in range(width):
            for dy in range(height):
                here = skin.image.getpixel((x + dx, y + dy))
                if here[3]:
                    skin.image.putpixel((x + dx, y + dy),
                                        GREY_HAIR if (dx + dy // 3) % 3 else GREY_HAIR_DARK)
    head = parts["head"]
    for name in ("top",) + SIDES:
        x, y, width, height = head[name]
        for dx in range(width):
            for dy in range(height):
                if skin.image.getpixel((x + dx, y + dy)) == look["hair"]:
                    skin.image.putpixel((x + dx, y + dy), GREY_HAIR)
    if not woman and people in BEARDED:
        draw_beard(skin, parts, look, GREY_HAIR)
    if people == "maya":
        for name in ("feather", "feather_mid"):
            region = REGIONS[name]
            for face in ALL:
                x, y, width, height = region[face]
                for dy in range(height):
                    for dx in range(width):
                        colour = look["accent"] if dy < height - 2 else RED
                        if dy == 0:
                            colour = GOLD
                        skin.image.putpixel((x + dx, y + dy), colour)


def craft_brewer(skin, parts, look, woman, people):
    """Пивовар: холщовый фартук, закатанные рукава и картуз.

    Отличается от пахаря и каменщика нарочно: у пахаря соломенная шляпа,
    у каменщика кожаный фартук с повязкой, у пивовара холст и картуз.
    Три фартука подряд — это не три ремесла, а один размытый.
    """
    jacket = parts["jacket"]
    x, y, width, height = jacket["front"]
    skin.fill((x + 1, y + 6, width - 2, 6), LINEN)
    skin.band(jacket["front"], LEATHER_DARK, top=11, rows=1)
    skin.px(jacket["front"], 2, 8, rgb(0x8C5A2A))
    for name in SIDES:
        skin.band(parts["arm"][name], look["skin"], top=5, rows=7)
        skin.band(parts["arm"][name], look["skin_dark"], top=11, rows=1)
        skin.band(parts["sleeve"][name], LINEN_DARK, top=4, rows=1)
        skin.band(parts["sleeve"][name], CLEAR, top=7, rows=1)
    cap = look["cloth_dark"]
    skin.fill(parts["hat"]["top"], cap)
    for name in SIDES:
        skin.band(parts["hat"][name], cap, rows=2)
    skin.band(parts["hat"]["front"], tone(cap, 0.75), top=1, rows=1)


def craft_merchant(skin, parts, look, woman, people):
    """Купец: кафтан с каймой народа, золотые пуговицы, шапка и кошель.

    Самое нужное лицо во всём моде и до сих пор единственное, которого
    не было вовсе. Купец стоит в каждой деревне и говорит с игроком чаще,
    чем кто угодно другой, — а рисовался чёрно-фиолетовым кубом.
    """
    jacket = parts["jacket"]
    coat = look["cloth_lit"]
    for name in SIDES:
        skin.weave(jacket[name], coat, tone(coat, 0.9), tone(coat, 1.06), salt=12)
    x, y, width, height = jacket["front"]
    skin.column(jacket["front"], look["accent"], left=0)
    skin.column(jacket["front"], look["accent"], left=width - 1)
    for dy in range(1, 10, 3):
        skin.px(jacket["front"], width // 2, dy, GOLD)
    skin.band(jacket["front"], look["belt"], top=9, rows=1)
    for name in SIDES:
        skin.band(parts["sleeve"][name], coat, rows=8)
        skin.band(parts["sleeve"][name], look["accent"], top=7, rows=1)
    cap = look["accent"]
    skin.fill(parts["hat"]["top"], cap)
    for name in SIDES:
        skin.band(parts["hat"][name], cap, rows=2)
        skin.band(parts["hat"][name], tone(cap, 0.75), top=2, rows=1)
    skin.px(parts["hat"]["front"], 5, 1, GOLD)
    region = REGIONS["purse"]
    skin.cube(region, LEATHER)
    skin.band(region["front"], GOLD, rows=1)
    skin.px(region["front"], 0, 1, LEATHER_DARK)


def craft_weaver(skin, parts, look, woman, people):
    """Прядильщица: светлая смена, цветные нити на рукавах, платок и моток."""
    jacket = parts["jacket"]
    for name in SIDES:
        skin.weave(jacket[name], LINEN, LINEN_DARK, tone(LINEN, 1.05), salt=13)
    skin.band(jacket["front"], look["accent"], top=3, rows=1)
    skin.band(jacket["front"], look["cloth"], top=7, rows=1)
    for name in SIDES:
        skin.band(parts["sleeve"][name], LINEN, rows=7)
        for dy in range(1, 7, 3):
            skin.band(parts["sleeve"][name], look["accent"], top=dy, rows=1)
    scarf = tone(look["accent"], 1.1)
    skin.fill(parts["hat"]["top"], scarf)
    skin.band(parts["hat"]["front"], scarf, rows=2)
    for name in ("left", "right", "back"):
        skin.band(parts["hat"][name], scarf, rows=3)
    skin.band(parts["hat"]["back"], tone(scarf, 0.85), top=3, rows=2)
    region = REGIONS["yarn"]
    for name in ALL:
        x, y, width, height = region[name]
        for dx in range(width):
            for dy in range(height):
                skin.image.putpixel((x + dx, y + dy),
                                    look["accent"] if (dx + dy) % 2 else tone(look["accent"], 0.8))
    if woman:
        draw_skirt(skin, look, colour=LINEN, trim=look["accent"])


CRAFTS = {
    "builder": craft_builder,
    "farmer": craft_farmer,
    "lumberjack": craft_lumberjack,
    "courier": craft_courier,
    "guard": craft_guard,
    "elder": craft_elder,
    "brewer": craft_brewer,
    "merchant": craft_merchant,
    "weaver": craft_weaver,
}


def build(culture, woman, craft):
    look = CULTURES[culture]
    parts = human_parts(woman)
    skin = Skin(64, 128, "%s/%s/%s" % (culture, woman, craft))
    draw_head(skin, parts, look, woman)
    draw_body(skin, parts, look, woman)
    draw_arm(skin, parts, look)
    draw_leg(skin, parts, look)
    if woman:
        draw_skirt(skin, look)
        if culture in LONG_HAIRED:
            draw_braid(skin, look)
        else:
            draw_hair(skin, look, GREY_HAIR if craft == "elder" else None)
    if culture == "elf":
        draw_ears(skin, look)
    if culture == "yamato":
        grey = GREY_HAIR if craft == "elder" else None
        if woman:
            draw_bun(skin, look, grey)
        else:
            draw_topknot(skin, look, grey)
    if not woman and culture in BEARDED and craft != "elder":
        # Борода — примета народа. У старейшины её рисует само ремесло,
        # и притом седую, поэтому здесь он пропускается.
        draw_beard(skin, parts, look)
    if not woman and culture in LONG_HAIRED:
        draw_long_beard(skin, look, GREY_HAIR if craft == "elder" else None)
    if craft in CRAFTS:
        CRAFTS[craft](skin, parts, look, woman, culture)
    if woman and culture in LONG_HAIRED and craft == "elder":
        draw_braid(skin, look, GREY_HAIR)
    for name in ("jacket", "sleeve", "pants"):
        skin.outline(parts[name], tone(look["cloth_dark"], 0.8))
    skin.mirror(parts["arm"], parts["arm_left"])
    skin.mirror(parts["sleeve"], parts["sleeve_left"])
    skin.mirror(parts["leg"], parts["leg_left"])
    skin.mirror(parts["pants"], parts["pants_left"])
    return skin


# --- кони ----------------------------------------------------------------------
#
# Пони перестали быть людьми с лошадиной мастью: заказчик сказал «сделай
# чтоб пони выглядели как пони», и они стали четвероногими. Разбор у коня
# поэтому свой целиком — от двуногого не подходит ни одна грань, и место
# каждой части выбирает упаковщик в citizen_body.

# Народы, которые ходят на четырёх. Списком, а не строкой в коде: завтра
# их может стать двое.
QUADRUPEDS = ("pony",)


# Масть, грива, глаза — у каждой пони своя пара цветов, как в мультфильме,
# где по цвету узнают раньше, чем по лицу. Пастельная шёрстка и яркая
# грива; глаза в тон гриве, но глубже. Кобылы и жеребцы одного ремесла
# окрашены по-разному: одинаковый пахарь в двух полах читался бы клоном.
#                  шёрстка     грива       прядь       глаза
PONY_COATS = {
    (False, None): (0x9FD3F0, 0x3B5BA8, 0x6F8FD8, 0x2E4A9A),
    (False, "builder"): (0xE8C48A, 0x8A4A1E, 0xB8743E, 0x6A3A1A),
    (False, "farmer"): (0xF4A560, 0xF8E08A, 0xFFF0B8, 0x3E8A3A),
    (False, "lumberjack"): (0xA8D08D, 0x3E7A3A, 0x62A05A, 0x2E5A2A),
    (False, "courier"): (0xC9B4E8, 0x3AA8A0, 0x6CD0C8, 0x2A7A74),
    (False, "guard"): (0xB8C4D0, 0x2E4A8A, 0x4E6EB0, 0x243A70),
    (False, "elder"): (0xD8D8E0, 0xF4F4F8, 0xC8C8D8, 0x6A6A90),
    (False, "brewer"): (0xF0D070, 0xB83A2E, 0xE0604A, 0x8A2A22),
    (False, "merchant"): (0xA8E8C8, 0x7A3AA8, 0xA868D0, 0x5A2A80),
    (False, "weaver"): (0xF8C8B0, 0x4A7AD8, 0x7AA4F0, 0x3A5AA8),
    (True, None): (0xF8B8D8, 0xD83A8A, 0xF070B0, 0x3A7AD0),
    (True, "builder"): (0xD0B8F0, 0x4A2E8A, 0x7050B8, 0x5A2E9A),
    (True, "farmer"): (0xF8E8A0, 0xF08AB0, 0xF8B0CC, 0x3AA0B8),
    (True, "lumberjack"): (0xC8F0B0, 0xE8803A, 0xF8A860, 0x2E7A3A),
    (True, "courier"): (0xB0E0F8, None, None, 0xB8305A),
    (True, "guard"): (0xF0F0F8, 0x3A5AD8, 0x6A8AF0, 0x2A4AB0),
    (True, "elder"): (0xE0E0E8, 0xB8A0D8, 0xD8C8F0, 0x6A4A9A),
    (True, "brewer"): (0xF8C090, 0xC8302E, 0xE86050, 0x3A7A3A),
    (True, "merchant"): (0xE0C8F0, 0x2E9A9A, 0x5AC8C8, 0x2A6A8A),
    (True, "weaver"): (0xF8F4F0, 0x7A4AC8, 0xA07AE0, 0x3A60C8),
}

# Радужная грива — одна на весь народ: у курьерши, самой быстрой.
RAINBOW = [rgb(v) for v in (0xE8403A, 0xF4A03A, 0xF8E04A, 0x5AC85A, 0x3A9AE8, 0x8A4AC8)]

# Знак на боку — ремесло, нарисованное так, как в мультфильме у пони
# нарисован её дар. Три на три текселя; буквы — цвета из CUTIE_INK.
CUTIE_MARKS = {
    None: ["R.R", "RRR", ".R."],
    "builder": ["SSS", ".W.", ".W."],
    "farmer": ["R.R", "...", ".R."],
    "lumberjack": [".G.", "GGG", ".W."],
    "courier": ["LLL", "LBL", "LLL"],
    "guard": ["SRS", "SSS", ".S."],
    "elder": [".Y.", "YYY", ".Y."],
    "brewer": ["LL.", "YYW", "YY."],
    "merchant": [".Y.", "YOY", ".Y."],
    "weaver": [".P.", "PLP", ".P."],
}
CUTIE_INK = {
    "R": rgb(0xE0405A), "S": rgb(0xA8B0B8), "W": rgb(0x8A5A2E), "G": rgb(0x4AA04A),
    "L": rgb(0xF8F8F0), "B": rgb(0x3A6AC8), "Y": rgb(0xF0C030), "O": rgb(0xC8901A),
    "P": rgb(0x9A5AD8),
}


def rainbow_strands(skin, rect):
    """Грива полосами радуги: по столбцу на цвет, волной вниз."""
    x, y, width, height = rect
    for dx in range(width):
        for dy in range(height):
            skin.image.putpixel((x + dx, y + dy), RAINBOW[(dx + dy // 3) % len(RAINBOW)])


def mix_pink(colour):
    return tuple(min(255, int(c * 0.6 + p * 0.4))
                 for c, p in zip(colour[:3], (0xF8, 0xA8, 0xC0))) + (255,)


def draw_pony(skin, look, woman, craft=None):
    """Пони: пастельная шёрстка, яркая грива, большие глаза спереди.

    Прежний пони был конём нарочно — глаза на скулах, буланая масть,
    длинная морда. Заказчик попросил другого: «как вдохновлены
    My Little Pony». Поэтому глаза вынесены вперёд и на пол-лица, морда
    коротка и в тон шёрстке, а масть — у каждой своя и нежная.
    """
    at = PONY_REGIONS
    coat_v, mane_v, streak_v, eye_v = PONY_COATS[(woman, craft)]
    coat = rgb(coat_v)
    dark, lit = tone(coat, 0.9), tone(coat, 1.06)
    for part in ("barrel", "neck", "skull", "leg", "dock", "ear", "muzzle"):
        for name in ALL:
            skin.weave(at[part][name], coat, tone(coat, 0.97), lit, density=0.06, salt=20)
    skin.fill(at["barrel"]["bottom"], dark)
    for name in ("left", "right", "front", "back"):
        skin.band(at["barrel"][name], dark, top=4, rows=1)
    # Уши: внутри розовее.
    skin.fill(at["ear"]["front"], mix_pink(coat))

    # Лицо: два больших глаза. Лицо — восемь столбцов на семь рядов;
    # два верхних ряда под чёлкой, глаза в рядах 2–4, по два столбца
    # с зазором посередине. Внешний столбец — белок с бликом, внутренний
    # — радужка; сверху тёмная кромка века.
    face = at["skull"]["front"]
    iris = rgb(eye_v)
    for inner, outer in ((2, 1), (5, 6)):
        skin.px(face, inner, 2, EYE)
        skin.px(face, outer, 2, EYE)
        skin.px(face, outer, 3, rgb(0xFFFFFF))
        skin.px(face, inner, 3, iris)
        skin.px(face, outer, 4, tone(iris, 1.25))
        skin.px(face, inner, 4, tone(iris, 0.7))
        if woman:
            # Ресница кобылы — наружу и вверх.
            skin.px(face, outer + (outer - inner), 2, EYE)
    # Мордочка — в тон шёрстке, с улыбкой и ноздрями.
    muzzle = at["muzzle"]["front"]
    skin.px(muzzle, 0, 0, tone(coat, 0.8))
    skin.px(muzzle, 3, 0, tone(coat, 0.8))
    skin.px(muzzle, 1, 1, tone(coat, 0.75))
    skin.px(muzzle, 2, 1, tone(coat, 0.75))

    # Грива, чёлка, хвост.
    for part in ("mane", "tail", "forelock", "bangs"):
        for name in ALL:
            if mane_v is None:
                rainbow_strands(skin, at[part][name])
            else:
                skin.strands(at[part][name], rgb(mane_v), rgb(streak_v))

    # Копытца — светлее ножек, как у мультяшной пони, а не тёмный рог коня.
    skin.cube(at["hoof"], tone(coat, 0.82))
    for name in SIDES:
        skin.band(at["hoof"][name], tone(coat, 0.95), rows=1)

    # Знак на боку: на заду, с обеих сторон. У правой грани столбцы идут
    # от хвоста к голове, у левой — наоборот, поэтому знак стоит в разных
    # столбцах, но на одном месте тела.
    mark = CUTIE_MARKS.get(craft, CUTIE_MARKS[None])
    for name, left in (("right", 1), ("left", at["barrel"]["left"][2] - 4)):
        for dy, row in enumerate(mark):
            for dx, ink in enumerate(row):
                if ink != ".":
                    skin.px(at["barrel"][name], left + dx, dy, CUTIE_INK[ink])


def headgear(skin, colour, dark=None):
    """Что надето на голову: шлем стража, косынка работницы."""
    cap = PONY_REGIONS["cap"]
    skin.fill(cap["top"], colour)
    for name in SIDES:
        skin.band(cap[name], colour, rows=1)
        if dark is not None:
            skin.band(cap[name], dark, top=1, rows=1)


def pony_builder(skin, look):
    """Строитель: косынка на лбу."""
    headgear(skin, rgb(0xE8803A))


def pony_farmer(skin, look):
    """Пахарь: соломенная шляпа."""
    for name in ("hat_crown", "hat_brim"):
        for face in ALL:
            x, y, width, height = PONY_REGIONS[name][face]
            for dx in range(width):
                for dy in range(height):
                    skin.image.putpixel((x + dx, y + dy),
                                        STRAW if (dx + dy) % 2 else STRAW_LIT)
    for name in SIDES:
        skin.band(PONY_REGIONS["hat_crown"][name], rgb(0xE0405A), top=1, rows=1)


def pony_lumberjack(skin, look):
    """Делянщица: зелёная косынка."""
    headgear(skin, rgb(0x3E8A3A))


def saddlebags(skin, colour, trim):
    bag = PONY_REGIONS["saddlebag"]
    skin.cube(bag, colour)
    for name in SIDES:
        skin.band(bag[name], trim, rows=1)
    skin.fill(bag["top"], tone(colour, 0.85))


def pony_courier(skin, look):
    """Курьер: перемётные сумочки."""
    saddlebags(skin, LEATHER, LEATHER_DARK)


def pony_guard(skin, look):
    """Страж: латы по спине, шлем и султан."""
    part = PONY_REGIONS["blanket"]
    for name in ("left", "right", "front", "back"):
        skin.band(part[name], IRON, rows=3)
        skin.band(part[name], IRON_DARK, top=3, rows=1)
    skin.fill(part["top"], IRON)
    headgear(skin, IRON, IRON_DARK)
    skin.cube(PONY_REGIONS["plume"], RED)
    skin.fill(PONY_REGIONS["plume"]["top"], tone(RED, 1.2))


def pony_elder(skin, look):
    """Старейшина: покров с золотой каймой."""
    cap = PONY_REGIONS["caparison"]
    purple = rgb(0x7A4AB0)
    for name in SIDES:
        skin.weave(cap[name], purple, tone(purple, 0.9), tone(purple, 1.08), salt=23)
        skin.band(cap[name], GOLD, top=cap[name][3] - 1, rows=1)
    skin.fill(cap["top"], CLEAR)
    skin.fill(cap["bottom"], CLEAR)


def pony_brewer(skin, look):
    """Квасник: знак на боку, и больше ничего — кружку видно и так."""


def pony_merchant(skin, look):
    """Купец: цветные сумы с золотой каймой."""
    saddlebags(skin, rgb(0x7A3AA8), GOLD)


def pony_weaver(skin, look):
    """Прядильщица: знак на боку — клубок."""


PONY_CRAFTS = {
    "builder": pony_builder,
    "farmer": pony_farmer,
    "lumberjack": pony_lumberjack,
    "courier": pony_courier,
    "guard": pony_guard,
    "elder": pony_elder,
    "brewer": pony_brewer,
    "merchant": pony_merchant,
    "weaver": pony_weaver,
}


def build_pony(culture, woman, craft):
    look = CULTURES[culture]
    body = citizen_body.PONY
    skin = Skin(body.width, body.height, "%s/%s/%s" % (culture, woman, craft))
    draw_pony(skin, look, woman, craft)
    if craft in PONY_CRAFTS:
        PONY_CRAFTS[craft](skin, look)
    return skin


def main():
    made = 0
    for culture in CULTURES:
        for gender, woman in (("male", False), ("female", True)):
            made += 1
            draw = build_pony if culture in QUADRUPEDS else build
            draw(culture, woman, None).save("%s/%s" % (culture, gender))
            for craft in CRAFTS:
                made += 1
                draw(culture, woman, craft).save(
                    "%s/%s_%s" % (culture, gender, craft))
    print("нарисовано обликов: %d" % made)


if __name__ == "__main__":
    main()
