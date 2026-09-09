package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Warehouse;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Колония умеет делать то, чего у неё нет.
 * <p>
 * Просьба игрока, и она закрывала настоящую дыру: схема дома требует
 * фахверк, ступени, доски, стёкла и кровати, а колония не умела ничего
 * из этого. Значит всё это игрок обязан был скрафтить руками и принести
 * в сундук — иначе стройка стояла. Деревня из шести домов превращалась
 * в поручение сходить к верстаку двести раз.
 * <p>
 * Рецепты берутся <b>ванильные</b>, из того же реестра, которым пользуется
 * верстак. Своей таблицы превращений у мода нет и не нужно: колония умеет
 * ровно то, что умеет игрок, и добавленный мод-рецепт достаётся ей
 * бесплатно.
 * <p>
 * Рецепты разложены <b>по выходу</b> один раз, а не перебираются каждый
 * раз заново. Разница не косметическая: ванильных рецептов верстака около
 * тысячи, и наивный перебор давал их по тысяче на каждую попытку сделать
 * предмет — а на втором ходу ещё по тысяче на каждый недостающий
 * ингредиент, то есть до сотни тысяч сравнений на один блок стены.
 * Указатель превращает это в один поиск по карте.
 * <p>
 * Превращение идёт <b>в два хода</b>, не глубже. Ступеням нужны доски,
 * доскам брёвна — одного хода не хватало, и стройка вставала на ступенях,
 * имея гору брёвен. Двух хватает всем цепочкам мода. Глубже не идём
 * намеренно: каждый ход — это обход всех рецептов игры, и бездонная
 * рекурсия по чужому датапаку однажды упёрлась бы в кольцо.
 */
public final class Crafting {

    /** Насколько глубоко колония доделывает ингредиенты ингредиентов. */
    private static final int MAX_DEPTH = 2;

    /**
     * Рецепты по предмету, который они дают.
     * <p>
     * Считается один раз на загруженный датапак. Сбрасывается по событию
     * перезагрузки — не по числу рецептов и не по времени: {@code /reload}
     * умеет заменить рецепт, не меняя их количества, и догадка «столько
     * же, значит те же» однажды подсунула бы колонии рецепт, которого
     * в датапаке уже нет.
     */
    private static Map<Item, List<CraftingRecipe>> byOutput;

    private Crafting() {
    }

    /**
     * Забыть разложенные рецепты. Зовётся на перезагрузке датапака.
     * <p>
     * Открыто наружу, потому что событие ловит запуск мода: класс
     * превращений про жизненный цикл сервера знать не обязан.
     */
    public static void forgetRecipes() {
        byOutput = null;
    }

    /**
     * Рецепты, дающие этот предмет.
     * <p>
     * Раскладка ленивая: на выделенном сервере без единой колонии она
     * не понадобится вовсе, а стоит обхода всех рецептов игры.
     */
    private static List<CraftingRecipe> recipesFor(ServerWorld world, Item want) {
        if (byOutput == null) {
            Map<Item, List<CraftingRecipe>> index = new HashMap<>();

            for (CraftingRecipe recipe : world.getRecipeManager()
                    .listAllOfType(RecipeType.CRAFTING)) {
                ItemStack output = recipe.getOutput(world.getRegistryManager());
                if (!output.isEmpty()) {
                    index.computeIfAbsent(output.getItem(), item -> new ArrayList<>()).add(recipe);
                }
            }

            byOutput = index;
            VillagePax.LOGGER.debug("Рецептов разложено по выходу: {}", index.size());
        }
        return byOutput.getOrDefault(want, List.of());
    }

    /**
     * Сделать один предмет из того, что лежит на складе.
     * <p>
     * Возвращает ложь, если рецепта нет или ингредиентов не хватает.
     * Изъятие «всё или ничего»: сперва проверяем, потом забираем, — иначе
     * половина ингредиентов исчезала бы даром.
     */
    public static boolean make(ServerWorld world, Warehouse warehouse, Item want) {
        return make(world, warehouse, want, 0);
    }

    private static boolean make(ServerWorld world, Warehouse warehouse, Item want, int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }

        for (CraftingRecipe recipe : recipesFor(world, want)) {
            ItemStack output = recipe.getOutput(world.getRegistryManager());
            Map<Item, Integer> cost = costOf(world, warehouse, recipe, depth);
            if (cost == null) {
                continue;
            }

            boolean paid = true;
            for (Map.Entry<Item, Integer> part : cost.entrySet()) {
                if (!warehouse.take(part.getKey(), part.getValue())) {
                    paid = false;
                    break;
                }
            }
            if (!paid) {
                // Списать всё сразу нельзя: часть уже ушла. Возвращаем
                // и пробуем следующий рецепт — потерянные материалы хуже
                // отказа.
                cost.forEach((item, count) -> warehouse.addOrScatter(world,
                        world.getSpawnPos(), new ItemStack(item, count)));
                continue;
            }

            warehouse.addOrScatter(world, world.getSpawnPos(), output.copy());
            return true;
        }
        return false;
    }

    /**
     * Во что обойдётся рецепт складу, или {@code null}, если хоть один
     * ингредиент склад покрыть не может.
     * <p>
     * У ингредиента бывает несколько подходящих предметов — «любое бревно»,
     * «любая доска». Берётся первый, который на складе есть: так колония
     * тратит то, чего у неё в избытке, а не требует именно дуб.
     */
    private static Map<Item, Integer> costOf(ServerWorld world, Warehouse warehouse,
                                             CraftingRecipe recipe, int depth) {
        Map<Item, Integer> cost = new LinkedHashMap<>();

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                // Пустая клетка формы: у фигурных рецептов их большинство.
                continue;
            }

            Item chosen = pick(world, warehouse, ingredient, cost, depth);
            if (chosen == null) {
                return null;
            }
            cost.merge(chosen, 1, Integer::sum);
        }

        return cost.isEmpty() ? null : cost;
    }

    /**
     * Чем закрыть этот ингредиент.
     * <p>
     * Сперва ищется то, что уже лежит на складе: так колония тратит
     * наличное, а не требует именно дуб. Если нет ничего — <b>доделывает
     * ингредиент</b> и проверяет снова. Ровно это и разрывает цепочку
     * «ступени из досок, доски из брёвен»: без второго хода стройка
     * вставала на ступенях, имея гору брёвен.
     */
    private static Item pick(ServerWorld world, Warehouse warehouse, Ingredient ingredient,
                             Map<Item, Integer> cost, int depth) {
        for (ItemStack option : ingredient.getMatchingStacks()) {
            Item candidate = option.getItem();
            if (warehouse.has(candidate, cost.getOrDefault(candidate, 0) + 1)) {
                return candidate;
            }
        }

        if (depth >= MAX_DEPTH) {
            return null;
        }
        for (ItemStack option : ingredient.getMatchingStacks()) {
            Item candidate = option.getItem();
            if (make(world, warehouse, candidate, depth + 1)
                    && warehouse.has(candidate, cost.getOrDefault(candidate, 0) + 1)) {
                return candidate;
            }
        }
        return null;
    }

    /** Что колония может сделать из наличного — для подсказок и приёмки. */
    public static List<Item> makeable(ServerWorld world, Warehouse warehouse, List<Item> wanted) {
        List<Item> able = new ArrayList<>();
        for (Item want : wanted) {
            for (CraftingRecipe recipe : recipesFor(world, want)) {
                if (costOf(world, warehouse, recipe, MAX_DEPTH) != null) {
                    able.add(want);
                    break;
                }
            }
        }
        return able;
    }
}
