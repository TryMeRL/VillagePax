#!/usr/bin/env python3
"""Рисует облик жителей: народ, пол и ремесло — каждый своей текстурой.

Зачем: до этого на всех жителей мира была одна картинка. Майя выглядели
норманнами, женщина — мужчиной, пахарь — стражником. К людям, которых
не различить, нельзя привязаться, и колония читалась счётчиком населения,
а не местом, где живут люди. Это и был главный ответ на «скучно».

Три яруса, и каждый виден с десяти шагов:

1. **Народ** — кожа, волосы, крой и цвет одежды. Норманны: бледная кожа,
   некрашеная шерсть, серые штаны. Майя: смуглая кожа, чёрные волосы,
   белый хлопок и красная кайма.
2. **Пол** — причёска и длина рубахи. Больше ничего: перекраивать модель
   ради этого мы не будем, а силуэт причёски различает издали.
3. **Ремесло** — то, во что человек одет для работы: фартук каменщика,
   соломенная шляпа пахаря, кольчуга стража, сумка курьера.

Раскладка классическая — та, которую ждёт ванильная BipedEntityModel:
левая рука и нога зеркалят правые, поэтому в текстуре их нет.

    python tools/make-citizen-textures.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/villagepax/textures/entity/citizen"

SIZE = 64


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, 255)


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
}

# Пони — луговые коневоды, третий народ мода. Масть, а не загар:
# пшеничная кожа и льняная грива взяты у их же лошадей, и рядом
# с бурым норманном и смуглым майя народ читается с одного взгляда.
# Зелёная тесьма — луг, на котором они живут.
PONY = {
    "skin": rgb(0xE0C08A),
    "skin_dark": rgb(0xC09E68),
    "hair": rgb(0xD8C48A),
    "hair_dark": rgb(0xB09A5E),
    "cloth": rgb(0xB8C49A),
    "cloth_dark": rgb(0x94A078),
    "cloth_lit": rgb(0xD2DCB4),
    "trousers": rgb(0x7A5C3A),
    "trousers_dark": rgb(0x5E462C),
    "boots": rgb(0x4A3520),
    "belt": rgb(0x8A6A3C),
    "accent": rgb(0x4E8A3E),
}

CULTURES = {"norman": NORMAN, "maya": MAYA, "pony": PONY}

EYE = rgb(0x2B2B33)
EYE_WHITE = rgb(0xE6E6DE)
MOUTH = rgb(0x8A5B4A)

IRON = rgb(0x9AA0A8)
IRON_DARK = rgb(0x767C84)
LEATHER = rgb(0x8A5C33)
LEATHER_DARK = rgb(0x5E3E24)
STRAW = rgb(0xD4B057)
STRAW_DARK = rgb(0xA8862F)
GREY_HAIR = rgb(0xC6C2B8)
GOLD = rgb(0xD8AA3C)
LINEN = rgb(0xE4DFD2)
GREEN = rgb(0x5E7A42)


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


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)

SIDES = ("front", "back", "left", "right")


class Skin:
    """Холст 64x64 с гранями кубов. Всё рисование — через грани."""

    def __init__(self):
        self.image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

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

    def cube(self, cube, colour):
        for rect in cube.values():
            self.fill(rect, colour)

    def px(self, rect, dx, dy, colour):
        x, y, width, height = rect
        if 0 <= dx < width and 0 <= dy < height:
            self.image.putpixel((x + dx, y + dy), colour)

    def shade(self, cube, dark):
        """Тень по правому краю каждой грани.

        Ваниль освещает модель по нормалям, но плоская заливка всё равно
        выглядит картоном. Один тёмный столбец справа и ряд снизу дают
        объём, которого не хватало.
        """
        for name in SIDES:
            x, y, width, height = cube[name]
            self.fill((x + width - 1, y, 1, height), dark)
            self.fill((x, y + height - 1, width, 1), dark)

    def folds(self, cube, dark, columns=(2, 5)):
        """Складки на ткани: без них рубаха — цветной прямоугольник."""
        for name in ("front", "back"):
            x, y, width, height = cube[name]
            for column in columns:
                if column < width:
                    self.fill((x + column, y + 2, 1, height - 4), dark)

    def save(self, name):
        path = OUT / (name + ".png")
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image.save(path, optimize=True)
        return path


# --- части тела -------------------------------------------------------------


def draw_head(skin, look, woman):
    skin.cube(HEAD, look["skin"])
    skin.fill(HEAD["bottom"], look["skin_dark"])
    skin.shade(HEAD, look["skin_dark"])

    # Волосы: макушка целиком, чёлка спереди, затылок глубже.
    skin.fill(HEAD["top"], look["hair"])
    for name in SIDES:
        skin.band(HEAD[name], look["hair"], rows=2)
    skin.band(HEAD["back"], look["hair"], rows=6)
    for name in SIDES:
        skin.band(HEAD[name], look["hair_dark"], top=1, rows=1)

    if woman:
        # Длинные волосы: бока и затылок до плеч — силуэт виден издали,
        # и это единственное, чем женщина отличается на модели.
        for name in ("left", "right"):
            skin.fill(HEAD[name], look["hair"])
            skin.band(HEAD[name], look["hair_dark"], top=7, rows=1)
        skin.fill(HEAD["back"], look["hair"])
        skin.band(HEAD["front"], look["hair"], rows=2)
        # Пряди вдоль лица во всю высоту: со спины и с боков причёску
        # не видно, а игрок смотрит в лицо. Без этого женщина отличалась
        # от мужчины только отсутствием бороды.
        for dy in range(8):
            skin.px(HEAD["front"], 0, dy, look["hair"])
            skin.px(HEAD["front"], 7, dy, look["hair_dark"])

    face = HEAD["front"]
    for dx in (1, 5):
        skin.px(face, dx, 4, EYE_WHITE)
        skin.px(face, dx + 1, 4, EYE)
    skin.px(face, 3, 5, look["skin_dark"])
    skin.px(face, 4, 5, look["skin_dark"])
    skin.px(face, 3, 6, MOUTH)
    skin.px(face, 4, 6, MOUTH)


def draw_beard(skin, look, colour=None):
    """Борода: нижние ряды лица и боков. Ремесло и годы — на лице."""
    colour = colour or look["hair"]
    face = HEAD["front"]
    for dx in range(1, 7):
        skin.px(face, dx, 7, colour)
    for dx in (1, 2, 5, 6):
        skin.px(face, dx, 6, colour)
    for name in ("left", "right"):
        skin.band(HEAD[name], colour, top=6, rows=2)


def draw_body(skin, look, woman):
    skin.cube(BODY, look["cloth"])
    skin.fill(BODY["right"], look["cloth_dark"])
    skin.fill(BODY["left"], look["cloth_dark"])
    skin.fill(BODY["bottom"], look["trousers"])
    skin.band(BODY["front"], look["cloth_lit"], rows=2)
    skin.folds(BODY, look["cloth_dark"])
    skin.shade(BODY, look["cloth_dark"])

    if woman:
        # Рубаха длиннее: ткань уходит ниже пояса, и пояс выше.
        for name in SIDES:
            skin.band(BODY[name], look["cloth"], top=9, rows=3)
        for name in SIDES:
            skin.band(BODY[name], look["belt"], top=7, rows=1)
    else:
        for name in SIDES:
            skin.band(BODY[name], look["belt"], top=9, rows=2)


def draw_arm(skin, look):
    skin.cube(ARM, look["cloth"])
    skin.fill(ARM["bottom"], look["skin_dark"])
    for name in SIDES:
        skin.band(ARM[name], look["skin"], top=7, rows=5)
    skin.shade(ARM, look["cloth_dark"])
    for name in SIDES:
        skin.band(ARM[name], look["cloth_dark"], top=6, rows=1)


def draw_leg(skin, look, woman):
    skin.cube(LEG, look["trousers"])
    skin.fill(LEG["bottom"], look["boots"])
    for name in SIDES:
        skin.band(LEG[name], look["boots"], top=9, rows=3)
    skin.shade(LEG, look["trousers_dark"])
    if woman:
        # Из-под подола видны только голени: верх ноги — та же ткань.
        for name in SIDES:
            skin.band(LEG[name], look["cloth"], rows=3)


# --- ремёсла ----------------------------------------------------------------
#
# Каждое ремесло — это одежда для работы, а не цветная нашивка. Игрок
# должен узнавать человека по делу, не наводя на него прицел.


def craft_builder(skin, look):
    """Каменщик: кожаный фартук и повязка на лбу."""
    for name in SIDES:
        skin.band(BODY[name], LEATHER, top=4, rows=6)
    skin.band(BODY["front"], LEATHER_DARK, top=9, rows=1)
    skin.band(BODY["back"], look["cloth"], top=4, rows=6)
    for name in SIDES:
        skin.band(HEAD[name], LEATHER, top=2, rows=1)
    skin.band(ARM["front"], LEATHER, top=6, rows=1)


def craft_farmer(skin, look):
    """Пахарь: соломенная шляпа и холщовый передник."""
    skin.fill(HEAD["top"], STRAW)
    for name in SIDES:
        skin.band(HEAD[name], STRAW, rows=2)
        skin.band(HEAD[name], STRAW_DARK, top=2, rows=1)
    for name in ("front", "back"):
        skin.band(BODY[name], LINEN, top=5, rows=4)
    skin.band(BODY["front"], look["cloth_dark"], top=8, rows=1)


def craft_lumberjack(skin, look):
    """Лесоруб: рукава закатаны, рубаха в клетку."""
    for name in SIDES:
        skin.band(ARM[name], look["skin"], top=4, rows=8)
    x, y, width, height = BODY["front"]
    for dy in range(height - 3):
        for dx in range(width):
            if ((dx // 4) + (dy // 4)) % 2 == 0:
                skin.px(BODY["front"], dx, dy, look["cloth_dark"])
    skin.band(BODY["front"], look["cloth_lit"], rows=1)


def craft_courier(skin, look):
    """Курьер: сумка через плечо и дорожный капюшон."""
    for dy in range(9):
        skin.px(BODY["front"], min(7, dy), dy, LEATHER)
        skin.px(BODY["back"], max(0, 7 - dy), dy, LEATHER)
    skin.fill((BODY["front"][0] + 5, BODY["front"][1] + 7, 3, 4), LEATHER)
    skin.fill((BODY["front"][0] + 5, BODY["front"][1] + 7, 3, 1), LEATHER_DARK)
    skin.fill(HEAD["top"], look["cloth_dark"])
    for name in SIDES:
        skin.band(HEAD[name], look["cloth_dark"], rows=2)


def craft_guard(skin, look):
    """Страж: шлем, кольчуга и цветная накидка народа."""
    skin.fill(HEAD["top"], IRON)
    for name in SIDES:
        skin.band(HEAD[name], IRON, rows=2)
        skin.band(HEAD[name], IRON_DARK, top=2, rows=1)
    skin.px(HEAD["front"], 3, 2, IRON)
    skin.px(HEAD["front"], 4, 2, IRON)
    for name in SIDES:
        skin.band(BODY[name], IRON, rows=9)
    x, y, width, height = BODY["front"]
    for dy in range(9):
        for dx in range(width):
            if (dx + dy) % 2 == 0:
                skin.px(BODY["front"], dx, dy, IRON_DARK)
    skin.fill((x + 3, y, 2, 9), look["accent"])
    skin.band(BODY["back"], look["accent"], rows=9)
    for name in SIDES:
        skin.band(ARM[name], IRON, rows=5)


def craft_elder(skin, look):
    """Старейшина: долгополое одеяние, седина и золотая кайма."""
    for name in SIDES:
        skin.band(BODY[name], look["accent"], rows=12)
        skin.band(BODY[name], GOLD, top=2, rows=1)
        skin.band(ARM[name], look["accent"], rows=10)
        skin.band(LEG[name], look["accent"], rows=8)
    skin.fill(HEAD["top"], GREY_HAIR)
    for name in SIDES:
        skin.band(HEAD[name], GREY_HAIR, rows=2)
    skin.band(HEAD["back"], GREY_HAIR, rows=6)
    draw_beard(skin, look, GREY_HAIR)


CRAFTS = {
    "builder": craft_builder,
    "farmer": craft_farmer,
    "lumberjack": craft_lumberjack,
    "courier": craft_courier,
    "guard": craft_guard,
    "elder": craft_elder,
}


def build(culture, woman, craft):
    look = CULTURES[culture]
    skin = Skin()
    draw_head(skin, look, woman)
    draw_body(skin, look, woman)
    draw_arm(skin, look)
    draw_leg(skin, look, woman)
    if not woman and culture == "norman" and craft != "elder":
        # Северянин с бородой, южанин без: это про народ, а не про моду.
        draw_beard(skin, look)
    if craft in CRAFTS:
        CRAFTS[craft](skin, look)
    return skin


def main():
    made = 0
    for culture in CULTURES:
        for gender, woman in (("male", False), ("female", True)):
            made += 1
            build(culture, woman, None).save("%s/%s" % (culture, gender))
            for craft in CRAFTS:
                made += 1
                build(culture, woman, craft).save(
                    "%s/%s_%s" % (culture, gender, craft))
    print("нарисовано обликов: %d" % made)


if __name__ == "__main__":
    main()
