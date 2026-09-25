"""Снаряжение народов: объёмная броня, оружие, иконки и рецепты.

Заказчик: «потом добавим броню и оружие уникальное». У каждого народа
свой набор из четырёх частей и своё оружие, и узнаётся он по силуэту,
а не по цвету: рогатый шлем северянина, конус с наносником норманна,
веер перьев майя, бородник гнома, венец эльфа, ушки и рог пони.

Силуэт не помещается в ванильную броню — у неё одна форма на всех,
на ладонь шире тела. Поэтому броня объёмная, моделью GeckoLib: те же
кости, что у брони игрока (`armorHead`, `armorBody`, …), и свои кубы
поверх. Всё — модель, развёртка, краска, иконки и рецепты — пишется
отсюда, как и остальной арт мода; руками не правится.

    python tools/make-gear.py
"""

import importlib.util
import json
import random
from pathlib import Path

from PIL import Image

import citizen_body as cb

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/villagepax"
DATA = ROOT / "src/main/resources/data/villagepax"

_spec = importlib.util.spec_from_file_location(
    "citizen_models", Path(__file__).resolve().parent / "make-citizen-models.py")
models = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(models)

PEOPLES = ("norman", "maya", "pony", "dwarf", "elf", "nord", "yamato")
PIECES = ("helmet", "chestplate", "leggings", "boots")
WEAPONS = {"norman": "longsword", "maya": "macuahuitl", "pony": "horseshoe_flail",
           "dwarf": "warhammer", "elf": "moon_blade", "nord": "bearded_axe",
           "yamato": "katana"}


