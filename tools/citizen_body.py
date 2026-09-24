"""Тела жителей: кости, кубы и раскладка текстуры — в одном месте.

Зачем отдельный модуль. Модель жителя читают трое: игра (geo-файл),
генератор кож (куда рисовать бороду, где у фартука перед) и просмотрщик
(чтобы увидеть результат без игры). Пока раскладка жила в генераторе кож
числами, а модель — в JSON числами же, они сходились только потому, что
их писал один человек в один день. Теперь раскладку считает этот модуль,
geo-файл пишется из него же (`make-citizen-models.py`), а генератор кож
спрашивает у него, где какая грань. Разойтись им негде.

Две части у человеческой кожи, и граница между ними не случайна:

* **верхние 64x64 — ровно современная кожа игрока** (Стив у мужчин,
  Алекс с тонкими руками у женщин), со вторым слоем: шляпа, куртка,
  рукава, штанины. Любой редактор скинов её откроет, и автор набора
  ресурсов может нарисовать жителя тем же инструментом, что и себя;
* **нижние 64x64 — приметы**: борода гнома, уши эльфа, поля шляпы
  пахаря, наплечники стража. Раскладка у них своя, её считает упаковщик.

Примета — это кость с условием в имени: `beard@dwarf+male`. После `@`
идут слова через `+` (все должны совпасть), у слова может быть `!`
(не должно совпасть) и варианты через `|` (хватит одного). Слова — народ,
пол и ремесло, те самые, из которых складывается путь к коже (`Looks`).
Клиент прячет кость, чьё условие не выполнено, и больше ничего о ней
не знает: народ из чужого датапака получает свои приметы, дописав кость
в свою модель, без строчки кода.
"""

import math

# --- кирпичи --------------------------------------------------------------------


class Cube:
    """Куб модели.

    `uv` — угол развёртки, если он задан раскладкой (кожа игрока), или
    `None`, если место выберет упаковщик (приметы). `share` — имя общей
    развёртки: левое ухо и правое рисуются одной картинкой, и упаковщик
    выделяет место один раз. `flat` — плоская развёртка по граням для
    широких тонких кубов (поля шляпы): коробочная развёртка для куба
    12x1x12 заняла бы 48 столбцов ради одной соломенной полосы.
    """

    def __init__(self, origin, size, uv=None, inflate=0.0, mirror=False,
                 pivot=None, rotation=None, share=None, flat=False, name=None):
        self.name = name
        self.origin = origin
        self.size = size
        self.uv = uv
        self.inflate = inflate
        self.mirror = mirror
        self.pivot = pivot
        self.rotation = rotation
        self.share = share
        self.flat = flat

    def texels(self):
        """Размер в текселях — вниз до целого, как считает сама GeckoLib.

        Куб в полтора текселя толщиной рисуется одним столбцом, растянутым
        на полтора: развёртка у него на столбец, а не на два.
        """
        return tuple(int(math.floor(v)) for v in self.size)

    def footprint(self):
        """Сколько места займёт развёртка: ширина и высота в текселях."""
        w, h, d = self.texels()
        if self.flat:
            # Верх и низ — одна картинка w x d, бока — полоса w x h под ней.
            return max(w, d), d + max(h, 1)
        return 2 * (w + d), d + h


class Bone:
    def __init__(self, name, parent, pivot, cubes=(), rotation=None):
        self.name = name
        self.parent = parent
        self.pivot = pivot
        self.cubes = list(cubes)
        self.rotation = rotation


class Body:
    def __init__(self, identifier, width, height, bones, pack_area, bounds):
        self.identifier = identifier
        self.width = width
        self.height = height
        self.bones = bones
        self.pack_area = pack_area
        self.bounds = bounds
        self.regions = {}
        _pack(self)

    def bone(self, name):
        for bone in self.bones:
            if bone.name == name:
                return bone
        raise KeyError(name)


