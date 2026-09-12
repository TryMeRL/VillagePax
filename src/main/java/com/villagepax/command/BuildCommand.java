package com.villagepax.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.villagepax.core.culture.Culture;
import com.villagepax.screen.BuildOrders;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Founding;
import com.villagepax.sim.Guide;
import com.villagepax.sim.Gender;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.work.Jobs;
import com.villagepax.sim.Settlement;
import java.util.ArrayList;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Ground;
import com.villagepax.sim.VillageSites;
import com.villagepax.sim.VillageSites;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.core.config.Config;
import com.villagepax.core.config.Configs;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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

    private BuildCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("villagepax")
                        // Права спрашиваются у каждой ветки отдельно, а не
                        // у корня. Смотреть — всем: без «где деревня» и «что
                        // с колонией» в мод нельзя играть вовсе, а в одиночной
                        // игре без читов корневое ограничение закрывало и это.
                        // Менять мир командой — по-прежнему оператору.
                        .then(literal("build")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(argument("schematic", IdentifierArgumentType.identifier())
                                        .suggests((context, builder) -> CommandSource
                                                .suggestIdentifiers(SchematicLoader.ids(), builder))
                                        .executes(context -> build(context, BlockRotation.NONE))
                                        .then(argument("rotation", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource
                                                        .suggestMatching(BuildOrders.ROTATIONS, builder))
                                                .executes(context -> build(context,
                                                        BuildOrders.rotation(StringArgumentType.getString(context, "rotation"))))))
                        )
                        .then(literal("supply")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(argument("schematic", IdentifierArgumentType.identifier())
                                        .suggests((context, builder) -> CommandSource
                                                .suggestIdentifiers(SchematicLoader.ids(), builder))
                                        .executes(BuildCommand::supply)))
                        .then(literal("hire")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(argument("profession", IdentifierArgumentType.identifier())
                                        .suggests((context, builder) -> CommandSource
                                                .suggestIdentifiers(ProfessionManager.ids(), builder))
                                        .executes(BuildCommand::hire)))
                        .then(literal("locate").executes(BuildCommand::locate))
                        .then(literal("sites").executes(BuildCommand::sites))
                        .then(literal("status").executes(BuildCommand::status))
                        .then(literal("guide").executes(BuildCommand::guide))
                        .then(literal("config")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(BuildCommand::reloadConfig))));
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

        // Проверки те же, что у экрана ратуши: один путь заказа на обоих.
        BuildOrders.Result result = BuildOrders.place(manager, colony, schematicId,
                player.getBlockPos(), rotation);

        // Разбор образцами, а не switch: switch по типам в Java 17 —
        // предпросмотр, а цель мода 17.
        if (result instanceof BuildOrders.Result.Placed placed) {
            tell(context, "Стройка размечена: " + placed.site().type()
                    + " ур. " + placed.site().level()
                    + ", след " + placed.footprint().getX() + "x" + placed.footprint().getZ()
                    + ", блоков " + placed.blocks()
                    + ". Нужен житель с профессией " + BuildJob.BUILDER + ".");
            return 1;
        }
        if (result instanceof BuildOrders.Result.NoSchematic missing) {
            tell(context, "Схемы " + missing.schematic() + " нет. Загружены: "
                    + SchematicLoader.ids());
        } else if (result instanceof BuildOrders.Result.BadName wrong) {
            tell(context, "Имя схемы обязано кончаться на _lvl<число>: " + wrong.schematic());
        } else if (result instanceof BuildOrders.Result.OutsideClaim outside) {
            tell(context, "Здесь не твоя земля: " + outside.anchor().toShortString()
                    + " вне границ колонии «" + colony.name() + "»");
        } else if (result instanceof BuildOrders.Result.Overlaps clash) {
            tell(context, "След пересекается с уже размеченным " + clash.clash().type()
                    + " в " + clash.clash().anchor().toShortString());
        }
        return 0;
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

        Warehouse warehouse = Warehouse.of(context.getSource().getWorld(), colony);
        if (warehouse.containerCount() == 0) {
            tell(context, "Складывать некуда: у колонии нет ни одного хранилища. "
                    + "Ратуша — стартовое, поставь её чертежом.");
            return 0;
        }

        Map<Item, Integer> needed = Materials.required(schematic);
        int asked = 0;
        int delivered = 0;
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            asked += entry.getValue();
            delivered += deliver(warehouse, entry.getKey(), entry.getValue());
        }

        tell(context, "Завезено " + delivered + " из " + asked + " штук по " + needed.size()
                + " видам предметов" + (delivered < asked ? " — дальше некуда класть" : ""));
        return 1;
    }

    /** Возвращает, сколько штук удалось положить: контейнеры конечны. */
    private static int deliver(Warehouse warehouse, Item item, int count) {
        int delivered = 0;
        while (delivered < count) {
            int chunk = Math.min(count - delivered, item.getMaxCount());
            ItemStack leftover = warehouse.add(new ItemStack(item, chunk));
            delivered += chunk - leftover.getCount();
            if (!leftover.isEmpty()) {
                break;
            }
        }
        return delivered;
    }

    /**
     * Нанять жителя нужной профессии.
     * <p>
     * Настоящий путь — жители приходят под жильё в задаче 1.8. Пока их
     * взять негде, и без этой команды курьера нельзя было бы увидеть
     * в игре вовсе: та же ловушка, что была с первым строителем.
     */
    private static int hire(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerWorld world = context.getSource().getWorld();
        SettlementManager manager = SettlementManager.get(world);

        Settlement colony = colonyOrTell(context, manager, player.getUuid());
        if (colony == null) {
            return 0;
        }

        Identifier profession = IdentifierArgumentType.getIdentifier(context, "profession");
        if (Jobs.forProfession(Optional.of(profession)).isEmpty()) {
            tell(context, "Профессии " + profession + " нет. Есть: " + ProfessionManager.ids());
            return 0;
        }
        if (!colony.hasRoomForCitizen()) {
            tell(context, "Некуда селить: уровень «" + colony.level().id() + "» держит "
                    + colony.level().maxCitizens() + " жителей, а их уже " + colony.population());
            return 0;
        }

        Culture culture = CultureManager.get(colony.culture());
        Citizen hired = culture == null
                ? Citizen.newborn("Безымянный", "", colony.culture(), Gender.MALE)
                : Founding.newCitizen(colony.culture(), culture, new Random(world.getRandom().nextLong()));
        hired.setProfession(profession);
        hired.setPosition(player.getPos());

        manager.update(colony.id(), settlement -> settlement.addCitizen(hired));
        CitizenSpawner.spawnBody(world, colony, hired);

        tell(context, "Нанят " + hired.fullName() + " — " + profession.getPath()
                + ". Жителей в колонии: " + colony.population());
        return 1;
    }

    /**
     * Где ближайшая деревня народа.
     * <p>
     * Без этого мод буквально нельзя найти. Места деревень считаются по
     * семени и стоят в среднем в семи с половиной сотнях блоков друг
     * от друга; игрок, который не знает, куда идти, будет ходить долго
     * и решит, что мод не работает. Команда отвечает на вопрос «а где
     * они вообще?» — и тем же ответом пользуюсь я, когда проверяю.
     * <p>
     * Считается по сетке, а не по загруженным чанкам: ответ нужен и про
     * те места, до которых игрок ещё не доходил.
     */
    /**
     * Выдать путевые записки.
     * <p>
     * Всем, а не оператору: это руководство, а не власть. Если руки
     * заняты — падает под ноги, потому что молча съесть книгу хуже,
     * чем уронить её.
     */
    private static int guide(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        player.getInventory().offerOrDrop(Guide.book());
        context.getSource().sendFeedback(
                () -> Text.translatable("villagepax.guide.given"), false);
        return 1;
    }

    private static int locate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = worldOf(source);
        BlockPos from = BlockPos.ofFloored(source.getPosition());

        List<VillageSites.Guess> found = VillageSites.guessEach(world, from);
        if (found.isEmpty()) {
            // Причину назвать обязательно. «Ничего нет» без объяснения
            // выглядит поломкой, а на деле это ответ: рядом нет биома,
            // в котором этот народ ставит деревни.
            source.sendFeedback(() -> Text.translatable("villagepax.locate.none",
                    Text.literal(biomesOfCultures())), false);
            return 0;
        }

        // Строка на каждый народ: игроку нужен выбор, а не один ответ.
        for (VillageSites.Guess guess : found) {
            int away = (int) Math.sqrt(guess.where().getSquaredDistance(from));
            source.sendFeedback(() -> Text.translatable("villagepax.locate.found",
                    Text.translatable("villagepax.culture." + guess.culture().getPath()),
                    Text.literal(guess.where().getX() + ", " + guess.where().getZ()),
                    Text.literal(String.valueOf(away))), false);
        }
        return found.size();
    }

    /** В каких биомах народы ставят деревни — для объяснения отказа. */
    private static String biomesOfCultures() {
        List<String> wanted = new ArrayList<>();
        CultureManager.all().values().forEach(culture -> wanted.add(culture.spawn().biomes()));
        return String.join(", ", wanted);
    }

    /**
     * Мир источника команды.
     * <p>
     * У консольного источника мира может не быть вовсе: команды с консоли
     * попадают в очередь ещё до того, как сервер создал миры, и источник
     * у них построен с пустым миром. Игрок такого не увидит никогда, но
     * падать с исключением из-за этого нельзя — да и разбирать поиск
     * деревень с консоли иначе невозможно.
     */
    private static ServerWorld worldOf(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        return world != null ? world : source.getServer().getOverworld();
    }

    /**
     * Разбор мест под деревни: почему тут пусто.
     * <p>
     * Написана после того, как игрок пришёл по указанным координатам
     * и не увидел ничего. Причина была в одной строке — землю искали
     * по карте высот мира, а она считает поверхностью верхушку листвы, —
     * но чтобы её найти, пришлось читать лог игрока. Такой команде место
     * в моде: она отвечает на вопрос «почему деревни нет» за один вызов.
     * <p>
     * Показывает по каждому ближайшему месту: чей народ, где, загружен ли
     * чанк, тот ли биом, нашлась ли земля и не занято ли место.
     */
    private static int sites(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = worldOf(source);
        BlockPos from = BlockPos.ofFloored(source.getPosition());
        SettlementManager manager = SettlementManager.get(world);

        VillageSites.Guess nearest = VillageSites.guessNearest(world, from);
        source.sendFeedback(() -> Text.literal("Ближайшее место по сетке: "
                + (nearest == null ? "нет в шести клетках"
                        : nearest.culture() + " " + nearest.where().toShortString())), false);

        for (Map.Entry<Identifier, Culture> entry : CultureManager.all().entrySet()) {
            BlockPos column = VillageSites.plannedSite(world, entry.getKey(), entry.getValue(),
                    Math.floorDiv(from.getX() >> 4, VillageSites.spacing(entry.getValue())),
                    Math.floorDiv(from.getZ() >> 4, VillageSites.spacing(entry.getValue())));

            String about;
            if (column == null) {
                about = "в этой клетке места нет (биом или высота)";
            } else {
                boolean loaded = world.isChunkLoaded(column);
                BlockPos ground = loaded
                        ? Ground.buildableAt(world, column.getX(), column.getZ()).orElse(null)
                        : null;
                // Карта высот показывается рядом с землёй намеренно: их
                // расхождение и есть та ошибка, из-за которой деревни
                // не появлялись. Под пологом леса эти числа расходятся,
                // и это видно сразу.
                int heightmap = loaded
                        ? world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                                column.getX(), column.getZ())
                        : -1;
                about = column.toShortString()
                        + ", чанк " + (loaded ? "загружен" : "не загружен")
                        + ", земля " + (ground == null ? "не найдена" : "на " + ground.getY())
                        + ", карта высот " + heightmap
                        + ", занято " + manager.isSettled(column);
            }

            String line = entry.getKey() + ": " + about;
            source.sendFeedback(() -> Text.literal(line), false);
        }

        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        SettlementManager manager = SettlementManager.get(context.getSource().getWorld());

        Settlement colony = colonyOrTell(context, manager, player.getUuid());
        if (colony == null) {
            return 0;
        }

        Warehouse warehouse = Warehouse.of(context.getSource().getWorld(), colony);
        for (Citizen citizen : colony.citizens()) {
            tell(context, "  " + citizen.fullName()
                    + " — " + citizen.profession().map(Identifier::getPath).orElse("без профессии")
                    + ", " + citizen.jobState().phase().id()
                    + citizen.jobState().firstLoad()
                            .map(load -> ", несёт " + load.count() + " x " + load.item())
                            .orElse(""));
        }

        tell(context, "Колония «" + colony.name() + "»: жителей " + colony.population()
                + ", зданий " + colony.buildings().size()
                + ", хранилищ " + warehouse.containerCount()
                + ", на складе штук " + warehouse.totalItems());

        for (Building building : colony.buildings()) {
            Optional<Schematic> schematic = SchematicLoader.get(BuildJob.schematicId(building));
            String progress = schematic
                    .map(s -> building.nextStep() + "/" + s.plan().steps().size())
                    .orElse("схемы нет");
            tell(context, "  " + building.type() + " ур. " + building.level()
                    + " — " + building.progress().id() + ", шагов " + progress
                    + ", в запасе площадки " + building.stock().total());
        }
        return 1;
    }

    private static Settlement colonyOrTell(CommandContext<ServerCommandSource> context,
                                           SettlementManager manager, UUID player) {
        Settlement colony = Founding.colonyOf(manager, player).orElse(null);
        if (colony == null) {
            tell(context, "У тебя нет колонии. Поставь ратушу чертежом.");
        }
        return colony;
    }

    /**
     * Перечитать настройки, не перезаходя в мир.
     * <p>
     * Правка файла без перезапуска — то, чего игрок ждёт от настроек в первую
     * очередь. Читает командный поток, а держит значение {@code volatile}
     * поле: иного общего изменяемого состояния в моде нет, и это названо
     * прямо в {@code Configs}.
     */
    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        Config config = Configs.load();
        tell(context, "Настройки перечитаны: деревни народов "
                + (config.autonomousVillages() ? "есть" : "выключены")
                + ", предел жителей x" + config.populationScale()
                + ", голод " + config.hungerWarnDays() + "/" + config.hungerLeaveDays()
                + " дней. Файл: " + Configs.path());
        return 1;
    }

    private static void tell(CommandContext<ServerCommandSource> context, String message) {
        context.getSource().sendFeedback(() -> Text.literal(message), false);
    }
}
