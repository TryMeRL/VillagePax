"""Читалка сохранений Minecraft: NBT и регионы Anvil — без библиотек.

Нужна, чтобы разбирать миры заказчика и проверочные миры прямо с диска:
что записано в villagepax_settlements.dat и какие блоки стоят вокруг
деревни. Сохранение говорит правду быстрее, чем лог и догадки.

    import savefile
    data = savefile.load(world / "data" / "villagepax_settlements.dat")
    blocks = savefile.Region(world / "region")
    blocks.block(200, 69, -248)   # 'minecraft:grass_block[snowy=false]'
"""
import gzip
import io
import math
import os
import struct
import zlib


def _read(buf, tag):
    if tag == 1:
        return struct.unpack(">b", buf.read(1))[0]
    if tag == 2:
        return struct.unpack(">h", buf.read(2))[0]
    if tag == 3:
        return struct.unpack(">i", buf.read(4))[0]
    if tag == 4:
        return struct.unpack(">q", buf.read(8))[0]
    if tag == 5:
        return struct.unpack(">f", buf.read(4))[0]
    if tag == 6:
        return struct.unpack(">d", buf.read(8))[0]
    if tag == 7:
        n = struct.unpack(">i", buf.read(4))[0]
        return buf.read(n)
    if tag == 8:
        n = struct.unpack(">H", buf.read(2))[0]
        return buf.read(n).decode("utf-8", "replace")
    if tag == 9:
        inner = buf.read(1)[0]
        n = struct.unpack(">i", buf.read(4))[0]
        return [_read(buf, inner) for _ in range(n)]
    if tag == 10:
        out = {}
        while True:
            inner = buf.read(1)[0]
            if inner == 0:
                return out
            n = struct.unpack(">H", buf.read(2))[0]
            name = buf.read(n).decode("utf-8", "replace")
            out[name] = _read(buf, inner)
    if tag == 11:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(">%di" % n, buf.read(4 * n)))
    if tag == 12:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(">%dq" % n, buf.read(8 * n)))
    raise ValueError("неизвестный тег NBT %d" % tag)


def parse(data):
    """Разобрать несжатый NBT: корневой тег без имени."""
    buf = io.BytesIO(data)
    tag = buf.read(1)[0]
    n = struct.unpack(">H", buf.read(2))[0]
    buf.read(n)
    return _read(buf, tag)


def load(path):
    """Прочитать файл NBT — сжатый gzip или нет."""
    raw = open(path, "rb").read()
    try:
        raw = gzip.decompress(raw)
    except OSError:
        pass
    return parse(raw)


class Region:
    """Блоки мира по регионам .mca; чанки читаются по требованию и помнятся."""

    def __init__(self, folder):
        self.folder = str(folder)
        self.files = {}
        self.chunks = {}

    def chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.chunks:
            return self.chunks[key]
        path = os.path.join(self.folder, "r.%d.%d.mca" % (cx >> 5, cz >> 5))
        if path not in self.files:
            self.files[path] = open(path, "rb").read() if os.path.exists(path) else None
        data = self.files[path]
        result = None
        if data:
            index = 4 * ((cx & 31) + (cz & 31) * 32)
            offset = int.from_bytes(data[index:index + 3], "big")
            if offset:
                start = offset * 4096
                length = struct.unpack(">i", data[start:start + 4])[0]
                kind = data[start + 4]
                body = data[start + 5:start + 4 + length]
                raw = zlib.decompress(body) if kind == 2 else gzip.decompress(body)
                result = Chunk(parse(raw))
        self.chunks[key] = result
        return result

    def block(self, x, y, z):
        """Блок с состоянием, как в команде /setblock, или None вне сохранённого."""
        chunk = self.chunk(x >> 4, z >> 4)
        return chunk.block(x & 15, y, z & 15) if chunk else None


class Chunk:
    """Секции чанка: палитра и упакованные номера — как их пишет 1.20."""

    def __init__(self, nbt):
        self.nbt = nbt
        self.sections = {}
        for section in nbt.get("sections", []):
            states = section.get("block_states")
            if not states:
                continue
            names = []
            for entry in states["palette"]:
                name = entry["Name"]
                props = entry.get("Properties")
                if props:
                    name += "[" + ",".join("%s=%s" % kv for kv in sorted(props.items())) + "]"
                names.append(name)
            data = states.get("data")
            if data is None:
                self.sections[section["Y"]] = (names, None, 0)
            else:
                bits = max(4, math.ceil(math.log2(len(names))))
                self.sections[section["Y"]] = (names, [d & 0xFFFFFFFFFFFFFFFF for d in data], bits)

    def block(self, x, y, z):
        section = self.sections.get(y >> 4)
        if not section:
            return "minecraft:air"
        names, data, bits = section
        if data is None:
            return names[0]
        index = ((y & 15) * 16 + z) * 16 + x
        per = 64 // bits
        word = data[index // per]
        return names[(word >> ((index % per) * bits)) & ((1 << bits) - 1)]
