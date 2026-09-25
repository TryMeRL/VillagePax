package com.villagepax.item.charm;

/**
 * Обереги: у каждого народа свой, и четыре общих — их делают сами.
 * <p>
 * Носят оберег в левой руке. Не в инвентаре: тогда сильнейшим стал бы
 * тот, кто таскает все одиннадцать сразу, и выбирать было бы нечего.
 * Левая рука — это выбор: оберег или щит, оберег или факел.
 * <ul>
 *   <li><b>ладанка паломника</b> (норманны) — второе дыхание: когда сил
 *       меньше трети, накрывает щитом золотых сердец и лечит;</li>
 *   <li><b>нефритовый ягуар</b> (майя) — видит в темноте, ночью быстр;</li>
 *   <li><b>подкова удачи</b> (пони) — удача в добыче, а на бегу — радуга;</li>
 *   <li><b>рудный самоцвет</b> (гномы) — под камнем чует руду и копает быстрее;</li>
 *   <li><b>лунный кулон</b> (эльфы) — лёгкость, а ночью раны затягиваются;</li>
 *   <li><b>руна Громовержца</b> (северяне) — молния туда, куда смотришь;</li>
 *   <li><b>лисий оберег</b> (ямато) — крадучись становишься невидим;</li>
 *   <li><b>оберег возвращения</b> — домой, к ратуше своей колонии;</li>
 *   <li><b>оберег-магнит</b> — вещи и опыт сами летят в руки;</li>
 *   <li><b>оберег ветра</b> — рывок вперёд, и падать мягко;</li>
 *   <li><b>раковина прибоя</b> — под водой дышишь и плывёшь, как дельфин.</li>
 * </ul>
 */
public enum Charm {
    PILGRIM_RELIQUARY("pilgrim_reliquary", "norman", 20 * 120),
    JADE_JAGUAR("jade_jaguar", "maya", 0),
    LUCKY_HORSESHOE("lucky_horseshoe", "pony", 0),
    ORE_GEM("ore_gem", "dwarf", 0),
    MOON_PENDANT("moon_pendant", "elf", 0),
    THUNDER_RUNE("thunder_rune", "nord", 20 * 60),
    KITSUNE_CHARM("kitsune_charm", "yamato", 0),
    HOMEWARD_CHARM("homeward_charm", null, 20 * 180),
    MAGNET_CHARM("magnet_charm", null, 0),
    WIND_CHARM("wind_charm", null, 20 * 3),
    SEA_SHELL("sea_shell", null, 0);

    private final String id;
    private final String people;
    private final int cooldown;

    Charm(String id, String people, int cooldown) {
        this.id = id;
        this.people = people;
        this.cooldown = cooldown;
    }

    public String id() {
        return id;
    }

    /** Чей это оберег — или {@code null}, если его делают сами. */
    public String people() {
        return people;
    }

    /** Сколько тиков оберег отдыхает после своего дела. */
    public int cooldown() {
        return cooldown;
    }

    /** Строка подсказки о том, что оберег делает. */
    public String descriptionKey() {
        return "item.villagepax." + id + ".desc";
    }

    /** Оберег народа по его пути, если такой есть. */
    public static Charm ofPeople(String people) {
        for (Charm charm : values()) {
            if (people.equals(charm.people)) {
                return charm;
            }
        }
        return null;
    }
}
