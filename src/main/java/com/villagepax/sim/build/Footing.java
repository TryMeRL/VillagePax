package com.villagepax.sim.build;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.Trait;
import com.villagepax.core.culture.Traits;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Settlement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.Optional;

/**
 * На чём стоит поселение: на земле, в толще горы или в кронах.
 * <p>
 * Одна дверь на весь мод для вопроса «где у этого народа пол». Заведена
 * она не из любви к отвлечённостям, а по прямому предупреждению, записанному
 * в {@link Trait}: «ветвление {@code if (culture == DWARF)} по всему коду —
 * тупик: каждый новый народ добавлял бы условия в десяток мест». С двумя
 * способами жить это было теорией; с третьим стало практикой — вопрос
 * «где пол» задают разметка, поиск мест, улицы и площадка, и четыре
 * одинаковых ветвления разошлись бы при первой же правке одного из них.
 *
 * <h2>Три ответа на один вопрос</h2>
 * <b>Земля</b> — пол следует за рельефом, у каждого здания своя высота,
 * и годность места решает уклон.
 * <p>
 * <b>Чертог</b> — пол один на всё поселение и лежит в толще; годность
 * решает не уклон, а свод над головой. См. {@link Hold}.
 * <p>
 * <b>Кроны</b> — пол тоже один, но висит над лесом; годность решает
 * не свод, а <b>лес под настилом</b>. См. {@link Canopy}.
 * <p>
 * Два последних роднит больше, чем разделяет: и там, и там отметка
 * едина, уклона не существует, а улица идёт ровно. Поэтому спрашивать
 * их порознь не надо — {@link #keepsOneLevel()} отвечает за обоих.
 */
public enum Footing {

    /** Как жили норманны, майя и пони: по земле, следуя рельефу. */
    GROUND,

    /** Как живут гномы: в толще горы, на одной отметке. */
    HOLD,

    /** Как живут эльфы: над лесом, на одной отметке. */
    CANOPY;

    public static Footing of(Identifier culture) {
        return byTraits(Traits.of(culture));
    }

    public static Footing of(Settlement settlement) {
        return settlement == null ? GROUND : of(settlement.culture());
    }

    public static Footing of(Culture culture) {
        return culture == null ? GROUND : byTraits(Traits.resolve(culture.traits()));
    }

    /**
     * Единственное место, где черта превращается в способ жить.
     * <p>
     * Спрашивают отсюда трое — по опознавателю, по поселению и по самой
     * культуре, — и каждый спрашивает об одном. Разбирать этот вопрос
     * трижды значило бы завести три ответа, которые разойдутся при первой
     * же новой черте: ровно от этого и заведён сам {@code Footing}.
     * <p>
     * Подземная старше крон: народ, объявивший обе, живёт в горе. Случай
     * бессмысленный, но молчать о нём нельзя — молчание здесь означало бы
     * «как выйдет».
     */
    private static Footing byTraits(java.util.Set<Trait> traits) {
        if (traits.contains(Trait.BUILDS_UNDERGROUND)) {
            return HOLD;
        }
        return traits.contains(Trait.BUILDS_IN_CANOPY) ? CANOPY : GROUND;
    }

    /**
     * Держит ли этот народ все здания на одной отметке.
     * <p>
     * Из этого следуют сразу три вещи: уклон не спрашивается, улица идёт
     * ровно и без ступеней, а путь жителя равен расстоянию по карте.
     */
    public boolean keepsOneLevel() {
        return this != GROUND;
    }

    /**
     * Ровняет ли этот народ землю под здание.
     * <p>
     * Только тот, кто на ней стоит. Гному ровнять нечего — под полом
     * сплошной камень. А эльфу ровнять <b>нельзя</b>: подсыпка честно
     * увидела бы ствол под углом настила, сочла бы это землёй и вывела
     * из-под дома земляной столб в дюжину блоков. Дом в кронах стоит
     * на сваях <b>нарочно</b>, и это тот редкий случай, когда «дыра под
     * зданием» — не поломка, а замысел.
     */
    public boolean levelsTheGround() {
        return this == GROUND;
    }

