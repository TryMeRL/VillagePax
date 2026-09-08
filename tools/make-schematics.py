#!/usr/bin/env python3
"""
Генерирует схемы зданий Village Pax в ванильном формате structure NBT.

Зачем скрипт, а не постройка в игре: тот же приём, каким сделаны текстуры —
источник правды читается глазами, вывод детерминирован, игра для генерации
не нужна, и чужих лицензий в файле нет. Схему по-прежнему можно перестроить
структурным блоком в игре и подложить файлом: формат ванильный.

    python tools/make-schematics.py

Формат схемы здесь — послойная ASCII-карта. Строка в слое идёт по оси Z,
символ в строке — по оси X, слои снизу вверх. Пробел не используется
намеренно: незаполненное место должно быть видно как символ.

gzip пишется с mtime=0, иначе один и тот же вход давал бы разные байты
и git видел бы изменение схемы там, где её не меняли.
"""

import gzip
import pathlib
import struct

# Прочитано из настоящего сохранения 1.20.1, а не взято из головы.
DATA_VERSION = 3465

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/villagepax/villagepax/schematics"

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


# --- запись NBT ---

def nbt_string(value):
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def nbt_compound(entries):
    """entries: последовательность (тип, имя, полезная нагрузка)."""
    body = b"".join(bytes([tid]) + nbt_string(nm) + payload for tid, nm, payload in entries)
    return body + bytes([TAG_END])


def nbt_int_list(values):
    return (bytes([TAG_INT]) + struct.pack(">i", len(values))
            + b"".join(struct.pack(">i", v) for v in values))


