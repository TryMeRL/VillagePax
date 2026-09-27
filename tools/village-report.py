#!/usr/bin/env python3
"""Отчёт о деревнях в сохранённом мире: как они легли на землю.

Разбирает villagepax_settlements.dat и блоки вокруг каждого поселения
и отвечает на вопросы, с которых начинаются жалобы на постановку:

- не налезают ли следы друг на друга и держат ли зазор;
- нет ли ступеней вне зданий, висящих над воздухом;
- проходим ли каждый вход — четыре шага наружу, как в проверке
  descentTrouble, только низкая крона над головой не считается стеной;
- откос: насколько земля сразу за стеной отстоит от площадки.
  Вода не считается — дом у берега стоит у берега, а не над обрывом.

    python tools/village-report.py "run/saves/Новый мир888"
    python tools/village-report.py <мир> --near 200 -248 64

Мир заказчика не меняется: чтение только с диска.
"""
import argparse
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import savefile  # noqa: E402

SCHEMATICS = HERE.parent / "src/main/resources/data/villagepax/villagepax/schematics"
GAP = 3  # BuildOrders.GAP

# Не держат ног и не бьют головой: сквозь них проходят.
SOFT_NAMES = {"air", "cave_air", "void_air", "short_grass", "grass", "tall_grass", "fern",
              "large_fern", "snow", "torch", "wall_torch", "water", "dead_bush",
              "sweet_berry_bush", "lily_pad", "villagepax:paper_lantern"}
SOFT_ENDINGS = ("_flower", "_sign", "_banner", "_button", "_pressure_plate", "_sapling", "rail",
                "_tulip", "wind_chime", "wheat", "carrots", "potatoes", "beetroots", "vine",
                "cobweb", "petals", "_leaves", "dandelion", "poppy", "cornflower", "bluet",
                "daisy", "allium", "orchid", "lily_of_the_valley")
ROTATIONS = {"none": 0, "clockwise_90": 1, "180": 2, "counterclockwise_90": 3}
SIDES = {"N": (0, -1), "E": (1, 0), "S": (0, 1), "W": (-1, 0)}


def short(name):
    base = name.split("[")[0]
    return base[10:] if base.startswith("minecraft:") else base


def soft(name):
    base = short(name)
    return base in SOFT_NAMES or any(base.endswith(end) for end in SOFT_ENDINGS)


class Plans:
    """Размеры и входы схем — тем же правилом, что BuildPlanner.entrancesOf."""

    def __init__(self):
        self.known = {}

    def get(self, building):
        path = building["type"].split(":")[1] + "_lvl%d" % building.get("level", 1)
        if path not in self.known:
            nbt = savefile.load(SCHEMATICS / (path + ".nbt"))
            size = nbt["size"]
            palette = [entry["Name"] for entry in nbt["palette"]]
            filled, doors = set(), {}
            for block in nbt["blocks"]:
                name = palette[block["state"]]
                x, y, z = block["pos"]
                if name not in ("minecraft:air", "minecraft:structure_void"):
                    filled.add((x, y, z))
                if "marker_door" in name or name.endswith("_door") or "fence_gate" in name:
                    if (x, z) not in doors or y < doors[(x, z)][1]:
                        doors[(x, z)] = (x, y, z)
            entrances = []
            for (x, y, z) in doors.values():
                best, score_best = "N", -10 ** 9
                for side, (ox, oz) in SIDES.items():
                    score = 0
                    for step in range(1, 5):
                        ax, az = x + ox * step, z + oz * step
                        if ax < 0 or az < 0 or ax >= size[0] or az >= size[2]:
                            score = 100 - step
                            break
                        if (ax, y, az) in filled:
                            break
                        score = step
                    if score > score_best:
                        score_best, best = score, side
                entrances.append(((x, y, z), best))
            self.known[path] = (size, entrances)
        return self.known[path]


def footprint(plans, building):
    size, _ = plans.get(building)
    turned = building.get("rotation", "none") in ("clockwise_90", "counterclockwise_90")
    fx, fz = (size[2], size[0]) if turned else (size[0], size[2])
    ax, _, az = building["anchor"]
    return ax, az, fx, fz


def to_world(anchor, size, rotation, local):
    width, depth = size[0], size[2]
    x, y, z = local
    turn = ROTATIONS[rotation]
    tx, tz = [(x, z), (depth - 1 - z, x), (width - 1 - x, depth - 1 - z), (z, width - 1 - x)][turn]
    return anchor[0] + tx, anchor[1] + y, anchor[2] + tz


