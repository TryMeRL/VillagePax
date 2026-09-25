#!/usr/bin/env python3
"""Сверяет сгенерированный арт с генераторами: то ли лежит в репозитории, что они пишут.

Зачем: весь арт мода — текстуры, модели жителей и мебели, схемы зданий —
пишется сценариями из tools/, а в репозиторий кладётся их вывод. Это
удобно (игра не запускает Python), но открывает тихую дыру: правишь
генератор и забываешь его запустить, или правишь картинку руками,
и следующий прогон генератора молча её затирает. Обе беды видны только
тогда, когда уже поздно.

Сверка запускает каждый генератор и сравнивает результат с тем, что
в последнем коммите, — **по содержимому, а не по байтам**: картинка
по пикселям, JSON разобранным, схема распакованной. Байты PNG зависят
от версии Pillow и zlib, и сравнение байтов падало бы на CI без всякой
разницы в облике.

Сверяются только файлы под src/: что генераторы трогают, то и смотрим.
После сверки рабочая копия возвращается как была.

    python tools/check-generated.py
"""

import gzip
import io
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GENERATORS = (
    "make-textures.py",
    "make-citizen-models.py",
    "make-citizen-textures.py",
    "make-furniture.py",
    "make-decor.py",
    "make-schematics.py",
    "make-recipe-advancements.py",
)


def git(*args, binary=False):
    out = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=True)
    return out.stdout if binary else out.stdout.decode("utf-8")


def committed(path):
    """Содержимое файла в последнем коммите — или None, если его там нет."""
    result = subprocess.run(["git", "show", "HEAD:" + path], cwd=ROOT, capture_output=True)
    return result.stdout if result.returncode == 0 else None


def same(path, before, after):
    """Одинаково ли по смыслу: пиксели, разобранный JSON, распакованная схема."""
    if path.endswith(".png"):
        a = Image.open(io.BytesIO(before)).convert("RGBA")
        b = Image.open(io.BytesIO(after)).convert("RGBA")
        return a.size == b.size and a.tobytes() == b.tobytes()
    if path.endswith(".json"):
        return json.loads(before) == json.loads(after)
    if path.endswith(".nbt"):
        return gzip.decompress(before) == gzip.decompress(after)
    return before.replace(b"\r\n", b"\n") == after.replace(b"\r\n", b"\n")


def main():
    # Что уже было изменено до сверки, трогать нельзя: это чужая работа.
    dirty = {p for p in git("status", "--porcelain").splitlines() if p}
    if any(line[3:].startswith("src/") for line in dirty):
        print("в src/ есть незафиксированные правки — сверка сравнивает с коммитом, "
              "сначала зафиксируй их или отложи")
        return 2

    for name in GENERATORS:
        result = subprocess.run([sys.executable, str(ROOT / "tools" / name)], cwd=ROOT,
                                capture_output=True)
        if result.returncode != 0:
            print("генератор упал: %s\n%s" % (name, result.stderr.decode("utf-8", "replace")))
            return 1

    changed = [p for p in git("diff", "--name-only", "--", "src").splitlines() if p]
    created = [p for p in git("ls-files", "--others", "--exclude-standard", "--", "src")
               .splitlines() if p]

    drift = []
    for path in changed:
        before = committed(path)
        after = (ROOT / path).read_bytes()
        if before is None or not same(path, before, after):
            drift.append(path)

    # Вернуть рабочую копию: байтовые отличия без разницы по смыслу
    # оставлять в ней незачем, а настоящие — тем более.
    if changed:
        git("checkout", "--", *changed)
    for path in created:
        (ROOT / path).unlink()

    if drift or created:
        for path in drift:
            print("генератор пишет иначе, чем лежит в репозитории:", path)
        for path in created:
            print("генератор пишет файл, которого нет в репозитории:", path)
        print("Запусти генераторы из tools/ и положи их вывод в коммит.")
        return 1
    print("сгенерированный арт совпадает с генераторами: %d сценариев" % len(GENERATORS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