def nbt_compound_list(payloads):
    # У пустого списка тип элемента 0 — так пишет и сама игра.
    element = TAG_COMPOUND if payloads else TAG_END
    return bytes([element]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def palette_entry(block_id, properties):
    entries = [(TAG_STRING, "Name", nbt_string(block_id))]
    if properties:
        entries.append((TAG_COMPOUND, "Properties", nbt_compound(
            [(TAG_STRING, key, nbt_string(value)) for key, value in sorted(properties.items())])))
    return nbt_compound(entries)


def block_entry(pos, state):
    return nbt_compound([
        (TAG_LIST, "pos", nbt_int_list(pos)),
        (TAG_INT, "state", struct.pack(">i", state)),
    ])


def write_schematic(path, size, palette, blocks):
    root = nbt_compound([
        (TAG_INT, "DataVersion", struct.pack(">i", DATA_VERSION)),
        (TAG_LIST, "size", nbt_int_list(size)),
        (TAG_LIST, "palette", nbt_compound_list([palette_entry(*entry) for entry in palette])),
        (TAG_LIST, "blocks", nbt_compound_list([block_entry(pos, state) for pos, state in blocks])),
        (TAG_LIST, "entities", nbt_compound_list([])),
    ])
    payload = bytes([TAG_COMPOUND]) + nbt_string("") + root

    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as handle:
        with gzip.GzipFile(fileobj=handle, mode="wb", mtime=0) as gz:
            gz.write(payload)


# --- палитра символов ---

def stairs(facing):
    return ("minecraft:dark_oak_stairs",
            {"facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false"})


def pane():
    return ("minecraft:glass_pane",
            {"north": "false", "south": "false", "east": "false", "west": "false",
             "waterlogged": "false"})


LEGEND = {
    ".": ("minecraft:air", {}),
    "C": ("minecraft:cobblestone", {}),
    # Обстановка: всё это лежит в теге villagepax:build_decor и вносится
    # последним, когда коробка здания готова.
    "t": ("minecraft:lantern", {"hanging": "false", "waterlogged": "false"}),
    "L": ("minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"}),
    "r": ("minecraft:red_carpet", {}),
    "b": ("minecraft:bookshelf", {}),
    # Кровать занимает две позиции. У facing=south голова лежит на +Z
    # от ног, поэтому "f" ставится перед "h" по оси Z.
    "f": ("minecraft:red_bed", {"facing": "south", "part": "foot", "occupied": "false"}),
    "h": ("minecraft:red_bed", {"facing": "south", "part": "head", "occupied": "false"}),
    "d": ("minecraft:dirt", {}),
    "F": ("minecraft:farmland", {"moisture": "7"}),
    # Вода лежит в теге build_decor, то есть ставится последней. Иначе она
    # растечётся сквозь недостроенную стену и зальёт окрестности.
    "~": ("minecraft:water", {"level": "0"}),
    # Морковь, а не пшеница: житель ест её прямо с грядки, и колония
    # начинает кормить себя сама. Пшенице нужны мельница и пекарь —
    # их ещё нет, и поле пшеницы кормило бы только склад.
    "*": ("minecraft:carrots", {"age": "0"}),
    "q": ("minecraft:oak_fence", {"north": "false", "east": "false", "south": "false",
                                  "west": "false", "waterlogged": "false"}),
    # Калитка ставится СРАЗУ ОТКРЫТОЙ, и это не небрежность. Ванильный
    # поиск пути считает закрытую калитку непроходимой (PathNodeType.FENCE),
    # а открывать калитки умеют только двери у деревенских жителей. С глухой
    # оградой фермер не может войти на своё поле, а лесоруб — в свою рощу:
    # оба стоят снаружи и не делают ничего.
    "g": ("minecraft:oak_fence_gate", {"facing": "east", "open": "true",
                                       "in_wall": "false", "powered": "false"}),
    "y": ("minecraft:oak_sapling", {"stage": "0"}),
    "B": ("minecraft:dark_oak_log", {"axis": "y"}),
    "H": ("minecraft:dark_oak_log", {"axis": "x"}),
    "Z": ("minecraft:dark_oak_log", {"axis": "z"}),
    "W": ("minecraft:white_terracotta", {}),
    # Камень и колокол — приметы второго уровня: издалека видно, что
    # колония поднялась, а не просто обзавелась ещё одним сараем.
    "M": ("minecraft:stone_bricks", {}),
    "J": ("minecraft:bell", {"attachment": "floor", "facing": "north", "powered": "false"}),
    "P": ("minecraft:dark_oak_planks", {}),
    "G": pane(),
    "n": stairs("north"),
    "s": stairs("south"),
    "e": stairs("east"),
    "w": stairs("west"),
    # Очаг в середине дома и приметы хозяйства. Костёр не украшение:
    # его дым идёт вверх по дымоходу и виден с улицы — это та самая
    # «мелкая жизнь», которой заказчику не хватало. Ванильный поиск пути
    # считает горящий костёр опасным, так что житель обходит его сам.
    "c": ("minecraft:campfire", {"facing": "north", "lit": "true",
                                 "signal_fire": "false", "waterlogged": "false"}),
    "A": ("minecraft:hay_block", {"axis": "y"}),
    "p": ("minecraft:carved_pumpkin", {"facing": "south"}),
    "D": ("villagepax:marker_door", {}),
    "K": ("villagepax:marker_workstation", {}),
    "S": ("villagepax:marker_storage", {}),
    "E": ("villagepax:marker_bed", {}),
    "O": ("villagepax:marker_decor", {}),
}


def hip_roof(size, levels):
    """Шатровая крыша: кольцо ступеней с настилом внутри, каждый слой уже на блок.

    Руками эти слои набирать незачем: они отличаются только отступом от края,
    и любая опечатка даёт дырку в крыше, которую видно только в игре. Скат
    смотрит наружу — ступени северного ряда на север, южного на юг, — тем же
    порядком, что и рукописная крыша домика лесоруба.
    """
    layers = []

    for inset in range(levels):
        low, high = inset, size - 1 - inset
        rows = []

        for z in range(size):
            row = []
            for x in range(size):
                if x < low or x > high or z < low or z > high:
                    row.append(".")
                elif z == low:
                    row.append("n")
                elif z == high:
                    row.append("s")
                elif x == low:
                    row.append("w")
                elif x == high:
                    row.append("e")
                else:
                    row.append("P")
            rows.append("".join(row))
        layers.append(rows)

    return layers


