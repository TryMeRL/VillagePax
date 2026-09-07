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
    "B": ("minecraft:dark_oak_log", {"axis": "y"}),
    "H": ("minecraft:dark_oak_log", {"axis": "x"}),
    "Z": ("minecraft:dark_oak_log", {"axis": "z"}),
    "W": ("minecraft:white_terracotta", {}),
    "P": ("minecraft:dark_oak_planks", {}),
    "G": pane(),
    "n": stairs("north"),
    "s": stairs("south"),
    "e": stairs("east"),
    "w": stairs("west"),
    "D": ("villagepax:marker_door", {}),
    "K": ("villagepax:marker_workstation", {}),
    "S": ("villagepax:marker_storage", {}),
    "E": ("villagepax:marker_bed", {}),
    "O": ("villagepax:marker_decor", {}),
}


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

SCHEMATICS = {
    "norman/town_hall_lvl1": NORMAN_TOWN_HALL,
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
