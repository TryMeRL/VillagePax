package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Founding;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.faith.Miracles;
import com.villagepax.sim.faith.Faith;
import com.villagepax.sim.faith.Blessings;
import com.villagepax.core.faith.Domain;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.Assignments;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

/**
 * Сеть экрана ратуши: снимок вниз, намерения вверх.
 * <p>
 * Сеть своя, а не owo-шная, хотя вёрстка экрана на owo-lib. Причина в том,
 * что <b>сервер о библиотеке знать не обязан</b>: снимок и намерения — это
 * ванильный {@code PacketByteBuf} плюс кодек, который у снимка всё равно
 * есть. Если библиотека когда-нибудь отвалится, пострадает раскладка,
 * а не движок.
 * <p>
 * Снимок едет как NBT, собранный тем же кодеком, каким пишется на диск:
 * одно описание данных на сохранение, датапак и сеть — то самое, за что
 * взят Codec.
 * <p>
 * Намерения принимаются <b>только от открытого пульта</b>: пакет от клиента,
 * у которого экран не открыт, отвергается молча. Иначе кнопка в интерфейсе
 * превратилась бы в способ строить откуда угодно, минуя и ратушу, и границы.
 */
public final class TownHallNet {

    private static final Identifier UNKNOWN = new Identifier(VillagePax.MOD_ID, "unknown");

    public static final Identifier VIEW = new Identifier(VillagePax.MOD_ID, "town_hall_view");
    public static final Identifier ORDER = new Identifier(VillagePax.MOD_ID, "town_hall_order");
    public static final Identifier ASSIGN = new Identifier(VillagePax.MOD_ID, "town_hall_assign");
    public static final Identifier UPGRADE = new Identifier(VillagePax.MOD_ID, "town_hall_upgrade");
    public static final Identifier PRIORITY = new Identifier(VillagePax.MOD_ID, "town_hall_priority");

    /**
     * Просьбы к небу: благословение и чудо.
     * <p>
     * Два канала, а не один с признаком: они стоят разных денег и
     * отказывают по разным причинам, и склеивать их значило бы писать
     * одну ветку с развилкой внутри — то самое место, где однажды
     * перепутается цена.
     */
    public static final Identifier BLESS = new Identifier(VillagePax.MOD_ID, "town_hall_bless");
    public static final Identifier MIRACLE = new Identifier(VillagePax.MOD_ID, "town_hall_miracle");

    /** Голограмма: клиент просит план схемы, потом примеряет место. */
    public static final Identifier PLAN_REQUEST = new Identifier(VillagePax.MOD_ID, "plan_request");
    public static final Identifier PLAN = new Identifier(VillagePax.MOD_ID, "plan");
    public static final Identifier PROBE = new Identifier(VillagePax.MOD_ID, "probe");
    public static final Identifier VERDICT = new Identifier(VillagePax.MOD_ID, "verdict");

    /** Снимок, который ничего не утверждает: показывать нечего, но экран жив. */
    public static final TownHallView EMPTY = new TownHallView("", UNKNOWN, "hamlet",
            0, 0, new TownHallView.Household(0, 0, 0, 0, 0), Optional.empty(),
            List.of(), List.of(), new ItemTally(), List.of(), List.of(), Optional.empty(),
            new TownHallView.Growth("villagepax.level.hamlet", Optional.empty(), 0, 1,
                    List.of(), true),
            TownHallView.FaithView.NONE);

