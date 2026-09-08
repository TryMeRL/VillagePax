package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Founding;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Assignments;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.List;
import java.util.Optional;
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

    /** Снимок, который ничего не утверждает: показывать нечего, но экран жив. */
    public static final TownHallView EMPTY = new TownHallView("", UNKNOWN, "hamlet",
            0, 0, 0, 0, 0, 0, 0, Optional.empty(), List.of(), List.of(), new ItemTally(), List.of());

    private TownHallNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ORDER, (server, player, handler, buf, sender) -> {
            // Читать надо здесь: за пределами обработчика буфер уже освобождён.
            Identifier schematic = buf.readIdentifier();
            server.execute(() -> order(player, schematic));
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
     * Разметить здание там, где стоит игрок.
     * <p>
     * Место и поворот в задаче 1.10 берутся от игрока: голограмма из 1.11
     * заменит это выбором глазами, и тогда пакет понесёт позицию с поворотом.
     * Пока «здесь и как я стою» — уже играбельно и стоит одного пакета.
     */
    private static void order(ServerPlayerEntity player, Identifier schematic) {
        Settlement colony = consoleColony(player);
        if (colony == null) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        BuildOrders.Result result = BuildOrders.place(manager, colony, schematic,
                player.getBlockPos(), facingViewer(player));

        // Разбор образцами, а не switch: switch по типам в Java 17 —
        // предпросмотр, а цель мода 17.
        if (result instanceof BuildOrders.Result.Placed placed) {
            Vec3i footprint = placed.footprint();
            tell(player, "villagepax.screen.order.placed",
                    Text.translatable(buildingKey(placed.site().type())),
                    Text.literal(footprint.getX() + "x" + footprint.getZ()),
                    Text.literal(String.valueOf(placed.blocks())));
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
     * Поворот так, чтобы «лицо» схемы смотрело на игрока.
     * <p>
     * Схемы норманнов нарисованы фасадом на юг, поэтому нужный поворот —
     * тот, который переводит юг в направление, откуда игрок смотрит.
     */
    private static BlockRotation facingViewer(ServerPlayerEntity player) {
        Direction front = player.getHorizontalFacing().getOpposite();
        return switch (front) {
            case WEST -> BlockRotation.CLOCKWISE_90;
            case NORTH -> BlockRotation.CLOCKWISE_180;
            case EAST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /**
     * Ключ локализации типа здания по соглашению об именовании:
     * {@code villagepax:norman/farm} → {@code villagepax.building.norman.farm}.
     * <p>
     * Заглушка до {@code BuildingType} из датапака: у профессии название
     * лежит в её файле, а у здания такого файла ещё нет.
     */
    public static String buildingKey(Identifier type) {
        return "villagepax.building." + type.getPath().replace('/', '.');
    }


    private static void tell(ServerPlayerEntity player, String key, Text... args) {
        player.sendMessage(Text.translatable(key, (Object[]) args), false);
    }
}