def pad(layers, width, rows):
    """Дополнить слои пустыми рядами: крыша квадратная, а след — нет."""
    return [layer + ["." * width] * rows for layer in layers]


def punch(layers, x, z, first):
    """Пробить колонну сквозь слои начиная с `first` — под дымоход.

    Руками это набирать нельзя: колонна проходит через потолок и все
    слои крыши, и пропущенная дырка означает дом, который дымит внутрь.
    """
    for y in range(first, len(layers)):
        row = list(layers[y][z])
        row[x] = "."
        layers[y][z] = "".join(row)


def chimney_stack(width, depth, x, z, levels):
    """Труба над крышей: кольцо камня вокруг пустой колонны."""
    layers = []

    for _ in range(levels):
        rows = []
        for row_z in range(depth):
            row = []
            for row_x in range(width):
                touching = max(abs(row_x - x), abs(row_z - z)) <= 1
                row.append("M" if touching and (row_x, row_z) != (x, z) else ".")
            rows.append("".join(row))
        layers.append(rows)

    return layers


def compile_layers(layers):
    """Послойная карта → размер, палитра, список блоков."""
    height = len(layers)
    depth = len(layers[0])
    width = len(layers[0][0])

    for y, layer in enumerate(layers):
        if len(layer) != depth:
            raise ValueError(f"слой {y}: строк {len(layer)}, ожидалось {depth}")
        for z, row in enumerate(layer):
            if len(row) != width:
                raise ValueError(f"слой {y}, строка {z}: символов {len(row)}, ожидалось {width}")

    palette = []
    index_of = {}
    blocks = []

    for y, layer in enumerate(layers):
        for z, row in enumerate(layer):
            for x, symbol in enumerate(row):
                if symbol not in LEGEND:
                    raise ValueError(f"неизвестный символ {symbol!r} в слое {y}, строке {z}")
                block_id, properties = LEGEND[symbol]
                key = (block_id, tuple(sorted(properties.items())))
                if key not in index_of:
                    index_of[key] = len(palette)
                    palette.append((block_id, properties))
                blocks.append(([x, y, z], index_of[key]))

    return [width, height, depth], palette, blocks


# --- ратуша норманнов, уровень 1 ---

# Фахверк: балки тёмного дуба по белой штукатурке на булыжном цоколе,
# двускатная крыша из ступеней. Вход по центру южной стены (z=0).
# Маркеры внутри: вход, рабочее место старейшины, сундук, гостевая
# кровать и два слота декора.
NORMAN_TOWN_HALL = [
    # y=0 — цоколь
    ["CCCCCCC",
     "CCCCCCC",
     "CCCCCCC",
     "CCCCCCC",
     "CCCCCCC",
     "CCCCCCC",
     "CCCCCCC"],
    # y=1 — первый ряд стен, вход, маркеры и обстановка
    ["BWWDWWB",
     "WO...OW",
     "Wt...tW",
     "B..r.EB",
     "Wb...bW",
     "WK.L.SW",
     "BWWWWWB"],
    # y=2 — второй ряд стен с окнами, проём входа продолжается
    ["BWG.GWB",
     "W.....W",
     "G.....G",
     "B.....B",
     "G.....G",
     "W.....W",
     "BWGWGWB"],
    # y=3 — верхняя обвязка
    ["HHHHHHH",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "HHHHHHH"],
    # y=4 — крыша, нижний ярус: ступени скатом наружу
    ["nnnnnnn",
     "wPPPPPe",
     "wPPPPPe",
     "wPPPPPe",
     "wPPPPPe",
     "wPPPPPe",
     "sssssss"],
    # y=5 — крыша, верхний ярус
    [".......",
     ".......",
     "..PPP..",
     "..PPP..",
     "..PPP..",
     ".......",
     "......."],
]

