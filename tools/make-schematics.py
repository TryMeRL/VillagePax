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
    # Своя стена народа, а не крашеная глина. Фахверк — примета норманнов,
    # и по нему деревню узнают издалека; глина же была заимствованием,
    # которое в любом чужом моде выглядит иначе.
    "W": ("villagepax:timber_frame", {}),
    "X": ("villagepax:plaster", {}),
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
    # Лестница на второй этаж. Без неё верхние кровати недостижимы:
    # житель дойдёт до дома и станет под ними.
    "l": ("minecraft:ladder", {"facing": "south", "waterlogged": "false"}),
    "k": ("minecraft:ladder", {"facing": "west", "waterlogged": "false"}),
    # --- майя ---
    # Стена охрой по извести. Красная не для красоты: норманнская
    # штукатурка кремовая, и известковая стена майя от неё почти
    # не отличалась бы — а народ обязан узнаваться с первого взгляда.
    "R": ("villagepax:ochre_plaster", {}),
    # Резной камень: то же место в архитектуре майя, какое у норманнов
    # занимает фахверк. Ставится в углах и на гребне, а не сплошь.
    "V": ("villagepax:carved_stone", {}),
    # Пальмовая кровля. Ступенчатая пирамида из неё — самая короткая
    # примета майя: силуэт читается издалека и ни с чем не путается.
    "T": ("villagepax:thatch", {}),
    # Дерево джунглей вместо тёмного дуба: обвязка, настил, ограда.
    "i": ("minecraft:jungle_log", {"axis": "x"}),
    "u": ("minecraft:jungle_log", {"axis": "z"}),
    "j": ("minecraft:jungle_log", {"axis": "y"}),
    "a": ("minecraft:jungle_planks", {}),
    "Q": ("minecraft:jungle_fence", {"north": "false", "east": "false", "south": "false",
                                     "west": "false", "waterlogged": "false"}),
    # Калитка, как и у норманнов, ставится сразу открытой: закрытую
    # ванильный поиск пути считает непроходимой, и фермер стоял бы
    # снаружи своего поля.
    "U": ("minecraft:jungle_fence_gate", {"facing": "east", "open": "true",
                                          "in_wall": "false", "powered": "false"}),
    "z": ("minecraft:jungle_sapling", {"stage": "0"}),
    # Лестница, прислонённая к западной стене: у майя лаз на второй
    # этаж идёт по стене, а свободная клетка нашлась только там.
    "x": ("minecraft:ladder", {"facing": "east", "waterlogged": "false"}),
    # Венец трубы: из него и идёт дым. Ставится только на самый верх
    # дымохода — ниже труба остаётся полой, иначе дыму неоткуда взяться.
    "Y": ("villagepax:chimney", {}),
    # Станок и кипа шерсти: приметы ткацкой. Ванильный ткацкий станок
    # здесь ровно к месту — он и в ванили для узора на ткани.
    "@": ("minecraft:loom", {"facing": "north"}),
    "#": ("minecraft:white_wool", {}),
    # Котёл и бочка: приметы мастерской, в которой варят. Котёл пустой —
    # воду в него наливать некому, а полный посреди стройки растёкся бы
    # по недостроенному полу.
    "0": ("minecraft:cauldron", {}),
    "v": ("minecraft:barrel", {"facing": "up", "open": "false"}),
    # --- строительный набор мода: им кроют и им же отделывают ---
    #
    # Своими ступенями и плитами дом отделывается изнутри мода, а не
    # ванильными досками: кровля майя обязана выглядеть соломенной
    # и по краю, а не обрываться кубом.
    "1": ("villagepax:thatch_stairs", {"facing": "north", "half": "bottom",
                                       "shape": "straight", "waterlogged": "false"}),
    "2": ("villagepax:thatch_stairs", {"facing": "south", "half": "bottom",
                                       "shape": "straight", "waterlogged": "false"}),
    "3": ("villagepax:thatch_stairs", {"facing": "east", "half": "bottom",
                                       "shape": "straight", "waterlogged": "false"}),
    "4": ("villagepax:thatch_stairs", {"facing": "west", "half": "bottom",
                                       "shape": "straight", "waterlogged": "false"}),
    "5": ("villagepax:thatch_slab", {"type": "bottom", "waterlogged": "false"}),
    "6": ("villagepax:plaster_slab", {"type": "bottom", "waterlogged": "false"}),
    "7": ("villagepax:timber_frame_slab", {"type": "bottom", "waterlogged": "false"}),
    "8": ("villagepax:ochre_plaster_slab", {"type": "bottom", "waterlogged": "false"}),
    "9": ("villagepax:carved_stone_slab", {"type": "bottom", "waterlogged": "false"}),
    "+": ("villagepax:carved_stone_wall", {"north": "low", "south": "low", "east": "low",
                                           "west": "low", "up": "true",
                                           "waterlogged": "false"}),
    # Факел. Ставится не для красоты (хотя и для неё): внутри здания без
    # очага ночью темно, а темнота в Minecraft — это не настроение,
    # а место рождения мобов. Скелет в мастерской строителя к утру
    # убивает того, кто придёт туда работать.
    #
    # Факел, а НЕ фонарь, и это не выбор вкуса. Фонарь стоит железных
    # самородков, железа у колонии нет и взять его негде — стройка
    # встала бы на последнем блоке и ждала игрока с рудником. Факел
    # колония складывает из палки и угля, а уголь ей и так нужен
    # на очаг в каждом доме.
    #
    # Стоячий пишется руками (на столбах ограды в поле и роще),
    # настенные расставляет генератор. У настенного facing — сторона,
    # КУДА он смотрит, то есть противоположная стене.
    "!": ("minecraft:torch", {}),
    "^": ("minecraft:wall_torch", {"facing": "north"}),
    ",": ("minecraft:wall_torch", {"facing": "south"}),
    ">": ("minecraft:wall_torch", {"facing": "east"}),
    "<": ("minecraft:wall_torch", {"facing": "west"}),
    # Алтарь: единственное место в моде, где говорят с небом. Блок
    # обычный, а не маркер: маркер билдер заменяет воздухом, оставляя
    # точку интереса, а с алтарём разговаривают руками — он обязан
    # остаться стоять.
    "$": ("villagepax:altar", {}),
    # Скамья лицом к алтарю. В храме она не убранство: пустой зал
    # читается складом с красивой крышей, а ряды скамей сразу говорят,
    # зачем сюда приходят.
    #
    # Лицом НА ЮГ, и это не безразлично. Дверь у храма на севере, алтарь
    # в южном конце зала, а у скамьи спинка стоит со стороны facing
    # наоборот: с «north» ряды смотрели бы в дверь, спиной к алтарю.
    # Такую мелочь в игре видно сразу, а в схеме — никогда.
    "=": ("villagepax:bench", {"facing": "south"}),
    # Обстановка жилья. Стол с лавками по обе стороны и полка у стены —
    # то, из-за чего дом перестаёт быть кибиткой. Ставится схемой,
    # а не слотом убранства, нарочно: случайный набор мебели даёт
    # случайную комнату, а жить надо в задуманной.
    ":": ("villagepax:bench", {"facing": "north"}),
    # Кладка гномов. Глубинный сланец, а не камень: он лежит там, где
    # они живут, и по цвету их зал не спутать ни с чьим другим. Резной
    # столб и решётка — всё, чем чертог украшен: гномы режут камень,
    # а не красят его.
    "-": ("minecraft:deepslate_bricks", {}),
    "?": ("minecraft:polished_deepslate", {}),
    "|": ("minecraft:deepslate_tiles", {}),
    "/": ("minecraft:chiseled_deepslate", {}),
    "`": ("minecraft:iron_bars", {"east": "false", "north": "true", "south": "true",
                                  "waterlogged": "false", "west": "false"}),
    # Подвесная лампа: единственный свет под землёй, который не сбивают
    # плечом в узком ходу.
    "'": ("minecraft:lantern", {"hanging": "true", "waterlogged": "false"}),
    # Дерево эльфов. Береза, а не дуб: белый ствол виден в лесу издалека,
    # и деревню в кронах замечают раньше, чем догадываются задрать голову.
    #
    # Листва ставится с persistent=true нарочно: сорванная с дерева, она
    # в ваниле осыпается за считаные минуты, и кровля эльфийского дома
    # исчезла бы сама собой на глазах у игрока.
    "{": ("minecraft:birch_planks", {}),
    "}": ("minecraft:birch_leaves", {"distance": "7", "persistent": "true",
                                     "waterlogged": "false"}),
    "%": ("villagepax:table", {"facing": "north"}),
    ";": ("villagepax:shelf", {"facing": "north"}),
    # --- пони ---
    # Третий народ живёт в саванне и строит из АКАЦИИ: ни тёмного дуба
    # норманнов, ни дерева джунглей майя. Рыжее дерево узнаётся издалека
    # не хуже фахверка и охры.
    #
    # Порода выбрана не по цвету, а по правилу, которому подчиняются
    # и первые два народа: <b>строят из того, что растёт под боком</b>.
    # В саванне растёт акация; поставь им ель — и лесоруб носил бы
    # на склад акацию, а билдер ждал бы ели, которой взять негде.
    # Ровно так однажды встала стройка на фонаре.
    "o": ("minecraft:acacia_log", {"axis": "y"}),
    "m": ("minecraft:acacia_log", {"axis": "x"}),
    "N": ("minecraft:acacia_log", {"axis": "z"}),
    "I": ("minecraft:acacia_planks", {}),
    # Жердь — главная примета народа, который держит лошадей. У пони
    # огорожено всё, включая двор ратуши: изгородь им важнее стены.
    "&": ("minecraft:acacia_fence", {"north": "false", "east": "false", "south": "false",
                                     "west": "false", "waterlogged": "false"}),
    # Калитка, как у обоих соседей, ставится сразу открытой: закрытую
    # ванильный поиск пути считает непроходимой.
    "(": ("minecraft:acacia_fence_gate", {"facing": "east", "open": "true",
                                          "in_wall": "false", "powered": "false"}),
    # Саженец акации в делянке: лесоруб сажает то же, что рубит.
    ")": ("minecraft:acacia_sapling", {"stage": "0"}),
    # Тёс на скаты и конёк.
    #
    # Не сено, хотя сенная кровля пони и шла бы. Сноп стоит девять зёрен,
    # на кровлю их уходит два десятка — почти две сотни пшеницы на один
    # дом, а колония растит морковь. Так уже вставала стройка на фонаре,
    # и второй раз наступать на это незачем: пони кроют тем, что рубят.
    # Сено осталось там, где его немного и оно к месту, — снопом в загоне,
    # в сенях сходни и перед алтарём.
    "[": ("minecraft:acacia_stairs", {"facing": "north", "half": "bottom",
                                      "shape": "straight", "waterlogged": "false"}),
    "]": ("minecraft:acacia_stairs", {"facing": "south", "half": "bottom",
                                      "shape": "straight", "waterlogged": "false"}),
    "_": ("minecraft:acacia_slab", {"type": "bottom", "waterlogged": "false"}),
    # Кирпич трубы. Буквы латиницы кончились — кирпич пишется кириллицей:
    # легенде всё равно, а читается «б» как то, что оно и есть.
    "б": ("minecraft:bricks", {}),
    # --- северяне ---
    # Ель и камень: народ заснеженной тайги строит из того, что растёт
    # у него под боком, — тем же правилом, что акация у пони. Латиница
    # в легенде кончилась, и ель пишется кириллицей.
    "л": ("minecraft:spruce_log", {"axis": "y"}),
    "ж": ("minecraft:spruce_log", {"axis": "x"}),
    "з": ("minecraft:spruce_log", {"axis": "z"}),
    "е": ("minecraft:spruce_planks", {}),
    "ф": ("minecraft:spruce_fence", {"north": "false", "east": "false", "south": "false",
                                     "west": "false", "waterlogged": "false"}),
    # Калитка, как у всех соседей, сразу открыта: закрытую поиск пути
    # считает стеной.
    "в": ("minecraft:spruce_fence_gate", {"facing": "east", "open": "true",
                                          "in_wall": "false", "powered": "false"}),
    "с": ("minecraft:spruce_sapling", {"stage": "0"}),
    "н": ("minecraft:spruce_stairs", {"facing": "north", "half": "bottom",
                                      "shape": "straight", "waterlogged": "false"}),
    "ю": ("minecraft:spruce_stairs", {"facing": "south", "half": "bottom",
                                      "shape": "straight", "waterlogged": "false"}),
    "п": ("minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"}),
    # Синяя постель вместо красной: северянин спит под шерстью цвета фьорда.
    "к": ("minecraft:blue_bed", {"facing": "south", "part": "foot", "occupied": "false"}),
    "г": ("minecraft:blue_bed", {"facing": "south", "part": "head", "occupied": "false"}),
    # Стяги северян: синий, как постель. Висят на стене и ставятся
    # последними, вместе с убранством, — к недостроенной стене не прибить.
    "щ": ("minecraft:blue_wall_banner", {"facing": "south"}),
    "ш": ("minecraft:blue_wall_banner", {"facing": "north"}),
    "э": ("minecraft:blue_wall_banner", {"facing": "east"}),
    "ы": ("minecraft:blue_wall_banner", {"facing": "west"}),
    "ё": ("minecraft:blue_banner", {"rotation": "0"}),
    # Сухая кладка поля и двора тинга.
    "ъ": ("minecraft:cobblestone_wall", {"east": "none", "north": "none", "south": "none",
                                         "west": "none", "up": "true", "waterlogged": "false"}),
    # Ворота в стене, что идёт с запада на восток.
    "ц": ("minecraft:spruce_fence_gate", {"facing": "south", "open": "true",
                                          "in_wall": "false", "powered": "false"}),
    "D": ("villagepax:marker_door", {}),
    "K": ("villagepax:marker_workstation", {}),
    "S": ("villagepax:marker_storage", {}),
    "E": ("villagepax:marker_bed", {}),
    "O": ("villagepax:marker_decor", {}),
}


