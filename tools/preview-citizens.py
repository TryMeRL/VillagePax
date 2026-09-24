#!/usr/bin/env python3
"""Рисует жителей по тем же geo-моделям, кожам и движениям, что читает игра.

Зачем: модель жителя собрана из трёх десятков костей с приметами,
и ошибиться в ней можно молча — игра не скажет ничего, просто борода
повиснет в воздухе, а поля шляпы уйдут в затылок. Прежний просмотрщик
раскладывал кубы плашками на плоскости и для второго слоя, поворотов
и примет был слеп. Этот — маленький честный отрисовщик в три четверти:

* оси и знаки поворотов те же, что у GeckoLib (X зеркалится, повороты
  по X и Y меняют знак, порядок — Z, Y, X);
* второй слой с отступом, зеркальные кубы, развёртка по граням;
* приметы по условию в имени кости — тем же правилом, что в игре;
* сложение народа — из того же `stature`, что объявил датапак;
* поза — из того же файла движений: `--pose walk@0.25`.

Света и теней нет, кроме яркости граней по нормали: проверяется облик,
и именно в нём ошибаются.

    python tools/preview-citizens.py
    python tools/preview-citizens.py --crafts guard,farmer --pose walk@0.25
    python tools/preview-citizens.py --people dwarf --views 0,90,180
"""

import argparse
import json
import math
from pathlib import Path

import numpy
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/villagepax"
GEO = ASSETS / "geo/entity"
DANCES = ASSETS / "animations/entity"
SKINS = ASSETS / "textures/entity/citizen"
CULTURES = ROOT / "src/main/resources/data/villagepax/villagepax/cultures"
OUT = ROOT / "build/citizen-preview.png"


# --- сложение: то же правило, что в Stature.java -------------------------------


def stature_of(height):
    """Рост, ширина и голова — тем же правилом, что в Stature.java."""
    if not height or height <= 0:
        height = 1.0
    shortfall = 1.0 - height
    return height, 1.0 + shortfall * 0.8, 1.0 + shortfall * 0.5


# --- условия примет: то же правило, что в CitizenGeoModel ----------------------


def shown(bone_name, words):
    """Видна ли кость этому жителю.

    `beard@dwarf+male` — все слова должны совпасть; `!elder` — не должно;
    `courier|merchant` — хватит одного из вариантов.
    """
    if "@" not in bone_name:
        return True
    for term in bone_name.split("@", 1)[1].split("+"):
        negate = term.startswith("!")
        options = term.lstrip("!").split("|")
        if any(option in words for option in options) == negate:
            return False
    return True


def words_of(skin_path):
    """Народ, пол и ремесло — из пути к коже, как в Looks.words."""
    people = skin_path.parent.name
    stem = skin_path.stem
    gender, _, craft = stem.partition("_")
    return {people, gender} | ({craft} if craft else set())


# --- матрицы -------------------------------------------------------------------


def identity():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    m = identity()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def scale(x, y, z):
    m = identity()
    m[0][0], m[1][1], m[2][2] = x, y, z
    return m


def rot_x(angle):
    c, s = math.cos(angle), math.sin(angle)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def rot_y(angle):
    c, s = math.cos(angle), math.sin(angle)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rot_z(angle):
    c, s = math.cos(angle), math.sin(angle)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    x, y, z = p
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


def turn(rx, ry, rz):
    """Поворот в градусах бедрока — так, как его применяет GeckoLib."""
    return mul(mul(rot_z(math.radians(rz)), rot_y(math.radians(-ry))),
               rot_x(math.radians(-rx)))


# --- поза из файла движений ------------------------------------------------------


# Переменные шага: те же имена, что заводит CitizenGeoModel.
STRIDE = {"query.villagepax_stride_amount": 0.0, "query.villagepax_stride": 0.0}


def molang(value):
    """Число или molang-выражение — в число, с тригонометрией в градусах.

    Ровно столько molang, сколько пишут движения жителя: косинус, синус,
    минимум и переменные шага. Незнакомое выражение падает с ошибкой,
    а не молча становится нулём, — иначе просмотрщик показал бы позу,
    которой в игре нет.
    """
    if not isinstance(value, str):
        return value
    text = value
    for name, number in sorted(STRIDE.items(), key=lambda item: -len(item[0])):
        text = text.replace(name, repr(number))
    text = text.replace("math.", "")
    scope = {
        "cos": lambda d: math.cos(math.radians(d)),
        "sin": lambda d: math.sin(math.radians(d)),
        "min": min, "max": max,
        "clamp": lambda v, lo, hi: max(lo, min(hi, v)),
        "__builtins__": {},
    }
    return float(eval(text, scope))


