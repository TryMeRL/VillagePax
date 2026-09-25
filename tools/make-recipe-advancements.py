#!/usr/bin/env python3
"""Пишет рецептурные достижения: то, что открывает рецепты мода в книге.

Книга рецептов показывает только открытые рецепты, а открывает их
невидимое достижение — «получил что-то из того, из чего это делается».
У ванильных рецептов такие достижения есть все до одного; у рецептов
мода их не было, и ни один из них в книге не появлялся никогда: скамью
или штукатурку можно было скрафтить, только заранее зная раскладку.

Достижение выводится из самого рецепта, поэтому пишется кодом: рецепт
поменял состав — перегенерируй, и условие открытия пойдёт за ним.
Открывается рецепт от любого своего ингредиента: игрок, поднявший глину,
должен увидеть штукатурку, не дожидаясь песка.

    python tools/make-recipe-advancements.py
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src/main/resources/data/villagepax"
RECIPES = DATA / "recipes"
OUT = DATA / "advancements/recipes"

# Папка — как у ванили: раздел книги, а не тип рецепта. У камнереза
# категории нет, его изделия — те же строительные блоки.
FOLDERS = {
    "building": "building_blocks",
    "misc": "misc",
    None: "building_blocks",
}


def ingredients(recipe):
    """Все ингредиенты рецепта — по одному на предмет или тег, в порядке появления."""
    if "key" in recipe:
        raw = list(recipe["key"].values())
    elif "ingredients" in recipe:
        raw = recipe["ingredients"]
    else:
        raw = [recipe["ingredient"]]

    found = []
    for entry in raw:
        for choice in entry if isinstance(entry, list) else [entry]:
            if choice not in found:
                found.append(choice)
    return found


def criterion(choice):
    """Условие «есть в сумке» и его имя: has_planks, has_gold_ingot."""
    if "tag" in choice:
        name = "has_" + choice["tag"].split(":")[1].replace("/", "_")
        return name, {"trigger": "minecraft:inventory_changed",
                      "conditions": {"items": [{"tag": choice["tag"]}]}}
    name = "has_" + choice["item"].split(":")[1].replace("/", "_")
    return name, {"trigger": "minecraft:inventory_changed",
                  "conditions": {"items": [{"items": [choice["item"]]}]}}


def advancement(recipe_id, recipe):
    criteria = {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                                   "conditions": {"recipe": recipe_id}}}
    for choice in ingredients(recipe):
        name, body = criterion(choice)
        criteria[name] = body
    return {
        "parent": "minecraft:recipes/root",
        "criteria": criteria,
        # Одно внутреннее «или»: хватает любого ингредиента.
        "requirements": [list(criteria)],
        "rewards": {"recipes": [recipe_id]},
        "sends_telemetry_event": False,
    }


def main():
    written = []
    for path in sorted(RECIPES.glob("*.json")):
        recipe = json.loads(path.read_text(encoding="utf-8"))
        recipe_id = "villagepax:" + path.stem
        folder = FOLDERS[recipe.get("category")]
        target = OUT / folder / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(advancement(recipe_id, recipe), indent=2) + "\n",
                          encoding="utf-8", newline="\n")
        written.append(target)
    print("рецептурных достижений: %d" % len(written))


if __name__ == "__main__":
    main()
