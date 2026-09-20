package com.villagepax.item;

import com.villagepax.core.faith.Domain;
import com.villagepax.core.war.WarParty;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.faith.Miracles;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.work.FarmJob;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Артефакт: вещь, которой нельзя добыть, скрафтить и купить.
 * <p>
 * Правило мода, ради которого всё это и затевалось: <b>цепляет то, чего
 * не получить иначе</b>. Рецепта у артефакта нет, в торговых столах его
 * нет, из мобов он не падает. Единственная дорога — полсотни игровых дней
 * жертв одному богу.
 * <p>
 * Действие у всех трёх — <b>колонии</b>, а не игроку. Это осознанный
 * выбор против соблазна сделать из артефактов снаряжение: ускоренная кирка
 * и непробиваемый щит превратили бы мод про деревню в мод про снаряжение,
 * а ради снаряжения никто не станет полсотни дней носить хлеб на алтарь.
 * Серп растит <b>поле колонии</b>, отвес поднимает <b>её стройку</b>,
 * око смотрит на <b>её врагов</b>.
 * <p>
 * Откат — ванильный {@code ItemCooldownManager}: он рисуется на предмете
 * сам, переживает перезаход в мир и знаком игроку по эндер-жемчугу.
 * Свой счётчик пришлось бы и хранить, и рисовать.
 */
public class ArtifactItem extends Item {

    /** Сколько тиков предмет отдыхает после работы. */
    private static final int COOLDOWN = 20 * 30;

    /** Далеко ли око видит врага. */
    private static final int WATCH_RANGE = 48;

    /** Насколько сильно око слепит налётчика. */
    private static final int WATCH_TICKS = 20 * 20;

    /** Сколько блоков кладёт отвес за нажатие. */
    private static final int PLUMB_BLOCKS = 32;

    private final Domain domain;

    public ArtifactItem(Domain domain, Settings settings) {
        super(settings);
        this.domain = domain;
    }

    public Domain domain() {
        return domain;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (world.isClient || player == null || !(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }

        BlockPos where = context.getBlockPos();
        SettlementManager manager = SettlementManager.get(serverWorld);
        Settlement colony = manager.at(where)
                .filter(settlement -> settlement.owner().isOwnedBy(player.getUuid()))
                .orElse(null);

        if (colony == null) {
            // Артефакт работает на своей земле. Не из жадности: серп растит
            // поля колонии, отвес двигает её стройку — вне границ ему просто
            // нечего делать, и молчание здесь читалось бы поломкой.
            player.sendMessage(Text.translatable("villagepax.artifact.not_here"), true);
            return ActionResult.CONSUME;
        }

        boolean worked = switch (domain) {
            case HARVEST -> sickle(serverWorld, colony, player, where);
            case STONE -> plumb(serverWorld, manager, colony, player);
            case WATCH -> eye(serverWorld, colony, player);
        };

        if (worked) {
            player.getItemCooldownManager().set(this, COOLDOWN);
        }
        return ActionResult.CONSUME;
    }

    /**
     * Серп изобилия: поле колонии подрастает целиком.
     * <p>
     * Не одна грядка под рукой, а <b>всё поле</b>, к которому она
     * принадлежит: наклоняться над каждым ростком с артефактом в руках —
     * это работа фермера, а не чудо. Одна ступень роста, а не спелость:
     * у фермера должно остаться что жать.
     */
    private boolean sickle(ServerWorld world, Settlement colony, PlayerEntity player,
                           BlockPos touched) {
        int grown = 0;
        for (Building building : colony.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            List<BlockPos> plots = FarmJob.plots(building);
            if (plots.stream().noneMatch(plot -> plot.isWithinDistance(touched, 24))) {
                continue;
            }
            for (BlockPos plot : plots) {
                BlockState state = world.getBlockState(plot);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                    world.setBlockState(plot, crop.withAge(crop.getAge(state) + 1));
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                            plot.getX() + 0.5, plot.getY() + 0.7, plot.getZ() + 0.5,
                            3, 0.3, 0.3, 0.3, 0.0);
                    grown++;
                }
            }
        }

        player.sendMessage(Text.translatable("villagepax.artifact.sickle", grown), true);
        if (grown > 0) {
            world.playSound(null, touched, SoundEvents.ITEM_BONE_MEAL_USE,
                    SoundCategory.BLOCKS, 0.8f, 1.2f);
        }
        return grown > 0;
    }

    /**
     * Отвес зодчего: стройка прыгает вперёд на три десятка блоков.
     * <p>
     * Материал шлёт артефакт, кладёт по-прежнему билдер — тем же путём,
     * каким это делает чудо камня. Две разные стройки в одном моде были бы
     * двумя разными наборами ошибок.
     */
    private boolean plumb(ServerWorld world, SettlementManager manager, Settlement colony,
                          PlayerEntity player) {
        Building site = Miracles.underConstruction(colony).orElse(null);
        if (site == null) {
            player.sendMessage(Text.translatable("villagepax.artifact.plumb_idle"), true);
            return false;
        }

        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        if (schematic == null) {
            return false;
        }

        Materials.shortfall(schematic, site, PLUMB_BLOCKS).forEach((item, count) ->
                site.stock().add(Registries.ITEM.getId(item), count));
        BuildJob.advance(world, manager, colony.id(), site.id(), PLUMB_BLOCKS);

        player.sendMessage(Text.translatable("villagepax.artifact.plumb",
                Text.translatable(com.villagepax.core.building.BuildingTypes
                        .displayName(site.type()))), true);
        world.playSound(null, site.anchor(), SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.BLOCKS, 0.6f, 1.4f);
        return true;
    }

    /**
     * Око дозора: видно, сколько их и где.
     * <p>
     * Свечение и слабость, а не урон. Убивать отряд артефактом значило бы
     * отменить войну одним предметом; видеть врага в темноте и драться
     * с ослабленным — это помощь, после которой бой всё равно нужно
     * выиграть руками стражи.
     */
    private boolean eye(ServerWorld world, Settlement colony, PlayerEntity player) {
        WarParty party = colony.siege().orElse(null);
        if (party == null) {
            player.sendMessage(Text.translatable("villagepax.artifact.eye_quiet"), true);
            return false;
        }

        int marked = 0;
        for (CitizenEntity raider : Raids.bodiesOf(world, party)) {
            if (!raider.isAlive() || !raider.getBlockPos().isWithinDistance(colony.center(),
                    WATCH_RANGE)) {
                continue;
            }
            raider.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, WATCH_TICKS));
            raider.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, WATCH_TICKS));
            marked++;
        }

        player.sendMessage(Text.translatable("villagepax.artifact.eye", marked), true);
        world.playSound(null, colony.center(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 0.7f, 0.8f);
        return marked > 0;
    }

    /**
     * Подсказка предмета: чей это дар и что он делает.
     * <p>
     * Обязательна. Артефакт выдаётся раз в полсотни дней, и игрок,
     * получивший его, не должен гадать, куда им нажимать: вещь, о которой
     * надо читать вики, в этом моде считается недоделанной.
     */
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines,
                              TooltipContext context) {
        lines.add(Text.translatable("villagepax.artifact.tooltip." + domain.id())
                .formatted(Formatting.GRAY));
        lines.add(Text.translatable("villagepax.artifact.tooltip.gift")
                .formatted(Formatting.DARK_PURPLE));
    }
}