def pose_of(dances, pose):
    """Повороты и сдвиги костей в миг `имя@время` — линейно между ключами."""
    if not pose:
        return {}
    name, _, moment = pose.partition("@")
    moment = float(moment or 0)
    dance = dances.get("animations", {}).get(name)
    if not dance:
        raise SystemExit("движения %s в файле нет" % name)
    out = {}
    for bone, channels in dance.get("bones", {}).items():
        for channel in ("rotation", "position"):
            frames = channels.get(channel)
            if frames is None:
                continue
            out.setdefault(bone, {})[channel] = sample(frames, moment)
    return out


def sample(frames, moment):
    if isinstance(frames, list):
        return [molang(v) for v in frames]
    keys = sorted((float(k), v) for k, v in frames.items())
    values = [(k, [molang(x) for x in (v if isinstance(v, list) else v.get("post", v.get("pre")))])
              for k, v in keys]
    if moment <= values[0][0]:
        return values[0][1]
    for (t0, v0), (t1, v1) in zip(values, values[1:]):
        if t0 <= moment <= t1:
            share = 0 if t1 == t0 else (moment - t0) / (t1 - t0)
            return [a + (b - a) * share for a, b in zip(v0, v1)]
    return values[-1][1]


# --- сборка --------------------------------------------------------------------


def bone_matrices(bones, pose, head_scale):
    """Матрица каждой кости: от корня вниз, как в GeoRenderer."""
    by_name = {bone["name"]: bone for bone in bones}
    done = {}

    def matrix(name):
        if name in done:
            return done[name]
        bone = by_name[name]
        parent = matrix(bone["parent"]) if bone.get("parent") else identity()
        px, py, pz = bone.get("pivot", [0, 0, 0])
        pivot = (-px, py, pz)
        rx, ry, rz = bone.get("rotation", [0, 0, 0])
        moved = pose.get(name, {})
        ax, ay, az = moved.get("rotation", [0, 0, 0])
        tx, ty, tz = moved.get("position", [0, 0, 0])
        size = head_scale if name == "head" else 1.0
        m = mul(parent, translate(-tx, ty, tz))
        m = mul(m, translate(*pivot))
        m = mul(m, turn(rx + ax, ry + ay, rz + az))
        m = mul(m, scale(size, size, size))
        m = mul(m, translate(-pivot[0], -pivot[1], -pivot[2]))
        done[name] = m
        return m

    return {name: matrix(name) for name in by_name}


def face_rects(cube, texture_w, texture_h):
    """Прямоугольники развёртки по граням — коробочные или заданные гранью."""
    uv = cube["uv"]
    w, h, d = (int(math.floor(v)) for v in cube["size"])
    if isinstance(uv, dict):
        names = {"north": "front", "south": "back", "east": "right", "west": "left",
                 "up": "top", "down": "bottom"}
        out = {}
        for key, face in uv.items():
            fu, fv = face["uv"]
            fw, fh = face.get("uv_size", [w, h])
            out[names[key]] = (fu, fv, fw, fh)
        return out
    u, v = uv
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def face_corners(x0, y0, z0, x1, y1, z1):
    """Три угла каждой грани: начало развёртки, конец строки, конец столбца.

    Мир здесь — пространство GeckoLib: правая рука жителя на +X, лицо к -Z.
    Направления развёртки выведены из того, какими краями грани
    соприкасаются в коробочной развёртке.
    """
    return {
        "front": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
        "back": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
        "right": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
        "left": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
        "top": ((x1, y1, z1), (x0, y1, z1), (x1, y1, z0)),
        "bottom": ((x1, y0, z0), (x0, y0, z0), (x1, y0, z1)),
    }