# Норманнский дом, 5x5x5: две настоящие кровати, вход и слот декора.
#
# Кровати стоят блоками, а не маркерами: кровать занимает две позиции,
# а маркер — одну. Маркер кровати при этом остался в словаре и означает
# место без настоящей кровати — походную подстилку.
NORMAN_HOUSE = [
    # y=0 — цоколь
    ["CCCCC",
     "CCCCC",
     "CCCCC",
     "CCCCC",
     "CCCCC"],
    # y=1 — стены, вход и две кровати
    ["BWDWB",
     "Wf.fW",
     "Wh.hW",
     "W.O.W",
     "BWWWB"],
    # y=2 — второй ряд стен с окнами, проём входа продолжается
    ["BW.WB",
     "W...W",
     "G...G",
     "W...W",
     "BWGWB"],
    # y=3 — верхняя обвязка
    ["HHHHH",
     "Z...Z",
     "Z...Z",
     "Z...Z",
     "HHHHH"],
    # y=4 — крыша
    ["nnnnn",
     "wPPPe",
     "wPPPe",
     "wPPPe",
     "sssss"],
]

# Домик лесоруба, 10x5x5: жильё слева, огороженная роща справа.
#
# Решение заказчика: роща у домика — постоянное хозяйство лесоруба, там он
# валит и сажает обратно. Дикий лес он просто счищает, без посадки.
#
# Саженцы стоят с промежутком: вплотную дубы не вырастут, им нужно место
# под кроной. Над рощей в схеме воздух — деревьям надо куда расти.
NORMAN_LUMBERJACK = [
    # y=0 — булыжный цоколь дома и земля под рощей
    ["CCCCCddddd",
     "CCCCCddddd",
     "CCCCCddddd",
     "CCCCCddddd",
     "CCCCCddddd"],
    # y=1 — стены с рабочим местом и сундуком, забор и четыре саженца
    ["BWDWBqqqqq",
     "WK.SWqy.yq",
     "W...Wq...g",
     "W.O.Wqy.yq",
     "BWWWBqqqqq"],
    # y=2 — второй ряд стен, над рощей пусто
    ["BW.WB.....",
     "W...W.....",
     "G...G.....",
     "W...W.....",
     "BWGWB....."],
    # y=3 — верхняя обвязка
    ["HHHHH.....",
     "Z...Z.....",
     "Z...Z.....",
     "Z...Z.....",
     "HHHHH....."],
    # y=4 — крыша
    ["nnnnn.....",
     "wPPPe.....",
     "wPPPe.....",
     "wPPPe.....",
     "sssss....."],
]

# Ратуша норманнов второго уровня, 9x8x9: камень вместо глины, шатровая
# крыша в четыре ската, колокол в зале, две кровати и два сундука.
#
# Решение заказчика: уровень колонии равен уровню ратуши, а второй уровень
# должен быть «больше и красивее». Поэтому и то и другое: след вырос с 7x7
# до 9x9 и вместимость вдвое, а материал сменился с крашеной глины на камень —
# издалека видно, что колония поднялась.
#
# Растёт от того же угла, что и первый уровень: улучшение не переносит
# здание, а надстраивает его на месте, и игроку не приходится выбирать
# место заново.
NORMAN_TOWN_HALL_2 = [
    # y=0 — каменный фундамент
    ["MMMMMMMMM"] * 9,
    # y=1 — зал: вход, колокол, кровати, сундуки, рабочее место
    ["BMMMDMMMB",
     "MtE...EtM",
     "M.......M",
     "M...r...M",
     "Mb..L..bM",
     "M...r...M",
     "MJ.....KM",
     "MSO...OSM",
     "BMMMMMMMB"],
    # y=2 — второй ряд стен с окнами
    ["BMGMMMGMB",
     "M.......M",
     "G.......G",
     "M.......M",
     "G.......G",
     "M.......M",
     "G.......G",
     "M.......M",
     "BMGMMMGMB"],
    # y=3 — обвязка и настил под крышей
    ["HHHHHHHHH",
     "ZPPPPPPPZ",
     "ZPPPPPPPZ",
     "ZPPPPPPPZ",
     "ZPPPPPPPZ",
     "ZPPPPPPPZ",
     "ZPPPPPPPZ",
     "ZPPPPPPPZ",
     "HHHHHHHHH"],
] + hip_roof(9, 4)

