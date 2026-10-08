package com.villagepax.sim.quest;

import net.minecraft.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Поручения по народам: у каждого ремесла каждого народа свои просьбы
 * и свои слова к ним.
 * <p>
 * Общие поручения ({@link Errands}) просят одно и то же у всех народов
 * и одними словами, и игрок их не отличал от квеста к квесту: «руки
 * заняты топором» говорил и норманн, и эльф. Здесь просьба — маленькая
 * история: норманнский пивовар варит сидр, майя — балче на мёде диких
 * пчёл, гном — грибное пиво. Ключ слов собирается из народа, ремесла
 * и номера просьбы: {@code villagepax.errand.<народ>.<ремесло>.<номер>}.
 * <p>
 * Таблица порождена из одного списка вместе со словарём, поэтому
 * правится там же, где и слова ({@code tools/make-errands.py}), — иначе
 * разошлась бы с ними.
 */
final class ErrandBook {

    static final Map<String, Map<String, List<Errands.Ask>>> BY_PEOPLE = Map.of(
            "norman", Map.of(
                    "elder", List.of(new Errands.Ask(Items.BREAD, 8), new Errands.Ask(Items.CANDLE, 6)),
                    "lumberjack", List.of(new Errands.Ask(Items.OAK_LOG, 16), new Errands.Ask(Items.IRON_AXE, 1)),
                    "farmer", List.of(new Errands.Ask(Items.WHEAT_SEEDS, 16), new Errands.Ask(Items.APPLE, 8)),
                    "brewer", List.of(new Errands.Ask(Items.APPLE, 12), new Errands.Ask(Items.SUGAR, 8)),
                    "weaver", List.of(new Errands.Ask(Items.WHITE_WOOL, 8), new Errands.Ask(Items.BLUE_DYE, 4)),
                    "guard", List.of(new Errands.Ask(Items.ARROW, 16), new Errands.Ask(Items.SHIELD, 1)),
                    "builder", List.of(new Errands.Ask(Items.STONE_BRICKS, 16), new Errands.Ask(Items.COBBLESTONE, 32)),
                    "courier", List.of(new Errands.Ask(Items.LEATHER, 4), new Errands.Ask(Items.PAPER, 8))),
            "maya", Map.of(
                    "elder", List.of(new Errands.Ask(Items.COCOA_BEANS, 12), new Errands.Ask(Items.GOLD_INGOT, 2)),
                    "lumberjack", List.of(new Errands.Ask(Items.JUNGLE_LOG, 16), new Errands.Ask(Items.VINE, 8)),
                    "farmer", List.of(new Errands.Ask(Items.PUMPKIN_SEEDS, 8), new Errands.Ask(Items.BONE_MEAL, 8)),
                    "brewer", List.of(new Errands.Ask(Items.HONEYCOMB, 4), new Errands.Ask(Items.COCOA_BEANS, 8)),
                    "weaver", List.of(new Errands.Ask(Items.STRING, 16), new Errands.Ask(Items.RED_DYE, 4)),
                    "guard", List.of(new Errands.Ask(Items.ARROW, 16), new Errands.Ask(Items.OBSIDIAN, 2)),
                    "builder", List.of(new Errands.Ask(Items.MOSSY_STONE_BRICKS, 12), new Errands.Ask(Items.CLAY_BALL, 12)),
                    "courier", List.of(new Errands.Ask(Items.FEATHER, 8), new Errands.Ask(Items.LEATHER, 3))),
            "pony", Map.of(
                    "elder", List.of(new Errands.Ask(Items.CAKE, 1), new Errands.Ask(Items.SUNFLOWER, 4)),
                    "lumberjack", List.of(new Errands.Ask(Items.BIRCH_LOG, 12), new Errands.Ask(Items.OAK_SAPLING, 6)),
                    "farmer", List.of(new Errands.Ask(Items.CARROT, 12), new Errands.Ask(Items.APPLE, 8)),
                    "brewer", List.of(new Errands.Ask(Items.SWEET_BERRIES, 12), new Errands.Ask(Items.SUGAR, 8)),
                    "weaver", List.of(new Errands.Ask(Items.PINK_WOOL, 6), new Errands.Ask(Items.STRING, 12)),
                    "guard", List.of(new Errands.Ask(Items.TORCH, 12), new Errands.Ask(Items.SHIELD, 1)),
                    "builder", List.of(new Errands.Ask(Items.BRICKS, 16), new Errands.Ask(Items.GLASS, 8)),
                    "courier", List.of(new Errands.Ask(Items.PAPER, 8), new Errands.Ask(Items.APPLE, 6))),
            "nord", Map.of(
                    "elder", List.of(new Errands.Ask(Items.COOKED_BEEF, 8), new Errands.Ask(Items.GOLD_INGOT, 2)),
                    "lumberjack", List.of(new Errands.Ask(Items.SPRUCE_LOG, 16), new Errands.Ask(Items.STICK, 32)),
                    "farmer", List.of(new Errands.Ask(Items.BEETROOT_SEEDS, 12), new Errands.Ask(Items.WHEAT, 16)),
                    "brewer", List.of(new Errands.Ask(Items.HONEY_BOTTLE, 4), new Errands.Ask(Items.WHEAT, 16)),
                    "weaver", List.of(new Errands.Ask(Items.WHITE_WOOL, 8), new Errands.Ask(Items.RED_DYE, 4)),
                    "guard", List.of(new Errands.Ask(Items.IRON_INGOT, 3), new Errands.Ask(Items.SHIELD, 1)),
                    "builder", List.of(new Errands.Ask(Items.SPRUCE_PLANKS, 24), new Errands.Ask(Items.COBBLESTONE, 32)),
                    "courier", List.of(new Errands.Ask(Items.LEATHER, 4), new Errands.Ask(Items.COOKED_COD, 6))),
            "yamato", Map.of(
                    "elder", List.of(new Errands.Ask(Items.PAPER, 8), new Errands.Ask(Items.CHERRY_SAPLING, 2)),
                    "lumberjack", List.of(new Errands.Ask(Items.BAMBOO, 16), new Errands.Ask(Items.CHERRY_LOG, 8)),
                    "farmer", List.of(new Errands.Ask(Items.BONE_MEAL, 8), new Errands.Ask(Items.BEETROOT, 10)),
                    "brewer", List.of(new Errands.Ask(Items.WHEAT, 16), new Errands.Ask(Items.GLASS_BOTTLE, 6)),
                    "weaver", List.of(new Errands.Ask(Items.STRING, 16), new Errands.Ask(Items.PINK_DYE, 4)),
                    "guard", List.of(new Errands.Ask(Items.IRON_INGOT, 3), new Errands.Ask(Items.ARROW, 16)),
                    "builder", List.of(new Errands.Ask(Items.CHERRY_PLANKS, 16), new Errands.Ask(Items.CLAY_BALL, 12)),
                    "courier", List.of(new Errands.Ask(Items.PAPER, 6), new Errands.Ask(Items.COOKED_SALMON, 4))),
            "dwarf", Map.of(
                    "elder", List.of(new Errands.Ask(Items.GOLD_INGOT, 3), new Errands.Ask(Items.TORCH, 16)),
                    "lumberjack", List.of(new Errands.Ask(Items.OAK_LOG, 16), new Errands.Ask(Items.CHARCOAL, 12)),
                    "farmer", List.of(new Errands.Ask(Items.BROWN_MUSHROOM, 8), new Errands.Ask(Items.POTATO, 12)),
                    "brewer", List.of(new Errands.Ask(Items.WHEAT, 16), new Errands.Ask(Items.BROWN_MUSHROOM, 6)),
                    "weaver", List.of(new Errands.Ask(Items.LEATHER, 6), new Errands.Ask(Items.STRING, 12)),
                    "guard", List.of(new Errands.Ask(Items.IRON_INGOT, 4), new Errands.Ask(Items.SHIELD, 1)),
                    "builder", List.of(new Errands.Ask(Items.STONE_BRICKS, 24), new Errands.Ask(Items.COBBLED_DEEPSLATE, 16)),
                    "courier", List.of(new Errands.Ask(Items.TORCH, 8), new Errands.Ask(Items.BREAD, 6))),
            "elf", Map.of(
                    "elder", List.of(new Errands.Ask(Items.LILY_OF_THE_VALLEY, 4), new Errands.Ask(Items.GLOW_BERRIES, 6)),
                    "lumberjack", List.of(new Errands.Ask(Items.BIRCH_SAPLING, 6), new Errands.Ask(Items.DARK_OAK_SAPLING, 4)),
                    "farmer", List.of(new Errands.Ask(Items.SWEET_BERRIES, 12), new Errands.Ask(Items.BONE_MEAL, 8)),
                    "brewer", List.of(new Errands.Ask(Items.HONEY_BOTTLE, 3), new Errands.Ask(Items.GLOW_BERRIES, 6)),
                    "weaver", List.of(new Errands.Ask(Items.STRING, 16), new Errands.Ask(Items.GREEN_DYE, 4)),
                    "guard", List.of(new Errands.Ask(Items.ARROW, 20), new Errands.Ask(Items.FEATHER, 8)),
                    "builder", List.of(new Errands.Ask(Items.BIRCH_PLANKS, 16), new Errands.Ask(Items.GLASS, 8)),
                    "courier", List.of(new Errands.Ask(Items.FEATHER, 4), new Errands.Ask(Items.APPLE, 6))));

    private ErrandBook() {
    }
}