def hexa(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# --- краски ---------------------------------------------------------------------
#
# У каждого народа: контур O, основа B, свет H, тень S, отделка T и два
# акцента A и X. Иконки и развёртка берут их отсюда же, поэтому шлем
# в руке и шлем на голове одного цвета.

PALETTES = {
    "norman": {"O": "2b2e33", "B": "9aa0a8", "H": "cfd4da", "S": "666c75", "T": "d9b44a",
               "A": "a8322d", "X": "e9d8a6", "G": "5a3a22"},
    "maya": {"O": "3a2a17", "B": "e6d8b8", "H": "fff4dc", "S": "b3a27f", "T": "e0b23a",
             "A": "2fa67a", "X": "d9442e", "G": "6b4424"},
    "pony": {"O": "4a2338", "B": "f3a6c8", "H": "ffd6e8", "S": "c9739b", "T": "f2c94c",
             "A": "ffffff", "X": "8d5ad8", "G": "7a4a2a"},
    "dwarf": {"O": "1b1d21", "B": "5e6570", "H": "96a0ad", "S": "3a3f47", "T": "e2b348",
              "A": "57e1e6", "X": "8a4b2a", "G": "4a3020"},
    "elf": {"O": "1f3a1c", "B": "5f9e4f", "H": "a6d886", "S": "3d6e35", "T": "d7e4ea",
            "A": "a877e0", "X": "e9e4d4", "G": "6e5236"},
    "nord": {"O": "25180d", "B": "8a8f94", "H": "c3c8cc", "S": "5a5f64", "T": "3d6fb6",
             "A": "e6dcc1", "X": "7a5433", "G": "4a3322"},
    # Ямато: чёрный лак пластин, золото, киноварная шёлковая шнуровка,
    # индиго рукавов и белые носки-таби.
    "yamato": {"O": "18141a", "B": "2a2830", "H": "4e4a5a", "S": "121016", "T": "d9b44a",
               "A": "b8322a", "X": "2e416e", "G": "6b4a2a", "W": "ece8dc"},
}
FUR = {"norman": ("8a6a48", "5e4630", "b08a62"), "maya": ("c9a15a", "8f6a34", "e8c98a"),
       "pony": ("ffffff", "e9d6e6", "ffffff"), "dwarf": ("6b4a2f", "46301e", "8f6844"),
       "elf": ("e9e4d4", "c9c2ad", "ffffff"), "nord": ("8a6a4a", "5b4330", "c9b79a"),
       "yamato": ("d9c38a", "b39a5e", "efdcac")}
RAINBOW = ("e8413c", "f39a2b", "f6d83a", "5cc85a", "3aa0e8", "8d5ad8")


def colour(people, key):
    return hexa(PALETTES[people][key])


# --- кубы -----------------------------------------------------------------------


def cube(name, origin, size, paint, inflate=0.0, pivot=None, rotation=None):
    made = cb.Cube(origin, size, inflate=inflate, pivot=pivot, rotation=rotation, name=name)
    made.paint = paint
    return made


def mirrored(made):
    """Тот же куб на левой стороне: X отражён, развёртка общая и зеркальная."""
    ox, oy, oz = made.origin
    w = made.size[0]
    twin = cb.Cube((-(ox + w), oy, oz), made.size, inflate=made.inflate,
                   pivot=None if made.pivot is None else (-made.pivot[0],) + tuple(made.pivot[1:]),
                   rotation=None if made.rotation is None
                   else (made.rotation[0], -made.rotation[1], -made.rotation[2]),
                   mirror=True, share=made.name)
    twin.paint = made.paint
    made.share = made.name
    return twin


# Кости — те же имена и те же опоры, что у брони игрока в GeckoLib:
# рендерер переносит на них позу ванильной модели и сдвигает от этих опор.
BONES = (("armorHead", (0, 24, 0)), ("armorBody", (0, 24, 0)),
         ("armorRightArm", (-5, 22, 0)), ("armorLeftArm", (5, 22, 0)),
         ("armorRightLeg", (-2, 12, 0)), ("armorLeftLeg", (2, 12, 0)),
         ("armorRightBoot", (-2, 12, 0)), ("armorLeftBoot", (2, 12, 0)))

HELM = ((-4, 24, -4), (8, 8, 8))
TORSO = ((-4, 12, -2), (8, 12, 4))
ARM = ((-8, 12, -2), (4, 12, 4))
LEG = ((-4, 0, -2), (4, 12, 4))
BOOT = ((-4, 0, -2), (4, 5, 4))


def outfit(head, body, arm, leg, boot):
    """Кости набора: правые руки и ноги описаны, левые отражаются."""
    bones = []
    for name, pivot in BONES:
        if name == "armorHead":
            cubes = head
        elif name == "armorBody":
            cubes = body
        elif name == "armorRightArm":
            cubes = arm
        elif name == "armorLeftArm":
            cubes = [mirrored(c) for c in arm]
        elif name == "armorRightLeg":
            cubes = leg
        elif name == "armorLeftLeg":
            cubes = [mirrored(c) for c in leg]
        elif name == "armorRightBoot":
            cubes = boot
        else:
            cubes = [mirrored(c) for c in boot]
        bones.append(cb.Bone(name, None, pivot, cubes))
    return bones


def horns():
    """Два рога из ступеней: наружу и вверх, как у тура."""
    steps = [((-8.5, 29.0, -1.25), (3.5, 2.5, 2.5)), ((-10.5, 30.5, -1.0), (2.5, 2.5, 2.0)),
             ((-11.8, 32.5, -0.75), (2.0, 2.5, 1.5)), ((-12.4, 34.8, -0.5), (1.4, 2.2, 1.0)),
             ((-12.5, 36.8, -0.35), (0.9, 1.6, 0.7))]
    right = [cube("horn%d" % i, o, s, ("horn", i)) for i, (o, s) in enumerate(steps)]
    return right + [mirrored(c) for c in right]


def sets():
    """Кубы каждого набора."""
    out = {}

    out["nord"] = outfit(
        [cube("helm", *HELM, ("helm", "spectacle"), inflate=1.0)] + horns(),
        [cube("torso", *TORSO, ("mail", None), inflate=1.01),
         cube("mantle", (-5.6, 21.6, -3.6), (11.2, 3.2, 7.2), ("fur", None))],
        [cube("arm", *ARM, ("mail", None), inflate=1.0),
         cube("fur_cap", (-8, 20.5, -2), (4, 3, 4), ("fur", None), inflate=1.5)],
        [cube("leg", *LEG, ("trousers", None), inflate=0.5)],
        [cube("boot", *BOOT, ("leather", "boot"), inflate=0.9),
         cube("cuff", (-5.2, 4.2, -3.2), (6.4, 2.0, 6.4), ("fur", None))])

    out["norman"] = outfit(
        [cube("helm", *HELM, ("helm", "nasal"), inflate=1.0),
         cube("cone1", (-3.5, 33, -3.5), (7, 1, 7), ("plate", None)),
         cube("cone2", (-2.5, 34, -2.5), (5, 1, 5), ("plate", None)),
         cube("cone3", (-1.5, 35, -1.5), (3, 1, 3), ("plate", None)),
         cube("finial", (-0.5, 36, -0.5), (1, 1, 1), ("gold", None))],
        [cube("torso", *TORSO, ("mail", None), inflate=1.01),
         cube("tabard", (-3.5, 8, -3.3), (7, 15, 0.2), ("tabard", "front")),
         cube("tabard_back", (-3.5, 8, 3.1), (7, 15, 0.2), ("tabard", "back"))],
        [cube("arm", *ARM, ("mail", None), inflate=1.0)],
        [cube("leg", *LEG, ("mail", None), inflate=0.5)],
        [cube("boot", *BOOT, ("leather", "boot"), inflate=0.9),
         cube("spur", (-2.5, 0.6, 2.9), (1, 1, 1.4), ("gold", None))])

    feathers = []
    for i, angle in enumerate((-60, -40, -20, 0, 20, 40, 60)):
        x = -1 + angle / 12.0
        feathers.append(cube("feather%d" % i, (x, 30, 4.2), (2, 12, 0),
                             ("feather", i % 3), pivot=(x + 1, 30, 4.2),
                             rotation=(-18, 0, -angle)))
    out["maya"] = outfit(
        [cube("helm", *HELM, ("helm", "band"), inflate=1.0),
         cube("spool", (-5.8, 26, -1), (0.6, 2, 2), ("jade", None))]
        + feathers
        + [cube("spool_l", (5.2, 26, -1), (0.6, 2, 2), ("jade", None))],
        [cube("torso", *TORSO, ("quilt", None), inflate=1.01),
         cube("pectoral", (-4.5, 19.5, -3.3), (9, 4, 0.3), ("pectoral", None))],
        [cube("arm", *ARM, ("quilt", "short"), inflate=1.0),
         cube("armlet", (-8, 13, -2), (4, 1.5, 4), ("gold", None), inflate=1.2)],
        [cube("leg", *LEG, ("quilt", "greave"), inflate=0.5)],
        [cube("boot", *BOOT, ("sandal", None), inflate=0.9)])

    out["dwarf"] = outfit(
        [cube("helm", *HELM, ("helm", "great"), inflate=1.0),
         cube("crest", (-0.5, 33, -4.5), (1, 2, 9), ("gold", None)),
         cube("beard_guard", (-3.5, 20, -5.6), (7, 4, 1), ("plate", "beard"))],
        [cube("torso", *TORSO, ("plate", "runes"), inflate=1.01)],
        [cube("arm", *ARM, ("plate", None), inflate=1.0),
         cube("pauldron", (-10, 21, -3.5), (6.5, 4.5, 7), ("plate", "trim")),
         cube("gauntlet", (-8, 11.5, -2), (4, 4, 4), ("plate", "trim"), inflate=1.25)],
        [cube("leg", *LEG, ("plate", None), inflate=0.5)],
        [cube("boot", *BOOT, ("plate", "boot"), inflate=1.0),
         cube("toe", (-4, 0, -3.6), (4, 2.5, 1.6), ("plate", "trim"), inflate=0.9)])

    out["elf"] = outfit(
        [cube("helm", *HELM, ("helm", "circlet"), inflate=0.6),
         cube("leaf_front", (-1, 30.4, -4.8), (2, 4, 0), ("leaf", None)),
         cube("leaf_side", (-5.0, 28, -2), (0, 4, 7), ("leaf", None),
              pivot=(-5.0, 28, -2), rotation=(-30, 0, 0)),
         cube("leaf_side_l", (5.0, 28, -2), (0, 4, 7), ("leaf", None),
              pivot=(5.0, 28, -2), rotation=(-30, 0, 0))],
        [cube("torso", *TORSO, ("leafmail", None), inflate=1.01),
         cube("cape", (-4.5, 6, 3.2), (9, 18, 0.4), ("cape", None)),
         cube("brooch", (-1, 21.5, -3.4), (2, 2, 0.4), ("gem", None))],
        [cube("arm", *ARM, ("leafmail", None), inflate=1.0),
         cube("leaf_pauldron", (-8, 22, -2), (4, 1, 4), ("leaf", None), inflate=1.6),
         cube("bracer", (-8, 12, -2), (4, 3, 4), ("silver", None), inflate=1.2)],
        [cube("leg", *LEG, ("leafmail", None), inflate=0.5)],
        [cube("boot", *BOOT, ("leather", "elf"), inflate=0.9)])

    out["pony"] = outfit(
        [cube("helm", *HELM, ("helm", "cap"), inflate=1.0),
         cube("ear", (-3.8, 33, -1), (2, 2.5, 1), ("ear", None)),
         cube("ear_l", (1.8, 33, -1), (2, 2.5, 1), ("ear", None)),
         cube("unihorn", (-0.75, 31.5, -5.2), (1.5, 6.5, 1.5), ("spiral", None),
              pivot=(0, 31.5, -4.5), rotation=(-30, 0, 0)),
         cube("mane", (-1, 33, -3.5), (2, 1.5, 8.5), ("rainbow", "z")),
         cube("mane_back", (-1, 24.5, 4.9), (2, 8.5, 1), ("rainbow", "y"))],
        [cube("torso", *TORSO, ("harness", None), inflate=1.01),
         cube("saddle", (-3, 15, 3.1), (6, 6, 1), ("saddle", None))],
        [cube("arm", *ARM, ("sleeve", None), inflate=1.0)],
        [cube("leg", *LEG, ("rainbow", "y"), inflate=0.5)],
        [cube("boot", *BOOT, ("hoof", None), inflate=1.0),
         cube("fetlock", (-4, 3.5, -2), (4, 2.5, 4), ("fur", None), inflate=1.3)])
    kuwagata = cube("kuwagata", (-3.2, 32, -5.8), (1, 5.5, 0.6), ("gold", None),
                    pivot=(-2.7, 32, -5.5), rotation=(0, 0, -24))
    fukigaeshi = cube("fukigaeshi", (-6.3, 26.5, -4.4), (1.2, 3, 2.2), ("lacquer", "gold"))
    sode = cube("sode", (-10.4, 15.5, -3.2), (1.3, 8, 6.4), ("lamellar", "sode"))
    out["yamato"] = outfit(
        [cube("helm", *HELM, ("helm", "kabuto"), inflate=1.0),
         cube("shikoro", (-6, 23.5, -2.5), (12, 3, 8), ("lamellar", "shikoro")),
         fukigaeshi, mirrored(fukigaeshi), kuwagata, mirrored(kuwagata),
         cube("maedate", (-1, 31, -5.9), (2, 2, 0.6), ("gold", "sun"))],
        [cube("torso", *TORSO, ("lamellar", "do"), inflate=1.01),
         cube("kusazuri", (-4.8, 7, -2.8), (9.6, 5, 5.6), ("lamellar", "skirt"))],
        [cube("arm", *ARM, ("kote", None), inflate=1.0), sode],
        [cube("leg", *LEG, ("haidate", None), inflate=0.5)],
        [cube("boot", *BOOT, ("waraji", None), inflate=0.9)])
    return out


# --- краска граней --------------------------------------------------------------


def shade(rgba, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgba[:3]) + (rgba[3],)


def helm_mask(style, face, x, y, w, h):
    """Видна ли клетка шлема: открытое лицо, прорези, обод."""
    if style == "spectacle":
        if face == "front":
            if y <= 2:
                return True
            if y in (3, 4):
                return x in (0, 3, 4, 7) or y == 3
            return x in (3, 4) and y == 5
        if face in ("right", "left"):
            return y <= 4 or (face == "right" and x <= 2) or (face == "left" and x >= w - 3)
        return face != "bottom"
    if style == "nasal":
        if face == "front":
            return y <= 3 or (x in (3, 4) and y <= 6)
        if face in ("right", "left"):
            return y <= 4
        if face == "back":
            return y <= 5
        return face == "top"
    if style == "band":
        return face in ("front", "back", "right", "left") and 1 <= y <= 2
    if style == "great":
        return face != "bottom"
    if style == "circlet":
        return face in ("front", "back", "right", "left") and y == 2 or (
            face == "front" and y == 1 and x in (3, 4))
    if style == "kabuto":
        if face == "front":
            return y <= 2
        if face in ("right", "left"):
            return y <= 3
        return face != "bottom"
    if style == "cap":
        if face == "front":
            return y <= 2
        if face in ("right", "left"):
            return y <= 3 or (face == "right" and x <= 3) or (face == "left" and x >= w - 4)
        return face != "bottom"
    return True


def texel(people, paint, face, x, y, w, h, rng, name):
    """Цвет одной клетки грани — или None, если клетка прозрачна."""
    kind, style = paint
    P = lambda k: colour(people, k)
    fur = [hexa(c) for c in FUR[people]]
    edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
    if kind == "helm":
        if not helm_mask(style, face, x, y, w, h):
            return None
        if style == "band":
            return P("T") if (x + y) % 3 == 0 else P("A")
        if style == "circlet":
            return P("A") if (face == "front" and x in (3, 4)) else P("T")
        if style == "cap":
            base = P("B")
            if face == "back" and y >= 3:
                return hexa(RAINBOW[(x + y // 2) % len(RAINBOW)])
            return shade(base, 1.1) if y == 0 else base
        base = P("B")
        if style == "great":
            if face == "front" and y == 4 and x not in (3, 4):
                return P("O")
            if face == "front" and x in (3, 4) and y >= 4:
                return P("S")
            if y == 7 or (face == "front" and y == 3):
                return P("T")
        if style in ("spectacle", "nasal") and y == 3 and face != "top":
            return P("T") if style == "spectacle" else P("S")
        if face == "top":
            return shade(base, 1.15 if (x + y) % 4 else 0.95)
        return shade(base, 1.12 if y == 0 else (0.9 if y >= h - 2 else 1.0))
    if kind == "plate":
        base = P("B")
        if style == "trim" and edge:
            return P("T")
        if style == "runes" and face == "front" and x in (3, 4) and 3 <= y <= 8:
            return P("A") if y % 2 else P("S")
        if style == "beard" and face == "front":
            return P("T") if x % 2 == y % 2 else base
        if style == "boot" and y >= h - 1:
            return P("O")
        if edge:
            return shade(base, 1.2 if (y == 0 or x == 0) else 0.75)
        return base if (x * 7 + y * 3) % 11 else P("H")
    if kind == "mail":
        base = P("B")
        return P("H") if (x + y) % 2 == 0 else (P("S") if y % 2 else base)
    if kind == "tabard":
        base = P("A")
        if style == "front" and (x == 3 or y == 4):
            return P("T")
        return P("T") if edge and y == h - 1 else base
    if kind == "fur":
        c = fur[rng.randrange(3)]
        return c if rng.random() > 0.15 else shade(c, 0.85)
    if kind == "leather":
        base = P("X") if people == "nord" else P("G")
        if style == "elf":
            base = P("S")
            if y == 0:
                return P("T")
        if y >= h - 1:
            return P("O")
        if y == 1 and x % 2 == 0:
            return shade(base, 1.25)
        return shade(base, 1.0 + rng.uniform(-0.06, 0.06))
    if kind == "trousers":
        base = P("T")
        if y >= 5 and (x + y) % 3 == 0:
            return P("A")
        return shade(base, 1.0 + rng.uniform(-0.07, 0.07))
    if kind == "gold":
        if style == "sun":
            return P("A") if face == "front" and 0 < x < w - 1 else P("T")
        return P("T") if (x + y) % 3 else shade(P("T"), 1.2)
    if kind == "horn":
        light = P("A")
        return shade(light, 1.0 - 0.12 * style - (0.05 if y == h - 1 else 0))
    if kind == "quilt":
        if style == "short" and y > 5:
            return None
        base = P("B")
        if style == "greave" and y % 4 == 0:
            return P("X")
        if y == 0 or (style != "greave" and y == h - 1):
            return P("A")
        return shade(base, 0.9) if (x % 3 == 0 or y % 3 == 0) else base
    if kind == "pectoral":
        return P("A") if (x + y) % 2 else P("T")
    if kind == "jade":
        return P("A") if (x + y) % 2 else shade(P("A"), 1.25)
    if kind == "feather":
        tone = (P("X"), P("A"), P("T"))[style]
        if w > 0 and x == w // 2:
            return shade(tone, 0.7)
        return shade(tone, 1.0 + 0.08 * ((y % 3) - 1))
    if kind == "sandal":
        if y >= h - 1:
            return P("G")
        return P("X") if y in (1, 3) else None
    if kind == "leaf":
        base = P("B")
        if x == w // 2 or y == h // 2:
            return P("H")
        return base if (x + y) % 3 else P("S")
    if kind == "leafmail":
        base = P("B")
        row = (x + (y // 2) % 2) % 2
        return P("H") if (y % 2 == 0 and row) else (P("S") if y % 2 else base)
    if kind == "cape":
        if edge and face in ("front", "back"):
            return P("T")
        return shade(P("S"), 1.0 + rng.uniform(-0.05, 0.05))
    if kind in ("gem",):
        return P("A") if not edge else P("T")
    if kind == "silver":
        return P("T") if (x + y) % 2 else shade(P("T"), 0.85)
    if kind == "ear":
        return P("H") if face == "front" and 0 < x < w - 1 and y > 0 else P("B")
    if kind == "spiral":
        return P("T") if (y + x) % 2 == 0 else hexa("fff1b0")
    if kind == "rainbow":
        index = (y if style == "y" else x) % len(RAINBOW)
        return hexa(RAINBOW[index])
    if kind == "harness":
        base = P("B")
        if face == "front" and 3 <= y <= 5 and 2 <= x <= 5:
            return P("T") if (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4), (2, 4), (5, 4), (3, 5), (4, 5)) \
                else base
        if y == 0 or x in (1, w - 2) and face == "front":
            return P("A")
        return base
    if kind == "saddle":
        return P("T") if edge else P("G")
    if kind == "sleeve":
        if y >= h - 2:
            return P("A")
        return P("B") if (x + y) % 4 else P("H")
    if kind == "hoof":
        if y >= h - 1:
            return P("O")
        if y == h - 2:
            return P("T")
        return shade(P("G"), 1.0 + rng.uniform(-0.05, 0.05))
    if kind == "helm_kabuto":
        return None
    if kind == "lamellar":
        # Пластины чёрного лака рядами, между рядами — киноварная шнуровка.
        if style in ("shikoro", "skirt") and face in ("top", "bottom"):
            return None
        if style == "shikoro" and face == "front":
            return None
        if y % 3 == 2:
            return P("A") if x % 2 == 0 else shade(P("A"), 0.8)
        if style == "do" and face == "front" and 3 <= x <= 4 and 3 <= y <= 4:
            return P("T")
        return P("H") if (x + y // 3) % 4 == 0 else P("B")
    if kind == "lacquer":
        return P("T") if edge and style == "gold" else P("B")
    if kind == "kote":
        # Рукав индиго, по нему — полосы лакированных пластин.
        return P("B") if y % 4 == 0 or y >= h - 2 else P("X")
    if kind == "haidate":
        if y <= 5:
            return P("A") if y % 3 == 2 else P("B")
        return P("B") if x % 2 else P("H")
    if kind == "waraji":
        if y >= h - 1:
            return P("T")
        return P("W") if y >= 1 else P("A")
    raise ValueError("нет краски %s" % kind)


def paint(people, body):
    image = Image.new("RGBA", (body.width, body.height), (0, 0, 0, 0))
    pixels = image.load()
    painted = set()
    for bone in body.bones:
        for made in bone.cubes:
            key = made.share or id(made)
            if key in painted:
                continue
            painted.add(key)
            rng = random.Random("%s/%s" % (people, made.name or made.share))
            for face, (u, v, w, h) in cb.cube_faces(made).items():
                for y in range(h):
                    for x in range(w):
                        c = texel(people, made.paint, face, x, y, w, h, rng, made.name)
                        if c is not None and 0 <= u + x < body.width and 0 <= v + y < body.height:
                            pixels[u + x, v + y] = c
    return image


# --- иконки ---------------------------------------------------------------------
#
# Шестнадцать на шестнадцать, буквами палитры. Точка — прозрачно,
# пробел в накладке — «не трогать».

ICONS = {
    "helmet": [
        "................",
        "................",
        "................",
        ".....OOOOOO.....",
        "....OHHBBBBO....",
        "...OHBBBBBBSO...",
        "...OHBBBBBBSO...",
        "..OTTTTTTTTTTO..",
        "..OBBBBBBBBBSO..",
        "..OBBO....OBSO..",
        "..OBO......OSO..",
        "..OBO......OSO..",
        "..OSO......OSO..",
        "...O........O...",
        "................",
        "................"],
    "chestplate": [
        "................",
        "...OOO....OOO...",
        "..OHHBO..OBBSO..",
        ".OHBBBBOOBBBBSO.",
        ".OHBBBBBBBBBBSO.",
        ".OOBBBBBBBBBBOO.",
        "..OOBBBBBBBBOO..",
        "...OBBBBBBBBO...",
        "...OHBBBBBBSO...",
        "...OHBBBBBBSO...",
        "...OTTTTTTTTO...",
        "...OBBBBBBBBO...",
        "...OBBBBBBBSO...",
        "...OSSSSSSSSO...",
        "....OOOOOOOO....",
        "................"],
    "leggings": [
        "................",
        "....OOOOOOOO....",
        "....OTTTTTTO....",
        "....OBBBBBBO....",
        "....OBBOOBBO....",
        "....OHBOOBSO....",
        "....OHBOOBSO....",
        "....OHBOOBSO....",
        "....OHBOOBSO....",
        "....OHBOOBSO....",
        "....OHBOOBSO....",
        "....OBBOOBBO....",
        "....OSSOOSSO....",
        "....OOO..OOO....",
        "................",
        "................"],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "....OOO..OOO....",
        "....OTO..OTO....",
        "....OBO..OBO....",
        "....OHO..OHO....",
        "....OHO..OHO....",
        "....OBO..OBO....",
        "..OOBBO..OBBOO..",
        ".OHBBBO..OBBBHO.",
        ".OSSSSO..OSSSSO.",
        ".OOOOOO..OOOOOO.",
        "................"],
}

# Накладки: что меняется у народа поверх общего силуэта.
OVERLAYS = {
    ("nord", "helmet"): [
        "A..............A",
        "AA............AA",
        ".AA..........AA.",
        "..AA.      .AA..",
        "..AAA      AAA..",
        "                ",
        "                ",
        "                ",
        "                ",
        "   OO......OO   ",
        "   OBO.OO.OBO   ",
        "      .BB.      "],
    ("norman", "helmet"): [
        "       OO       ",
        "      OHBO      ",
        "     OHBBSO     ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "   OO..OO..OO   ",
        "      .OBO.     ",
        "      .OBO.     ",
        "       OO       "],
    ("maya", "helmet"): [
        "   X.A.T.A.X    ",
        "   XX.A.T.AXX   ",
        "  .XXAATTAAXX.  ",
        "  ..XAATTAAX..  ",
        "    .XATTAX.    "],
    ("pony", "helmet"): [
        "       T        ",
        "   OO  T  OO    ",
        "  OBBO T OBBO   ",
        "  OBHBO TOBHBO  ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "   OO......OO   "],
    ("dwarf", "helmet"): [
        "                ",
        "                ",
        "      OTTO      ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "  OBBBBBBBBBSO  ",
        "  OOOOBBBOOOSO  ",
        "  OBBBBBBBBBSO  ",
        "  OTBTBTBTBTSO  ",
        "   OTBTBTBTBO   ",
        "    OOOOOOOO    "],
    ("elf", "helmet"): [
        "                ",
        "       OO       ",
        "      OBHO      ",
        "     .OBBO.     ",
        "......OAAO......",
        "................",
        "................",
        "..OTTTTAATTTTO..",
        "..OTTTTTTTTTTO..",
        "................",
        "................",
        "................",
        "................",
        "................"],
    ("nord", "chestplate"): [
        "                ",
        "  AAAA    AAAA  ",
        " AAAAAA..AAAAAA ",
        " AAAAAAAAAAAAAA "],
    ("norman", "chestplate"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "     AAATAA     ",
        "     AAATAA     ",
        "     TTTTTT     ",
        "     AAATAA     ",
        "     AAATAA     ",
        "     AAATAA     ",
        "     AAATAA     "],
    ("maya", "chestplate"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "    TATATATA    ",
        "     ATATAT     ",
        "      TAAT      "],
    ("pony", "chestplate"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "       TT       ",
        "      TTTT      ",
        "       TT       "],
    ("dwarf", "chestplate"): [
        "                ",
        " TTTT      TTTT ",
        "TBBBBT    TBBBBT",
        "TBBBBT    TBBBBT",
        "                ",
        "                ",
        "       AA       ",
        "      A  A      ",
        "       AA       "],
    ("elf", "chestplate"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "       AA       ",
        "      HBBH      ",
        "     HBBBBH     "],
    ("pony", "leggings"): [
        "                ",
        "                ",
        "                ",
        "     11  11     ",
        "     22  22     ",
        "     33  33     ",
        "     44  44     ",
        "     55  55     ",
        "     66  66     ",
        "     11  11     ",
        "     22  22     "],
    ("yamato", "helmet"): [
        "   T        T   ",
        "    T      T    ",
        "     T    T     ",
        "      TAAT      ",
        "                ",
        "                ",
        "                ",
        "  AAAAAAAAAAAA  ",
        " OBBBBBBBBBBBBO ",
        "OBBO........OBBO",
        "OBO..........OBO"],
    ("yamato", "chestplate"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "    AAAAAAAA    ",
        "                ",
        "    AAATTAAA    ",
        "                ",
        "    AAAAAAAA    ",
        "                ",
        "                ",
        "    AAAAAAAA    ",
        "                ",
        "    AAAAAAAA    "],
    ("yamato", "leggings"): [
        "                ",
        "                ",
        "                ",
        "     AA  AA     ",
        "                ",
        "     AA  AA     ",
        "                ",
        "     AA  AA     "],
    ("yamato", "boots"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "     W    W     ",
        "     W    W     ",
        "     W    W     ",
        "    WW    WW    ",
        "  WWWW    WWWW  ",
        "  TTTT    TTTT  "],
    ("nord", "boots"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "   AAAA  AAAA   ",
        "   AAAA  AAAA   "],
    ("pony", "boots"): [
        "                ",
        "                ",
        "                ",
        "                ",
        "   AAAA  AAAA   ",
        "   AAAA  AAAA   ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        " TTTTTT  TTTTTT "],
}

WEAPON_ART = {
    "longsword": [
        "..............OO",
        ".............OHO",
        "............OHBO",
        "...........OHBO.",
        "..........OHBO..",
        ".........OHBO...",
        "........OHBO....",
        ".......OHBO.....",
        "..OO..OHBO......",
        "..OTOOHBO.......",
        "...OTTBO........",
        "....OTTO........",
        "...OGOOTO.......",
        "..OGO..OTO......",
        ".OTO....OO......",
        ".OO.............",
    ],
    "macuahuitl": [
        "...........O.O..",
        "..........OGOGO.",
        ".........OGGGGO.",
        "........OAGGGGGO",
        ".......OGGGGGGO.",
        "......OAGGGGGO..",
        ".....OGGGGGGO...",
        "....OAGGGGAO....",
        "....OGGGGGO.....",
        "...OOGGGAO......",
        "..OGOOOOO.......",
        ".OGO............",
        "OGO.............",
        "OO..............",
        "................",
        "................",
    ],
    "horseshoe_flail": [
        "........OOOOO...",
        ".......OTTTTTO..",
        "......OTOOOOOTO.",
        "......OTO...OTO.",
        "......OTO...OTO.",
        "......OOO...OOO.",
        ".....O..........",
        "....O.1.........",
        "...O..2.........",
        "..OGO.3.........",
        ".OGO..4.........",
        "OGO...5.........",
        "OO....6.........",
        "................",
        "................",
        "................",
    ],
    "warhammer": [
        "........OOOOOO..",
        ".......OHBBBBSO.",
        "......OHBTTBBSO.",
        "......OHBBBBSSO.",
        ".......OSSSSSO..",
        "........OOGOO...",
        ".......OGO......",
        "......OGO.......",
        ".....OGO........",
        "....OGO.........",
        "...OTO..........",
        "..OGO...........",
        ".OGO............",
        "OTO.............",
        "OO..............",
        "................",
    ],
    "moon_blade": [
        "...........OOO..",
        "..........OHHO..",
        ".........OHBO...",
        "........OHBO....",
        ".......OHBO.....",
        "......OHBO......",
        ".....OHBO.......",
        "....OHBBO.......",
        "..OOHBBO........",
        "..OTAAO.........",
        "...OTTO.........",
        "..OGOOTO........",
        ".OGO..OO........",
        "OTO.............",
        "OO..............",
        "................",
    ],
    "bearded_axe": [
        "................",
        "......OOOO......",
        ".....OHHBBO.....",
        "....OHBBBBSOO...",
        "....OHBBBBOGO...",
        ".....OBBBOGO....",
        "......OBOGO.....",
        ".....OSOGO......",
        "....OSOGSO......",
        "....OSGOSO......",
        "...OGO.OO.......",
        "..OGO...........",
        ".OGO............",
        "OGO.............",
        "OO..............",
        "................",
    ],
}

WEAPON_ART["katana"] = [
    "...............O",
    "..............OH",
    ".............OHO",
    "............OHBO",
    "...........OHBO.",
    "..........OHBO..",
    ".........OHBO...",
    "........OHBO....",
    ".......OHBO.....",
    "......TTBO......",
    ".....TTTT.......",
    "....OATT........",
    "...OAO..........",
    "..OAO...........",
    ".OAO............",
    ".OO.............",
]

WEAPON_PALETTE = {
    "longsword": {"H": "e9eef2", "B": "a9b1b9", "T": "d9b44a", "G": "5a3a22"},
    "macuahuitl": {"G": "8a5a32", "A": "1d1426", "O": "2a1a0c"},
    "horseshoe_flail": {"T": "f2c94c", "G": "7a4a2a"},
    "warhammer": {"H": "9aa4b0", "B": "6a727d", "S": "3f454d", "T": "57e1e6", "G": "5a3a22"},
    "moon_blade": {"H": "f2fbff", "B": "b9d3ea", "A": "a877e0", "T": "d7e4ea", "G": "3d6e35"},
    "bearded_axe": {"H": "d0d5da", "B": "8a8f94", "S": "5a5f64", "G": "6b4a2f"},
    "katana": {"H": "f4f8fb", "B": "b8c2cc", "T": "d9b44a", "A": "8a1f1a", "O": "15121a"},
}


def draw(rows, palette, extra=None):
    for row in list(rows) + list(extra or []):
        assert len(row) == 16, "строка иконки не в 16 клеток: %r" % row
    assert len(rows) == 16 and len(extra or []) <= 16
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    grid = [list(row) for row in rows]
    for y, row in enumerate(extra or []):
        for x, ch in enumerate(row):
            if ch != " ":
                grid[y][x] = ch
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch.isdigit():
                px[x, y] = hexa(RAINBOW[(int(ch) - 1) % len(RAINBOW)])
            else:
                px[x, y] = hexa(palette[ch])
    return image


# --- рецепты --------------------------------------------------------------------
#
# Форма у всех одна, ванильная, а в сердцевине — своё: норманн вшивает
# красное сукно, майя нефрит, пони золото, гном алмаз, эльф аметист,
# северянин мех. Материал основы — тоже свой: у майя стёганая шерсть,
# у пони и эльфа кожа.

MATERIAL = {"norman": ("minecraft:iron_ingot", "minecraft:red_wool"),
            "maya": ("minecraft:white_wool", "minecraft:emerald"),
            "pony": ("minecraft:leather", "minecraft:gold_ingot"),
            "dwarf": ("minecraft:iron_ingot", "minecraft:diamond"),
            "elf": ("minecraft:leather", "minecraft:amethyst_shard"),
            "nord": ("minecraft:iron_ingot", "minecraft:leather"),
            "yamato": ("minecraft:iron_ingot", "minecraft:string")}
SHAPES = {"helmet": ["MXM", "M M"], "chestplate": ["M M", "MXM", "MMM"],
          "leggings": ["MXM", "M M", "M M"], "boots": ["X X", "M M"]}
WEAPON_RECIPES = {
    "longsword": ([" M ", " M ", "MSM"], {"M": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    "macuahuitl": (["OPO", "OPO", " S "], {"O": "minecraft:obsidian",
                                           "P": "minecraft:jungle_planks", "S": "minecraft:stick"}),
    "horseshoe_flail": ([" G ", "GCG", " S "], {"G": "minecraft:gold_ingot",
                                                "C": "minecraft:chain", "S": "minecraft:stick"}),
    "warhammer": (["MMM", "MDM", " S "], {"M": "minecraft:iron_ingot", "D": "minecraft:diamond",
                                          "S": "minecraft:stick"}),
    "moon_blade": ([" A ", " I ", " S "], {"A": "minecraft:amethyst_shard",
                                           "I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    "bearded_axe": (["MMM", "MS ", " S "], {"M": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    "katana": (["  M", " M ", "S  "], {"M": "minecraft:iron_ingot", "S": "minecraft:stick"}),
}


def recipe(pattern, key, result):
    return {"type": "minecraft:crafting_shaped", "category": "equipment",
            "pattern": pattern, "key": {k: {"item": v} for k, v in key.items()},
            "result": {"item": result}}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n",
                    encoding="utf-8", newline="\n")


def main():
    bodies = sets()
    for people in PEOPLES:
        body = cb.Body("geometry.villagepax.armor_" + people, 64, 64, bodies[people],
                       (0, 0, 64, 64), (3, 3.5, 1.5))
        (ASSETS / "geo/armor").mkdir(parents=True, exist_ok=True)
        (ASSETS / "geo/armor" / (people + ".geo.json")).write_text(
            models.compact(models.model_json(body)) + "\n", encoding="utf-8", newline="\n")
        (ASSETS / "textures/armor").mkdir(parents=True, exist_ok=True)
        paint(people, body).save(ASSETS / "textures/armor" / (people + ".png"))

        palette = PALETTES[people]
        for piece in PIECES:
            name = "%s_%s" % (people, piece)
            draw(ICONS[piece], palette, OVERLAYS.get((people, piece))).save(
                ASSETS / "textures/item" / (name + ".png"))
            write_json(ASSETS / "models/item" / (name + ".json"),
                       {"parent": "minecraft:item/generated",
                        "textures": {"layer0": "villagepax:item/" + name}})
            base, core = MATERIAL[people]
            write_json(DATA / "recipes" / (name + ".json"),
                       recipe(SHAPES[piece], {"M": base, "X": core}, "villagepax:" + name))

        weapon = WEAPONS[people]
        name = "%s_%s" % (people, weapon)
        colours = dict(palette)
        colours.update(WEAPON_PALETTE[weapon])
        draw(WEAPON_ART[weapon], colours).save(ASSETS / "textures/item" / (name + ".png"))
        write_json(ASSETS / "models/item" / (name + ".json"),
                   {"parent": "minecraft:item/handheld",
                    "textures": {"layer0": "villagepax:item/" + name}})
        pattern, key = WEAPON_RECIPES[weapon]
        write_json(DATA / "recipes" / (name + ".json"), recipe(pattern, key, "villagepax:" + name))

    # Ваниль всё равно спрашивает текстуру слоя по имени материала, даже
    # когда броню рисует GeckoLib, и сыплет в журнал «файл не найден».
    # Слой прозрачный: видно только объёмную модель.
    layers = ROOT / "src/main/resources/assets/minecraft/textures/models/armor"
    layers.mkdir(parents=True, exist_ok=True)
    for people in PEOPLES:
        for layer in (1, 2):
            Image.new("RGBA", (64, 32), (0, 0, 0, 0)).save(
                layers / ("villagepax_%s_layer_%d.png" % (people, layer)))

    # Движений у брони нет, но GeckoLib спрашивает файл — пусть он будет пуст.
    write_json(ASSETS / "animations/armor/gear.animation.json",
               {"format_version": "1.8.0", "animations": {}})
    print("снаряжение: %d народов, %d предметов" % (len(PEOPLES), len(PEOPLES) * 5))


if __name__ == "__main__":
    main()
