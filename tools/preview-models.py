#!/usr/bin/env python3
"""Рисует блочные модели мода в трёх четвертях — чтобы смотреть на них без игры.

Зачем: заказчик дважды поймал кривизну, которой я не видел, — плоское
бельё и вещи, налезающие друг на друга. Тексты и тесты такого не ловят,
а запускать клиент ради каждой правки модели нельзя.

Здесь — маленький честный отрисовщик по тем же JSON-моделям, что читает
игра:

* наследование `parent` — и мода, и ванили (`block/cube_all`, `block/block`);
* ванильные текстуры — прямо из клиента Minecraft в кэше Loom, поэтому
  стол из дубовых досок выглядит столом, а не серым ящиком;
* развёртка по умолчанию и заданная, поворот развёртки и поворот элемента;
* буфер глубины, а не порядок элементов: мелкая вещь на полке не тонет
  в полке;
* вид с двух сторон — спереди слева и сзади справа, — потому что
  кривизна обычно прячется именно там, куда не смотрели.

Света нет, кроме яркости граней по сторонам, как у ванили.

    python tools/preview-models.py                # все модели блоков мода
    python tools/preview-models.py bench table altar
    python tools/preview-models.py --items        # модели предметов
"""

import argparse
import json
import math
import zipfile
from pathlib import Path

import numpy
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets"
OUT = ROOT / "build/model-preview.png"
CLIENT = Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"

# Яркость граней по сторонам — та же лесенка, что у ванили.
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}

_jar = None


def vanilla():
    global _jar
    if _jar is None and CLIENT.exists():
        _jar = zipfile.ZipFile(CLIENT)
    return _jar


def read(namespace, relative):
    """Файл ресурсов: наш — с диска, ванильный — из клиента."""
    if namespace == "minecraft":
        jar = vanilla()
        if jar is None:
            return None
        try:
            return jar.read("assets/minecraft/" + relative)
        except KeyError:
            return None
    path = ASSETS / namespace / relative
    return path.read_bytes() if path.exists() else None


def split(identifier, default="minecraft"):
    return identifier.split(":", 1) if ":" in identifier else (default, identifier)


def model(identifier):
    """Модель с разобранным наследованием: текстуры сливаются, элементы — последние."""
    namespace, path = split(identifier)
    raw = read(namespace, "models/" + path + ".json")
    if raw is None:
        return {"textures": {}, "elements": None}
    data = json.loads(raw)
    parent = model(data["parent"]) if "parent" in data else {"textures": {}, "elements": None}
    textures = dict(parent["textures"])
    textures.update(data.get("textures", {}))
    return {
        "textures": textures,
        "elements": data.get("elements", parent["elements"]),
        "generated": data.get("parent", "").endswith("item/generated")
        or parent.get("generated", False),
        "handheld": data.get("parent", "").endswith("item/handheld") or parent.get("handheld", False),
    }


_images = {}


def texture(textures, key):
    seen = 0
    while key.startswith("#") and seen < 8:
        key = textures.get(key[1:], "")
        seen += 1
    if not key:
        return None
    if key in _images:
        return _images[key]
    namespace, path = split(key)
    raw = read(namespace, "textures/" + path + ".png")
    image = None
    if raw is None and (namespace != "minecraft" or vanilla() is not None):
        # Игра нарисует на месте такой текстуры чёрно-фиолетовую клетку:
        # фонтан пони однажды получил «smooth_quartz», которой у ванили нет.
        print("НЕТ ТЕКСТУРЫ: %s" % key)
    if raw is not None:
        from io import BytesIO
        image = Image.open(BytesIO(raw)).convert("RGBA")
        # Анимированные картинки — столбик кадров: берём первый.
        if image.height > image.width:
            image = image.crop((0, 0, image.width, image.width))
    _images[key] = image
    return image


def default_uv(face, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]