    private TownHallNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ORDER, (server, player, handler, buf, sender) -> {
            // Читать надо здесь: за пределами обработчика буфер уже освобождён.
            Identifier schematic = buf.readIdentifier();
            BlockPos anchor = buf.readBlockPos();
            BlockRotation rotation = BuildOrders.rotation(buf.readString(16));
            server.execute(() -> order(player, schematic, anchor, rotation));
        });

        ServerPlayNetworking.registerGlobalReceiver(PLAN_REQUEST, (server, player, handler, buf, sender) -> {
            Identifier schematic = buf.readIdentifier();
            server.execute(() -> sendPlan(player, schematic));
        });

        ServerPlayNetworking.registerGlobalReceiver(PROBE, (server, player, handler, buf, sender) -> {
            Identifier schematic = buf.readIdentifier();
            BlockPos anchor = buf.readBlockPos();
            BlockRotation rotation = BuildOrders.rotation(buf.readString(16));
            server.execute(() -> probe(player, schematic, anchor, rotation));
        });

        ServerPlayNetworking.registerGlobalReceiver(PRIORITY, (server, player, handler, buf, sender) -> {
            UUID building = buf.readUuid();
            int shift = buf.readInt();
            server.execute(() -> reorder(player, building, shift));
        });

        ServerPlayNetworking.registerGlobalReceiver(UPGRADE, (server, player, handler, buf, sender) -> {
            UUID building = buf.readUuid();
            server.execute(() -> upgrade(player, building));
        });

        ServerPlayNetworking.registerGlobalReceiver(BLESS, (server, player, handler, buf, sender) -> {
            String domain = buf.readString(32);
            server.execute(() -> bless(player, domain));
        });

        ServerPlayNetworking.registerGlobalReceiver(MIRACLE, (server, player, handler, buf, sender) -> {
            String domain = buf.readString(32);
            server.execute(() -> miracle(player, domain));
        });

        ServerPlayNetworking.registerGlobalReceiver(ASSIGN, (server, player, handler, buf, sender) -> {
            UUID citizen = buf.readUuid();
            Optional<Identifier> profession = buf.readOptional(PacketByteBuf::readIdentifier);
            server.execute(() -> assign(player, citizen, profession));
        });
    }

    // --- снимок ---

    public static void writeView(PacketByteBuf buf, TownHallView view) {
        DataResult<?> encoded = TownHallView.CODEC.encodeStart(NbtOps.INSTANCE, view);
        buf.writeNbt((NbtCompound) encoded.result().orElseThrow(() -> new IllegalStateException(
                "снимок колонии не кодируется: "
                        + encoded.error().map(Object::toString).orElse("причина неизвестна"))));
    }

    /**
     * Снимок с сервера. Битый снимок — не причина ронять клиент: экран
     * покажет пустую колонию, а в логе останется причина.
     */
    public static TownHallView readView(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt == null) {
            return EMPTY;
        }
        DataResult<TownHallView> decoded = TownHallView.CODEC.parse(NbtOps.INSTANCE, nbt);
        decoded.error().ifPresent(error ->
                VillagePax.LOGGER.warn("Снимок колонии не читается: {}", error.message()));
        return decoded.result().orElse(EMPTY);
    }

    public static void sendView(ServerPlayerEntity viewer, TownHallView view) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeView(buf, view);
        ServerPlayNetworking.send(viewer, VIEW, buf);
    }

    // --- намерения ---

    /**
     * Разметить здание там, где игрок поставил голограмму.
     * <p>
     * Проверка открытого пульта здесь не годится: экран закрывается, как
     * только игрок выбрал здание, — дальше он выбирает место в мире.
     * Вместо неё владение колонией и <b>расстояние</b>: место приходит
     * от клиента, и «разметить на другом конце мира» не должно быть
     * возможно, даже если клиент попросит.
     */
    private static void order(ServerPlayerEntity player, Identifier schematic, BlockPos anchor,
                              BlockRotation rotation) {
        Settlement colony = ownedColony(player);
        if (colony == null) {
            tell(player, "villagepax.screen.order.no_colony");
            return;
        }
        if (tooFarToPlace(player, anchor)) {
            tell(player, "villagepax.screen.order.too_far");
            return;
        }

        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        BuildOrders.Result result = BuildOrders.place(manager, colony, schematic, anchor, rotation);

        // Разбор образцами, а не switch: switch по типам в Java 17 —
        // предпросмотр, а цель мода 17.
        if (result instanceof BuildOrders.Result.Placed placed) {
            Vec3i footprint = placed.footprint();
            tell(player, "villagepax.screen.order.placed",
                    Text.translatable(buildingKey(placed.site().type())),
                    Text.literal(footprint.getX() + "x" + footprint.getZ()),
                    Text.literal(String.valueOf(placed.blocks())));

            // И сразу — что нести. Без этой строки игрок уходил в шахту
            // наугад: список материалов пульт показывал только той стройке,
            // за которую уже взялся билдер.
            Map<Item, Integer> missing = BuildOrders.stillNeeded(world, colony, placed.site());
            if (missing.isEmpty()) {
                tell(player, "villagepax.screen.order.needs_none");
            } else {
                tell(player, "villagepax.screen.order.needs",
                        BuildOrders.shoppingLine(missing, 5));
            }
        } else if (result instanceof BuildOrders.Result.NoSchematic missing) {
            tell(player, "villagepax.screen.order.no_schematic",
                    Text.literal(missing.schematic().toString()));
        } else if (result instanceof BuildOrders.Result.BadName wrong) {
            tell(player, "villagepax.screen.order.bad_name",
                    Text.literal(wrong.schematic().toString()));
        } else if (result instanceof BuildOrders.Result.OutsideClaim outside) {
            tell(player, "villagepax.screen.order.outside",
                    Text.literal(outside.anchor().toShortString()));
        } else if (result instanceof BuildOrders.Result.Overlaps clash) {
            tell(player, "villagepax.screen.order.overlaps",
                    Text.translatable(buildingKey(clash.clash().type())),
                    Text.literal(clash.clash().anchor().toShortString()));
        }
    }

    /**
     * Подвинуть стройку в очереди.
     * <p>
     * Требует открытого пульта, как и остальные приказы: иначе кнопка
     * в интерфейсе стала бы способом распоряжаться колонией откуда угодно.
     */
    private static void reorder(ServerPlayerEntity player, UUID building, int shift) {
        Settlement colony = consoleColony(player);
        if (colony == null) {
            tell(player, "villagepax.screen.console.not_yours");
            return;
        }

        SettlementManager manager = SettlementManager.get(player.getServerWorld());
        manager.update(colony.id(), settlement -> settlement.building(building)
                .ifPresent(site -> site.setPriority(
                        Math.max(-99, Math.min(99, site.priority() + shift)))));
    }

    /**
     * Улучшить здание до следующего уровня.
     * <p>
     * Требует открытого пульта: улучшение заказывается кнопкой в экране,
     * а не голограммой — место уже выбрано, здание растёт от своего угла.
     */
    private static void upgrade(ServerPlayerEntity player, UUID building) {
        Settlement colony = consoleColony(player);
        if (colony == null) {
            tell(player, "villagepax.screen.console.not_yours");
            return;
        }

        SettlementManager manager = SettlementManager.get(player.getServerWorld());
        BuildOrders.Result result = BuildOrders.upgrade(manager, colony, building);

        Text name = colony.building(building)
                .map(known -> (Text) Text.translatable(buildingKey(known.type())))
                .orElse(Text.literal("?"));
        int level = colony.building(building).map(Building::level).orElse(0);

        tell(player, BuildOrders.upgradeKey(result), name, Text.literal(String.valueOf(level)));

        // И сразу — что нести. Без этой строки «улучшение не проходит»:
        // заказ принят, билдер ждёт камня, а игрок видит только то, что
        // ничего не происходит. Ровно та же строка, что и при разметке,
        // и по той же причине.
        if (result instanceof BuildOrders.Result.Placed placed) {
            Map<Item, Integer> missing = BuildOrders.stillNeeded(
                    player.getServerWorld(), colony, placed.site());
            if (missing.isEmpty()) {
                tell(player, "villagepax.screen.order.needs_none");
            } else {
                tell(player, "villagepax.screen.order.needs",
                        BuildOrders.shoppingLine(missing, 5));
            }
        }
    }

    /**
     * Отправить клиенту план схемы для голограммы.
     * <p>
     * Один раз на выбранное здание: план не меняется, а игрок водит
     * призраком по земле сколько захочет.
     */
    private static void sendPlan(ServerPlayerEntity player, Identifier schematicId) {
        if (ownedColony(player) == null) {
            // Молчание тут стоило заказчику вечера: он жал «Заказать»,
            // призрак не появлялся, и понять, что своей колонии у него
            // просто нет, было неоткуда.
            tell(player, "villagepax.screen.order.no_colony");
            return;
        }

        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        GhostPlan.of(schematicId, schematic).write(buf);
        ServerPlayNetworking.send(player, PLAN, buf);
    }

    /**
     * Примерка: можно ли строить вот здесь. Колония не меняется.
     * <p>
     * Проверяет тот же {@link BuildOrders}, что и сам заказ. Иначе правила
     * «где можно строить» оказались бы описаны дважды — в проверке и
     * в подсказке, — и разошлись бы в первый же день.
     */
    private static void probe(ServerPlayerEntity player, Identifier schematic, BlockPos anchor,
                              BlockRotation rotation) {
        Settlement colony = ownedColony(player);
        if (colony == null) {
            // Приговор вместо молчания: призрак так и скажет, чего не хватает.
            PacketByteBuf refusal = PacketByteBufs.create();
            refusal.writeBoolean(false);
            refusal.writeString("villagepax.hologram.no_colony");
            ServerPlayNetworking.send(player, VERDICT, refusal);
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        if (tooFarToPlace(player, anchor)) {
            buf.writeBoolean(false);
            buf.writeString("villagepax.hologram.too_far");
        } else {
            BuildOrders.Result verdict = BuildOrders.check(colony, schematic, anchor, rotation);
            buf.writeBoolean(verdict instanceof BuildOrders.Result.Placed);
            buf.writeString(BuildOrders.hologramKey(verdict));
        }
        ServerPlayNetworking.send(player, VERDICT, buf);
    }

    private static boolean tooFarToPlace(ServerPlayerEntity player, BlockPos anchor) {
        return !player.getBlockPos().isWithinDistance(anchor, BuildOrders.PLACEMENT_RANGE);
    }

    /** Колония игрока — без требования открытого пульта. */
    private static Settlement ownedColony(ServerPlayerEntity player) {
        SettlementManager manager = SettlementManager.get(player.getServerWorld());
        return Founding.colonyOf(manager, player.getUuid()).orElse(null);
    }

    /**
     * Сменить жителю профессию — решение заказчика: назначается само,
     * но игрок вправе переназначить.
     * <p>
     * Мастерские раздаются сразу же: иначе новый лесоруб ждал бы рассвета,
     * и игрок решил бы, что кнопка не работает. Тот же урок, что с домом,
     * достроенным в полдень.
     */
    private static void assign(ServerPlayerEntity player, UUID citizenId,
                               Optional<Identifier> profession) {
        Settlement colony = consoleColony(player);
        if (colony == null) {
            tell(player, "villagepax.screen.console.not_yours");
            return;
        }
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Citizen citizen = colony.citizen(citizenId).orElse(null);

        Assignments.Result result = Assignments.set(world, manager, colony, citizenId, profession);
        if (result == Assignments.Result.NO_SUCH_PROFESSION) {
            tell(player, "villagepax.screen.assign.unknown",
                    Text.literal(profession.map(Identifier::toString).orElse("?")));
            return;
        }
        if (result == Assignments.Result.LOCKED) {
            tell(player, "villagepax.screen.assign.locked");
            return;
        }
        if (result == Assignments.Result.TOO_YOUNG) {
            // Кнопка ремесла стоит у каждого жителя в списке, и ребёнок
            // в нём ничем от взрослого не отличается. Нажал — и обязан
            // услышать почему.
            tell(player, "villagepax.screen.assign.too_young");
            return;
        }
        if (result == Assignments.Result.WRONG_NATURE) {
            // С именем, а не безлично: отказал не мод, отказал Роллон,
            // и в следующий раз игрок посмотрит в характер прежде, чем
            // выбирать, кому дать меч.
            tell(player, "villagepax.screen.assign.wrong_nature",
                    Text.literal(citizen == null ? "?" : citizen.fullName()));
            return;
        }
        if (result != Assignments.Result.DONE || citizen == null) {
            return;
        }
        tell(player, "villagepax.screen.assign.done",
                Text.literal(citizen.fullName()),
                profession.flatMap(ProfessionManager::get)
                        .map(known -> (Text) Text.translatable(known.displayName()))
                        .orElse(Text.translatable("villagepax.profession.none")));
    }

    /**
     * Колония, чей пульт у игрока открыт.
     * <p>
     * Проверяется и владение, и открытый экран: намерение без открытого
     * пульта — либо чужой клиент, либо наш собственный после того, как
     * колонию отобрали. И то и другое отвергается молча.
     */
    private static Settlement consoleColony(ServerPlayerEntity player) {
        SettlementManager manager = SettlementManager.get(player.getServerWorld());
        Settlement colony = Founding.colonyOf(manager, player.getUuid()).orElse(null);

        if (colony == null) {
            return null;
        }
        if (!(player.currentScreenHandler instanceof TownHallScreenHandler console)
                || !console.settlement().equals(colony.id())) {
            return null;
        }
        return colony;
    }

    /**
     * Ключ локализации названия здания.
     * <p>
     * Из файла типа здания, как у профессии. Соглашение об именовании
     * осталось запасным путём: у здания без объявленного типа пусть будет
     * хоть какое-то имя, а не пустая строка в интерфейсе.
     */
    public static String buildingKey(Identifier type) {
        return BuildingTypes.displayName(type);
    }


    // --- вера ---

    /**
     * Позвать благословение.
     * <p>
     * Кнопка в пульте не заперта никогда, и весь смысл этой ветки в том,
     * чтобы на каждое нажатие был <b>ответ словами</b>. Запертая кнопка
     * объясняет ровно столько же, сколько молчащая, а это в моде уже
     * считается поломкой.
     */
    private static void bless(ServerPlayerEntity player, String domain) {
        Settlement colony = consoleColony(player);
        if (colony == null) {
            tell(player, Blessings.Verdict.NOT_YOURS.key());
            return;
        }

        Domain which = domainOf(domain);
        if (which == null) {
            tell(player, Blessings.Verdict.NO_GOD.key());
            return;
        }

        ServerWorld world = player.getServerWorld();
        long today = Schedule.dayOf(world.getTimeOfDay());
        Blessings.Verdict verdict = Blessings.invoke(SettlementManager.get(world), colony,
                which, today);

        tell(player, verdict.key(), Text.translatable(which.key()),
                Text.literal(String.valueOf(Faith.BLESSING_COST)),
                Text.literal(String.valueOf(Faith.BLESSING_DAYS)));
    }

    /** Попросить чудо. Отказ «просить нечего» приходит до списания очков. */
    private static void miracle(ServerPlayerEntity player, String domain) {
        Settlement colony = consoleColony(player);
        if (colony == null) {
            tell(player, Miracles.Verdict.NOT_YOURS.key());
            return;
        }

        Domain which = domainOf(domain);
        if (which == null) {
            tell(player, Miracles.Verdict.NO_GOD.key());
            return;
        }

        ServerWorld world = player.getServerWorld();
        long today = Schedule.dayOf(world.getTimeOfDay());
        Miracles.Verdict verdict = Miracles.call(world, SettlementManager.get(world), colony,
                which, today);

        tell(player, verdict.key(), Text.translatable(which.key()),
                Text.literal(String.valueOf(Faith.MIRACLE_COST)));
    }

    /**
     * Домен по строке из пакета.
     * <p>
     * Клиент прислать может что угодно — хоть из своего мода, хоть
     * из битого пакета. Неизвестное имя тут не падение, а обычный отказ
     * «нет такого бога»: сервер не обязан верить клиенту, но и ронять
     * себя из-за него не должен.
     */
    private static Domain domainOf(String id) {
        for (Domain domain : Domain.values()) {
            if (domain.id().equals(id)) {
                return domain;
            }
        }
        return null;
    }

    private static void tell(ServerPlayerEntity player, String key, Text... args) {
        player.sendMessage(Text.translatable(key, (Object[]) args), false);
    }
}
