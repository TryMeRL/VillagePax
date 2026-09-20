package com.villagepax.sim.quest;

import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.quest.Quest;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.core.profession.Profession;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.faith.Faith;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Насколько цель квеста уже выполнена — одним правилом на весь мод.
 * <p>
 * Правило здесь <b>одно</b>, и это главное решение этого файла. Сдаёт
 * квест {@link Quests}, а показывает его экран, и если бы каждый считал
 * «хватает ли» по-своему, они разошлись бы на первой же новой цели: в окне
 * галочка, при сдаче отказ. Такое уже случалось в этом моде со «сколько
 * принесено», и вывод записан: правило живёт в одном месте.
 * <p>
 * Ни одна цель не требует <b>следа</b> за игроком. Это не бедность
 * замысла, а необходимость: кодек поселения полон (шестнадцать полей
 * из шестнадцати), новой памяти моду взять негде, а счётчик, который
 * негде хранить, — это счётчик, который теряется при перезаходе. Поэтому
 * каждая цель спрашивает <b>состояние мира в миг сдачи</b>: что в сумке,
 * что построено, сколько намолено, с кем знаком.
 */
public final class Progress {

    /**
     * Кто выполняет и чем располагает.
     * <p>
     * Колония может отсутствовать: игрок вправе носить просьбы соседям,
     * не основав своей. Тогда цели, которые смотрят на колонию, честно
     * стоят на нуле — а не отказывают молча.
     *
     * @param player  чьи успехи считаем
     * @param carried что у него при себе
     * @param colony  его колония, если она есть
     * @param manager все поселения мира: нужен целям про других
     */
    public record Seeker(UUID player, Inventory carried, Optional<Settlement> colony,
                         SettlementManager manager) {

        public static Seeker of(UUID player, Inventory carried, Settlement colony,
                                SettlementManager manager) {
            return new Seeker(player, carried, Optional.ofNullable(colony), manager);
        }
    }

    /**
     * Одно требование в том виде, в каком его показывают и проверяют.
     *
     * @param key  ключ строки требования
     * @param item предмет для значка; пусто — у цели нет предмета
     * @param what чем измеряется: имя здания, домен бога, народ
     * @param need сколько надо
     * @param have сколько есть
     */
    public record Step(String key, Optional<Item> item, Optional<String> what,
                       int need, int have) {

        public boolean enough() {
            return have >= need;
        }
    }

    private Progress() {
    }

    /** Насколько выполнена эта цель. */
    public static Step of(Quest.Objective objective, Seeker seeker) {
        if (objective instanceof Quest.Objective.Deliver deliver) {
            return new Step(objective.describeKey(), Optional.of(deliver.item()),
                    Optional.empty(), deliver.count(), seeker.carried().count(deliver.item()));
        }
        if (objective instanceof Quest.Objective.Build build) {
            return new Step(objective.describeKey(), Optional.empty(),
                    Optional.of(ProfessionManager.get(build.workplace())
                            .map(Profession::displayName)
                            .orElseGet(() -> build.workplace().toString())),
                    build.level(), builtLevel(seeker, build.workplace()));
        }
        if (objective instanceof Quest.Objective.Favour favour) {
            return new Step(objective.describeKey(), Optional.empty(),
                    Optional.of(favour.domain().key()),
                    favour.amount(), favourOf(seeker, favour.domain()));
        }
        if (objective instanceof Quest.Objective.Friendship friendship) {
            return new Step(objective.describeKey(), Optional.empty(),
                    Optional.of("villagepax.culture." + friendship.culture().getPath()),
                    friendship.trust(), trustWith(seeker, friendship.culture()));
        }
        throw new IllegalStateException("род цели без правила: " + objective.type());
    }

    /**
     * Какого уровня мастерская этого ремесла стоит у игрока.
     * <p>
     * Считаются только <b>достроенные</b>: размеченный фундамент — это
     * намерение, а не мастерская. То же правило, по которому башни
     * убавляют отряд, и по той же причине — обещать игроку то, чего
     * у него нет, мод не должен.
     * <p>
     * Ищется <b>лучшая</b>, а не первая: у игрока их может быть две,
     * и просьба «поставь мастерскую второго уровня» обязана считаться
     * выполненной по той, что выше.
     * <p>
     * По ремеслу, а не по типу здания: у колонии игрока может быть свой
     * народ со своими названиями. Кто в здании работает, объявляет сам
     * тип здания, и спрашивается это у него.
     */
    private static int builtLevel(Seeker seeker, Identifier profession) {
        Settlement colony = seeker.colony().orElse(null);
        if (colony == null) {
            return 0;
        }
        int best = 0;
        for (Building building : colony.buildings()) {
            if (building.isOperational()
                    && BuildingTypes.employs(building.type(), profession)) {
                best = Math.max(best, building.level());
            }
        }
        return best;
    }

    /**
     * Сколько благосклонности набрано у бога этого домена.
     * <p>
     * По <b>лучшему</b> богу домена, а не по сумме: домен у народа один,
     * и бог в нём один. Сумма понадобилась бы только датапаку, который
     * объявит двух богов урожая, — а такой датапак уже получил жалобу
     * в лог от загрузчика.
     */
    private static int favourOf(Seeker seeker, com.villagepax.core.faith.Domain domain) {
        Settlement colony = seeker.colony().orElse(null);
        if (colony == null) {
            return 0;
        }
        return Gods.of(colony.culture()).stream()
                .filter(god -> Gods.get(god).map(known -> known.domain() == domain).orElse(false))
                .mapToInt(colony::favourOf)
                .max()
                .orElse(0);
    }

    /**
     * Насколько игрок знаком с этим народом.
     * <p>
     * По <b>лучшей</b> деревне народа, а не по средней. «Подружись с майя»
     * — это про один настоящий разговор с одной деревней; средним же
     * выходило бы, что новая, ещё не встреченная деревня того же народа
     * <i>отнимает</i> у игрока уже заслуженное.
     */
    private static int trustWith(Seeker seeker, Identifier culture) {
        int best = 0;
        for (Settlement settlement : seeker.manager().all()) {
            if (settlement.owner().isAutonomous() && settlement.culture().equals(culture)) {
                best = Math.max(best, settlement.reputationOf(seeker.player()));
            }
        }
        return best;
    }

    /**
     * Нужна ли этому квесту колония игрока — хоть одной целью или наградой.
     * <p>
     * Спрашивается <b>до</b> сдачи: просьба, которую нельзя выполнить без
     * колонии, обязана сказать об этом словами, а не отказывать молча
     * и не проглатывать награду. Правило мода, заработанное чужой ратушей
     * с мёртвыми кнопками.
     */
    public static boolean needsAColony(Quest quest) {
        for (Quest.Objective objective : quest.objectives()) {
            if (objective instanceof Quest.Objective.Build
                    || objective instanceof Quest.Objective.Favour) {
                return true;
            }
        }
        for (Quest.Reward reward : quest.rewards()) {
            if (reward instanceof Quest.Reward.Settler || reward instanceof Quest.Reward.Grace) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли у поселения бог такого домена: без него цель невыполнима. */
    public static boolean hasGodOf(Settlement colony, com.villagepax.core.faith.Domain domain) {
        return Gods.inDomain(colony.culture(), domain).isPresent();
    }

    /** Ступень благосклонности по числу очков: показывать её удобнее, чем число. */
    public static Faith.Tier tierOf(int favour) {
        return Faith.tierOf(favour);
    }
}
