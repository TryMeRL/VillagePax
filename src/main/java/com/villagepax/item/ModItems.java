package com.villagepax.item;

import com.villagepax.VillagePax;
import com.villagepax.block.ModBlocks;
import com.villagepax.core.faith.Domain;
import com.villagepax.effect.ModEffects;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ModItems {

    /** Порядок показа в творческой вкладке. */
    private static final List<Item> TAB_ORDER = new ArrayList<>();

    /**
     * Чертёж ратуши. Основной путь получения — награда за стартовую цепочку квестов
     * у чужой деревни. Крафт существует как подстраховка: на неудачном сиде рядом
     * может не оказаться ни одного поселения, и без чертежа мод было бы не начать.
     */
    public static final Item TOWN_HALL_BLUEPRINT = register("town_hall_blueprint",
            new TownHallBlueprintItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    /**
     * Медяк — единица счёта. Все цены в моде названы в медяках.
     * <p>
     * Монета не крафтится из руды, и это правило, а не недоделка: было бы
     * можно — кошель набивался бы кайлом, и торговля обесценилась бы
     * к третьему дню. Монета входит в мир только выручкой деревень
     * и наградой за квесты.
     */
    public static final Item COIN = register("coin", new Item(new Item.Settings()));

    /** Серебряк: девять медяков. */
    public static final Item SILVER_COIN = register("silver_coin", new Item(new Item.Settings()));

    /** Золотой: девять серебряков. */
    public static final Item GOLD_COIN = register("gold_coin", new Item(new Item.Settings()));

    /**
     * Кошель. Решение заказчика: «нужен просто кошелёк как предмет».
     * <p>
     * Внутри не слоты, а одно число — сколько в нём медяков. Монета
     * одинакова, и раскладывать её по слотам значило бы придумать работу
     * на ровном месте.
     */
    public static final Item PURSE = register("purse",
            new PurseItem(new Item.Settings().maxCount(1)));

    /**
     * Норманнский эль: то, ради чего колония нужна.
     * <p>
     * Первая вещь в моде, которой <b>нельзя добыть киркой и нельзя
     * скрафтить</b>. Её варит пивовар колонии из зерна, и больше она
     * не берётся ниоткуда. Это и есть ответ на «зацепиться не за что»:
     * до сих пор колония давала игроку ровно то, что он добыл бы сам,
     * только медленнее.
     * <p>
     * Бодрость, а не сила: эль пьют перед работой, и ускоренная кирка
     * — то, что игрок чувствует сразу и хочет снова. Сила сделала бы
     * из мода про деревню мод про драку.
     */
    public static final Item ALE = register("ale", new DrinkItem(new Item.Settings()
            .maxCount(16)
            .food(new FoodComponent.Builder()
                    .hunger(4)
                    .saturationModifier(0.4f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20 * 120, 0), 1.0f)
                    .alwaysEdible()
                    .build())));

    /**
     * Какао майя: густой горький напиток, который они пьют перед дорогой.
     * <p>
     * Пара элю и его противоположность по смыслу: север даёт руки, юг —
     * ноги. Два народа должны отличаться тем, что от них получает игрок,
     * а не только цветом стен.
     */
    public static final Item CACAO = register("cacao", new DrinkItem(new Item.Settings()
            .maxCount(16)
            .food(new FoodComponent.Builder()
                    .hunger(5)
                    .saturationModifier(0.6f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 120, 0), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 8, 0), 1.0f)
                    .alwaysEdible()
                    .build())));

    /**
     * Сукно: первый товар колонии <b>на вывоз</b>.
     * <p>
     * Эль и какао колония пьёт сама, а сукно — продаёт: деревни берут его
     * дороже всего, что она может им предложить. Этим замыкается круг,
     * которого моду не хватало. Раньше монета входила в мир только
     * наградой за квест и выручкой деревень, то есть игроку доставалась
     * подачкой; теперь у него есть <b>чем торговать</b>, и колония
     * начинает себя окупать.
     * <p>
     * Оно же — материал будущей тканевой одежды, которую заказчик просил
     * иметь в виду, и вешается на бельевую верёвку уже сейчас.
     */
    public static final Item CLOTH = register("cloth", new Item(new Item.Settings()));

    // --- угощения народов: у каждого свой нрав и свой эффект ---

    /**
     * Радужный кекс пони: розовая глазурь, посыпка и радуга на полминуты —
     * бежишь быстрее и оставляешь за собой цветной след.
     */
    public static final Item RAINBOW_CUPCAKE = register("rainbow_cupcake", new Item(
            new Item.Settings().food(new FoodComponent.Builder()
                    .hunger(3)
                    .saturationModifier(0.3f)
                    .snack()
                    .alwaysEdible()
                    .statusEffect(new StatusEffectInstance(ModEffects.RAINBOW_DASH, 20 * 45, 0), 1.0f)
                    .build())));

    /**
     * Гномий стаут: тёмный, как штольня, — и камень начинает подсказывать,
     * где руда. Ночное зрение в придачу: в забое без него не разглядеть.
     */
    public static final Item DWARVEN_STOUT = register("dwarven_stout", new DrinkItem(
            new Item.Settings().maxCount(16).food(new FoodComponent.Builder()
                    .hunger(3)
                    .saturationModifier(0.4f)
                    .alwaysEdible()
                    .statusEffect(new StatusEffectInstance(ModEffects.ORE_SENSE, 20 * 90, 0), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 20 * 90, 0), 1.0f)
                    .build()), Items.GLASS_BOTTLE));

    /**
     * Эльфийский нектар: мёд и светящиеся ягоды. Лёгкость на две минуты:
     * прыгаешь выше и падаешь, как лист.
     */
    public static final Item ELVEN_NECTAR = register("elven_nectar", new DrinkItem(
            new Item.Settings().maxCount(16).food(new FoodComponent.Builder()
                    .hunger(2)
                    .saturationModifier(0.5f)
                    .alwaysEdible()
                    .statusEffect(new StatusEffectInstance(ModEffects.LIGHTNESS, 20 * 120, 0), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20 * 120, 1), 1.0f)
                    .build())));

    /** Хлопушка норманнского праздника: конфетти, и деревня машет в ответ. */
    public static final Item POPPER = register("popper", new PopperItem(new Item.Settings().maxCount(16)));

    /**
     * Артефакты: три вещи, которых нельзя добыть, скрафтить и купить.
     * <p>
     * Рецепта у них нет и не будет — это не упущение, а весь смысл.
     * Единственная дорога в мир у артефакта одна: поселение полсотни
     * игровых дней носило жертвы одному богу и дошло до ступени
     * «Хранимый». До сих пор самой дальней целью мода была ратуша
     * четвёртого уровня, после которой строить нечего; теперь дальше
     * есть куда идти.
     * <p>
     * По одной вещи на домен, а не по одной на бога, и это решение
     * по содержанию. Бог норманнов и бог майя, отвечающие за урожай, —
     * разные лица одного дела; серп у них поэтому один. Народы
     * различаются тем, <b>как</b> к нему идёшь: одним нужен эль,
     * другим какао.
     * <p>
     * Редкость эпическая и {@code maxCount(1)}: артефакт не складывается
     * в стопку, потому что второго такого в мире нет.
     */
    public static final Item SICKLE_OF_PLENTY = register("sickle_of_plenty",
            new ArtifactItem(Domain.HARVEST, artifact()));

    public static final Item BUILDERS_PLUMB = register("builders_plumb",
            new ArtifactItem(Domain.STONE, artifact()));

    public static final Item WATCHERS_EYE = register("watchers_eye",
            new ArtifactItem(Domain.WATCH, artifact()));

    /** Общие свойства артефакта: один в стопке, эпический, не горит. */
    private static Item.Settings artifact() {
        return new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof();
    }

    public static final ItemGroup GROUP = Registry.register(
            Registries.ITEM_GROUP,
            new Identifier(VillagePax.MOD_ID, "general"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModBlocks.TOWN_HALL))
                    .displayName(Text.translatable("itemGroup.villagepax.general"))
                    .entries((context, entries) -> TAB_ORDER.forEach(entries::add))
                    .build());

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        Item registered = Registry.register(Registries.ITEM, new Identifier(VillagePax.MOD_ID, name), item);
        TAB_ORDER.add(registered);
        return registered;
    }

    /**
     * Зарегистрировать предмет из другого места мода — снаряжение народов
     * живёт в своём пакете, но во вкладку ложится той же очередью.
     */
    public static Item add(String name, Item item) {
        return register(name, item);
    }

    /**
     * Предметы-блоки создаются здесь, а не в {@link ModBlocks}, чтобы блок ничего
     * не знал о своём предмете: блоки нужны на сервере всегда, а предмет — только
     * для инвентаря и творческой вкладки.
     */
    public static void registerBlockItems() {
        for (Map.Entry<Identifier, Block> entry : ModBlocks.registered().entrySet()) {
            register(entry.getKey().getPath(), new BlockItem(entry.getValue(), new Item.Settings()));
        }
    }

    public static void init() {
        VillagePax.LOGGER.debug("Предметов во вкладке: {}", TAB_ORDER.size());
    }
}