# Ферма норманнов, 7x4x7: земляное основание, грядки с колодцем в середине,
# ограда и место для пугала.
#
# Решение заказчика: фермер работает только на построенной ферме. Грядки,
# вода и ограда приходят из схемы, а не вскапываются жителем где попало.
#
# Основание из земли под всем полем нужно не для красоты: без него вода
# из колодца просто утекла бы вниз. Что сеять, тоже сказано схемой —
# посаженная пшеница и есть указание.
NORMAN_FARM = [
    # y=0 — земляное основание, чтобы вода не ушла вниз
    ["ddddddd",
     "ddddddd",
     "ddddddd",
     "ddddddd",
     "ddddddd",
     "ddddddd",
     "ddddddd"],
    # y=1 — грядки и колодец в середине: он поливает всё поле
    ["ddddddd",
     "dFFFFFd",
     "dFFFFFd",
     "dFF~FFd",
     "dFFFFFd",
     "dFFFFFd",
     "ddddddd"],
    # y=2 — ограда и морковные грядки
    ["qqqqqqq",
     "q*****q",
     "q*****q",
     "g**.**q",
     "q*****q",
     "q*****q",
     "qqqqqqq"],
    # y=3 — место пугала: рабочее место фермера
    [".......",
     ".......",
     ".......",
     "...K...",
     ".......",
     ".......",
     "......."],
]

# --- дом норманнов, уровень 2 ---

# Решение заказчика: второй уровень — «больше и красивее». След вырос
# с 5x5 до 7x7, кроватей стало четыре вместо двух, а в середине комнаты
# встал очаг с дымоходом наружу.
#
# Очаг тут не для тепла: дым из трубы видно с улицы, и деревня перестаёт
# выглядеть макетом. Он же и повод не расширять след ещё сильнее —
# улучшение с 5x5 сразу на 7x9 упиралось бы в соседний дом почти всегда,
# и игрок видел бы один отказ.
#
# Дымоход пробивается кодом, а не руками: колонна проходит через потолок
# и все три слоя крыши, и одна пропущенная дырка — это дом, который дымит
# внутрь. Такое видно только в игре.
def _norman_house_2():
    layers = [
        # y=0 — булыжный цоколь
        ["CCCCCCC"] * 7,
        # y=1 — стены, вход, четыре кровати по стенам и очаг в середине
        ["BWWDWWB",
         "Wf...fW",
         "Wh...hW",
         "W..c..W",
         "Wf...fW",
         "Wh...hW",
         "BWWWWWB"],
        # y=2 — второй ряд стен с окнами, проём входа продолжается
        ["BWG.GWB",
         "W.....W",
         "G.....G",
         "W.....W",
         "G.....G",
         "W.....W",
         "BWGWGWB"],
        # y=3 — обвязка и настил под крышей
        ["HHHHHHH",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "HHHHHHH"],
    ]
    layers += hip_roof(7, 3)
    layers += chimney_stack(7, 7, 3, 3, 2)
    punch(layers, 3, 3, 2)
    return layers


NORMAN_HOUSE_2 = _norman_house_2()

# --- ферма норманнов, уровень 2 ---