    /**
     * Отметка пола поселения — для тех, кто держит его единым.
     * <p>
     * Хранится нигде: это высота середины поселения, то есть та самая
     * точка, которую выбрало основание. Отдельное поле однажды разошлось
     * бы с местом ратуши.
     */
    public static int levelOf(Settlement settlement) {
        return settlement.center().getY();
    }

    /**
     * Куда в этой колонне встанет угол здания, или {@code null},
     * если строить тут не на чем.
     */
    public BlockPos spot(ServerWorld world, Settlement settlement, BlockPos column) {
        if (keepsOneLevel()) {
            return new BlockPos(column.getX(), levelOf(settlement), column.getZ());
        }
        return Ground.buildableAt(world, column.getX(), column.getZ()).orElse(null);
    }

    /**
     * Годится ли место под здание такого размера.
     * <p>
     * Вопрос один, ответы разные: на земле это уклон, в горе — свод
     * над сводом, в кронах — лес под настилом.
     */
    public boolean fits(ServerWorld world, Settlement settlement, BlockPos anchor, Vec3i size) {
        return this == GROUND ? isFlatEnough(world, settlement, anchor, size)
                : holds(world, anchor, size);
    }

    /**
     * Держит ли это место здание такого размера — не спрашивая поселения.
     * <p>
     * Нужно там, где поселения ещё нет: при основании колонии игрок ставит
     * блок ратуши, и отказать ему надо <b>до</b> того, как колония
     * возникнет. Уклон здесь не спрашивается вовсе — он зависит от черты
     * народа, а народ с земли на своей земле помещается всегда.
     */
    public boolean holds(ServerWorld world, BlockPos anchor, Vec3i size) {
        return switch (this) {
            case HOLD -> Hold.isCarvable(world, anchor, size);
            case CANOPY -> Canopy.isBorne(world, anchor, size);
            case GROUND -> true;
        };
    }

    /**
     * Ключ отказа, если место этому народу не подходит.
     * <p>
     * Отказ обязан говорить причину — правило мода, и здесь оно особенно
     * дорого: колония, встав не там, не построила бы ни одного здания
     * и молчала бы об этом.
     */
    public String refusalKey() {
        return switch (this) {
            case HOLD -> "villagepax.found.needs_mountain";
            case CANOPY -> "villagepax.found.needs_forest";
            case GROUND -> "villagepax.found.bad_ground";
        };
    }

    /**
     * Где под этой колонной середина будущего поселения — при основании.
     * <p>
     * Народ с земли встаёт на землю, народ из горы — на дюжину блоков
     * ниже склона, народ из крон — выше подлеска. Спрашивается у культуры,
     * а не у поселения: поселения ещё нет, его как раз и размечают.
     */
    public static Optional<BlockPos> centreUnder(ServerWorld world, Culture culture, int x, int z) {
        return switch (of(culture)) {
            case HOLD -> Hold.floorUnder(world, x, z);
            case CANOPY -> Canopy.deckOver(world, x, z);
            case GROUND -> Ground.buildableAt(world, x, z);
        };
    }

    /**
     * Насколько неровным может быть след здания у народа с земли.
     * <p>
     * Перенесено сюда из разметки целиком: вопрос «годится ли место»
     * должен отвечаться в одном месте, иначе завтра он начнёт
     * отвечаться по-разному в разметке и в пульте игрока.
     */
    private static boolean isFlatEnough(ServerWorld world, Settlement settlement,
                                        BlockPos anchor, Vec3i size) {
        int allowed = Traits.maxSlope(settlement.culture());

        for (int dx = 0; dx < size.getX(); dx += Math.max(1, size.getX() - 1)) {
            for (int dz = 0; dz < size.getZ(); dz += Math.max(1, size.getZ() - 1)) {
                BlockPos corner = Ground.buildableAt(world, anchor.getX() + dx,
                        anchor.getZ() + dz).orElse(null);
                if (corner == null || Math.abs(corner.getY() - anchor.getY()) > allowed) {
                    return false;
                }
            }
        }
        return true;
    }
}