def corners(face, a, b):
    """Угол начала развёртки, конец строки и конец столбца грани — как у ванили."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        "north": ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
        "south": ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
        "west": ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
        "east": ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)),
        "up": ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
        "down": ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1)),
    }[face]


def rotate_point(point, rotation):
    if not rotation:
        return point
    ox, oy, oz = rotation.get("origin", [8, 8, 8])
    angle = math.radians(rotation.get("angle", 0))
    c, s = math.cos(angle), math.sin(angle)
    x, y, z = point[0] - ox, point[1] - oy, point[2] - oz
    axis = rotation.get("axis", "y")
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


def quads(resolved):
    """Все непрозрачные тексели модели: четыре угла, цвет, сторона."""
    out = []
    for element in resolved["elements"] or []:
        a, b = element["from"], element["to"]
        rotation = element.get("rotation")
        for face, spec in element.get("faces", {}).items():
            image = texture(resolved["textures"], spec.get("texture", ""))
            uv = spec.get("uv") or default_uv(face, a, b)
            u1, v1, u2, v2 = uv
            p0, p1, p2 = (rotate_point(p, rotation) for p in corners(face, a, b))
            du = [p1[i] - p0[i] for i in range(3)]
            dv = [p2[i] - p0[i] for i in range(3)]
            steps_u = max(1, int(round(abs(u2 - u1))))
            steps_v = max(1, int(round(abs(v2 - v1))))
            turn = spec.get("rotation", 0) % 360
            for i in range(steps_u):
                for j in range(steps_v):
                    s, t = (i + 0.5) / steps_u, (j + 0.5) / steps_v
                    su, tv = {0: (s, t), 90: (t, 1 - s), 180: (1 - s, 1 - t),
                              270: (1 - t, s)}[turn]
                    u = u1 + (u2 - u1) * su
                    v = v1 + (v2 - v1) * tv
                    if image is None:
                        colour = (150, 150, 150, 255)
                    else:
                        tx = min(image.width - 1, max(0, int(u / 16 * image.width)))
                        ty = min(image.height - 1, max(0, int(v / 16 * image.height)))
                        colour = image.getpixel((tx, ty))
                    if colour[3] < 128:
                        continue
                    s0, s1 = i / steps_u, (i + 1) / steps_u
                    t0, t1 = j / steps_v, (j + 1) / steps_v
                    points = [tuple(p0[k] + du[k] * ss + dv[k] * tt for k in range(3))
                              for ss, tt in ((s0, t0), (s1, t0), (s1, t1), (s0, t1))]
                    out.append((points, colour, face))
    return out


def draw(resolved, yaw, pitch=30, px=10):
    size = int(26 * px)
    colour = numpy.zeros((size, size, 4), dtype=numpy.uint8)
    # Камера с юга смотрит на север: ближе тот, у кого Z больше, а восток
    # справа — как у игрока, стоящего к модели лицом с южной стороны.
    depth = numpy.full((size, size), -numpy.inf)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return size / 2 + x * px, size / 2 - y * px, z

    for points, rgba, face in quads(resolved):
        projected = [view(p) for p in points]
        xs = numpy.array([p[0] for p in projected])
        ys = numpy.array([p[1] for p in projected])
        zs = numpy.array([p[2] for p in projected])
        left, right = max(0, int(xs.min())), min(size - 1, int(math.ceil(xs.max())))
        top, bottom = max(0, int(ys.min())), min(size - 1, int(math.ceil(ys.max())))
        if left > right or top > bottom:
            continue
        gx, gy = numpy.meshgrid(numpy.arange(left, right + 1) + 0.5,
                                numpy.arange(top, bottom + 1) + 0.5)
        light = SHADE[face]
        shade = [int(c * light) for c in rgba[:3]] + [255]
        for i, j, k in ((0, 1, 2), (0, 2, 3)):
            x0, y0, x1, y1, x2, y2 = xs[i], ys[i], xs[j], ys[j], xs[k], ys[k]
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            w0 = ((x1 - gx) * (y2 - gy) - (x2 - gx) * (y1 - gy)) / area
            w1 = ((x2 - gx) * (y0 - gy) - (x0 - gx) * (y2 - gy)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * zs[i] + w1 * zs[j] + w2 * zs[k]
            window = depth[top:bottom + 1, left:right + 1]
            nearer = inside & (z > window + 1e-4)
            window[nearer] = z[nearer]
            colour[top:bottom + 1, left:right + 1][nearer] = shade
    return Image.fromarray(colour, "RGBA")


def sprite(resolved, px=10):
    size = int(26 * px)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    image = texture(resolved["textures"], "#layer0")
    if image is not None:
        big = image.resize((16 * px, 16 * px), Image.NEAREST)
        canvas.alpha_composite(big, ((size - big.width) // 2, (size - big.height) // 2))
    return canvas


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("names", nargs="*")
    parser.add_argument("--items", action="store_true", help="модели предметов, а не блоков")
    parser.add_argument("--px", type=float, default=8)
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()

    folder = "item" if args.items else "block"
    root = ASSETS / "villagepax/models" / folder
    names = args.names or sorted(p.stem for p in root.glob("*.json"))
    cells = []
    for name in names:
        resolved = model("villagepax:%s/%s" % (folder, name))
        if resolved["elements"]:
            cells.append((name, [draw(resolved, -35, px=args.px), draw(resolved, 145, px=args.px)]))
        elif resolved.get("generated") or resolved.get("handheld"):
            cells.append((name, [sprite(resolved, px=int(args.px))]))

    cell = int(26 * args.px)
    columns = 8
    width = cell * 2
    rows = (len(cells) + columns // 2 - 1) // (columns // 2)
    sheet = Image.new("RGBA", (width * (columns // 2), (cell + 14) * max(1, rows)),
                      (30, 32, 38, 255))
    label = ImageDraw.Draw(sheet)
    for index, (name, images) in enumerate(cells):
        x = (index % (columns // 2)) * width
        y = (index // (columns // 2)) * (cell + 14)
        for offset, image in enumerate(images):
            sheet.alpha_composite(image, (x + offset * cell, y))
        label.text((x + 4, y + cell), name, fill=(210, 210, 210, 255))
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out)
    print("лист:", args.out, "моделей:", len(cells))


if __name__ == "__main__":
    main()
