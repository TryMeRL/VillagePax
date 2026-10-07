package com.villagepax.sim.life;

import com.villagepax.core.config.Configs;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Суточный ход жизни: все прожили день, кто-то женился, кто-то родился,
 * кто-то умер от старости.
 * <p>
 * Одна дверь на всё это, и зовётся она оттуда же, откуда считаются голод
 * и приток, — <b>и только когда колонию видно</b>. Правило мода, купленное
 * дорого: голод когда-то считался везде, а поесть житель мог лишь рядом
 * с игроком, и ушедший на неделю возвращался к пустой колонии. Жизнь
 * идёт там, где на неё смотрят.
 * <p>
 * Смертность выключается настройкой. Кому она мешает — снимает, и колония
 * живёт вечно; расти сама она при этом не перестаёт, просто никто
 * не уходит.
 */
public final class Life {

    /** Как далеко слышно весть о свадьбе, родах и похоронах. */
    private static final int HEARD = 64;

    private Life() {
    }

    /** Суточный ход. Зовётся раз в сутки и только под присмотром. */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement settlement,
                              Random random) {
        Families.newDay(world, settlement, random);
        // Характеры — после семей и до смертей: набожный, родившийся
        // сегодня, в храм не пойдёт (он ребёнок), а набожный, которому
        // сегодня умирать, последнюю жертву донесёт. Порядок выбран так,
        // чтобы мёртвый ничего не делал, а живой успел.
        Natures.newDay(world, settlement);
        // Знакомства — после рождений и до смертей: родившийся сегодня
        // ещё ребёнок и ни с кем не сходится, а умирающий успевает
        // прожить последний день среди тех, кто его помнит.
        Bonds.newDay(settlement);
        if (Configs.get().mortality()) {
            Mortality.newDay(world, settlement);
        }
    }

    /**
     * Сказать колонии — и всем, кто рядом.
     * <p>
     * В чат, а не в лог: свадьба, о которой узнал только файл, свадьбой
     * не является. Слышно её в радиусе, а не по всему миру: событие
     * принадлежит месту.
     */
    static void tell(ServerWorld world, Settlement settlement, String key, String... names) {
        Chronicle.heard(world, settlement, key, names);
        Object[] args = new Object[names.length];
        for (int at = 0; at < names.length; at++) {
            args[at] = Text.literal(names[at]).formatted(Formatting.WHITE);
        }

        Text line = Text.translatable(key, args).formatted(Formatting.GOLD);
        Vec3d middle = Vec3d.ofCenter(settlement.center());

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getPos().isInRange(middle, HEARD)) {
                player.sendMessage(line, false);
            }
        }
    }

    /** Тело жителя, если оно есть в мире. */
    static void discardBody(ServerWorld world, Citizen citizen) {
        citizen.entityUuid().map(world::getEntity).ifPresent(Entity::discard);
    }

    /** Звук события в середине колонии. */
    static void sound(ServerWorld world, Settlement settlement, net.minecraft.sound.SoundEvent what,
                      float pitch) {
        world.playSound(null, settlement.center(), what, SoundCategory.NEUTRAL, 0.8f, pitch);
    }

    /** Звук рождения — тот, которым ваниль отмечает удачу. */
    static void chime(ServerWorld world, Settlement settlement) {
        sound(world, settlement, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f);
    }

    /** Звук похорон: низкий колокол, и ничего больше. */
    static void knell(ServerWorld world, Settlement settlement) {
        sound(world, settlement, SoundEvents.BLOCK_BELL_RESONATE, 0.6f);
    }

    /**
     * Все жители поселения, отсортированные по возрасту от старших.
     * <p>
     * Нужно там, где порядок решает: умирает <b>старейший</b>, а не первый
     * попавшийся. Без сортировки смерть выбирала бы по порядку в списке,
     * то есть по случайности загрузки, — и игрок видел бы, как молодой
     * уходит раньше деда.
     */
    static List<Citizen> byAge(Settlement settlement) {
        List<Citizen> all = new ArrayList<>(settlement.citizens());
        all.sort((one, other) -> Integer.compare(Ages.daysOf(other), Ages.daysOf(one)));
        return all;
    }
}