def faces(u, v, width, height, depth):
    """Шесть граней куба в коробочной развёртке Minecraft.

    Имена — со стороны самого жителя: «правая» грань та, что у его правой
    руки. Так же их называет генератор кож с первого дня.
    """
    return {
        "top": (u + depth, v, width, depth),
        "bottom": (u + depth + width, v, width, depth),
        "right": (u, v + depth, depth, height),
        "front": (u + depth, v + depth, width, height),
        "left": (u + depth + width, v + depth, depth, height),
        "back": (u + depth + width + depth, v + depth, width, height),
    }


def flat_faces(u, v, width, height, depth):
    """Плоская развёртка: верх и низ общие, все бока — одна полоса."""
    h = max(height, 1)
    return {
        "top": (u, v, width, depth),
        "bottom": (u, v, width, depth),
        "front": (u, v + depth, width, h),
        "back": (u, v + depth, width, h),
        "right": (u, v + depth, depth, h),
        "left": (u, v + depth, depth, h),
    }


def cube_faces(cube):
    u, v = cube.uv
    w, h, d = cube.texels()
    return flat_faces(u, v, w, h, d) if cube.flat else faces(u, v, w, h, d)


# --- упаковщик ------------------------------------------------------------------


def _pack(body):
    """Разложить приметы по свободной части текстуры полками.

    Полки, а не оптимальная укладка: примет два десятка, места втрое
    больше, чем им нужно, и важнее, чтобы место каждой не менялось от
    перестановки соседей по списку, — иначе каждая правка модели
    перерисовывала бы все кожи. Поэтому порядок — порядок объявления.
    """
    x0, y0, x1, y1 = body.pack_area
    x, y, shelf = x0, y0, 0
    shared = {}
    for bone in body.bones:
        for cube in bone.cubes:
            key = cube.share or None
            if cube.uv is None and key in shared:
                cube.uv = shared[key]
                continue
            if cube.uv is not None:
                if key:
                    shared.setdefault(key, cube.uv)
                continue
            w, h = cube.footprint()
            if x + w > x1:
                x, y, shelf = x0, y + shelf, 0
            if y + h > y1:
                raise ValueError("приметам не хватило места на текстуре: %s" % bone.name)
            cube.uv = (x, y)
            x += w
            shelf = max(shelf, h)
            if key:
                shared[key] = cube.uv
    for bone in body.bones:
        for cube in bone.cubes:
            if cube.uv is not None and (cube.share or cube.name or "@" in bone.name):
                name = cube.share or cube.name or bone.name.split("@")[0]
                body.regions.setdefault(name, cube_faces(cube))


# --- человек --------------------------------------------------------------------
#
# Координаты модели бедрока: ступни на нуле, лицо к -Z, правая рука
# на отрицательном X. Высота 32, как у игрока.

# Кожа игрока, вторая версия раскладки: у каждой части свой угол.
SKIN = {
    "head": (0, 0), "hat": (32, 0),
    "leg_right": (0, 16), "body": (16, 16), "arm_right": (40, 16),
    "pants_right": (0, 32), "jacket": (16, 32), "sleeve_right": (40, 32),
    "leg_left": (16, 48), "arm_left": (32, 48),
    "pants_left": (0, 48), "sleeve_left": (48, 48),
}

HAT = 0.5      # второй слой головы отстоит на полтекселя, как у игрока
LAYER = 0.25   # второй слой тела — на четверть


def _arm(side, sign):
    """Рука: широкая у мужчины, тонкая у женщины, — и оба рукава поверх.

    Две кости с условием внутри одной несущей: движения крутят
    `arm_<сторона>`, а какую из рук под ней показать, решает пол.
    Кисть одна на обе ширины: предмет в тонкой руке сдвигается на
    полтекселя, и глаз этого не различает.
    """
    base, sleeve = SKIN["arm_" + side], SKIN["sleeve_" + side]
    wide_x = -8 if sign < 0 else 4
    slim_x = -7 if sign < 0 else 4
    arm = "arm_" + side
    return [
        Bone(arm, "body", (5 * sign, 22, 0)),
        Bone("wide_%s@male" % side, arm, (5 * sign, 22, 0), [
            Cube((wide_x, 12, -2), (4, 12, 4), uv=base),
            Cube((wide_x, 12, -2), (4, 12, 4), uv=sleeve, inflate=LAYER),
        ]),
        Bone("slim_%s@female" % side, arm, (5 * sign, 22, 0), [
            Cube((slim_x, 12, -2), (3, 12, 4), uv=base),
            Cube((slim_x, 12, -2), (3, 12, 4), uv=sleeve, inflate=LAYER),
        ]),
    ]