def quads_of(model, skin, pose, words, build, view):
    """Все видимые тексели модели как четырёхугольники экрана с глубиной."""
    geometry = model["minecraft:geometry"][0]
    texture_w = geometry["description"]["texture_width"]
    texture_h = geometry["description"]["texture_height"]
    sx, sy = skin.size[0] / texture_w, skin.size[1] / texture_h
    pixels = skin.load()
    height, girth, head = build
    bones = geometry["bones"]
    matrices = bone_matrices(bones, pose, head)
    hidden = set()
    for bone in bones:
        if not shown(bone["name"], words) or bone.get("parent") in hidden:
            hidden.add(bone["name"])

    world = mul(view, scale(girth, height, girth))
    out = []
    for bone in bones:
        if bone["name"] in hidden:
            continue
        m = mul(world, matrices[bone["name"]])
        for cube in bone.get("cubes", []):
            ox, oy, oz = cube["origin"]
            w, h, d = cube["size"]
            grow = cube.get("inflate", 0)
            x0 = -(ox + w) - grow
            x1 = -ox + grow
            y0, y1 = oy - grow, oy + h + grow
            z0, z1 = oz - grow, oz + d + grow
            cm = m
            if cube.get("rotation"):
                cpx, cpy, cpz = cube.get("pivot", cube["origin"])
                pivot = (-cpx, cpy, cpz)
                cm = mul(cm, translate(*pivot))
                cm = mul(cm, turn(*cube["rotation"]))
                cm = mul(cm, translate(-pivot[0], -pivot[1], -pivot[2]))
            mirror = cube.get("mirror", False)
            rects = face_rects(cube, texture_w, texture_h)
            corners = face_corners(x0, y0, z0, x1, y1, z1)
            for name, (a, b, c) in corners.items():
                source = rects.get({"right": "left", "left": "right"}.get(name, name)
                                   if mirror and not isinstance(cube["uv"], dict) else name)
                if source is None:
                    continue
                u, v, uw, vh = source
                if uw == 0 or vh == 0:
                    continue
                pa, pb, pc = apply(cm, a), apply(cm, b), apply(cm, c)
                du = [(pb[i] - pa[i]) for i in range(3)]
                dv = [(pc[i] - pa[i]) for i in range(3)]
                normal = (du[1] * dv[2] - du[2] * dv[1],
                          du[2] * dv[0] - du[0] * dv[2],
                          du[0] * dv[1] - du[1] * dv[0])
                length = math.sqrt(sum(n * n for n in normal)) or 1.0
                light = 0.62 + 0.38 * max(0.0, (-normal[2] * 0.55 + normal[1] * 0.8
                                                 - normal[0] * 0.25) / length)
                cols, rows = int(abs(uw)), int(abs(vh))
                for i in range(cols):
                    for j in range(rows):
                        tu = u + (cols - 1 - i if mirror else i) * (1 if uw > 0 else -1)
                        tv = v + j * (1 if vh > 0 else -1)
                        px_ = int(tu * sx)
                        py_ = int(tv * sy)
                        if not (0 <= px_ < skin.size[0] and 0 <= py_ < skin.size[1]):
                            continue
                        colour = pixels[px_, py_]
                        if colour[3] < 128:
                            continue
                        s0, s1 = i / cols, (i + 1) / cols
                        t0, t1 = j / rows, (j + 1) / rows
                        points = [tuple(pa[k] + du[k] * s + dv[k] * t for k in range(3))
                                  for s, t in ((s0, t0), (s1, t0), (s1, t1), (s0, t1))]
                        depth = sum(p[2] for p in points) / 4
                        shade = tuple(min(255, int(ch * light)) for ch in colour[:3])
                        out.append((depth, points, shade))
    return out