def rotate(side, rotation):
    order = ["N", "E", "S", "W"]
    return order[(order.index(side) + ROTATIONS[rotation]) % 4]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("world")
    parser.add_argument("--near", nargs=3, type=int, metavar=("X", "Z", "R"),
                        help="только поселения в R блоках от точки")
    args = parser.parse_args()

    world = Path(args.world)
    data = savefile.load(world / "data" / "villagepax_settlements.dat")["data"]
    blocks = savefile.Region(world / "region")
    plans = Plans()

    def solid(x, y, z):
        name = blocks.block(x, y, z)
        return name is not None and not soft(name)

    def ground(x, z, walk):
        for y in range(walk + 2, walk - 5, -1):
            if solid(x, y, z):
                return y
        return None

    # Убранство улиц (навес колодца из ступеней, фонари) висит над воздухом
    # по замыслу — его помнит запись поселений, и оно в счёт не идёт.
    decor = set()
    for entry in data.get("decor", []):
        for packed in entry.get("at", []):
            packed &= 0xFFFFFFFFFFFFFFFF
            x = packed >> 38
            z = (packed >> 12) & 0x3FFFFFF
            y = packed & 0xFFF
            x = x - (1 << 26) if x >= 1 << 25 else x
            z = z - (1 << 26) if z >= 1 << 25 else z
            y = y - (1 << 12) if y >= 1 << 11 else y
            decor.add((x, y, z))

    total_doors = bad_doors = 0
    for settlement in data["settlements"]:
        cx, cy, cz = settlement["center"]
        if args.near and math.hypot(cx - args.near[0], cz - args.near[1]) > args.near[2]:
            continue
        culture = settlement["culture"].split(":")[1]
        buildings = settlement["buildings"]
        boxes = [footprint(plans, b) for b in buildings]
        print("=" * 72)
        print("%s — %s, центр %s, зданий %d" % (culture, settlement.get("name", "?"),
                                              settlement["center"], len(buildings)))

        for i in range(len(buildings)):
            for j in range(i + 1, len(buildings)):
                ax, az, afx, afz = boxes[i]
                bx, bz, bfx, bfz = boxes[j]
                gap = max(bx - (ax + afx), ax - (bx + bfx), bz - (az + afz), az - (bz + bfz))
                if gap < GAP:
                    print("  ! следы ближе зазора (%d): %s и %s" % (
                        gap, buildings[i]["type"], buildings[j]["type"]))

        if culture in ("dwarf", "elf"):
            continue

        def inside(x, z):
            return any(bx <= x < bx + fx and bz <= z < bz + fz for bx, bz, fx, fz in boxes)

        floating = 0
        for x in range(cx - 40, cx + 41):
            for z in range(cz - 40, cz + 41):
                if inside(x, z):
                    continue
                for y in range(cy - 20, cy + 25):
                    name = blocks.block(x, y, z)
                    if name and "stairs" in name and (x, y, z) not in decor                             and short(blocks.block(x, y - 1, z) or "air") == "air":
                        floating += 1
        print("  ступеней над воздухом вне зданий: %d" % floating)

        cells = walls = 0
        for building in buildings:
            if building["type"].endswith("town_hall") or building.get("progress") != "done":
                continue
            size, entrances = plans.get(building)
            rotation = building.get("rotation", "none")
            for local, side in entrances:
                door = to_world(building["anchor"], size, rotation, local)
                ox, oz = SIDES[rotate(side, rotation)]
                walk, trouble = door[1], None
                for step in range(1, 5):
                    g = ground(door[0] + ox * step, door[2] + oz * step, walk)
                    if g is None:
                        break
                    if g + 1 < walk - 1:
                        trouble = "обрыв %d на шаге %d" % (walk - g - 1, step)
                        break
                    if g + 1 > walk + 1:
                        trouble = "стена %d на шаге %d" % (g + 1 - walk, step)
                        break
                    walk = g + 1
                total_doors += 1
                if trouble:
                    bad_doors += 1
                    print("  ! вход %s у %s: %s" % (door, building["type"], trouble))

            ax, az, fx, fz = footprint(plans, building)
            pad = building["anchor"][1] - 1
            for x in range(ax - 1, ax + fx + 1):
                for z in range(az - 1, az + fz + 1):
                    if inside(x, z):
                        continue
                    top = None
                    for y in range(pad + 6, pad - 7, -1):
                        name = blocks.block(x, y, z)
                        if name and short(name) == "water":
                            top = "water"
                            break
                        if solid(x, y, z):
                            top = y
                            break
                    if top is None or top == "water":
                        continue
                    cells += 1
                    if abs(top - pad) > 1:
                        walls += 1
        if cells:
            print("  откос: клеток за стенами %d, дальше блока от площадки %d (%.0f%%)" % (
                cells, walls, 100.0 * walls / cells))
    print("=" * 72)
    print("входов проверено %d, непроходимых %d" % (total_doors, bad_doors))


if __name__ == "__main__":
    main()