# Поле выросло с 5x5 грядок до 7x7, и колодец в середине по-прежнему
# поливает всё: ванильная влажность добирает четыре блока, а от середины
# до дальней грядки их три.
#
# Пугало — соломенный тюк с резной тыквой. От ворон оно в Minecraft не
# спасает и спасать не должно: это примета живого хозяйства, из того же
# ряда, что дым из трубы.
NORMAN_FARM_2 = [
    # y=0 — земляное основание под всем полем, чтобы вода не ушла вниз
    ["ddddddddd"] * 9,
    # y=1 — грядки и колодец в середине
    ["ddddddddd",
     "dFFFFFFFd",
     "dFFFFFFFd",
     "dFFFFFFFd",
     "dFFF~FFFd",
     "dFFFFFFFd",
     "dFFFFFFFd",
     "dFFFFFFFd",
     "ddddddddd"],
    # y=2 — ограда с калиткой, морковные грядки и соломенный тюк пугала
    ["qqqqqqqqq",
     "q*******q",
     "q*A*****q",
     "q*******q",
     "g***.***q",
     "q*******q",
     "q*******q",
     "q*******q",
     "qqqqqqqqq"],
    # y=3 — тыква пугала и место фермера над колодцем
    [".........",
     ".........",
     "..p......",
     ".........",
     "....K....",
     ".........",
     ".........",
     ".........",
     "........."],
]

SCHEMATICS = {
    "norman/town_hall_lvl1": NORMAN_TOWN_HALL,
    "norman/town_hall_lvl2": NORMAN_TOWN_HALL_2,
    "norman/house_lvl1": NORMAN_HOUSE,
    "norman/house_lvl2": NORMAN_HOUSE_2,
    "norman/lumberjack_lvl1": NORMAN_LUMBERJACK,
    "norman/farm_lvl1": NORMAN_FARM,
    "norman/farm_lvl2": NORMAN_FARM_2,
}


# --- обратное чтение, чтобы не выкладывать в репозиторий битый файл ---

def read_back(path):
    raw = gzip.open(path, "rb").read()
    cursor = [0]

    def take(count):
        start = cursor[0]
        cursor[0] += count
        return raw[start:start + count]

    def read_string():
        length = struct.unpack(">H", take(2))[0]
        return take(length).decode("utf-8")

    def read_payload(tid):
        if tid == TAG_INT:
            return struct.unpack(">i", take(4))[0]
        if tid == TAG_STRING:
            return read_string()
        if tid == TAG_LIST:
            element = take(1)[0]
            count = struct.unpack(">i", take(4))[0]
            return [read_payload(element) for _ in range(count)]
        if tid == TAG_COMPOUND:
            result = {}
            while True:
                inner = take(1)[0]
                if inner == TAG_END:
                    return result
                # Имя читается в переменную намеренно: в `d[k()] = v()` Python
                # вычисляет правую часть раньше ключа, и поток байтов разошёлся бы.
                key = read_string()
                result[key] = read_payload(inner)
        raise ValueError(f"чтение тега {tid} не поддержано — оно и не нужно этому формату")

    assert take(1)[0] == TAG_COMPOUND, "корень схемы обязан быть compound"
    read_string()
    return read_payload(TAG_COMPOUND)


def main():
    for name, layers in SCHEMATICS.items():
        size, palette, blocks = compile_layers(layers)
        path = OUT / (name + ".nbt")
        write_schematic(path, size, palette, blocks)

        parsed = read_back(path)
        assert parsed["size"] == size, f"{name}: размер не совпал"
        assert len(parsed["palette"]) == len(palette), f"{name}: палитра не совпала"
        assert len(parsed["blocks"]) == len(blocks), f"{name}: число блоков не совпало"
        assert parsed["DataVersion"] == DATA_VERSION

        markers = sum(1 for _, index in blocks
                      if palette[index][0].startswith("villagepax:marker_"))
        air = sum(1 for _, index in blocks if palette[index][0] == "minecraft:air")

        print(f"{name}: размер {size}, палитра {len(palette)}, "
              f"блоков {len(blocks)} (воздух {air}, маркеров {markers}), "
              f"{path.stat().st_size} байт")


if __name__ == "__main__":
    main()