def figure(model, skin, pose, words, build, yaw, pitch, px):
    """Один житель: z-буфер, а не порядок граней.

    Художник, раскладывающий тексели по глубине их середины, путал
    близкие грани: поля шляпы и шишак шлема проваливались под макушку.
    Буфер глубины решает это попиксельно, как видеокарта.
    """
    view = mul(rot_x(math.radians(pitch)), rot_y(math.radians(yaw)))
    quads = quads_of(model, skin, pose, words, build, view)
    size = int(46 * px)
    colour = numpy.zeros((size, size, 4), dtype=numpy.uint8)
    depth = numpy.full((size, size), numpy.inf)
    middle_x, floor_y = size / 2, size - 6 * px
    for _, points, shade in quads:
        xs = numpy.array([middle_x - p[0] * px for p in points])
        ys = numpy.array([floor_y - p[1] * px for p in points])
        zs = numpy.array([p[2] for p in points])
        left, right = max(0, int(xs.min())), min(size - 1, int(math.ceil(xs.max())))
        top, bottom = max(0, int(ys.min())), min(size - 1, int(math.ceil(ys.max())))
        if left > right or top > bottom:
            continue
        gx, gy = numpy.meshgrid(numpy.arange(left, right + 1) + 0.5,
                                numpy.arange(top, bottom + 1) + 0.5)
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            x0, y0, x1, y1, x2, y2 = xs[a], ys[a], xs[b], ys[b], xs[c], ys[c]
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            w0 = ((x1 - gx) * (y2 - gy) - (x2 - gx) * (y1 - gy)) / area
            w1 = ((x2 - gx) * (y0 - gy) - (x0 - gx) * (y2 - gy)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * zs[a] + w1 * zs[b] + w2 * zs[c]
            window = depth[top:bottom + 1, left:right + 1]
            nearer = inside & (z < window - 1e-4)
            window[nearer] = z[nearer]
            colour[top:bottom + 1, left:right + 1][nearer] = shade + (255,)
    return Image.fromarray(colour, "RGBA")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--people", default="")
    parser.add_argument("--crafts", default=",farmer,guard,elder,courier,builder,merchant,weaver")
    parser.add_argument("--genders", default="male,female")
    parser.add_argument("--pose", default="")
    parser.add_argument("--views", default="-28")
    parser.add_argument("--pitch", type=float, default=12)
    parser.add_argument("--px", type=float, default=4)
    parser.add_argument("--columns", type=int, default=16)
    parser.add_argument("--poses", default="",
                        help="несколько поз через запятую: walk@0,chop@0.1 — по столбцу на позу")
    parser.add_argument("--stride", type=float, default=0.0,
                        help="пройденный путь шага (query.villagepax_stride)")
    parser.add_argument("--amount", type=float, default=0.0,
                        help="размах шага от 0 до 1 (query.villagepax_stride_amount)")
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()

    peoples = []
    for path in sorted(CULTURES.glob("*.json")):
        if args.people and path.stem not in args.people.split(","):
            continue
        declared = json.loads(path.read_text(encoding="utf-8")).get("stature", 1.0)
        peoples.append((path.stem, declared))

    STRIDE["query.villagepax_stride"] = args.stride
    STRIDE["query.villagepax_stride_amount"] = args.amount
    crafts = args.crafts.split(",")
    genders = args.genders.split(",")
    views = [float(v) for v in args.views.split(",")]
    poses = args.poses.split(",") if args.poses else [args.pose]
    cells = [(g, c, v, p) for g in genders for c in crafts for v in views for p in poses]
    cell = int(46 * args.px)
    columns = max(1, min(args.columns, len(cells)))
    lines = (len(cells) + columns - 1) // columns
    sheet = Image.new("RGBA", (cell * columns, (cell + 14) * lines * len(peoples)),
                      (30, 32, 38, 255))
    label = ImageDraw.Draw(sheet)

    for row, (people, declared) in enumerate(peoples):
        own = GEO / ("citizen_%s.geo.json" % people)
        path = own if own.exists() else GEO / "citizen.geo.json"
        model = json.loads(path.read_text(encoding="utf-8"))
        dances = json.loads((DANCES / path.name.replace(".geo.", ".animation.")).read_text(
            encoding="utf-8"))
        build = stature_of(declared)
        for column, (gender, craft, yaw, pose_name) in enumerate(cells):
            pose = {}
            for part in pose_name.split("+") if pose_name else []:
                for bone, channels in pose_of(dances, part).items():
                    pose.setdefault(bone, {}).update(channels)
            skin_path = SKINS / people / ("%s%s.png" % (gender, "_" + craft if craft else ""))
            if not skin_path.exists():
                continue
            skin = Image.open(skin_path).convert("RGBA")
            drawn = figure(model, skin, pose, words_of(skin_path), build, yaw, args.pitch,
                           args.px)
            x = (column % columns) * cell
            y = (row * lines + column // columns) * (cell + 14)
            sheet.alpha_composite(drawn, (x, y))
            label.text((x + 4, y + cell), "%s %s %s %s" % (people, gender[0], craft or "-",
                                                         pose_name or ""),
                       fill=(210, 210, 210, 255))

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out)
    print("лист:", args.out)


if __name__ == "__main__":
    main()
