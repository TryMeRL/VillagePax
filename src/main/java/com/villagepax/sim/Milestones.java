package com.villagepax.sim;

import com.villagepax.VillagePax;
import com.villagepax.core.building.BuildingType;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.profession.Profession;
import com.villagepax.core.profession.ProfessionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Ступень колонии — событие, а не число в углу экрана.
 * <p>
 * Ступени были в моде с первой недели и не значили почти ничего: предел
 * населения да радиус границ, то есть <b>больше того же самого</b>. Игрок
 * поднимал ратушу и не замечал, что что-то произошло. Заказчик сказал
 * об этом прямо: расти незачем, зацепиться не за что.
 * <p>
 * Теперь ступень — это <b>ключ</b>. За неё открываются ремёсла и здания,
 * которых раньше не было, и о каждом игроку говорят вслух в тот миг,
 * когда оно открылось. Награда, о которой не сказали, наградой
 * не ощущается.
 */
public final class Milestones {

    /** Как далеко слышен колокол ступени. */
    private static final int HEARD = 48;

    private Milestones() {
    }

    /**
     * Колония поднялась на ступень: сказать, показать и перечислить,
     * что открылось.
     */
    public static void reached(ServerWorld world, Settlement colony, SettlementLevel level) {
        VillagePax.LOGGER.info("Колония {} стала на ступень {}", colony.name(), level.id());

        world.playSound(null, colony.center(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                colony.center().getX() + 0.5, colony.center().getY() + 1.5,
                colony.center().getZ() + 0.5, 60, 1.5, 1.0, 1.5, 0.2);

        com.villagepax.sim.life.Chronicle.note(world, colony, "villagepax.chronicle.level",
                com.villagepax.sim.life.Chronicle.level(level));
        tell(world, colony, Text.translatable("villagepax.level.reached",
                        Text.literal(colony.name()),
                        Text.translatable(levelKey(level)))
                .formatted(Formatting.GOLD));

        // Что открылось — это о заказах в пульте, а пульт есть только
        // у колонии. Деревне народа хватит колокола и слов.
        if (!colony.owner().isAutonomous()) {
            for (Text line : opened(level)) {
                tell(world, colony, line);
            }
        }
    }

    /**
     * Что открывает эта ступень: ремёсла и здания, названные вслух.
     * <p>
     * Считается по датапаку, а не по списку в коде: народ, добавивший
     * своё ремесло на третью ступень, получит объявление о нём даром.
     */
    public static List<Text> opened(SettlementLevel level) {
        List<Text> lines = new ArrayList<>();

        for (Identifier id : ProfessionManager.byHiringPriority()) {
            Profession craft = ProfessionManager.get(id).orElse(null);
            if (craft != null && craft.minLevel() == level) {
                lines.add(Text.translatable("villagepax.level.opened_craft",
                        Text.translatable(craft.displayName())).formatted(Formatting.GREEN));
            }
        }
        for (var entry : BuildingTypes.all().entrySet()) {
            BuildingType kind = entry.getValue();
            if (kind.minLevel() == level) {
                lines.add(Text.translatable("villagepax.level.opened_building",
                        Text.translatable(kind.displayName())).formatted(Formatting.GREEN));
            }
        }
        return lines;
    }

    /** Ключ названия ступени: одно слово, которое игрок и запомнит. */
    public static String levelKey(SettlementLevel level) {
        return "villagepax.level." + level.id();
    }

    /**
     * Сказать всем, кто рядом.
     * <p>
     * Не только хозяину: ступень — событие колонии, и если у неё гостят
     * товарищи по серверу, праздник общий.
     */
    private static void tell(ServerWorld world, Settlement colony, Text message) {
        Vec3d centre = Vec3d.ofCenter(colony.center());
        for (ServerPlayerEntity player : world.getPlayers()) {
            boolean owner = colony.owner().isOwnedBy(player.getUuid());
            if (owner || player.squaredDistanceTo(centre) <= (double) HEARD * HEARD) {
                player.sendMessage(message, false);
            }
        }
    }

    /** Чтобы список игроков не тянул за собой лишний импорт в тестах. */
    public static boolean hears(PlayerEntity player, Settlement colony) {
        return colony.owner().isOwnedBy(player.getUuid())
                || player.squaredDistanceTo(Vec3d.ofCenter(colony.center()))
                <= (double) HEARD * HEARD;
    }
}