def _leg(side, sign):
    x = -4 if sign < 0 else 0
    return Bone("leg_" + side, "root", (2 * sign, 12, 0), [
        Cube((x, 0, -2), (4, 12, 4), uv=SKIN["leg_" + side]),
        Cube((x, 0, -2), (4, 12, 4), uv=SKIN["pants_" + side], inflate=LAYER),
    ])


HUMAN = Body(
    "geometry.citizen", 64, 128,
    [
        Bone("root", None, (0, 0, 0)),
        # Туловище вращается от бёдер, а не от шеи: так дыхание и наклон
        # на бегу качают плечи, а не поясницу.
        Bone("body", "root", (0, 12, 0), [
            Cube((-4, 12, -2), (8, 12, 4), uv=SKIN["body"]),
            Cube((-4, 12, -2), (8, 12, 4), uv=SKIN["jacket"], inflate=LAYER),
        ]),
        Bone("head", "body", (0, 24, 0), [
            Cube((-4, 24, -4), (8, 8, 8), uv=SKIN["head"]),
            Cube((-4, 24, -4), (8, 8, 8), uv=SKIN["hat"], inflate=HAT),
        ]),

        # --- приметы народа -----------------------------------------------------
        # Борода гнома — не нарисованная, а настоящая: висит из-под
        # подбородка на грудь. Со спины народ узнаётся по росту, спереди —
        # по ней, и это главная черта подгорного народа с детских книжек.
        Bone("beard@dwarf+male+adult", "head", (0, 24, 0), [
            Cube((-4, 18, -5), (8, 7, 2)),
        ]),
        # Коса гномки — по спине. Сбоку от головы она ложилась бы на плечо,
        # а плечо ходит вместе с рукой: коса пролезала бы сквозь руку.
        Bone("braid@dwarf+female", "head", (0, 24, 0), [
            Cube((-1, 15, 3.5), (2, 10, 2)),
        ]),
        # Волосы до плеч у женщин, кроме гномок: у тех коса.
        Bone("hair@female+!dwarf", "head", (0, 24, 0), [
            Cube((-4, 19, 3), (8, 5, 1)),
        ]),
        # Уши эльфа — острые и отведены назад. Одна картинка на оба:
        # левое зеркалит правое.
        Bone("ears@elf", "head", (0, 24, 0), [
            Cube((-5, 27, -0.5), (1, 5, 2), pivot=(-4.5, 27.5, 0.5),
                 rotation=(-26, 0, -38), share="ear"),
            Cube((4, 27, -0.5), (1, 5, 2), pivot=(4.5, 27.5, 0.5),
                 rotation=(-26, 0, 38), share="ear", mirror=True),
        ]),
        # Убор старейшины майя: три пера веером над затылком.
        Bone("plume@maya+elder", "head", (0, 32, 2), [
            Cube((-0.5, 31, 1.5), (1, 8, 1), pivot=(0, 31, 2),
                 rotation=(-10, 0, 28), share="feather"),
            Cube((-0.5, 31, 1.5), (1, 9, 1), pivot=(0, 31, 2),
                 rotation=(-14, 0, 0), share="feather_mid"),
            Cube((-0.5, 31, 1.5), (1, 8, 1), pivot=(0, 31, 2),
                 rotation=(-10, 0, -28), share="feather", mirror=True),
        ]),

        # --- приметы ремесла ----------------------------------------------------
        # Поля соломенной шляпы. Тулья нарисована на втором слое головы,
        # а поля — отдельно: силуэт шляпы пахаря виден с другого конца поля,
        # и на коже одной её не нарисовать.
        Bone("brim@farmer", "head", (0, 24, 0), [
            Cube((-6, 29, -6), (12, 1, 12), flat=True),
        ]),
        # Шишак норманнского шлема и наносник: конус над макушкой узнают
        # по гобеленам, и страж норманнов становится норманном.
        Bone("helm@guard+norman", "head", (0, 24, 0), [
            Cube((-3.5, 32.1, -3.5), (7, 1, 7), name="helm_base"),
            Cube((-2.5, 33.1, -2.5), (5, 1, 5), name="helm_cone"),
            Cube((-1.5, 34.1, -1.5), (3, 1, 3), name="helm_crown"),
            Cube((-0.5, 35.1, -0.5), (1, 1, 1), name="helm_tip"),
            Cube((-0.5, 25, -5), (1, 4, 1), name="nasal"),
        ]),
        # Гребень из перьев поперёк головы — страж майя.
        Bone("crest@guard+maya", "head", (0, 24, 0), [
            Cube((-0.5, 32, -3.5), (1, 3, 7)),
        ]),
        # Котомка курьера — за спиной: сбоку от бедра её задевала бы рука.
        Bone("knapsack@courier", "body", (0, 12, 0), [
            Cube((-3, 14, 2), (6, 7, 3)),
        ]),
        # Сумка с инструментом на поясе каменщика.
        Bone("pouch@builder", "body", (0, 12, 0), [
            Cube((-3.5, 10, -3), (3, 3, 1)),
        ]),
        # Кошель купца — тот самый, из которого он платит.
        Bone("purse@merchant", "body", (0, 12, 0), [
            Cube((1, 10, -3), (2, 3, 1)),
        ]),
        # Моток пряжи у пояса прядильщицы.
        Bone("yarn@weaver", "body", (0, 12, 0), [
            Cube((1, 10, -3.5), (2, 2, 2)),
        ]),
        # Юбка до колен. Ноги при шаге выглядывают из-под неё так же,
        # как у ванильного жителя из-под рясы, — и так же никого не смущают.
        Bone("skirt@female+!elder", "body", (0, 12, 0), [
            Cube((-4, 6, -2), (8, 6, 4), inflate=0.5),
        ]),
        # Ряса старейшины — до щиколоток: старость видна силуэтом.
        Bone("robe@elder", "body", (0, 12, 0), [
            Cube((-4, 2, -2), (8, 10, 4), inflate=0.6),
        ]),
    ]
    + _arm("right", -1)
    + [
        # Кисть — к ней привязан предмет в руке. Имя знает отрисовщик.
        Bone("hand_right", "arm_right", (-6, 12, 0)),
        Bone("pauldron_right@guard", "arm_right", (-5, 22, 0), [
            Cube((-8.5, 22, -2.5), (5, 3, 5), share="pauldron"),
        ]),
    ]
    + _arm("left", 1)
    + [
        Bone("pauldron_left@guard", "arm_left", (5, 22, 0), [
            Cube((3.5, 22, -2.5), (5, 3, 5), share="pauldron", mirror=True),
        ]),
        _leg("right", -1),
        _leg("left", 1),
    ],
    pack_area=(0, 64, 64, 128),
    bounds=(3, 4, 1.5),
)


