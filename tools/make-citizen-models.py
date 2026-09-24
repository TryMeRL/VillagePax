#!/usr/bin/env python3
"""Пишет geo-модели жителей из описания тел в `citizen_body.py`.

Модель не правится руками: кости, кубы и раскладка развёртки живут
в одном модуле, и его же читает генератор кож. Поправил тело там —
запусти этот сценарий и `make-citizen-textures.py`, и модель с кожей
сойдутся сами.

    python tools/make-citizen-models.py
"""

import json
from pathlib import Path

import citizen_body

ROOT = Path(__file__).resolve().parent.parent
GEO = ROOT / "src/main/resources/assets/villagepax/geo/entity"


def number(value):
    """Целое без дробной части: `4`, а не `4.0` — модель читают и глазами."""
    return int(value) if float(value).is_integer() else round(value, 3)


def vector(values):
    return [number(v) for v in values]


def cube_json(cube):
    out = {"origin": vector(cube.origin), "size": vector(cube.size)}
    if cube.texel is not None:
        # Одна краска на все грани: веко — это кусочек кожи, а не рисунок.
        u, v = cube.texel
        face = {"uv": [u, v], "uv_size": [1, 1]}
        out["uv"] = {name: face for name in ("north", "south", "east", "west", "up", "down")}
    elif cube.flat:
        w, h, d = cube.texels()
        u, v = cube.uv
        top = {"uv": [u, v], "uv_size": [w, d]}
        side = {"uv": [u, v + d], "uv_size": [w, max(h, 1)]}
        edge = {"uv": [u, v + d], "uv_size": [d, max(h, 1)]}
        out["uv"] = {"north": side, "south": side, "east": edge, "west": edge,
                     "up": top, "down": top}
    else:
        out["uv"] = list(cube.uv)
    if cube.inflate:
        out["inflate"] = number(cube.inflate)
    if cube.mirror:
        out["mirror"] = True
    if cube.rotation:
        out["pivot"] = vector(cube.pivot or cube.origin)
        out["rotation"] = vector(cube.rotation)
    return out


def bone_json(bone):
    out = {"name": bone.name}
    if bone.parent:
        out["parent"] = bone.parent
    out["pivot"] = vector(bone.pivot)
    if bone.rotation:
        out["rotation"] = vector(bone.rotation)
    if bone.cubes:
        out["cubes"] = [cube_json(cube) for cube in bone.cubes]
    return out


def model_json(body):
    width, height, offset = body.bounds
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": body.identifier,
                "texture_width": body.width,
                "texture_height": body.height,
                "visible_bounds_width": number(width),
                "visible_bounds_height": number(height),
                "visible_bounds_offset": [0, number(offset), 0],
            },
            "bones": [bone_json(bone) for bone in body.bones],
        }],
    }


def compact(value, indent=0):
    """JSON с короткими списками в строку: кость читается целиком на экране."""
    pad = "  " * indent
    if isinstance(value, dict):
        if not value:
            return "{}"
        items = ['%s  "%s": %s' % (pad, key, compact(item, indent + 1))
                 for key, item in value.items()]
        return "{\n" + ",\n".join(items) + "\n" + pad + "}"
    if isinstance(value, list):
        if all(not isinstance(item, (dict, list)) for item in value):
            return "[" + ", ".join(json.dumps(item) for item in value) + "]"
        items = [pad + "  " + compact(item, indent + 1) for item in value]
        return "[\n" + ",\n".join(items) + "\n" + pad + "]"
    return json.dumps(value, ensure_ascii=False)


def main():
    for name, body in citizen_body.BODIES.items():
        path = GEO / (name + ".geo.json")
        # Перевод строки LF, как велит .gitattributes: иначе на Windows
        # каждый прогон переписывал бы модель целиком в CRLF.
        path.write_text(compact(model_json(body)) + "\n", encoding="utf-8", newline="\n")
        print("модель:", path.name, "костей", len(body.bones))


if __name__ == "__main__":
    main()
