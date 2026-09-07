package com.villagepax.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Founding;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Отладочная команда: поставить здание в очередь стройки и наполнить склад.
 * <p>
 * Настоящий путь заказа здания — интерфейс ратуши из задачи 1.10, а для
 * автономных деревень — контроллер из 1.12. Пока ни того, ни другого нет,
 * и без команды стройку можно было бы увидеть только в игровом тесте.
 * Права оператора требуются именно поэтому: это инструмент разработки.
 */
public final class BuildCommand {

    private static final List<String> ROTATIONS = List.of("none", "cw90", "cw180", "ccw90");

    private BuildCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("villagepax")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("build")
                                .then(argument("schematic", IdentifierArgumentType.identifier())
                                        .suggests((context, builder) -> CommandSource
                                                .suggestIdentifiers(SchematicLoader.ids(), builder))
                                        .executes(context -> build(context, BlockRotation.NONE))
                                        .then(argument("rotation", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource
                                                        .suggestMatching(ROTATIONS, builder))
                                                .executes(context -> build(context,
                                                        rotation(StringArgumentType.getString(context, "rotation"))))))
                        )
                        .then(literal("supply")
                                .then(argument("schematic", IdentifierArgumentType.identifier())
                                        .suggests((context, builder) -> CommandSource
                                                .suggestIdentifiers(SchematicLoader.ids(), builder))
                                        .executes(BuildCommand::supply)))
                        .then(literal("status").executes(BuildCommand::status))));
    }

    private static int build(CommandContext<ServerCommandSource> context, BlockRotation rotation)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        SettlementManager manager = SettlementManager.get(context.getSource().getWorld());

        Settlement colony = colonyOrTell(context, manager, player.getUuid());
        if (colony == null) {
            return 0;
        }

        Identifier schematicId = IdentifierArgumentType.getIdentifier(context, "schematic");
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            tell(context, "Схемы " + schematicId + " нет. Загружены: " + SchematicLoader.ids());
            return 0;
        }

        Identifier type = BuildJob.buildingTypeOf(schematicId).orElse(null);
        int level = BuildJob.levelOf(schematicId).orElse(1);
        if (type == null) {
            tell(context, "Имя схемы обязано кончаться на _lvl<число>: " + schematicId);
            return 0;
        }

        BlockPos anchor = player.getBlockPos();
        if (!colony.claims(anchor)) {
            tell(context, "Здесь не твоя земля: место вне границ колонии «" + colony.name() + "»");
            return 0;
        }

        Building clash = overlapping(colony, anchor, rotation, schematic);
        if (clash != null) {
            tell(context, "След пересекается с уже размеченным " + clash.type()
                    + " в " + clash.anchor().toShortString());
            return 0;
        }

        Building site = new Building(UUID.randomUUID(), type, level, anchor, rotation,
                BuildProgress.PLANNED, List.of());
        manager.update(colony.id(), settlement -> settlement.addBuilding(site));

        Vec3i footprint = BuildSite.rotatedSize(schematic.size(), rotation);
        tell(context, "Стройка размечена: " + type + " ур. " + level
                + ", след " + footprint.getX() + "x" + footprint.getZ()
                + ", блоков " + schematic.plan().blockCount()
                + ". Нужен житель с профессией " + BuildJob.BUILDER + ".");
        return 1;
    }

    /** Наполнить склад ровно тем, что нужно на схему: 1.7 заменит это курьером. */
    private static int supply(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        SettlementManager manager = SettlementManager.get(context.getSource().getWorld());

        Settlement colony = colonyOrTell(context, manager, player.getUuid());
        if (colony == null) {
            return 0;
        }

        Identifier schematicId = IdentifierArgumentType.getIdentifier(context, "schematic");
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            tell(context, "Схемы " + schematicId + " нет");
            return 0;
        }

        Map<Identifier, Integer> needed = Materials.required(schematic);
        manager.update(colony.id(), settlement ->
                needed.forEach((item, count) -> settlement.warehouse().add(item, count)));

        tell(context, "На склад добавлено видов предметов: " + needed.size()
                + ", всего штук: " + needed.values().stream().mapToInt(Integer::intValue).sum());
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        SettlementManager manager = SettlementManager.get(context.getSource().getWorld());

        Settlement colony = colonyOrTell(context, manager, player.getUuid());
        if (colony == null) {
            return 0;
        }

        tell(context, "Колония «" + colony.name() + "»: жителей " + colony.population()
                + ", зданий " + colony.buildings().size()
                + ", на складе штук " + colony.warehouse().total());

        for (Building building : colony.buildings()) {
            Optional<Schematic> schematic = SchematicLoader.get(BuildJob.schematicId(building));
            String progress = schematic
                    .map(s -> building.nextStep() + "/" + s.plan().steps().size())
                    .orElse("схемы нет");
            tell(context, "  " + building.type() + " ур. " + building.level()
                    + " — " + building.progress().id() + ", шагов " + progress);
        }
        return 1;
    }

    /**
     * Наложение следов. Два здания на одном месте перетирали бы блоки друг
     * друга бесконечно, каждое считая, что чинит повреждение.
     */
    private static Building overlapping(Settlement colony, BlockPos anchor, BlockRotation rotation,
                                        Schematic schematic) {
        Vec3i footprint = BuildSite.rotatedSize(schematic.size(), rotation);

        for (Building existing : colony.buildings()) {
            Schematic other = SchematicLoader.get(BuildJob.schematicId(existing)).orElse(null);
            if (other == null) {
                continue;
            }
            Vec3i otherFootprint = BuildSite.rotatedSize(other.size(), existing.rotation());
            if (boxesOverlap(anchor, footprint, existing.anchor(), otherFootprint)) {
                return existing;
            }
        }
        return null;
    }

    private static boolean boxesOverlap(BlockPos a, Vec3i sizeA, BlockPos b, Vec3i sizeB) {
        return a.getX() < b.getX() + sizeB.getX() && b.getX() < a.getX() + sizeA.getX()
                && a.getY() < b.getY() + sizeB.getY() && b.getY() < a.getY() + sizeA.getY()
                && a.getZ() < b.getZ() + sizeB.getZ() && b.getZ() < a.getZ() + sizeA.getZ();
    }

    private static Settlement colonyOrTell(CommandContext<ServerCommandSource> context,
                                           SettlementManager manager, UUID player) {
        Settlement colony = Founding.colonyOf(manager, player).orElse(null);
        if (colony == null) {
            tell(context, "У тебя нет колонии. Поставь ратушу чертежом.");
        }
        return colony;
    }

    private static BlockRotation rotation(String name) {
        return switch (name) {
            case "cw90" -> BlockRotation.CLOCKWISE_90;
            case "cw180" -> BlockRotation.CLOCKWISE_180;
            case "ccw90" -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private static void tell(CommandContext<ServerCommandSource> context, String message) {
        context.getSource().sendFeedback(() -> Text.literal(message), false);
    }
}