def hip_roof(size, levels, ridge=None):
    """Шатровая крыша: кольцо ступеней с настилом внутри, каждый слой уже на блок.

    Руками эти слои набирать незачем: они отличаются только отступом от края,
    и любая опечатка даёт дырку в крыше, которую видно только в игре. Скат
    смотрит наружу — ступени северного ряда на север, южного на юг, — тем же
    порядком, что и рукописная крыша домика лесоруба.

    Конёк — плита вдоль хребта поверх настила. Без него крыша кончается
    плоской площадкой, и дом читается коробкой с крышкой; с ним у силуэта
    появляется хребет, по которому дом и узнают издалека. Стоит он одну
    плиту на дом и виден с любого расстояния.
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

    if ridge is not None:
        middle = size // 2
        rows = []
        for z in range(size):
            row = []
            for x in range(size):
                # Хребет идёт по середине от края до края: одна линия,
                # а не крестовина, — иначе это уже не конёк, а второй этаж.
                row.append(ridge if z == middle and levels <= x < size - levels else ".")
            rows.append("".join(row))
        layers.append(rows)

    return layers


def gable(width, depth, levels, ridge=None, body="I", north="[", south="]"):
    """Двускатная кровля: скаты на север и юг, конёк вдоль длинной стороны.

    Третий силуэт мода и третий способ накрыть дом. У норманнов шатёр
    из ступеней, у майя ступенчатая пирамида — обе сходятся в точку.
    У пони кровля сходится в ЛИНИЮ, и этого хватает, чтобы их деревню
    отличали от чужой с того же расстояния, с какого отличают две первые.

    Тело кровли — тёс той же акации, из которой сложен сруб: народ
    кроет тем, что рубит. Край — ступень, а не обрыв куба: ступень даёт
    наклон, и кровля читается кровлей, а не недостроенным этажом.
    """
    layers = []

    for inset in range(levels):
        low, high = inset, depth - 1 - inset
        rows = []
        for z in range(depth):
            if z < low or z > high:
                rows.append("." * width)
            elif z == low:
                rows.append(north * width)
            elif z == high:
                rows.append(south * width)
            else:
                rows.append(body * width)
        layers.append(rows)

    if ridge is not None:
        middle = depth // 2
        layers.append([(ridge * width) if z == middle else ("." * width)
                       for z in range(depth)])

    return layers


def steep_gable(width, depth, rise_north, rise_south, end, ridge, beam=None, window=None):
    """Крутая двускатная кровля: полая, в один ряд ступеней, с фронтонами.

    Жалоба заказчика: «исправь дома». Прежние дома накрывались шатром
    в два яруса — плоским ящиком с крышкой, из-за которого дом читался
    коробкой. Эта кровля поднимается на полширины дома, как у настоящей
    избы или фахверка: скаты идут ступенями до конька, торцы закрыты
    фронтонами — со своей балкой посередине и оконцем под коньком.

    Полая нарочно. Сплошное тело кровли съедало бы полсотни досок на дом,
    которых никто не видит, а полая даёт комнате высокий потолок под
    скатами — дом изнутри становится просторнее, а не только снаружи.

    Ступень северного ската смотрит НА ЮГ: у ванильной ступени высокая
    часть лежит с той стороны, куда она смотрит, а скат обязан расти
    к коньку. Повернуть наоборот — и край кровли торчит гребешком наружу.
    """
    levels = depth // 2
    middle = depth // 2
    layers = []
    for level in range(levels):
        rows = []
        for z in range(depth):
            if z == level:
                rows.append(rise_north * width)
            elif z == depth - 1 - level:
                rows.append(rise_south * width)
            elif level < z < depth - 1 - level:
                inside = "." * (width - 2)
                gable_end = end
                if z == middle and level == 0 and beam is not None:
                    gable_end = beam
                if z == middle and level == 1 and window is not None:
                    gable_end = window
                rows.append(gable_end + inside + gable_end)
            else:
                rows.append("." * width)
        layers.append(rows)
    layers.append([(ridge * width) if z == middle else ("." * width) for z in range(depth)])
    return layers


def with_flue(layers, x, z, through_from, ring_from, stone="б"):
    """Дымоход сквозь полую кровлю: колонна открыта, вокруг — кладка.

    Прежняя труба ставилась кольцом НАД крышей и под крутым скатом
    повисла бы в воздухе. Здесь кладка поднимается вдоль колонны от
    первого же слоя кровли — там, где снаружи воздух, — кирпичом, а не
    камнем: каменная труба на фахверке читалась глыбой, — и выходит над
    коньком венцом, из которого идёт дым.
    """
    layers = [list(layer) for layer in layers]
    for y in range(through_from, len(layers)):
        row = list(layers[y][z])
        row[x] = "."
        layers[y][z] = "".join(row)
    for y in range(ring_from, len(layers)):
        for rz in range(z - 1, z + 2):
            row = list(layers[y][rz])
            for rx in range(x - 1, x + 2):
                if (rx, rz) != (x, z) and 0 <= rx < len(row) and row[rx] == ".":
                    row[rx] = stone
            layers[y][rz] = "".join(row)
    top = []
    for rz in range(len(layers[0])):
        top.append("".join("Y" if max(abs(rx - x), abs(rz - z)) == 1 else "."
                           for rx in range(len(layers[0][0]))))
    return layers + [top]


def pyramid(size, symbol, cap=None):
    """Ступенчатая пирамида: каждый ярус уже предыдущего на блок с каждой стороны.

    Руками эти ярусы набирать незачем — они отличаются только отступом,
    и пропущенный блок даёт дырку в кровле, которую видно только в игре.
    Вершина у храма сменяется резным камнем: это гребень, и по нему
    ратуша майя отличается от жилого дома издалека.
    """
    # Край каждого яруса — СКАТ, а не обрыв.
    #
    # Ступенчатая пирамида из кубов читается лестницей: у неё по краю
    # ровно такие же прямые углы, как у стены, и кровля не отличается
    # от недостроенного этажа. Ступень по кромке даёт наклон, и крыша
    # становится крышей — с одного и того же расстояния видно, где
    # кончается дом и начинается кровля.
    #
    # Скат ставится только у соломы: у неё есть своя ступень. Камень
    # (второй символ этой функции — гребень храма) кроется по-прежнему
    # кубом, и это правильно: у пирамиды майя ступени каменные и есть.
    slopes = {"north": "1", "south": "2", "east": "3", "west": "4"} if symbol == "T" else None

    layers = []
    inset = 0
    while size - 2 * inset >= 1:
        rows = []
        low, high = inset, size - 1 - inset
        for z in range(size):
            row = []
            for x in range(size):
                inside = (low <= x <= high) and (low <= z <= high)
                if not inside:
                    row.append(".")
                elif slopes is None or (low == high):
                    # Вершина в один блок скатом быть не может.
                    row.append(symbol)
                elif z == low:
                    row.append(slopes["north"])
                elif z == high:
                    row.append(slopes["south"])
                elif x == low:
                    row.append(slopes["west"])
                elif x == high:
                    row.append(slopes["east"])
                else:
                    row.append(symbol)
            rows.append("".join(row))
        layers.append(rows)
        inset += 1

    if cap is not None:
        middle = size // 2
        top = ["." * size for _ in range(size)]
        row = list(top[middle])
        row[middle] = cap
        top[middle] = "".join(row)
        layers.append(top)

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

    for level in range(levels):
        top = level == levels - 1
        rows = []
        for row_z in range(depth):
            row = []
            for row_x in range(width):
                touching = max(abs(row_x - x), abs(row_z - z)) <= 1
                if (row_x, row_z) == (x, z):
                    # Устье остаётся открытым сверху донизу: дыму надо
                    # куда-то идти, и проверка дома это стережёт.
                    row.append(".")
                elif touching:
                    # Венец — верхний ряд кольца: он закопчён и дымит.
                    row.append("Y" if top else "M")
                else:
                    row.append(".")
            rows.append("".join(row))
        layers.append(rows)

    return layers


# Стороны света в осях схемы: север — к z=0, как у ванили.
SIDES = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}

# Что считается стеной, к которой прислоняют полку: несущее, а не убранство.
WALLS = set("WXBHZPMCRVTaiuj{}-?|/omNIG+" "лжзеб")


def orient(layers, x, y, z, block_id, properties):
    """Мебель, повёрнутая по комнате, а не по одной букве легенды.

    Жалоба заказчика: «мебель одной лишь стороной». Так и было: у стола
    обе лавки сидели к нему спиной — северная смотрела на север, южная
    на юг, — потому что лавка в легенде одна на всю схему и повёрнута
    одинаково, где бы ни стояла. Теперь поворот выводится из соседей:
    лавка у стола смотрит на стол, полка стоит спиной к стене. Схема
    говорит «здесь лавка», а куда ей смотреть, решает комната.
    """
    def at(dx, dz):
        row_z, col_x = z + dz, x + dx
        layer = layers[y]
        if 0 <= row_z < len(layer) and 0 <= col_x < len(layer[row_z]):
            return layer[row_z][col_x]
        return "."

    if block_id == "villagepax:bench":
        for side, (dx, dz) in SIDES.items():
            if at(dx, dz) == "%":
                return {**properties, "facing": side}
    if block_id == "villagepax:shelf":
        for side, (dx, dz) in SIDES.items():
            ox, oz = SIDES[OPPOSITE[side]]
            if at(dx, dz) in WALLS and at(ox, oz) not in WALLS:
                # У полки facing — куда смотрит лицо, а спина — к стене.
                return {**properties, "facing": OPPOSITE[side]}
    return properties


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
                properties = orient(layers, x, y, z, block_id, properties)
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
    # y=2 — второй ряд стен: штукатурка между брусом, окна на месте.
    #
    # Штукатурка, а не фахверк: снизу дом тяжёлый и тёмный от балок,
    # сверху светлый — так половинчатый брус и выглядит в действительности.
    ["BXG.GXB",
     "X.....X",
     "G.....G",
     "B.....B",
     "G.....G",
     "X.....X",
     "BXGWGXB"],
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

# --- норманнский дом, уровень 1: 7x7 ---
#
# Перестроен целиком по жалобе заказчика: «неуютные кибитки, ещё и зайти
# нельзя из-за скамьи».
#
# Обе половины жалобы были правдой. След 5x5 давал девять клеток пола,
# из которых четыре занимали кровати, одну очаг и одну слот убранства:
# на жизнь оставалось три клетки, то есть коридор. А у майя и пони слот
# убранства стоял <b>прямо за дверью</b> — и когда в него вставала скамья
# из набора культуры, в дом было не войти буквально.
#
# Новый след 7x7 даёт двадцать пять клеток, и устроены они по одному
# правилу: <b>средний столбец свободен от двери до очага</b>. В него
# не ставится ничего и никогда — ни мебель схемы, ни слот убранства,
# — поэтому загородить вход нечем в принципе.
#
# Слева спальня в две кровати, справа стол с лавками по обе стороны,
# полка у стены и сундук. Очаг у дальней стены, в конце прохода:
# видно от двери, а ходить сквозь него не приходится.
NORMAN_HOUSE = [
    # y=0 — цоколь
    ["CCCCCCC"] * 7,
    # y=1 — жильё: слева кровати, справа стол, посередине проход
    ["BWWDWWB",
     "W.....W",
     "Wff.:SW",
     "Whh.%;W",
     "W.O.=.W",
     "WO.c.OW",
     "BWWWWWB"],
    # y=2 — второй ряд стен: штукатурка между брусом, окна на три стороны
    ["BXG.GXB",
     "X.....X",
     "G.....G",
     "B.....B",
     "G.....G",
     "X.....X",
     "BXGWGXB"],
    # y=3 — верхняя обвязка
    ["HHHHHHH",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "HHHHHHH"],
] + steep_gable(7, 7, "s", "n", "X", "7", beam="B", window="G")

# Домик лесоруба, 10x5x5: жильё слева, огороженная роща справа.
#
# Решение заказчика: роща у домика — постоянное хозяйство лесоруба, там он
# валит и сажает обратно. Дикий лес он просто счищает, без посадки.
#
# Саженцы стоят с промежутком: вплотную дубы не вырастут, им нужно место
# под кроной. Над рощей в схеме воздух — деревьям надо куда расти.
#
# В середине рощи — слот декора под открытым небом. Он нужен затем, что
# заказчик просил «бельё во дворе», а все прочие слоты стоят внутри
# зданий: во дворе им места не было.
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
     "W...Wq.O.g",
     "W.O.Wqy.yq",
     "BWWWBqqqqq"],
    # y=2 — второй ряд стен, над рощей пусто, факелы на углах ограды
    #
    # Роща — такой же рабочий двор, как поле, и темнота в ней стоит
    # того же: лесоруб выходит к стволам на рассвете. Свет по углам
    # заодно не даёт мобам встать прямо между саженцами.
    ["BX.XB!...!",
     "X...X.....",
     "G...G.....",
     "X...X.....",
     "BXGWB!...!"],
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
# --- вторые уровни: надстраиваются, а не строятся заново ---

# Игрок сказал прямо: улучшение должно делать здание лучше, а не ломать
# и создавать новое. Отсюда правило, которое теперь держат все уровни:
#
#   СЛЕД НЕ МЕНЯЕТСЯ, А ПЕРВЫЙ ЭТАЖ СОХРАНЯЕТСЯ.
#
# Второй уровень — это тот же дом с надстроенным этажом: цоколь, стены
# первого этажа, вход и обстановка стоят на тех же местах теми же блоками.
# Билдер по плану пропускает всё, что уже стоит, поэтому улучшение
# ставит только новое: пол второго этажа, его стены, лестницу и крышу.
# Меняется одна крыша — её и правда приходится снять, чтобы надстроить.
#
# У этого правила есть и вторая выгода, не косметическая: след не растёт,
# значит улучшение никогда не упрётся в соседний дом. Прежние вторые
# уровни росли на два блока в каждую сторону, и в плотной деревне игрок
# видел один отказ «некуда расти».

def storey(base, floor, walls, roof_levels, flue=None, stack=2, extra=(), roof=None):
    """Тот же след, надстроенный этажом: низ как был, сверху новое.

    base   — слои первого уровня, из которых берётся всё до крыши
    floor  — слой пола второго этажа (он же потолок первого)
    walls  — слои стен второго этажа
    """
    size = len(base[0][0])
    layers = [list(layer) for layer in base[:3]] + [list(floor)] + [list(w) for w in walls]

    # Дописывание в базовые слои: только туда, где у первого уровня воздух.
    # Так лестница появляется вторым уровнем, а первый остаётся как был —
    # правило «улучшение достраивает» не нарушено, и проверка это видит.
    for y, x, z, symbol in extra:
        if layers[y][z][x] != ".":
            raise ValueError(f"дописывание в занятую клетку {x},{y},{z}: "
                             f"там {layers[y][z][x]!r}, а не воздух")
        row = list(layers[y][z])
        row[x] = symbol
        layers[y][z] = "".join(row)
    # Крыша своя у каждого народа: норманнам шатёр из ступеней, майя
    # ступенчатая пирамида. Поэтому её можно передать готовой.
    layers += [list(layer) for layer in
               (roof if roof is not None else hip_roof(size, roof_levels))]

    if flue is not None:
        punch(layers, flue[0], flue[1], 3)
        layers += chimney_stack(size, size, flue[0], flue[1], stack)
    return layers


NORMAN_TOWN_HALL_2 = storey(
    NORMAN_TOWN_HALL,
    # y=3 — пол второго этажа: обвязка первого зашивается настилом
    ["HHHHHHH",
     "ZlPPPPZ",
     "ZPPPPPZ",
     "ZPPPPPZ",
     "ZPPPPPZ",
     "ZPPPPPZ",
     "HHHHHHH"],
    [
        # y=4 — верхний зал: колокол, две кровати, сундуки, светильники
        ["BMMMMMB",
         "Mfl.MfM",
         "Mh.J.hM",
         "M..r..M",
         "MS...SM",
         "Mt...tM",
         "BMMMMMB"],
        # y=5 — второй ряд верхних стен с окнами
        ["BMGMGMB",
         "M.....M",
         "G.....G",
         "M.....M",
         "G.....G",
         "M.....M",
         "BMGMGMB"],
        # y=6 — обвязка под крышей
        ["HHHHHHH",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "HHHHHHH"],
    ],
    3)

# Дом второго уровня: тот же след 5x5, надстроенная горница и труба.
#
# Наверху ещё две кровати — четыре на дом вместо двух, — а лестница
# в дальнем углу: она ставится там, где у первого уровня воздух, поэтому
# первый этаж не меняется ни на блок.
#
# Очаг стоит уже на первом уровне, а труба открывается вторым: дым идёт
# сквозь пол горницы наружу. Дырка в середине верхнего пола — не
# небрежность, а как раз то, как в таких домах и жили.
NORMAN_HOUSE_2 = storey(
    NORMAN_HOUSE,
    # y=3 — пол горницы; лаз у западной стены, дымоход над очагом
    ["HHHHHHH",
     "ZPPPPPZ",
     "ZPPPPPZ",
     "ZPPPPPZ",
     "ZxPPPPZ",
     "ZPPPPPZ",
     "HHHHHHH"],
    [
        # y=4 — горница: две кровати, стол у окна и слот убранства.
        # Клетка над очагом оставлена пустой: там проходит дымоход.
        ["BWWWWWB",
         "W.....W",
         "Wff.%.W",
         "Whh.=.W",
         "W...O.W",
         "W.....W",
         "BWWWWWB"],
        # y=5 — второй ряд стен горницы
        ["BXG.GXB",
         "X.....X",
         "G.....G",
         "B.....B",
         "G.....G",
         "X.....X",
         "BXGWGXB"],
        # y=6 — обвязка и настил под крышей
        ["HHHHHHH",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "ZPPPPPZ",
         "HHHHHHH"],
    ],
    0,
    # Лестница снизу доверху: там, где у первого уровня воздух.
    # У западной стены, в углу за кроватями, — единственное место,
    # где она не встаёт ни на проходе, ни поверх мебели.
    extra=((1, 1, 4, "x"), (2, 1, 4, "x")),
    roof=steep_gable(7, 7, "s", "n", "X", "7", beam="B", window="G"))
# Дым очага идёт сквозь пол горницы и полую кровлю: колонна над костром
# открыта с потолка первого этажа, кладка трубы — от первого слоя кровли.
NORMAN_HOUSE_2 = with_flue(NORMAN_HOUSE_2, 3, 5, 3, 7)

# --- ферма норманнов, уровень 1 ---

# Земляное основание, грядки с колодцем в середине, ограда и место
# для пугала.
#
# Решение заказчика: фермер работает только на построенной ферме. Грядки,
# вода и ограда приходят из схемы, а не вскапываются жителем где попало.
#
# Основание из земли под всем полем нужно не для красоты: без него вода
# из колодца просто утекла бы вниз. Что сеять, тоже сказано схемой —
# посаженная морковь и есть указание.
NORMAN_FARM = [
    # y=0 — земляное основание, чтобы вода не ушла вниз
    ["ddddddd"] * 7,
    # y=1 — грядки и колодец в середине: он поливает всё поле
    ["ddddddd",
     "dFFFFFd",
     "dFFFFFd",
     "dFF~FFd",
     "dFFFFFd",
     "dFFFFFd",
     "ddddddd"],
    # y=2 — ограда с калиткой, морковные грядки и тюк пугала
    #
    # Пугало стоит уже на первом уровне, а не приходит со вторым.
    # Поле без него — просто вскопанная земля за забором; с ним видно,
    # что земля чья-то и за ней ходят. От ворон оно в Minecraft
    # не спасает и спасать не должно: это примета живого хозяйства,
    # а не механика.
    ["qqqqqqq",
     "q*****q",
     "q*****q",
     "g**.**q",
     "q*****q",
     "qA****q",
     "qqqqqqq"],
    # y=3 — рабочее место фермера и факелы по углам ограды
    # Факелы на угловых столбах: под крышей их вешать не на что, а без
    # света поле к ночи зарастает мобами. Зомби на грядке — это не
    # неудобство: фермер выходит работать на рассвете и встречает его
    # первым. Четыре угла освещают весь след целиком.
    ["!.....!",
     ".......",
     ".......",
     "...K...",
     ".......",
     ".p.....",
     "!.....!"],
]

# --- ферма норманнов, уровень 2 ---

# Поле растёт в две стороны — на восток и на юг, — а якорь остаётся тем же.
# Так улучшение остаётся надстройкой: старые грядки, колодец, западная
# и северная ограда и калитка стоят на своих местах теми же блоками.
# Переделывается только та ограда, которая оказалась посреди поля: её
# билдер снимает и на её месте сеет. Это и есть улучшение поля —
# двадцать три грядки становятся сорока шестью.
#
# Второй колодец нужен по делу: ванильная влажность добирает четыре
# блока, и без него дальний угол поля сох бы.
#
# Пугало — соломенный тюк с резной тыквой. От ворон оно в Minecraft
# не спасает и спасать не должно: это примета живого хозяйства.
NORMAN_FARM_2 = [
    # y=0 — земляное основание под всем полем
    ["ddddddddd"] * 9,
    # y=1 — грядки и два колодца
    ["ddddddddd",
     "dFFFFFFFd",
     "dFFFFFFFd",
     "dFF~FFFFd",
     "dFFFFFFFd",
     "dFFFFFFFd",
     "dFFFF~FFd",
     "dFFFFFFFd",
     "ddddddddd"],
    # y=2 — ограда с калиткой, морковь и тюк пугала
    ["qqqqqqqqq",
     "q*******q",
     "q*******q",
     "g**.****q",
     "q*******q",
     "qA******q",
     "q****.**q",
     "q*******q",
     "qqqqqqqqq"],
    # y=3 — тыква пугала, место фермера и факелы по углам ограды
    #
    # Пугало осталось ровно там, где стояло на первом уровне: улучшение
    # надстраивает поле, а не переставляет на нём вещи. Игрок, уходивший
    # с поля вчера, должен узнать его сегодня.
    ["!.......!",
     ".........",
     ".........",
     "...K.....",
     ".........",
     ".p.......",
     ".........",
     ".........",
     "!.......!"],
]



# ============================ МАЙЯ ============================
#
# Второй народ. Взято из плана фазы 0.2, и выбраны именно майя: их черта
# `terrace_farming` работает на том, что в моде уже есть, а гномам нужна
# подземная генерация — это отдельная фаза.
#
# Силуэт другой во всём. У норманнов двускатная крыша из ступеней,
# коричневые балки и кремовая штукатурка; у майя ступенчатая пирамида
# пальмовой кровли, красные охряные стены и резной камень в углах.
# Дерево — джунглевое, ограда джунглевая, поле террасное. Спутать
# деревню одного народа с деревней другого нельзя даже издалека.

# --- ратуша майя, уровень 1 ---
#
# Каменная подошва, красные стены с резными углами, обвязка из бревна
# джунглей и кровля в четыре яруса с резным гребнем на вершине.
# Вход по центру южной стены, как у всех: игрок не должен искать дверь.
MAYA_TOWN_HALL = [
    # y=0 — подошва из тёсаного камня: платформа, на которой стоит храм
    ["MMMMMMM"] * 7,
    # y=1 — стены, вход, обстановка и маркеры
    ["VRRDRRV",
     "R.O.O.R",
     "Rt...tR",
     "V..r.EV",
     "R.....R",
     "RK.L.SR",
     "VRRRRRV"],
    # y=2 — второй ряд стен с узкими проёмами
    #
    # Проёмы в один блок, а не окна: стекла у майя не было. Мимоходом
    # это ещё и безопасно — в дыру высотой в один блок мобы не пролезают.
    ["VR.R.RV",
     "R.....R",
     "......R",
     "R.....R",
     "R......",
     "R.....R",
     "VR.R.RV"],
    # y=3 — обвязка из бревна джунглей
    ["iiiiiii",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "iiiiiii"],
] + pyramid(7, "T", cap="V")

# --- дом майя, уровень 1 ---
#
# Тот же приём в малом: подошва, красные стены, пирамида кровли.
# Две настоящие кровати — как и у норманнов, кровать занимает две клетки.
# --- дом майя, уровень 1: 7x7 ---
#
# Тот же план, что и у норманнов, и это нарочно: народы отличаются
# кладкой и кровлей, а не тем, насколько в их домах тесно. Средний
# столбец свободен от двери до очага, слева спальня, справа стол
# с лавками, полка и сундук.
#
# Именно у майя слот убранства стоял раньше <b>прямо за дверью</b>:
# войти в дом, которому выпала скамья, было буквально нельзя.
MAYA_HOUSE = [
    # y=0 — каменная подошва
    ["MMMMMMM"] * 7,
    # y=1 — жильё: слева кровати, справа стол, посередине проход
    ["VRRDRRV",
     "R.....R",
     "Rff.:SR",
     "Rhh.%;R",
     "R.O.=.R",
     "RO.c.OR",
     "VRRRRRV"],
    # y=2 — второй ряд стен с проёмами
    ["VRG.GRV",
     "R.....R",
     "G.....G",
     "V.....V",
     "G.....G",
     "R.....R",
     "VRGRGRV"],
    # y=3 — обвязка из дерева джунглей
    ["iiiiiii",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "iiiiiii"],
] + pyramid(7, "T")

# --- домик лесоруба майя, 10x5 ---
#
# Устроен как норманнский: жильё слева, огороженная роща справа. Саженцы
# джунглевые и стоят с промежутком — вплотную дерево не вырастет.
MAYA_LUMBERJACK = [
    # y=0 — подошва дома и земля под рощей
    ["MMMMMddddd"] * 5,
    # y=1 — стены с рабочим местом и сундуком, ограда и четыре саженца
    ["VRDRVQQQQQ",
     "RK.SRQz.zQ",
     "R...RQ.O.U",
     "R.O.RQz.zQ",
     "VRRRVQQQQQ"],
    # y=2 — второй ряд стен, над рощей пусто, факелы на углах ограды
    ["VR.RV!...!",
     "R...R.....",
     "....R.....",
     "R...R.....",
     "VR.RV!...!"],
    # y=3 — обвязка
    ["iiiii.....",
     "u...u.....",
     "u...u.....",
     "u...u.....",
     "iiiii....."],
    # y=4 — первый ярус кровли
    ["TTTTT.....",
     "TTTTT.....",
     "TTTTT.....",
     "TTTTT.....",
     "TTTTT....."],
    # y=5 — второй ярус
    ["..........",
     ".TTT......",
     ".TTT......",
     ".TTT......",
     ".........."],
    # y=6 — вершина
    ["..........",
     "..........",
     "..T.......",
     "..........",
     ".........."],
]

# --- ратуша майя, уровень 2 ---
#
# Тот же след 7x7, надстроенный залом: улучшение достраивает, а не
# переносит. Наверх ведёт лестница по западной стене, кровля стала выше
# на ярус, и резной гребень поднялся вместе с ней — издалека видно,
# что деревня выросла.
MAYA_TOWN_HALL_2 = storey(
    MAYA_TOWN_HALL,
    # y=3 — пол второго этажа: обвязка первого зашивается настилом,
    # в западном ряду оставлен лаз с лестницей
    ["iiiiiii",
     "uaaaaau",
     "uaaaaau",
     "uaaaaau",
     "uxaaaau",
     "uaaaaau",
     "iiiiiii"],
    [
        # y=4 — верхний зал: две кровати, два сундука, слот декора
        ["VRRRRRV",
         "Rf...fR",
         "Rh.O.hR",
         "V.....V",
         "R.....R",
         "RS...SR",
         "VRRRRRV"],
        # y=5 — второй ряд стен зала
        ["VR.R.RV",
         "R.....R",
         "......R",
         "R.....R",
         "R......",
         "R.....R",
         "VR.R.RV"],
        # y=6 — обвязка и настил под кровлей
        ["iiiiiii",
         "uaaaaau",
         "uaaaaau",
         "uaaaaau",
         "uaaaaau",
         "uaaaaau",
         "iiiiiii"],
    ],
    0,
    # Лестница снизу доверху — там, где у первого уровня воздух.
    # Прислонена к западной стене: свободная клетка у стены нашлась
    # только там, а ванильная лестница без стены не держится.
    extra=((1, 1, 4, "x"), (2, 1, 4, "x")),
    roof=pyramid(7, "T", cap="V"))

# --- ратуша норманнов, уровень 3 ---
#
# Ступень «Город» была недостижима: ратуши выше второго уровня в моде
# не существовало, и лестница ступеней, ради которой всё затевалось,
# упиралась в стену на второй же ступени.
#
# Третий уровень строится <b>из второго целиком</b> — в прямом смысле:
# берутся его слои до кровли, и поверх ставится зал совета. Правило
# «улучшение достраивает, а не перестраивает» тут соблюдено не проверкой,
# а самим способом сборки: переделать нечего, потому что низ — тот же
# список слоёв.
#
# Лаз идёт тем же столбцом, что и с первого этажа на второй: лестница
# продолжается, а в перекрытии пробивается проём.
def third_storey(base, keep, walls, roof, ladders=()):
    """Надстроить третий ярус: низ как был, сверху зал и кровля."""
    layers = [list(layer) for layer in base[:keep]]
    for y, x, z, symbol in ladders:
        row = list(layers[y][z])
        # Точка — это проём: перекрытие второго яруса пробивается нарочно,
        # иначе на третий не подняться. Всё прочее ставится только в воздух:
        # надстройка не имеет права затирать то, что уже стоит.
        if symbol != "." and row[x] != ".":
            raise ValueError(f"лаз третьего яруса упёрся в {row[x]!r} на {x},{y},{z}")
        row[x] = symbol
        layers[y][z] = "".join(row)
    return layers + [list(layer) for layer in walls] + [list(layer) for layer in roof]


NORMAN_TOWN_HALL_3 = third_storey(
    NORMAN_TOWN_HALL_2,
    7,
    [
        # y=7 — зал совета: книги, налой, сундуки и два слота убранства
        ["BMMMMMB",
         "MOl..OM",
         "M.....M",
         "Mb.r.bM",
         "M.....M",
         "MS.L.SM",
         "BMMMMMB"],
        # y=8 — обвязка под кровлей с окнами по сторонам света
        ["HHHHHHH",
         "ZPPPPPZ",
         "GPPPPPG",
         "ZPPPPPZ",
         "GPPPPPG",
         "ZPPPPPZ",
         "HHHHHHH"],
    ],
    # Два яруса кровли, а не три, и зал в один ряд стен вместо двух.
    # Причина не в красоте: проверка «каждая схема строится со стоячих
    # мест» показала, что до верха тринадцатиметровой ратуши билдеру
    # не дотянуться, а в сундук ратуши не влезают материалы на неё.
    # Здание, которое нельзя построить, — не здание.
    hip_roof(7, 2),
    # Лестница продолжается тем же столбцом, перекрытие пробивается.
    ladders=((5, 2, 1, "l"), (6, 2, 1, ".")))


# --- ратуша майя, уровень 3 ---
#
# Тот же приём и тот же смысл: у майя третий ярус — ещё одна ступень
# пирамиды. Храм растёт вверх, а не вширь, и это ровно то, чем их
# постройки и отличаются от норманнских.
MAYA_TOWN_HALL_3 = third_storey(
    MAYA_TOWN_HALL_2,
    7,
    [
        # y=7 — святилище: жаровня, сундуки, резной камень по углам
        ["VRRRRRV",
         "RO...OR",
         "R.....R",
         "R..c..R",
         "R.....R",
         "RS...SR",
         "VRRRRRV"],
        # y=8 — настил под кровлей с проёмами по сторонам
        ["iiiiiii",
         "uaaaaau",
         ".aaaaa.",
         "uaaaaau",
         ".aaaaa.",
         "uaaaaau",
         "iiiiiii"],
    ],
    # Пирамида в два яруса: до вершины пятнадцатиметрового храма
    # билдеру не дотянуться — это показала проверка стоячих мест.
    pyramid(7, "T", cap="V")[:2],
    ladders=((5, 1, 4, "x"), (6, 1, 4, ".")))


# --- дом майя, уровень 2 ---
MAYA_HOUSE_2 = storey(
    MAYA_HOUSE,
    # y=3 — пол горницы с лазом у западной стены
    ["iiiiiii",
     "uaaaaau",
     "uaaaaau",
     "uaaaaau",
     "uxaaaau",
     "uaaaaau",
     "iiiiiii"],
    [
        # y=4 — горница: ещё две кровати, стол и слот убранства
        ["VRRRRRV",
         "R.....R",
         "Rff.%.R",
         "Rhh.=.R",
         "R...O.R",
         "R.....R",
         "VRRRRRV"],
        # y=5 — второй ряд стен горницы
        ["VRG.GRV",
         "R.....R",
         "G.....G",
         "V.....V",
         "G.....G",
         "R.....R",
         "VRGRGRV"],
    ],
    0,
    extra=((1, 1, 4, "x"), (2, 1, 4, "x")),
    roof=pyramid(7, "T"))

# --- террасная ферма майя, уровень 1 ---
#
# Вот ради чего у народа черта `terrace_farming`: поле идёт двумя
# уровнями, и подпорная стена между ними видна с первого взгляда.
# Нижняя терраса на юге, верхняя на севере, у каждой свой колодец —
# ванильная влажность добирает четыре блока, и без второго дальний
# край сох бы.
#
# Место фермера — наверху подпорной стены: с него видно оба поля.
MAYA_FARM = [
    # y=0 — земляная подошва под всем полем, чтобы вода не ушла вниз
    ["ddddddd"] * 7,
    # y=1 — грядки нижней террасы и тело верхней
    ["ddddddd",
     "dFFFFFd",
     "dFF~FFd",
     "ddddddd",
     "ddddddd",
     "ddddddd",
     "ddddddd"],
    # y=2 — ограда и морковь нижней террасы, подпорная стена, грядки верхней
    ["QQQQQQQ",
     "U*****Q",
     "Q**.**Q",
     "MMMMMMM",
     "dFF~FFd",
     "dFVFFFd",
     "ddddddd"],
    # y=3 — морковь верхней террасы, её ограда, место фермера на стене
    # и факелы на углах нижней ограды
    # Факелы на угловых столбах: под крышей их вешать не на что, а без
    # света поле к ночи зарастает мобами. Зомби на грядке — это не
    # неудобство: фермер выходит работать на рассвете и встречает его
    # первым. Четыре угла освещают весь след целиком.
    ["!.....!",
     ".......",
     "!.....!",
     "...K...",
     "Q**.**Q",
     "Q*V***Q",
     "QQQQQQQ"],
    # y=4 — факелы на углах верхней ограды: террасы две, и тёмной
    # не должна остаться ни одна
    [".......",
     ".......",
     ".......",
     ".......",
     "!.....!",
     ".......",
     "!.....!"],
]

# --- террасная ферма майя, уровень 2 ---
#
# Поле растёт на восток и на юг, якорь остаётся тем же — правило то же,
# что у норманнов. Террасы, колодцы, западная ограда и калитка стоят
# на своих местах; переделывается только та ограда, что оказалась
# посреди поля.
#
# В поле встаёт стела резного камня в два блока: у норманнов на этом
# месте соломенное пугало, а у майя — знак, что земля чья-то.
MAYA_FARM_2 = [
    # y=0 — подошва
    ["ddddddddd"] * 9,
    # y=1 — грядки нижней террасы и тело верхней
    ["ddddddddd",
     "dFFFFFFFd",
     "dFF~FFFFd",
     "ddddddddd",
     "ddddddddd",
     "ddddddddd",
     "ddddddddd",
     "ddddddddd",
     "ddddddddd"],
    # y=2 — нижняя терраса, подпорная стена, грядки верхней и второй колодец
    ["QQQQQQQQQ",
     "U*******Q",
     "Q**.****Q",
     "MMMMMMMMM",
     "dFF~FFFFd",
     "dFVFFFFFd",
     "dFFFFFFFd",
     "dFFFF~FFd",
     "ddddddddd"],
    # y=3 — морковь верхней террасы, стела, ограда, место фермера
    # и факелы на углах нижней ограды
    ["!.......!",
     ".........",
     "!.......!",
     "...K.....",
     "Q**.****Q",
     "Q*V*****Q",
     "Q*******Q",
     "Q****.**Q",
     "QQQQQQQQQ"],
    # y=4 — факелы на углах верхней ограды
    [".........",
     ".........",
     ".........",
     ".........",
     "!.......!",
     ".........",
     ".........",
     ".........",
     "!.......!"],
]



# --- склад норманнов, 7x5 ---
#
# Оба этих здания — склад и мастерская строителя — были объявлены
# в культуре с первого дня и не имели схемы: тип есть, а построить нельзя.
# Деревня их молча пропускала, а игрок не находил в списке заказов.
#
# Склад — это четыре сундука под одной крышей. Больше в нём ничего и
# не нужно: он нужен затем, чтобы вынесенная за околицу стройка
# перестала зависеть от ратуши, и курьеру было куда носить.
# --- пивоварня норманнов, 5x5 ---
#
# Та же коробка, что у хижины строителя, и это к месту: мастерская
# и должна выглядеть мастерской, а не вторым домом. Отличают её котёл
# у стены и бочки — по ним видно, что внутри варят.
NORMAN_BREWERY = [
    # y=0 — цоколь
    ["CCCCC"] * 5,
    # y=1 — котёл, бочки, сундук и место пивовара
    ["BWDWB",
     "W.K.W",
     "W0.OW",
     "WS.vW",
     "BWWWB"],
    # y=2 — второй ряд стен, окно на улицу
    ["BX.XB",
     "X...X",
     "G...G",
     "X...X",
     "BXGWB"],
    # y=3 — обвязка
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
    # y=5 — конёк: плита вдоль хребта. Без неё крыша кончается плоской
    # площадкой, и дом читается коробкой с крышкой; с ней у силуэта
    # появляется хребет, по которому дом узнают издалека.
    [".....",
     ".....",
     ".777.",
     ".....",
     "....."],
]


# --- дом какао майя, 5x5 ---
#
# То же назначение и тот же размер, свой облик: охра по извести, резной
# камень в углах, пальмовая кровля. Народ должен узнаваться с первого
# взгляда даже в мастерской.
MAYA_BREWERY = [
    # y=0 — каменная подошва
    ["MMMMM"] * 5,
    # y=1 — котёл, бочка, сундук и место знахаря
    ["VRDRV",
     "R.K.R",
     "R0.OR",
     "RS.vR",
     "VRRRV"],
    # y=2 — второй ряд стен с проёмом
    ["VR.RV",
     "R...R",
     "G...G",
     "R...R",
     "VRGRV"],
    # y=3 — обвязка из дерева джунглей
    ["iiiii",
     "u...u",
     "u...u",
     "u...u",
     "iiiii"],
    # y=4 — пальмовая кровля
    ["T111T",
     "4aaa3",
     "4aaa3",
     "4aaa3",
     "T222T"],
]


# --- ткацкая норманнов, 5x5 ---
#
# Третья мастерская мода и первая, что делает товар НА ВЫВОЗ. Пивоварня
# кормит своих, ткацкая — торгует: сукно деревни берут дороже всего,
# что колония может им предложить.
NORMAN_WEAVERY = [
    # y=0 — цоколь
    ["CCCCC"] * 5,
    # y=1 — станок, кипа шерсти, сундук и место ткача
    ["BWDWB",
     "W.K.W",
     "W@..W",
     "WS.#W",
     "BWWWB"],
    # y=2 — второй ряд стен с окном
    ["BX.XB",
     "X...X",
     "G...G",
     "X...X",
     "BXGWB"],
    # y=3 — обвязка
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
    # y=5 — конёк: плита вдоль хребта. Без неё крыша кончается плоской
    # площадкой, и дом читается коробкой с крышкой; с ней у силуэта
    # появляется хребет, по которому дом узнают издалека.
    [".....",
     ".....",
     ".777.",
     ".....",
     "....."],
]


# --- ткацкая майя, 5x5 ---
MAYA_WEAVERY = [
    # y=0 — каменная подошва
    ["MMMMM"] * 5,
    # y=1 — станок, кипа хлопка, сундук и место ткача
    ["VRDRV",
     "R.K.R",
     "R@..R",
     "RS.#R",
     "VRRRV"],
    # y=2 — второй ряд стен с проёмом
    ["VR.RV",
     "R...R",
     "G...G",
     "R...R",
     "VRGRV"],
    # y=3 — обвязка из дерева джунглей
    ["iiiii",
     "u...u",
     "u...u",
     "u...u",
     "iiiii"],
    # y=4 — пальмовая кровля
    ["T111T",
     "4aaa3",
     "4aaa3",
     "4aaa3",
     "T222T"],
]


NORMAN_WAREHOUSE = [
    # y=0 — цоколь
    ["CCCCCCC"] * 5,
    # y=1 — стены, вход, четыре сундука и мешок снеди в середине
    ["BWWDWWB",
     "WS...SW",
     "W..O..W",
     "WS...SW",
     "BWWWWWB"],
    # y=2 — второй ряд стен: штукатурка между брусом
    ["BXG.GXB",
     "X.....X",
     "G.....G",
     "X.....X",
     "BXGWGXB"],
    # y=3 — верхняя обвязка
    ["HHHHHHH",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "HHHHHHH"],
    # y=4 — крыша
    ["nnnnnnn",
     "wPPPPPe",
     "wPPPPPe",
     "wPPPPPe",
     "sssssss"],
]

# --- мастерская строителя норманнов, 5x5 ---
#
# Рабочее место билдера и два сундука под материалы: это его двор,
# а не жильё. Похожа на домик лесоруба, и это верно — сарай ремесленника
# у одного народа устроен одинаково; разница в том, что стоит внутри.
NORMAN_BUILDER_HUT = [
    # y=0 — цоколь
    ["CCCCC"] * 5,
    # y=1 — рабочее место, два сундука, поленница у стены
    ["BWDWB",
     "WK.SW",
     "W...W",
     "WS.OW",
     "BWWWB"],
    # y=2 — второй ряд стен: штукатурка между брусом
    ["BX.XB",
     "X...X",
     "G...G",
     "X...X",
     "BXGWB"],
    # y=3 — обвязка
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
    # y=5 — конёк: плита вдоль хребта. Без неё крыша кончается плоской
    # площадкой, и дом читается коробкой с крышкой; с ней у силуэта
    # появляется хребет, по которому дом узнают издалека.
    [".....",
     ".....",
     ".777.",
     ".....",
     "....."],
]


# --- склад майя, 7x5 ---
#
# То же назначение, свой облик: охряные стены, резные углы и вальмовая
# кровля из пальмовой соломы. Пирамиду сюда не поставить — след
# не квадратный, — и это к месту: храм остаётся выше всего в деревне.
MAYA_WAREHOUSE = [
    # y=0 — каменная подошва
    ["MMMMMMM"] * 5,
    # y=1 — стены, вход, четыре сундука
    ["VRRDRRV",
     "RS...SR",
     "R..O..R",
     "RS...SR",
     "VRRRRRV"],
    # y=2 — второй ряд стен с узкими проёмами
    ["VR.R.RV",
     "R.....R",
     "......R",
     "R.....R",
     "VR.R.RV"],
    # y=3 — обвязка из бревна джунглей
    ["iiiiiii",
     "u.....u",
     "u.....u",
     "u.....u",
     "iiiiiii"],
    # y=4 — кровля, нижний ярус
    ["TTTTTTT"] * 5,
    # y=5 — второй ярус
    [".......",
     ".TTTTT.",
     ".TTTTT.",
     ".TTTTT.",
     "......."],
    # y=6 — гребень
    [".......",
     ".......",
     "..TTT..",
     ".......",
     "......."],
]

# --- мастерская строителя майя, 5x5 ---
MAYA_BUILDER_HUT = [
    # y=0 — подошва
    ["MMMMM"] * 5,
    # y=1 — рабочее место, два сундука, слот декора
    ["VRDRV",
     "RK.SR",
     "R...R",
     "RS.OR",
     "VRRRV"],
    # y=2 — второй ряд стен
    ["VR.RV",
     "R...R",
     "....R",
     "R...R",
     "VR.RV"],
    # y=3 — обвязка
    ["iiiii",
     "u...u",
     "u...u",
     "u...u",
     "iiiii"],
] + pyramid(5, "T")

# --- ларёк норманнов, 5x5 ---
#
# Первое здание мода БЕЗ СТЕН, и это не экономия на кладке. Прилавок
# должен быть виден с улицы: игрок подходит и сразу понимает, что здесь
# торгуют, — а закрытая коробка с дверью ничем не отличалась бы от дома.
# Отсюда и устройство: столбы по углам, навес поверх, прилавок к улице
# и купец за ним.
#
# Вход при этом с ЗАДНЕЙ стороны: прилавок — не порог, через него ходить
# нельзя, и лесенку крыльца надо вести туда, где купец в самом деле
# заходит внутрь.
NORMAN_MARKET_STALL = [
    # y=0 — цоколь
    ["CCCCC"] * 5,
    # y=1 — прилавок к улице, место купца за ним, сундук и слот убранства
    ["BPPPB",
     "..K..",
     ".S.O.",
     ".....",
     "B.D.B"],
    # y=2 — столбы и фонари на прилавке: ларёк виден и в сумерках
    ["Bt.tB",
     ".....",
     ".....",
     ".....",
     "B...B"],
    # y=3 — обвязка
    ["HHHHH",
     "Z...Z",
     "Z...Z",
     "Z...Z",
     "HHHHH"],
    # y=4 — навес
    ["nnnnn",
     "wPPPe",
     "wPPPe",
     "wPPPe",
     "sssss"],
    # y=5 — конёк: плита вдоль хребта. Без неё крыша кончается плоской
    # площадкой, и дом читается коробкой с крышкой; с ней у силуэта
    # появляется хребет, по которому дом узнают издалека.
    [".....",
     ".....",
     ".777.",
     ".....",
     "....."],
]


# --- ларёк майя, 5x5 ---
#
# То же устройство и свой облик: подошва из камня, прилавок и навес
# из дерева джунглей, пальмовая кровля.
MAYA_MARKET_STALL = [
    # y=0 — каменная подошва
    ["MMMMM"] * 5,
    # y=1 — прилавок, место торговца, сундук и слот убранства
    ["jaaaj",
     "..K..",
     ".S.O.",
     ".....",
     "j.D.j"],
    # y=2 — столбы и фонари
    ["jt.tj",
     ".....",
     ".....",
     ".....",
     "j...j"],
    # y=3 — обвязка из дерева джунглей
    ["iiiii",
     "u...u",
     "u...u",
     "u...u",
     "iiiii"],
    # y=4 — пальмовая кровля
    ["T111T",
     "4aaa3",
     "4aaa3",
     "4aaa3",
     "T222T"],
]


def gallery(base, plinth, post, beam, deck):
    """Четвёртый уровень: тот же дом, а вокруг — крытое гульбище.

    Вверх ратуше расти больше нельзя, и это выяснено дорого: третий ярус
    пришлось укоротить на ряд стен и ярус кровли, потому что до верха
    билдер не дотягивался, а материалы не влезали в сундук. Поэтому
    столица растёт ВШИРЬ.

    И только на восток и юг: якорь здания — минимальный угол следа,
    и рост в другую сторону означал бы, что улучшение переносит дом.
    Это же правило проверяет containment_check.

    Гульбище — крытая галерея на столбах по внешнему краю: пола у неё
    нет своего, полом служит цоколь, а между столбами открыто. Столица
    обязана выглядеть столицей с первого взгляда, и делает это не высотой,
    которой нельзя, а размахом.
    """
    old_width = len(base[0][0])
    old_depth = len(base[0])
    width = old_width + 2
    depth = old_depth + 2

    layers = []
    for layer in base:
        rows = [row + ".." for row in layer]
        rows += ["." * width, "." * width]
        layers.append(rows)

    def put(y, x, z, symbol):
        row = list(layers[y][z])
        row[x] = symbol
        layers[y][z] = "".join(row)

    added = [(x, z) for x in range(width) for z in range(depth)
             if x >= old_width or z >= old_depth]

    for x, z in added:
        # Цоколь под гульбищем — он же его пол.
        put(0, x, z, plinth)

        # Столбы через клетку по внешнему краю: реже — и навес повиснет,
        # чаще — и получится стена, а гульбище должно просматриваться.
        edge = x == width - 1 or z == depth - 1
        if edge and (x + z) % 2 == 0:
            put(1, x, z, post)
            put(2, x, z, post)

        put(3, x, z, beam)
        put(4, x, z, deck)

    return layers


NORMAN_TOWN_HALL_4 = gallery(NORMAN_TOWN_HALL_3, "C", "B", "H", "P")

# У майя гульбище то же, а сложено своим: подошва из камня, столбы
# и обвязка из дерева джунглей, настил пальмовый.
MAYA_TOWN_HALL_4 = gallery(MAYA_TOWN_HALL_3, "M", "j", "i", "T")


# --- рынок норманнов, 7x7 ---
#
# Награда за столицу и старший брат ларька: два прилавка, два места
# для купцов, два сундука. Устроен так же — без стен, навесом на столбах:
# торговое место должно читаться с улицы.
NORMAN_MARKET = [
    # y=0 — цоколь
    ["CCCCCCC"] * 7,
    # y=1 — два прилавка к улице, два места купцов, сундуки и убранство
    ["BPPPPPB",
     "..K.K..",
     ".S...S.",
     ".O...O.",
     ".......",
     ".......",
     "B..D..B"],
    # y=2 — столбы и фонари над прилавком
    ["Bt...tB",
     ".......",
     ".......",
     ".......",
     ".......",
     ".......",
     "B.....B"],
    # y=3 — обвязка
    ["HHHHHHH",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "HHHHHHH"],
    # y=4 — навес
    ["nnnnnnn",
     "wPPPPPe",
     "wPPPPPe",
     "wPPPPPe",
     "wPPPPPe",
     "wPPPPPe",
     "sssssss"],
    # y=5 — конёк вдоль хребта.
    [".......",
     ".......",
     ".......",
     ".77777.",
     ".......",
     ".......",
     "......."],
]


# --- торговая площадь майя, 7x7 ---
MAYA_MARKET = [
    # y=0 — каменная подошва
    ["MMMMMMM"] * 7,
    # y=1 — прилавки, места торговцев, сундуки и убранство
    ["jaaaaaj",
     "..K.K..",
     ".S...S.",
     ".O...O.",
     ".......",
     ".......",
     "j..D..j"],
    # y=2 — столбы и фонари
    ["jt...tj",
     ".......",
     ".......",
     ".......",
     ".......",
     ".......",
     "j.....j"],
    # y=3 — обвязка из дерева джунглей
    ["iiiiiii",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "iiiiiii"],
    # y=4 — пальмовая кровля
    ["T11111T",
     "4aaaaa3",
     "4aaaaa3",
     "4aaaaa3",
     "4aaaaa3",
     "4aaaaa3",
     "T22222T"],
]


# --- сторожевая башня норманнов, 5x9x5 ---
#
# Первое укрепление мода и единственное здание, которое строится не ради
# работы, а ради ВИДА СВЕРХУ. Отсюда и устройство: глухой низ, лаз
# по лестнице, площадка с зубцами и фонарями наверху. Страж стоит там,
# и его видно с земли — работа обязана быть видна.
#
# Девять блоков, а не тринадцать: до верха тринадцатиметровой ратуши
# билдер не дотягивался, и это уже стоило одного переделанного уровня.
NORMAN_WATCHTOWER = [
    # y=0 — цоколь
    ["CCCCC"] * 5,
    # y=1 — вход с севера и лаз у восточной стены
    ["BWDWB",
     "W...W",
     "W..kW",
     "W...W",
     "BWWWB"],
    # y=2 — бойницы по сторонам
    ["BXGXB",
     "X...X",
     "X..kX",
     "X...X",
     "BXGXB"],
    # y=3 — глухой ярус
    ["BXXXB",
     "X...X",
     "X..kX",
     "X...X",
     "BXXXB"],
    # y=4 — ещё бойницы
    ["BXGXB",
     "X...X",
     "X..kX",
     "X...X",
     "BXGXB"],
    # y=5 — глухой ярус
    ["BXXXB",
     "X...X",
     "X..kX",
     "X...X",
     "BXXXB"],
    # y=6 — перекрытие с лазом: лестница проходит его насквозь
    ["BPPPB",
     "PPPPP",
     "PPPkP",
     "PPPPP",
     "BPPPB"],
    # y=7 — площадка стражи: место дозора, фонари и выход с лестницы
    ["B...B",
     ".t...",
     "..Kk.",
     "...t.",
     "B...B"],
    # y=8 — зубцы с проёмами на север и юг
    ["BX.XB",
     "X...X",
     "B...B",
     "X...X",
     "BX.XB"],
]


# --- дозорная башня майя, 5x9x5 ---
#
# То же назначение и тот же силуэт, свой материал: подошва из камня,
# охра по извести, резной камень в углах, настил и лаз из дерева джунглей.
# Лестница у западной стены — как и в доме майя.
MAYA_WATCHTOWER = [
    # y=0 — каменная подошва
    ["MMMMM"] * 5,
    # y=1 — вход и лаз
    ["VRDRV",
     "R...R",
     "Rx..R",
     "R...R",
     "VRRRV"],
    # y=2 — бойницы
    ["VRGRV",
     "R...R",
     "Rx..R",
     "R...R",
     "VRGRV"],
    # y=3 — глухой ярус
    ["VRRRV",
     "R...R",
     "Rx..R",
     "R...R",
     "VRRRV"],
    # y=4 — бойницы
    ["VRGRV",
     "R...R",
     "Rx..R",
     "R...R",
     "VRGRV"],
    # y=5 — глухой ярус
    ["VRRRV",
     "R...R",
     "Rx..R",
     "R...R",
     "VRRRV"],
    # y=6 — настил с лазом
    ["VaaaV",
     "aaaaa",
     "axaaa",
     "aaaaa",
     "VaaaV"],
    # y=7 — площадка дозора
    ["V...V",
     "...t.",
     ".xK..",
     ".t...",
     "V...V"],
    # y=8 — гребень с проёмами
    ["VR.RV",
     "R...R",
     "V...V",
     "R...R",
     "VR.RV"],
]


# Символы, к которым факел крепится.
#
# Настенный факел держится за боковую грань соседнего блока, а стоячий —
# за верхнюю грань нижнего; ванили нужна полная грань, и настил, бревно,
# стена, ступень и плита её дают, а решётка, лестница, забор и маркер —
# нет. Список белый, а не чёрный, намеренно: забытый символ оставит
# здание без света, забытое исключение — уронит факел на пол.
# Кириллица — ель северян и кирпич трубы: они такие же глухие, как их
# латинские соседи, и без них факел не нашёл бы стены в срубе.
SOLID = set("CdBHZWXMPARVTiuja#123456789nsewomNI[]_-?|/{}" "лжзенюпб")

# Куда смотрит факел, прислонённый к стене с этой стороны.
# Стена на севере — факел смотрит на юг: он торчит ОТ стены, а не в неё.
AWAY = {(0, -1): ",", (0, 1): "^", (-1, 0): ">", (1, 0): "<"}


def ring(width, depth, gaps=(), bars=(), corner="/", wall="-", bar="`"):
    """Кольцо стены: углы резным столбом, стены кладкой, середина пуста.

    gaps — клетки, где стены нет вовсе: это верхняя половина дверного
    проёма. bars — где вместо кладки решётка.

    Разделять эти два списка пришлось не из аккуратности. Первая версия
    считала проём и решётку одним и тем же, и решётка честно встала
    ПРЯМО НАД ДВЕРЬЮ: дверь открывалась в железные прутья, и в чертог
    было не войти. Ровно та же болезнь, из-за которой у майя скамья
    стояла за порогом, — и поймана она тем же способом, разливом
    по воздуху от ратуши до неба.
    """
    rows = []
    for z in range(depth):
        row = []
        for x in range(width):
            edge = x in (0, width - 1) or z in (0, depth - 1)
            corner_cell = (x in (0, width - 1)) and (z in (0, depth - 1))
            if (x, z) in gaps:
                row.append(".")
            elif (x, z) in bars:
                row.append(bar if edge else ".")
            elif corner_cell:
                row.append(corner)
            elif edge:
                row.append(wall)
            else:
                row.append(".")
        rows.append("".join(row))
    return rows


def chamber(furnished, height=5, door=None, grates=()):
    """Зал, вырубленный в камне: пол, стены до свода и сплошной потолок.

    furnished — слой y=1 целиком, написанный руками: он несёт дверь
                и утварь, и только он у каждого здания свой.
    height    — от пола до свода включительно. Пять — жилой зал: три
                свободных блока по высоте, ровно как в доме на лугу.
    door      — (x, z) проёма над дверью: дверь высотой в два блока,
                и второй ряд стен обязан её пропустить.
    grates    — где во втором ряду стоят решётки. Окон под землёй быть
                не может, и это не потеря: решётка в галерею говорит
                ровно то же, что окно на улицу, — внутри живут.

    Свода в один блок толщиной довольно: над ним ещё три блока горы,
    которых требует разметка чертога, и вскрыть зал сверху нечем.
    """
    width, depth = len(furnished[0]), len(furnished)
    second = ring(width, depth, gaps=((door,) if door else ()), bars=tuple(grates))
    plain = ring(width, depth)
    layers = [["?" * width] * depth, list(furnished), second]
    layers += [list(plain) for _ in range(height - 4)]
    layers.append(["|" * width] * depth)
    return layers


def leaf_crown(width, depth, levels):
    """Полог из листвы: каждый ярус уже предыдущего на блок с каждой стороны.

    Своя, а не ступенчатая пирамида майя, ровно по одной причине: пирамида
    квадратная, а склад эльфов семь на пять. Кровля, посчитанная по ширине,
    вылезала бы за след здания на два блока с каждой стороны, и генератор
    честно падал бы — он и упал.
    """
    layers = []
    for inset in range(levels):
        rows = []
        for z in range(depth):
            if z < inset or z >= depth - inset:
                rows.append("." * width)
            else:
                rows.append("." * inset + "}" * (width - 2 * inset) + "." * inset)
        layers.append(rows)
    return layers


def bower(furnished, height=5, door=None, panes=(), crown=2):
    """Помост эльфов: настил, берёзовые стены и лиственный полог.

    Зеркало гномьего зала и написан нарочно как зеркало: там пол вырублен
    в породе и над ним свод, здесь пол настлан над лесом и над ним крона.
    Разница в одном слове — «вырублен» против «настлан», — и она же вся
    разница между двумя народами.

    Полог — та же ступенчатая пирамида, что кроет храм майя, только
    из листвы. Свой силуэт кровли у мода уже четвёртый, и это дешевле
    любого нового блока: деревню узнают по очертанию, а не по текстуре.
    """
    width, depth = len(furnished[0]), len(furnished)
    second = ring(width, depth, gaps=((door,) if door else ()), bars=tuple(panes),
                  corner="{", wall="{", bar="G")
    plain = ring(width, depth, corner="{", wall="{")
    layers = [["{" * width] * depth, list(furnished), second]
    layers += [list(plain) for _ in range(height - 3)]
    return layers + leaf_crown(width, depth, crown)


def raise_bower(base, extra, crown=2):
    """Тот же помост с полом выше на несколько блоков.

    Так у эльфов выглядит улучшение — и опять зеркально гномам: те
    поднимают свод, потому что над ними гора, эльфы поднимают стены,
    потому что над ними небо. Ни те, ни другие не надстраивают этаж:
    лестница внутри дома съела бы половину и без того небольшого пола.
    """
    width, depth = len(base[0][0]), len(base[0])
    body = base[:-crown]
    return body + [ring(width, depth, corner="{", wall="{") for _ in range(extra)] \
        + leaf_crown(width, depth, crown)


def deepen(base, extra):
    """Тот же зал со сводом выше на несколько блоков.

    Так у гномов выглядит улучшение. Второго этажа чертог не строит
    и строить не может: над ним гора, а не небо, — зато свод можно
    поднять, и высокий зал читается как богатый ровно так же, как
    у норманнов читается второй этаж.
    """
    width, depth = len(base[0][0]), len(base[0])
    return base[:-1] + [ring(width, depth) for _ in range(extra)] \
        + [["|" * width] * depth]


def light_up(name, layers):
    """Вешает факел на стену под самой высокой кровлей здания.

    Место ищется, а не пишется руками, по одной причине: зданий три
    десятка, и у каждого свой потолок. Правило простое — самая высокая
    пустая клетка под сплошной кровлей, у которой есть стена под боком,
    а из таких ближайшая к середине следа. Под кровлей — значит над
    головой: места для ходьбы факел не отнимает.

    Край следа не рассматривается вовсе, и это не придирка. У ратуши
    майя в поясе балок оставлены проёмы, и самая высокая клетка «воздух
    под настилом» оказалась именно там — факел встал бы снаружи, в дыре
    карниза, а зал остался бы тёмным. Внутри — значит внутри.

    У поля и рощи кровли нет, и они возвращаются как есть: там свет
    стоит на угловых столбах ограды, и это написано в самой схеме.
    """
    depth, width = len(layers[0]), len(layers[0][0])
    middle_x, middle_z = (width - 1) / 2.0, (depth - 1) / 2.0

    def roofed(y, x, z):
        """Над клеткой и над всеми четырьмя её соседями есть кровля.

        Одного блока сверху мало. У ратуши майя четвёртого уровня
        гульбище пристроено к старому поясу балок, и в поясе остались
        проёмы: клетка проёма имеет над собой солому — и факел встал бы
        на кромке крыши снаружи дома. Сплошная кровля над пятью клетками
        означает, что мы под ней, а не у её края.
        """
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if layers[y + 1][z + dz][x + dx] not in SOLID:
                return False
        return True

    def wall(y, x, z):
        """Сторона, с которой к клетке примыкает стена, и куда смотреть."""
        for (dx, dz), facing in AWAY.items():
            if layers[y][z + dz][x + dx] in SOLID:
                return facing
        return None

    best = None
    for y in range(len(layers) - 1):
        for z in range(1, depth - 1):
            for x in range(1, width - 1):
                if layers[y][z][x] != "." or not roofed(y, x, z):
                    continue
                facing = wall(y, x, z)
                if facing is None:
                    continue
                # Выше — лучше; на равной высоте — ближе к середине.
                away = abs(x - middle_x) + abs(z - middle_z)
                if best is None or (y, -away) > (best[0], -best[3]):
                    best = (y, x, z, away, facing)

    if best is None:
        return layers

    y, x, z, _, facing = best
    rows = list(layers[y])
    rows[z] = rows[z][:x] + facing + rows[z][x + 1:]
    lit = list(layers)
    lit[y] = rows
    return lit


# --- часовня норманнов, 7x7 ---
#
# Первое здание мода, которое ничего не производит и никого не селит.
# Оно и не должно: храм — это место, куда приходят, а не мастерская
# с богом вместо станка. Отсюда и облик — один высокий зал, окна в два
# ряда, скамьи по сторонам и алтарь в дальнем конце.
#
# Зал в три ряда стен, а не в два, как дом: разница в один блок высоты
# читается с улицы мгновенно, и часовня видна над крышами. Это дешевле
# любого шпиля и работает так же.
NORMAN_CHAPEL = [
    # y=0 — булыжный цоколь
    ["CCCCCCC"] * 7,
    # y=1 — стены, вход, скамьи и алтарь в дальнем конце
    ["BWWDWWB",
     "W.....W",
     "W=...=W",
     "W.....W",
     "W=...=W",
     "W..$..W",
     "BWWWWWB"],
    # y=2 — второй ряд стен с окнами; над дверью воздух, иначе в неё
    # не войти: проём обязан быть в рост человека
    ["BXG.GXB",
     "X.....X",
     "G.....G",
     "X.....X",
     "G.....G",
     "X.....X",
     "BXGXGXB"],
    # y=3 — третий ряд: тот самый лишний блок высоты
    ["BXXXXXB",
     "X.....X",
     "G.....G",
     "X.....X",
     "G.....G",
     "X.....X",
     "BXXXXXB"],
    # y=4 — верхняя обвязка
    ["HHHHHHH",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "Z.....Z",
     "HHHHHHH"],
] + hip_roof(7, 2, ridge="7")

# --- святилище майя, 7x7 ---
#
# Та же мысль, другой народ. У норманнов высокий зал под скатом,
# у майя — приземистая палата на каменной подошве под ступенчатой
# кровлей. Спутать нельзя ни силуэтом, ни цветом, и это то самое,
# ради чего у мода вообще два народа.
MAYA_SHRINE = [
    # y=0 — подошва из тёсаного камня: платформа, на которой стоит храм
    ["MMMMMMM"] * 7,
    # y=1 — красные стены с резными углами, скамьи и алтарь
    ["VRRDRRV",
     "R.....R",
     "R=...=R",
     "R.....R",
     "R=...=R",
     "R..$..R",
     "VRRRRRV"],
    # y=2 — второй ряд; над дверью воздух
    ["VRR.RRV",
     "R.....R",
     "R.....R",
     "R.....R",
     "R.....R",
     "R.....R",
     "VRRRRRV"],
    # y=3 — третий ряд
    ["VRRRRRV",
     "R.....R",
     "R.....R",
     "R.....R",
     "R.....R",
     "R.....R",
     "VRRRRRV"],
    # y=4 — обвязка из дерева джунглей
    ["iiiiiii",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "u.....u",
     "iiiiiii"],
] + pyramid(7, "T")


# ======================= ПОНИ: ТРЕТИЙ НАРОД =======================
#
# Проверка главного тезиса мода: народ добавляется данными, а не кодом.
# Здесь — его облик; всё остальное (имена, здания, боги, торговля,
# квесты) лежит в датапаке и не трогает ни строчки в моде.
#
# Кто они. Степные коневоды: пони — это их низкорослые лошади, а со
# временем и прозвище самих хозяев. Четвероногими их не сделать без
# своей модели, и выдумывать её ради проверки тезиса незачем; зато
# мир вокруг них читается сразу — рыжий сруб, сено и жерди до горизонта.

# --- ратуша пони, уровень 1: длинный дом 7x7 ---
PONY_TOWN_HALL = [
    # y=0 — цоколь
    ["CCCCCCC"] * 7,
    # y=1 — стены, вход с севера, очаг посреди зала и снопы по сторонам
    ["oIIDIIo",
     "IO...OI",
     "I..A..I",
     "o..c..o",
     "I..A..I",
     "IK.E.SI",
     "oIIIIIo"],
    # y=2 — второй ряд стен с окнами
    ["oIG.GIo",
     "I.....I",
     "G.....G",
     "o.....o",
     "G.....G",
     "I.....I",
     "oIGIGIo"],
    # y=3 — обвязка
    ["mmmmmmm",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "mmmmmmm"],
] + gable(7, 7, 2, ridge="_")

# --- изба пони, уровень 1: 7x7 ---
#
# Тот же план, что у соседей: проход по середине, спальня слева, стол
# с лавками справа. Рыжий сруб акации и двускатная кровля — всё, чем
# изба отличается от нормандского дома и дома майя.
PONY_HOUSE = [
    ["CCCCCCC"] * 7,
    # y=1 — жильё
    ["oIIDIIo",
     "I.....I",
     "Iff.:SI",
     "Ihh.%;I",
     "I.O.=.I",
     "IO.c.OI",
     "oIIIIIo"],
    # y=2 — второй ряд стен с окнами
    ["oIG.GIo",
     "I.....I",
     "G.....G",
     "o.....o",
     "G.....G",
     "I.....I",
     "oIGIGIo"],
    # y=3 — обвязка
    ["mmmmmmm",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "mmmmmmm"],
] + steep_gable(7, 7, "]", "[", "I", "_", beam="o", window="G")

# --- дом пони, уровень 2 ---
#
# Тот же след, надстроенный светёлкой: ещё две кровати и слот убранства.
# Лаз идёт по западной стене — единственной, у которой нашлась свободная
# клетка в обоих рядах первого этажа.
PONY_HOUSE_2 = storey(
    PONY_HOUSE,
    # y=3 — пол светёлки с проёмом под лаз
    ["mmmmmmm",
     "NIIIIIN",
     "NIIIIIN",
     "NIIIIIN",
     "NkIIIIN",
     "NIIIIIN",
     "mmmmmmm"],
    [
        # y=4 — светёлка: ещё две кровати, стол и слот убранства
        ["oIIIIIo",
         "I.....I",
         "Iff.%.I",
         "Ihh.=.I",
         "I...O.I",
         "I.....I",
         "oIIIIIo"],
        # y=5 — верхний ряд стен с окнами
        ["oIG.GIo",
         "I.....I",
         "G.....G",
         "o.....o",
         "G.....G",
         "I.....I",
         "oIGIGIo"],
    ],
    0,
    extra=((1, 1, 4, "k"), (2, 1, 4, "k")),
    roof=steep_gable(7, 7, "]", "[", "I", "_", beam="o", window="G"))

# --- ратуша пони, уровень 2 ---
PONY_TOWN_HALL_2 = storey(
    PONY_TOWN_HALL,
    # y=3 — пол второго этажа
    ["mmmmmmm",
     "NlIIIIN",
     "NIIIIIN",
     "NIIIIIN",
     "NIIIIIN",
     "NIIIIIN",
     "mmmmmmm"],
    [
        # y=4 — верхний зал: две кровати, сундуки и убранство
        ["oIIIIIo",
         "Ifl..fI",
         "Ih...hI",
         "o..A..o",
         "IS...SI",
         "IO...OI",
         "oIIIIIo"],
        # y=5 — второй ряд верхних стен с окнами
        ["oIGIGIo",
         "I.....I",
         "G.....G",
         "o.....o",
         "G.....G",
         "I.....I",
         "oIGIGIo"],
    ],
    0,
    extra=((1, 2, 1, "l"), (2, 2, 1, "l")),
    roof=gable(7, 7, 2, ridge="_"))

# --- ратуша пони, уровень 3 ---
#
# Нижний ярус кровли второго уровня служит полом третьему — тот же
# приём, что у обоих соседей, и по той же причине: сплошной настил
# уже есть, и класть поверх него второй значило бы поднять зал
# на лишний блок, до которого билдеру не дотянуться.
PONY_TOWN_HALL_3 = third_storey(
    PONY_TOWN_HALL_2,
    7,
    [
        # y=7 — зал совета
        ["oIIIIIo",
         "IOl..OI",
         "I.....I",
         "o..A..o",
         "I.....I",
         "IS...SI",
         "oIIIIIo"],
        # y=8 — обвязка под кровлей с окнами
        ["mmmmmmm",
         "NIIIIIN",
         "GIIIIIG",
         "NIIIIIN",
         "GIIIIIG",
         "NIIIIIN",
         "mmmmmmm"],
    ],
    gable(7, 7, 2),
    ladders=((5, 2, 1, "l"), (6, 2, 1, ".")))

# --- ратуша пони, уровень 4: гульбище на жердях ---
PONY_TOWN_HALL_4 = gallery(PONY_TOWN_HALL_3, "C", "o", "m", "I")

# --- загон пони, 7x7 ---
#
# Не поле, а ЗАГОН: то же назначение, что у норманнской пашни и
# террасы майя, но обнесено жердями по кругу и с двумя снопами внутри.
# Народ, который держит лошадей, косит и сеет на одном и том же клине.
PONY_FARM = [
    ["ddddddd"] * 7,
    # y=1 — грядки и колодец посередине
    ["ddddddd",
     "dFFFFFd",
     "dFF~FFd",
     "dFFFFFd",
     "dFFFFFd",
     "dFFFFFd",
     "ddddddd"],
    # y=2 — жерди, калитка, морковь, снопы и место фермера
    ["&&&&&&&",
     "(*****&",
     "&**.**&",
     "&*A*A*&",
     "&**K**&",
     "&*****&",
     "&&&&&&&"],
    # y=3 — факелы на угловых столбах: тёмный загон к утру зарастает
    # мобами, а фермер выходит на рассвете и встречает их первым
    ["!.....!",
     ".......",
     ".......",
     ".......",
     ".......",
     ".......",
     "!.....!"],
]

# --- загон пони, уровень 2 ---
#
# Растёт на восток и на юг, якорь тот же. Старая изгородь, оказавшаяся
# посреди загона, превращается в грядки — переделка законная, ровно та
# же, что у соседей.
PONY_FARM_2 = [
    ["ddddddddd"] * 9,
    ["ddddddddd",
     "dFFFFFdFd",
     "dFF~FFdFd",
     "dFFFFFdFd",
     "dFFFFFdFd",
     "dFFFFFdFd",
     "dddddddFd",
     "dFFFFFFFd",
     "ddddddddd"],
    ["&&&&&&&&&",
     "(*******&",
     "&**.****&",
     "&*A*A***&",
     "&**K****&",
     "&*******&",
     "&*******&",
     "&*******&",
     "&&&&&&&&&"],
    ["!.......!",
     ".........",
     ".........",
     ".........",
     ".........",
     ".........",
     ".........",
     ".........",
     "!.......!"],
]

# --- домик лесоруба пони, 10x5 ---
PONY_LUMBERJACK = [
    ["CCCCCddddd"] * 5,
    ["oIDIo&&&&&",
     "IK.SI&).)&",
     "I...I&.O.(",
     "I.O.I&).)&",
     "oIIIo&&&&&"],
    ["oI.Io!...!",
     "I...I.....",
     "G...G.....",
     "I...I.....",
     "oIGIo!...!"],
    ["mmmmm.....",
     "N...N.....",
     "N...N.....",
     "N...N.....",
     "mmmmm....."],
    ["[[[[[.....",
     "IIIII.....",
     "IIIII.....",
     "IIIII.....",
     "]]]]]....."],
    ["..........",
     "[[[[[.....",
     "IIIII.....",
     "]]]]].....",
     ".........."],
    ["..........",
     "..........",
     "_____.....",
     "..........",
     ".........."],
]

# --- склад пони, 7x5 ---
PONY_WAREHOUSE = [
    ["CCCCCCC"] * 5,
    ["oIIDIIo",
     "IS...SI",
     "I..O..I",
     "IS...SI",
     "oIIIIIo"],
    ["oI.I.Io",
     "I.....I",
     "G.....G",
     "I.....I",
     "oIGIGIo"],
    ["mmmmmmm",
     "N.....N",
     "N.....N",
     "N.....N",
     "mmmmmmm"],
] + gable(7, 5, 2, ridge="_")

# --- мастерская строителя пони, 5x5 ---
PONY_BUILDER_HUT = [
    ["CCCCC"] * 5,
    ["oIDIo",
     "IK.SI",
     "I...I",
     "IS.OI",
     "oIIIo"],
    ["oI.Io",
     "I...I",
     "G...G",
     "I...I",
     "oIGIo"],
    ["mmmmm",
     "N...N",
     "N...N",
     "N...N",
     "mmmmm"],
] + gable(5, 5, 2, ridge="_")

# --- пивоварня пони, 5x5 ---
PONY_BREWERY = [
    ["CCCCC"] * 5,
    ["oIDIo",
     "I.K.I",
     "I0.OI",
     "IS.vI",
     "oIIIo"],
    ["oI.Io",
     "I...I",
     "G...G",
     "I...I",
     "oIGIo"],
    ["mmmmm",
     "N...N",
     "N...N",
     "N...N",
     "mmmmm"],
] + gable(5, 5, 2, ridge="_")

# --- ткацкая пони, 5x5 ---
PONY_WEAVERY = [
    ["CCCCC"] * 5,
    ["oIDIo",
     "I.K.I",
     "I@..I",
     "IS.#I",
     "oIIIo"],
    ["oI.Io",
     "I...I",
     "G...G",
     "I...I",
     "oIGIo"],
    ["mmmmm",
     "N...N",
     "N...N",
     "N...N",
     "mmmmm"],
] + gable(5, 5, 2, ridge="_")

# --- ларёк пони, 5x5 ---
#
# Без стен, как у обоих соседей, и по той же причине: прилавок должен
# быть виден с улицы. Вход с задней стороны — через прилавок не ходят.
PONY_MARKET_STALL = [
    ["CCCCC"] * 5,
    ["oIIIo",
     "..K..",
     ".S.O.",
     ".....",
     "o.D.o"],
    ["o!.!o",
     ".....",
     ".....",
     ".....",
     "o...o"],
    ["mmmmm",
     "N...N",
     "N...N",
     "N...N",
     "mmmmm"],
] + gable(5, 5, 2)

# --- рынок пони, 7x7 ---
PONY_MARKET = [
    ["CCCCCCC"] * 7,
    ["oIIIIIo",
     "..K.K..",
     ".S...S.",
     ".O...O.",
     ".......",
     ".......",
     "o..D..o"],
    ["o!...!o",
     ".......",
     ".......",
     ".......",
     ".......",
     ".......",
     "o.....o"],
    ["mmmmmmm",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "mmmmmmm"],
] + gable(7, 7, 2)

# --- сторожевая вышка пони, 5x9x5 ---
#
# Лаз идёт по западной стене: ванили нужна полная грань позади
# лестницы, и в срубе такая есть только у стены.
PONY_WATCHTOWER = [
    ["CCCCC"] * 5,
    ["oIDIo",
     "I...I",
     "Ix..I",
     "I...I",
     "oIIIo"],
    ["oIGIo",
     "I...I",
     "Ix..I",
     "I...I",
     "oIGIo"],
    ["oIIIo",
     "I...I",
     "Ix..I",
     "I...I",
     "oIIIo"],
    ["oIGIo",
     "I...I",
     "Ix..I",
     "I...I",
     "oIGIo"],
    ["oIIIo",
     "I...I",
     "Ix..I",
     "I...I",
     "oIIIo"],
    # y=6 — настил с лазом
    ["oIIIo",
     "IIIII",
     "IxIII",
     "IIIII",
     "oIIIo"],
    # y=7 — площадка дозора
    ["o...o",
     ".....",
     ".xK..",
     ".....",
     "o...o"],
    # y=8 — гребень с проёмами
    ["oI.Io",
     "I...I",
     "o...o",
     "I...I",
     "oI.Io"],
]

# --- святилище пони, 7x7 ---
#
# Алтарь в южном конце зала, дверь на севере, скамьи лицом к алтарю
# и сноп перед ним: народ приносит богам то, чем живёт.
PONY_SHRINE = [
    ["CCCCCCC"] * 7,
    ["oIIDIIo",
     "I=...=I",
     "I=...=I",
     "I.....I",
     "I..A..I",
     "I..$..I",
     "oIIIIIo"],
    ["oIG.GIo",
     "I.....I",
     "G.....G",
     "o.....o",
     "G.....G",
     "I.....I",
     "oIGIGIo"],
    ["mmmmmmm",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "N.....N",
     "mmmmmmm"],
] + gable(7, 7, 2, ridge="_")


# =============================== ГНОМЫ ========================================
#
# Четвёртый народ и первый, который живёт не на земле, а в ней. Дорожная
# карта называет это проверкой главного тезиса: майя и пони доказали, что
# данными задаётся облик, гномы должны доказать, что данными задаётся
# образ жизни.
#
# Схемы у них поэтому устроены иначе не мелочью, а целиком. Нет цоколя:
# пол вырубается в породе. Нет кровли: над головой гора. Нет окон: вместо
# них решётки в галерею. Зато есть свод — и он же единственное, чем
# отличается богатый чертог от бедного: гном не надстраивает этаж,
# он поднимает потолок.
#
# Стены выбиваются теми же шагами расчистки, какими на лугу валится
# дерево: воздух в схеме и так означает «здесь должно стать пусто, даже
# если сейчас гора». Движку про гномов знать не пришлось ничего.

# --- чертог гномов, уровень 1: 7x7 ---
#
# Зал с очагом посреди: у гномов он не греет, а светит и виден от самых
# ворот. Станок старейшины у входа, ларь и лежанка по сторонам.
DWARF_TOWN_HALL_ROWS = [
    "/--D--/",
    "-K...S-",
    "-.....-",
    "-..c..-",
    "-.....-",
    "-E...O-",
    "/-----/",
]
DWARF_GRATES = ((2, 0), (4, 0), (0, 2), (0, 4), (6, 2), (6, 4), (2, 6), (4, 6))

DWARF_TOWN_HALL = chamber(DWARF_TOWN_HALL_ROWS, height=5, door=(3, 0),
                          grates=DWARF_GRATES)

# --- чертог, уровни 2-4: свод поднимается ---
#
# По блоку на уровень. Четвёртый чертог — зал в шесть свободных блоков
# высоты, и это ровно вдвое против первого: разницу видно с порога,
# а раскопок над сводом не требует ни один из них.
DWARF_TOWN_HALL_2 = deepen(DWARF_TOWN_HALL, 1)
DWARF_TOWN_HALL_3 = deepen(DWARF_TOWN_HALL, 2)
DWARF_TOWN_HALL_4 = deepen(DWARF_TOWN_HALL, 3)

# --- жильё гномов, уровень 1: 7x7 ---
#
# Тот же план, что у всех трёх соседей, и это нарочно: народы отличаются
# кладкой, а не теснотой. Средний столбец свободен от двери до очага,
# слева спальня, справа стол с лавками, полка и ларь.
DWARF_HOUSE = chamber([
    "/--D--/",
    "-.....-",
    "-ff.:S-",
    "-hh.%;-",
    "-.O.=.-",
    "-O.c.O-",
    "/-----/",
], height=5, door=(3, 0), grates=DWARF_GRATES)

# --- жильё гномов, уровень 2 ---
#
# Свод выше на блок и ещё две лежанки: чертог растёт вглубь и вверх,
# но не вширь — соседний зал стоит в трёх шагах за камнем.
DWARF_HOUSE_2 = deepen([
    DWARF_HOUSE[0],
    ["/--D--/",
     "-ff.ff-",
     "-hh.hh-",
     "-..%..-",
     "-.O.=.-",
     "-O.c.S-",
     "/-----/"],
] + DWARF_HOUSE[2:], 1)

# --- подземные грядки гномов, уровень 1: 7x7 ---
#
# Поле под землёй растёт под лампами, а не под солнцем, и это не поблажка:
# в Minecraft посев всходит от любого света, и лампа под сводом даёт его
# больше, чем пасмурное утро наверху.
#
# Выращивают гномы корнеплоды. Грибы были бы вернее по духу, но фермер
# мода умеет только посевы на пашне, и грядка с грибами оставила бы его
# без работы — заглушка хуже отсутствия.
DWARF_FARM = [
    # y=0 — пашня и колодец посреди неё
    ["???????",
     "?FFFFF?",
     "?FFFFF?",
     "?FF~FF?",
     "?FFFFF?",
     "?FFFFF?",
     "???????"],
    # y=1 — стены, дверь и сам посев
    ["/--D--/",
     "-*****-",
     "-*****-",
     "-**.**-",
     "-*****-",
     "-K****-",
     "/-----/"],
    # y=2 — второй ряд стен с проёмом над дверью и решётками
    ring(7, 7, gaps=((3, 0),), bars=DWARF_GRATES),
    # y=3 — лампы под сводом: четыре на след, и весь посев в свету
    ["/-----/",
     "-.'.'.-",
     "-.....-",
     "-.....-",
     "-.....-",
     "-.'.'.-",
     "/-----/"],
    ["|||||||"] * 7,
]

# --- склад гномов, 7x5 ---
DWARF_WAREHOUSE = chamber([
    "/--D--/",
    "-S...S-",
    "-..O..-",
    "-S...S-",
    "/-----/",
], height=5, door=(3, 0), grates=((0, 2), (6, 2)))

# --- мастерская каменщика, 5x5 ---
DWARF_BUILDER_HUT = chamber([
    "/-D-/",
    "-K.S-",
    "-...-",
    "-S.O-",
    "/---/",
], height=5, door=(2, 0), grates=((0, 2), (4, 2)))

# --- пивоварня гномов, 5x5 ---
DWARF_BREWERY = chamber([
    "/-D-/",
    "-.K.-",
    "-...-",
    "-S.O-",
    "/---/",
], height=5, door=(2, 0), grates=((0, 2), (4, 2)))

# --- лавка гномов, 5x5 ---
DWARF_MARKET_STALL = chamber([
    "/-D-/",
    "-.K.-",
    "-...-",
    "-S.O-",
    "/---/",
], height=5, door=(2, 0), grates=((0, 2), (4, 2)))

# --- торговый зал гномов, 7x7 ---
#
# Подземный рынок — это не площадь под открытым небом, а самый широкий
# зал чертога: свод в четыре блока и два ряда прилавков.
DWARF_MARKET = deepen(chamber([
    "/--D--/",
    "-K...K-",
    "-.....-",
    "-%...%-",
    "-.....-",
    "-S.O.S-",
    "/-----/",
], height=5, door=(3, 0), grates=DWARF_GRATES), 1)

# --- караульня у ворот, 5x5 ---
#
# У народа из горы дозорной башни быть не может: смотреть неоткуда.
# Зато есть то, чего нет ни у кого, — единственный вход, и он стоит
# караула. Сторож сидит у самых ворот, а не на вершине.
DWARF_GATEHOUSE = chamber([
    "/-D-/",
    "-K.O-",
    "`...`",
    "-E.S-",
    "/---/",
], height=5, door=(2, 0), grates=())

# --- святилище гномов, 7x7 ---
#
# Свод в пять блоков — выше любого другого зала чертога. То же решение,
# что у норманнской часовни: высота читается как святость дешевле любого
# убранства, и под землёй это работает даже сильнее, чем наверху.
DWARF_SHRINE = deepen(chamber([
    "/--D--/",
    "-:...:-",
    "-:...:-",
    "-.....-",
    "-:...:-",
    "-./$/.-",
    "/-----/",
], height=5, door=(3, 0), grates=DWARF_GRATES), 2)


# =============================== ЭЛЬФЫ ========================================
#
# Пятый народ и зеркало четвёртого. Гномы доказали, что данными задаётся
# образ жизни; эльфы доказывают, что то же самое место кода умеет и обратное.
# У гномов пол вырублен В толще, здесь настлан НАД ней; у гномов годность
# места решает свод над головой, здесь — лес под настилом; гномы прорубают
# ход наружу, эльфы спускают лестницу вниз.
#
# Если одно и то же место кода умеет и закапывать деревню, и подвешивать
# её, то оно точно не знает слов «гномы» и «эльфы». Ради этого пара
# и написана.
#
# Схемы поэтому устроены так же зеркально: нет цоколя — под настилом
# воздух; нет каменной подошвы — настил берёзовый; вместо свода полог
# из листвы.

ELF_PANES = ((2, 0), (4, 0), (0, 2), (0, 4), (6, 2), (6, 4), (2, 6), (4, 6))
ELF_PANES_SMALL = ((0, 2), (4, 2))

# --- палата эльфов, уровень 1: 7x7 ---
#
# Очаг посреди, станок старейшины у входа, ларь и лежанка по сторонам:
# тот же план, что у всех четырёх соседей. Народы отличаются кладкой
# и кровлей, а не тем, насколько в их домах тесно, — и у народа, который
# живёт в кронах, это правило работает ровно так же.
ELF_TOWN_HALL = bower([
    "{{{D{{{",
    "{K...S{",
    "{.....{",
    "{..c..{",
    "{.....{",
    "{E...O{",
    "{{{{{{{",
], height=5, door=(3, 0), panes=ELF_PANES)

ELF_TOWN_HALL_2 = raise_bower(ELF_TOWN_HALL, 1)
ELF_TOWN_HALL_3 = raise_bower(ELF_TOWN_HALL, 2)
ELF_TOWN_HALL_4 = raise_bower(ELF_TOWN_HALL, 3)

# --- жильё эльфов, уровень 1: 7x7 ---
ELF_HOUSE = bower([
    "{{{D{{{",
    "{.....{",
    "{ff.:S{",
    "{hh.%;{",
    "{.O.=.{",
    "{O.c.O{",
    "{{{{{{{",
], height=5, door=(3, 0), panes=ELF_PANES)

# --- жильё эльфов, уровень 2 ---
#
# Ещё две лежанки и стены выше на блок. Второго этажа нет и здесь:
# лестница внутри съела бы половину и без того небольшого пола, а места
# вширь у дерева нет — соседний помост висит на своём стволе.
ELF_HOUSE_2 = raise_bower([
    ELF_HOUSE[0],
    ["{{{D{{{",
     "{ff.ff{",
     "{hh.hh{",
     "{..%..{",
     "{.O.=.{",
     "{O.c.S{",
     "{{{{{{{"],
] + ELF_HOUSE[2:], 1)

# --- висячий сад эльфов, уровень 1: 7x7 ---
#
# Поле у народа в кронах — это не поле, а сад на помосте: земля насыпана
# на настил, вода посреди, по краю перила. Растёт под открытым небом,
# и это его отличие от гномьих грядок: тем света не хватает, этим его
# больше, чем у всех.
ELF_FARM = [
    # y=0 — земля на настиле и колодец в середине
    ["ddddddd",
     "dFFFFFd",
     "dFFFFFd",
     "dFF~FFd",
     "dFFFFFd",
     "dFFFFFd",
     "ddddddd"],
    # y=1 — перила с калиткой и сам посев
    ["qqqgqqq",
     "q*****q",
     "q*****q",
     "q**.**q",
     "q*****q",
     "qK****q",
     "qqqqqqq"],
    # y=2 — фонари на угловых столбах: помост висит над лесом,
    # и ночью сад надо видеть с земли
    ["t.....t",
     ".......",
     ".......",
     ".......",
     ".......",
     ".......",
     "t.....t"],
]

# --- кладовая эльфов, 7x5 ---
ELF_WAREHOUSE = bower([
    "{{{D{{{",
    "{S...S{",
    "{..O..{",
    "{S...S{",
    "{{{{{{{",
], height=5, door=(3, 0), panes=((0, 2), (6, 2)))

# --- мастерская резчика, 5x5 ---
ELF_BUILDER_HUT = bower([
    "{{D{{",
    "{K.S{",
    "{...{",
    "{S.O{",
    "{{{{{",
], height=5, door=(2, 0), panes=ELF_PANES_SMALL)

# --- прядильня эльфов, 5x5 ---
ELF_WEAVERY = bower([
    "{{D{{",
    "{.K.{",
    "{...{",
    "{S.O{",
    "{{{{{",
], height=5, door=(2, 0), panes=ELF_PANES_SMALL)

# --- лавка эльфов, 5x5 ---
ELF_MARKET_STALL = bower([
    "{{D{{",
    "{.K.{",
    "{...{",
    "{S.O{",
    "{{{{{",
], height=5, door=(2, 0), panes=ELF_PANES_SMALL)

# --- торг эльфов, 7x7 ---
ELF_MARKET = bower([
    "{{{D{{{",
    "{K...K{",
    "{.....{",
    "{%...%{",
    "{.....{",
    "{S.O.S{",
    "{{{{{{{",
], height=6, door=(3, 0), panes=ELF_PANES)

# --- дозорный помост, 5x5 ---
#
# Единственный народ, которому дозорная башня не нужна: они и так живут
# выше леса. Поэтому у них не башня, а помост — стены в один ряд,
# чтобы смотреть, а не прятаться.
ELF_WATCHPOST = bower([
    "{{D{{",
    "{K.O{",
    "G...G",
    "{E.S{",
    "{{{{{",
], height=4, door=(2, 0), panes=())

# --- святилище эльфов, 7x7 ---
#
# Самый высокий помост деревни и самый открытый: стены в два ряда окон,
# алтарь под пологом. То же решение, что у норманнской часовни и гномьего
# алтаря — высота читается как святость дешевле любого убранства.
ELF_SHRINE = bower([
    "{{{D{{{",
    "{:...:{",
    "{:...:{",
    "{.....{",
    "{:...:{",
    "{.}$}.{",
    "{{{{{{{",
], height=7, door=(3, 0), panes=ELF_PANES)


# ======================= СЕВЕРЯНЕ: ШЕСТОЙ НАРОД =======================
#
# Заказчик: «пусть каждый народ будет уникальным, всё своё, прям чтоб вау
# было». Сначала северянам схемы переводились с пони — акация в ель, —
# и деревня выходила деревней пони в другом цвете. Теперь у них свой дом.
#
# Чем северянин узнаётся издалека:
#   * ДЛИННЫЙ ДОМ. Сруб вытянут вдоль конька, вход с длинной стороны,
#     стены низкие — в два венца, — и кровля начинается почти от земли:
#     в снежном краю тепло держит крыша, а не стена.
#   * СРУБ ИЗ ЛЕЖАЧИХ БРЁВЕН. Венцы вдоль стены, столбы по углам —
#     не тёс на каркасе, как у пони, а бревно на бревне.
#   * ДРАКОНЬИ РОГА. Конёк — цельное бревно, и на обоих его концах торчат
#     жерди: силуэт кровли с рогами виден с другого берега фьорда.
#   * СИНИЕ СТЯГИ на стенах зала и у алтаря и ОЧАГ посреди дома.
#
# Проверки те же, что у всех: вход с земли, стройка с опоры, свет,
# свободный порог. Схемы пишутся кириллицей, и в ней есть двойники
# латиницы: «с» — саженец, «c» — костёр; «е» — доска, «e» — ступень;
# «к» — синяя постель, «k» — лестница. Сверка идёт по палитре готового
# файла, а не по глазам.


def dragon_roof(width, depth, window="G"):
    """Крутая еловая кровля с коньком-бревном и рогами на его концах."""
    roof = steep_gable(width, depth, "ю", "н", "е", "ж", beam="л", window=window)
    middle = depth // 2
    roof.append([("ф" + "." * (width - 2) + "ф") if z == middle else "." * width
                 for z in range(depth)])
    return roof


def nord_hut(ground, upper, width, depth):
    """Сруб в два венца на каменном цоколе под драконьей кровлей.

    Под коньком посередине висит фонарь: у сруба с полой кровлей нет
    плоского потолка, под которым генератор нашёл бы место факелу, а
    фонарь на цепи с конькового бревна и есть северный светильник.
    """
    roof = dragon_roof(width, depth)
    under_ridge = roof[-3]
    row = under_ridge[depth // 2]
    middle = width // 2
    under_ridge[depth // 2] = row[:middle] + "'" + row[middle + 1:]
    return [["C" * width] * depth, ground, upper] + roof


def svalgang(base, posts="л", deck="е", eave="н"):
    """Крытая галерея вдоль южной стены: столбы, настил и скат-навес.

    Свалганг — открытая галерея северных срубов, где сидят летом и
    сушат зимой. Ратуша растёт ею вширь, а не вверх, и только на юг:
    якорь здания — северо-западный угол, и он остаётся на месте.
    """
    width = len(base[0][0])
    layers = [list(layer) + ["." * width, "." * width] for layer in base]
    layers[0][-2:] = ["C" * width, "C" * width]
    pillars = "".join(posts if x % 2 == 0 else "." for x in range(width))
    benches = list("." * width)
    for x in (1, width - 2):
        benches[x] = "="
    for x in (3, width - 4):
        benches[x] = "O"
    lamps = list("." * width)
    for x in (2, width - 3):
        lamps[x] = "'"
    layers[1][-2:] = ["".join(benches), pillars]
    layers[2][-2:] = ["".join(lamps), pillars]
    layers[3][-2:] = [deck * width, eave * width]
    return layers


# --- изба северян, уровень 1: длинный дом 9x5 ---
#
# Вход посередине длинной стены, против него очаг. Слева лежанки, справа
# стол с лавкой и полка, по стенам синие стяги.
NORD_HOUSE = nord_hut(
    ["лжжжDжжжл",
     "зкк...:Sз",
     "згг.c.%;з",
     "зO..O...з",
     "лжжжжжжжл"],
    ["лжGж.жGжл",
     "з.......з",
     "G......ыз",
     "з...ш...з",
     "лжGжжжGжл"],
    9, 5)

# --- изба северян, уровень 2: полати над столом ---
#
# Вторым уровнем дом не надстраивается сплошным этажом, а поднимает стены
# и получает полати — помост под крышей над восточным концом, с перилами
# и ещё двумя постелями. Над очагом по-прежнему открыто до самой кровли:
# дым уходит вверх, а зал становится высоким, как настоящий скаали.
NORD_HOUSE_2 = storey(
    NORD_HOUSE,
    ["жжжжжжжжж",
     "з....ееез",
     "з....ееез",
     "з....ееkз",
     "жжжжжжжжж"],
    [
        ["лжжжжжжжл",
         "з....фккз",
         "з....фггз",
         "з....ф..з",
         "лжжжжжжжл"],
        ["лжGжжжGжл",
         "з.......з",
         "G.......G",
         "з.......з",
         "лжGжжжGжл"],
    ],
    0,
    extra=((1, 7, 3, "k"), (2, 7, 3, "k")),
    roof=dragon_roof(9, 5))

# --- ратуша северян, уровень 1: палаты ярла 9x7 ---
#
# Один высокий зал: лавки вдоль длинных стен, посередине долгий очаг
# в каменной оправе, в восточном торце — место ярла под тремя стягами.
NORD_TOWN_HALL = nord_hut(
    ["лжжжDжжжл",
     "зS==.==Oз",
     "з.......з",
     "з..CcC.Kз",
     "з.......з",
     "зO::.::Eз",
     "лжжжжжжжл"],
    ["лжGж.жGжл",
     "з.......з",
     "з......ыз",
     "G......ыз",
     "з......ыз",
     "з.......з",
     "лжжGжGжжл"],
    9, 7)

# --- ратуша северян, уровень 2: верхние палаты с каменной трубой ---
NORD_TOWN_HALL_2 = with_flue(storey(
    NORD_TOWN_HALL,
    ["жжжжжжжжж",
     "зееееееез",
     "зxеееееез",
     "зееееееез",
     "зееееееез",
     "зееееееез",
     "жжжжжжжжж"],
    [
        ["лжжжжжжжл",
         "з.S...ккз",
         "з.....ггз",
         "з.......з",
         "зS......з",
         "зO.....Oз",
         "лжжжжжжжл"],
        ["лжGжжжGжл",
         "з.......з",
         "з.......з",
         "G.......G",
         "з.......з",
         "з.......з",
         "лжGжжжGжл"],
    ],
    0,
    extra=((1, 1, 2, "x"), (2, 1, 2, "x")),
    roof=dragon_roof(9, 7)), 4, 3, 3, 4, stone="C")

# --- ратуша северян, уровень 3: галерея-свалганг на юг ---
NORD_TOWN_HALL_3 = svalgang(NORD_TOWN_HALL_2)


def thing_yard(base):
    """Четвёртый уровень: двор тинга за палатами — стена, стяги, колокол.

    Тинг — народное собрание северян, и столица узнаётся по нему: мощёный
    двор за галереей, обнесённый камнем, с колоколом посередине, двумя
    кострами по бокам и стягами у ворот.
    """
    width = len(base[0][0])
    layers = [list(layer) + ["." * width] * 3 for layer in base]
    layers[0][-3:] = ["C" * width] * 3
    middle = width // 2
    gate = list("ъ" * width)
    gate[middle - 1], gate[middle], gate[middle + 1] = "ё", "ц", "ё"
    side = "ъ" + "." * (width - 2) + "ъ"
    # Колокол — у галереи, а не у ворот: клетка за воротами обязана
    # быть свободной, как за любой дверью.
    bell = list(side)
    bell[middle] = "J"
    fires = list(side)
    fires[1], fires[width - 2] = "c", "c"
    layers[1][-3:] = ["".join(bell), "".join(fires), "".join(gate)]
    layers[2][-3:] = ["." * width, "." * width, "t" + "." * (width - 2) + "t"]
    return layers


NORD_TOWN_HALL_4 = thing_yard(NORD_TOWN_HALL_3)

# --- поле северян: каменная ограда и стога на жердях ---
#
# Поле обнесено не жердями, а сухой кладкой из булыжника — так огораживают
# каменистую землю, где камень выходит из пашни сам. Сено сушат не в снопах,
# а на жердях, как северяне сушат его до сих пор, а по углам ограды горят
# фонари.
NORD_FARM = [
    ["ddddddd"] * 7,
    ["ddddddd",
     "dFFFFFd",
     "dFF~FFd",
     "dFFFFFd",
     "dFFFFFd",
     "dFFFFFd",
     "ddddddd"],
    ["ъъъъъъъ",
     "в*****ъ",
     "ъ**.**ъ",
     "ъ*ф*ф*ъ",
     "ъ**K**ъ",
     "ъ*****ъ",
     "ъъъъъъъ"],
    ["t.....t",
     ".......",
     ".......",
     "..A.A..",
     ".......",
     ".......",
     "t.....t"],
]

NORD_FARM_2 = [
    ["ddddddddd"] * 9,
    ["ddddddddd",
     "dFFFFFdFd",
     "dFF~FFdFd",
     "dFFFFFdFd",
     "dFFFFFdFd",
     "dFFFFFdFd",
     "dddddddFd",
     "dFFFFFFFd",
     "ddddddddd"],
    ["ъъъъъъъъъ",
     "в*******ъ",
     "ъ**.****ъ",
     "ъ*ф*ф***ъ",
     "ъ**K****ъ",
     "ъ*******ъ",
     "ъ*******ъ",
     "ъ*******ъ",
     "ъъъъъъъъъ"],
    ["t.......t",
     ".........",
     ".........",
     "..A.A....",
     ".........",
     ".........",
     ".........",
     ".........",
     "t.......t"],
]

# --- лесоруб северян: сруб у ельника ---
NORD_LUMBERJACK = [
    ["CCCCCddddd"] * 5,
    ["лжDжлффффф",
     "зK.Sзфс.сф",
     "з...зф.O.в",
     "з.O.зфс.сф",
     "лжжжлффффф"],
    ["лж.жлt...t",
     "з...з.....",
     "G...G.....",
     "з...з.....",
     "лжGжлt...t"],
] + [[row + "....." for row in layer] for layer in dragon_roof(5, 5)]

# --- склад северян: длинный амбар 7x5 ---
NORD_WAREHOUSE = nord_hut(
    ["лжжDжжл",
     "зS...Sз",
     "з..O..з",
     "зS...Sз",
     "лжжжжжл"],
    ["лжж.жжл",
     "з.....з",
     "G.....G",
     "з.....з",
     "лжGжGжл"],
    7, 5)

NORD_BUILDER_HUT = nord_hut(
    ["лжDжл",
     "зK.Sз",
     "з...з",
     "зS.Oз",
     "лжжжл"],
    ["лж.жл",
     "з...з",
     "G...G",
     "з...з",
     "лжGжл"],
    5, 5)

# Медоварня: котёл, бочка и вторая бочка поверх первой.
NORD_BREWERY = nord_hut(
    ["лжDжл",
     "з.K.з",
     "з0.Oз",
     "зS.vз",
     "лжжжл"],
    ["лж.жл",
     "з...з",
     "G...G",
     "з..vз",
     "лжGжл"],
    5, 5)

# Ткацкая: станок, шерсть и стяг на стене — ткачи их и ткут.
NORD_WEAVERY = nord_hut(
    ["лжDжл",
     "з.K.з",
     "з@..з",
     "зS.#з",
     "лжжжл"],
    ["лж.жл",
     "з...з",
     "G...G",
     "з.ш.з",
     "лGжGл"],
    5, 5)

# --- ларёк и торг северян: навесы на столбах, фонари на задней стене ---
NORD_MARKET_STALL = [
    ["CCCCC"] * 5,
    ["лжжжл",
     "..K..",
     ".S.O.",
     ".....",
     "л.D.л"],
    ["лt.tл",
     ".....",
     ".....",
     ".....",
     "л...л"],
] + dragon_roof(5, 5)

NORD_MARKET = [
    ["CCCCCCC"] * 7,
    ["лжжжжжл",
     "..KvK..",
     ".S...S.",
     ".O...O.",
     ".......",
     ".......",
     "лё.D.ёл"],
    ["лt...tл",
     ".......",
     ".......",
     ".......",
     ".......",
     ".......",
     "л.....л"],
] + dragon_roof(7, 7)

# --- дозорная башня северян: сруб с сигнальным костром наверху ---
#
# Варде — сигнальный огонь на вершине: увидел врага — зажги, и соседняя
# башня зажжёт свой. Костёр горит на площадке всегда, и башню северян
# ночью видно за полмира. Углы площадки венчают рога.
NORD_WATCHTOWER = [
    ["CCCCC"] * 5,
    ["лжDжл", "з...з", "зx..з", "з...з", "лжжжл"],
    ["лж.жл", "з...з", "зx..з", "з...з", "лжGжл"],
    ["лжжжл", "з...з", "зx..G", "з...з", "лжжжл"],
    ["лжGжл", "з...з", "зx..з", "з...з", "лжGжл"],
    ["лжжжл", "з...з", "зx..з", "з...з", "лжжжл"],
    ["лееел", "еееее", "еxеее", "еееее", "лееел"],
    ["л...л", ".....", ".xK..", "...c.", "л...л"],
    ["лж.жл", "з...з", "л...л", "з...з", "лж.жл"],
    ["ф...ф", ".....", ".....", ".....", "ф...ф"],
]

# --- капище северян ---
#
# Лавки у входа, алтарь в дальнем конце между двумя кострами, по стенам
# стяги. Над коньком — мачта с фонарём: капище видно поверх изб.
NORD_SHRINE = nord_hut(
    ["лжжDжжл",
     "з=...=з",
     "з=...=з",
     "з.....з",
     "з.....з",
     "зc.$.cз",
     "лжжжжжл"],
    ["лжG.Gжл",
     "з.....з",
     "G.....G",
     "з.....з",
     "зэ...ыз",
     "з..ш..з",
     "лжжжжжл"],
    7, 7)
NORD_SHRINE[-1] = [row if z != 3 else "ф..л..ф" for z, row in enumerate(NORD_SHRINE[-1])]
NORD_SHRINE.append(["...t..." if z == 3 else "......." for z in range(7)])


RAW_SCHEMATICS = {
    "norman/town_hall_lvl1": NORMAN_TOWN_HALL,
    "norman/town_hall_lvl2": NORMAN_TOWN_HALL_2,
    "norman/house_lvl1": NORMAN_HOUSE,
    "norman/house_lvl2": NORMAN_HOUSE_2,
    "norman/lumberjack_lvl1": NORMAN_LUMBERJACK,
    "norman/farm_lvl1": NORMAN_FARM,
    "norman/farm_lvl2": NORMAN_FARM_2,
    "maya/town_hall_lvl1": MAYA_TOWN_HALL,
    "maya/town_hall_lvl2": MAYA_TOWN_HALL_2,
    "maya/house_lvl1": MAYA_HOUSE,
    "maya/house_lvl2": MAYA_HOUSE_2,
    "maya/lumberjack_lvl1": MAYA_LUMBERJACK,
    "maya/farm_lvl1": MAYA_FARM,
    "maya/farm_lvl2": MAYA_FARM_2,
    "norman/warehouse_lvl1": NORMAN_WAREHOUSE,
    "norman/builder_hut_lvl1": NORMAN_BUILDER_HUT,
    "maya/warehouse_lvl1": MAYA_WAREHOUSE,
    "maya/builder_hut_lvl1": MAYA_BUILDER_HUT,
    "norman/brewery_lvl1": NORMAN_BREWERY,
    "maya/brewery_lvl1": MAYA_BREWERY,
    "norman/weavery_lvl1": NORMAN_WEAVERY,
    "maya/weavery_lvl1": MAYA_WEAVERY,
    "norman/town_hall_lvl3": NORMAN_TOWN_HALL_3,
    "maya/town_hall_lvl3": MAYA_TOWN_HALL_3,
    "norman/market_stall_lvl1": NORMAN_MARKET_STALL,
    "maya/market_stall_lvl1": MAYA_MARKET_STALL,
    "norman/town_hall_lvl4": NORMAN_TOWN_HALL_4,
    "maya/town_hall_lvl4": MAYA_TOWN_HALL_4,
    "norman/market_lvl1": NORMAN_MARKET,
    "maya/market_lvl1": MAYA_MARKET,
    "norman/watchtower_lvl1": NORMAN_WATCHTOWER,
    "maya/watchtower_lvl1": MAYA_WATCHTOWER,
    "norman/chapel_lvl1": NORMAN_CHAPEL,
    "maya/shrine_lvl1": MAYA_SHRINE,
    "pony/town_hall_lvl1": PONY_TOWN_HALL,
    "pony/town_hall_lvl2": PONY_TOWN_HALL_2,
    "pony/town_hall_lvl3": PONY_TOWN_HALL_3,
    "pony/town_hall_lvl4": PONY_TOWN_HALL_4,
    "pony/house_lvl1": PONY_HOUSE,
    "pony/house_lvl2": PONY_HOUSE_2,
    "pony/farm_lvl1": PONY_FARM,
    "pony/farm_lvl2": PONY_FARM_2,
    "pony/lumberjack_lvl1": PONY_LUMBERJACK,
    "pony/warehouse_lvl1": PONY_WAREHOUSE,
    "pony/builder_hut_lvl1": PONY_BUILDER_HUT,
    "pony/brewery_lvl1": PONY_BREWERY,
    "pony/weavery_lvl1": PONY_WEAVERY,
    "pony/market_stall_lvl1": PONY_MARKET_STALL,
    "pony/market_lvl1": PONY_MARKET,
    "pony/watchtower_lvl1": PONY_WATCHTOWER,
    "pony/shrine_lvl1": PONY_SHRINE,
    "nord/town_hall_lvl1": NORD_TOWN_HALL,
    "nord/town_hall_lvl2": NORD_TOWN_HALL_2,
    "nord/town_hall_lvl3": NORD_TOWN_HALL_3,
    "nord/town_hall_lvl4": NORD_TOWN_HALL_4,
    "nord/house_lvl1": NORD_HOUSE,
    "nord/house_lvl2": NORD_HOUSE_2,
    "nord/farm_lvl1": NORD_FARM,
    "nord/farm_lvl2": NORD_FARM_2,
    "nord/lumberjack_lvl1": NORD_LUMBERJACK,
    "nord/warehouse_lvl1": NORD_WAREHOUSE,
    "nord/builder_hut_lvl1": NORD_BUILDER_HUT,
    "nord/brewery_lvl1": NORD_BREWERY,
    "nord/weavery_lvl1": NORD_WEAVERY,
    "nord/market_stall_lvl1": NORD_MARKET_STALL,
    "nord/market_lvl1": NORD_MARKET,
    "nord/watchtower_lvl1": NORD_WATCHTOWER,
    "nord/shrine_lvl1": NORD_SHRINE,
    "dwarf/town_hall_lvl1": DWARF_TOWN_HALL,
    "dwarf/town_hall_lvl2": DWARF_TOWN_HALL_2,
    "dwarf/town_hall_lvl3": DWARF_TOWN_HALL_3,
    "dwarf/town_hall_lvl4": DWARF_TOWN_HALL_4,
    "dwarf/house_lvl1": DWARF_HOUSE,
    "dwarf/house_lvl2": DWARF_HOUSE_2,
    "dwarf/farm_lvl1": DWARF_FARM,
    "dwarf/warehouse_lvl1": DWARF_WAREHOUSE,
    "dwarf/builder_hut_lvl1": DWARF_BUILDER_HUT,
    "dwarf/brewery_lvl1": DWARF_BREWERY,
    "dwarf/market_stall_lvl1": DWARF_MARKET_STALL,
    "dwarf/market_lvl1": DWARF_MARKET,
    "dwarf/gatehouse_lvl1": DWARF_GATEHOUSE,
    "dwarf/shrine_lvl1": DWARF_SHRINE,
    "elf/town_hall_lvl1": ELF_TOWN_HALL,
    "elf/town_hall_lvl2": ELF_TOWN_HALL_2,
    "elf/town_hall_lvl3": ELF_TOWN_HALL_3,
    "elf/town_hall_lvl4": ELF_TOWN_HALL_4,
    "elf/house_lvl1": ELF_HOUSE,
    "elf/house_lvl2": ELF_HOUSE_2,
    "elf/farm_lvl1": ELF_FARM,
    "elf/warehouse_lvl1": ELF_WAREHOUSE,
    "elf/builder_hut_lvl1": ELF_BUILDER_HUT,
    "elf/weavery_lvl1": ELF_WEAVERY,
    "elf/market_stall_lvl1": ELF_MARKET_STALL,
    "elf/market_lvl1": ELF_MARKET,
    "elf/watchtower_lvl1": ELF_WATCHPOST,
    "elf/shrine_lvl1": ELF_SHRINE,
}


# Свет добавляется одним местом на весь мод, а не тридцатью правками
# в тексте схем: новое здание получает фонарь само, и забыть про него
# нельзя. Проверка целости и запись на диск идут уже по этому словарю,
# поэтому фонарь виден и второму уровню.
SCHEMATICS = {name: light_up(name, layers) for name, layers in RAW_SCHEMATICS.items()}


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


# Сколько блоков первого этажа второму уровню позволено переделать.
# Пятая часть — это ещё надстройка; больше — уже снос.
ALLOWED_CHANGE = 0.2


def containment_check(name, low, high):
    """Второй уровень обязан сохранять первый этаж первого.

    Правило заказчика: улучшение делает здание лучше, а не ломает и
    создаёт новое. Проверяется здесь же, при генерации, потому что
    увидеть нарушение в игре можно только разобрав дом по блокам.
    """
    # След может РАСТИ, но только на восток и юг: тогда локальные
    # координаты первого уровня не сдвигаются, якорь остаётся тем же,
    # и улучшение никуда не переносит здание. Рост назад или уменьшение
    # означали бы перенос — а игрок ставил здание сам и выбирал место.
    if len(high[0][0]) < len(low[0][0]) or len(high[0]) < len(low[0]):
        raise ValueError(f"{name}: след второго уровня меньше первого — "
                         f"улучшение сносило бы часть здания")

    kept = same = 0
    for y in range(min(3, len(low))):
        for z, row in enumerate(low[y]):
            for x, symbol in enumerate(row):
                if symbol == ".":
                    continue
                kept += 1
                if high[y][z][x] == symbol:
                    same += 1

    # Допуск есть, и он нужен по делу. У дома и ратуши первый этаж
    # сохраняется целиком, а у поля улучшение — это как раз превращение
    # земляной каймы в грядки: законная переделка, а не снос. Порог
    # ловит настоящую беду — перестройку с нуля, где меняется всё.
    changed = kept - same
    if changed > kept * ALLOWED_CHANGE:
        raise ValueError(f"{name}: второй уровень меняет {changed} блоков первого этажа "
                         f"из {kept} — это перестройка, а не надстройка")
    return kept, changed


def main():
    # Пары берутся из готового словаря, а не из исходных списков: фонарь
    # висит в готовой схеме, и проверять надо то, что ляжет на диск.
    for low, high in (("norman/town_hall_lvl1", "norman/town_hall_lvl2"),
                      ("norman/house_lvl1", "norman/house_lvl2"),
                      ("norman/farm_lvl1", "norman/farm_lvl2"),
                      ("maya/town_hall_lvl1", "maya/town_hall_lvl2"),
                      ("maya/house_lvl1", "maya/house_lvl2"),
                      ("maya/farm_lvl1", "maya/farm_lvl2"),
                      ("norman/town_hall_lvl3", "norman/town_hall_lvl4"),
                      ("maya/town_hall_lvl3", "maya/town_hall_lvl4"),
                      ("pony/town_hall_lvl1", "pony/town_hall_lvl2"),
                      ("pony/house_lvl1", "pony/house_lvl2"),
                      ("pony/farm_lvl1", "pony/farm_lvl2"),
                      ("pony/town_hall_lvl3", "pony/town_hall_lvl4"),
                      ("nord/town_hall_lvl1", "nord/town_hall_lvl2"),
                      ("nord/house_lvl1", "nord/house_lvl2"),
                      ("nord/farm_lvl1", "nord/farm_lvl2"),
                      ("dwarf/town_hall_lvl1", "dwarf/town_hall_lvl2"),
                      ("dwarf/town_hall_lvl3", "dwarf/town_hall_lvl4"),
                      ("dwarf/house_lvl1", "dwarf/house_lvl2"),
                      ("elf/town_hall_lvl1", "elf/town_hall_lvl2"),
                      ("elf/town_hall_lvl3", "elf/town_hall_lvl4"),
                      ("elf/house_lvl1", "elf/house_lvl2")):
        pair = (low, SCHEMATICS[low], SCHEMATICS[high])
        kept, changed = containment_check(*pair)
        print(f"{pair[0]}: второй уровень сохраняет {kept - changed} блоков первого этажа "
              f"из {kept}, меняет {changed}")

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