# --- конь -----------------------------------------------------------------------
#
# Пони — четвероногие, и раскладка у коня своя целиком: кожу игрока
# на него не натянуть, поэтому место всем кубам выбирает упаковщик.
# Морда к -Z, как у человека лицо.

PONY = Body(
    "geometry.citizen_pony", 64, 128,
    [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 12, 0), [
            Cube((-4, 8, -6), (8, 8, 12), share="barrel"),
            # Попона — второй слой бочки: на ней написано ремесло, как
            # у человека на куртке. Где попоны нет — прозрачно.
            Cube((-4, 8, -6), (8, 8, 12), inflate=0.4, share="blanket"),
        ]),
        # Груз едет на спине — к этой кости его и привязывает отрисовщик.
        Bone("pack", "body", (0, 16, 1)),
        Bone("tail", "body", (0, 15, 6), [
            Cube((-1, 13, 5.5), (2, 3, 2), share="dock"),
        ], rotation=(34, 0, 0)),
        Bone("tail_hair", "tail", (0, 13.5, 6.5), [
            Cube((-1.5, 5, 5.5), (3, 9, 2), share="tail"),
        ], rotation=(-26, 0, 0)),
        # Шея поднята вперёд: у коня голова держится выше холки,
        # и горизонтальная шея читалась собакой.
        Bone("neck", "body", (0, 14, -5), [
            Cube((-2, 13, -8.5), (4, 8, 5), share="neck"),
        ], rotation=(26, 0, 0)),
        # Грива — по гребню шеи и дальше чёлкой между ушей: у буланого
        # масть узнаётся по тёмной гриве, и видна она должна быть сбоку.
        Bone("mane", "neck", (0, 14, -4), [
            Cube((-0.5, 14, -4), (1, 8, 2), share="mane"),
        ]),
        Bone("head", "neck", (0, 21, -6), [
            Cube((-2.5, 18.5, -10), (5, 5, 6), share="skull"),
            Cube((-2.5, 18.5, -10), (5, 5, 6), inflate=0.3, share="cap"),
            Cube((-1, 23, -7.5), (2, 1, 2), share="forelock"),
        ], rotation=(14, 0, 0)),
        Bone("muzzle", "head", (0, 19.5, -11), [
            Cube((-2, 18.5, -13), (4, 4, 3), share="muzzle"),
        ]),
        Bone("ear_right", "head", (-1.5, 23.5, -6), [
            Cube((-2.5, 23.5, -6.5), (2, 3, 1), share="ear"),
        ], rotation=(0, 0, 8)),
        Bone("ear_left", "head", (1.5, 23.5, -6), [
            Cube((0.5, 23.5, -6.5), (2, 3, 1), share="ear", mirror=True),
        ], rotation=(0, 0, -8)),

        # --- приметы ремесла ----------------------------------------------------
        # Соломенная шляпа между ушей — пахарь даже у коня пахарь.
        Bone("hat@farmer", "head", (0, 23.5, -7), [
            Cube((-1.5, 23.8, -8.5), (3, 2, 3), share="hat_crown"),
            Cube((-3.5, 23.3, -10.5), (7, 1, 7), flat=True, share="hat_brim"),
        ]),
        # Султан на оголовье стража.
        Bone("plume@guard", "head", (0, 23.5, -8), [
            Cube((-0.5, 23.5, -9), (1, 4, 2), rotation=(-20, 0, 0),
                 pivot=(0, 23.5, -8), share="plume"),
        ]),
        # Перемётные сумы — у курьера и у купца: оба возят.
        Bone("saddlebags@courier|merchant", "body", (0, 12, 0), [
            Cube((-5.5, 9, -2.5), (1.5, 4, 5), share="saddlebag"),
            Cube((4, 9, -2.5), (1.5, 4, 5), share="saddlebag", mirror=True),
        ]),
        # Долгая попона старейшины свисает ниже бочки.
        Bone("caparison@elder", "body", (0, 12, 0), [
            Cube((-4, 4.5, -5.5), (8, 4, 11), inflate=0.45, share="caparison"),
        ]),
    ]
    + [
        Bone(name, "root", pivot, [
            Cube(origin, (3, 8, 3), share="leg", mirror=mirror),
            Cube((origin[0], 0, origin[2]), (3, 1.5, 3), inflate=0.25,
                 share="hoof", mirror=mirror),
        ])
        for name, pivot, origin, mirror in (
            ("leg_front_right", (-2.5, 8, -4), (-4, 0, -5.5), False),
            ("leg_front_left", (2.5, 8, -4), (1, 0, -5.5), True),
            ("leg_back_right", (-2.5, 8, 3.5), (-4, 0, 2.5), False),
            ("leg_back_left", (2.5, 8, 3.5), (1, 0, 2.5), True),
        )
    ],
    pack_area=(0, 0, 64, 128),
    bounds=(3, 2.5, 1.25),
)

BODIES = {"citizen": HUMAN, "citizen_pony": PONY}
