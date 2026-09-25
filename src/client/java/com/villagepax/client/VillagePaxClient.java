package com.villagepax.client;

import com.villagepax.VillagePax;
import com.villagepax.block.ModBlocks;
import com.villagepax.entity.ModEntities;
import com.villagepax.item.ModItems;
import com.villagepax.item.PurseItem;
import com.villagepax.item.TownHallBlueprintItem;
import com.villagepax.client.hologram.HologramHud;
import com.villagepax.client.hologram.HologramKeys;
import com.villagepax.client.hologram.HologramRenderer;
import com.villagepax.client.hologram.Placement;
import com.villagepax.client.screen.QuestScreen;
import com.villagepax.client.screen.TownHallScreen;
import com.villagepax.screen.ColonyMap;
import com.villagepax.screen.QuestNet;
import com.villagepax.screen.QuestView;
import com.villagepax.screen.ColonyNet;
import com.villagepax.screen.GhostPlan;
import com.villagepax.screen.TownHallNet;
import com.villagepax.screen.TownHallScreens;
import com.villagepax.screen.TownHallView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import com.villagepax.block.entity.ModBlockEntities;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public class VillagePaxClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Слоя модели больше нет: кости жителя приходят из geo-файла,
        // а не собираются кодом. Регистрировать надо только переменные
        // шага — до того, как GeckoLib прочтёт первый файл движений.
        CitizenGeoModel.registerVariables();
        EntityRendererRegistry.register(ModEntities.CITIZEN, CitizenEntityRenderer::new);
        // Облик и тело народа спрашиваются у хранилища ресурсов один раз
        // и помнятся: ходить в файловую систему каждый кадр за каждым
        // жителем нельзя. Значит, забывать надо вручную — ровно тогда,
        // когда хранилище перечитали.
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return new Identifier(VillagePax.MOD_ID, "citizen_looks");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        CitizenGeoModel.forget();
                        CitizenEntityRenderer.forget();
                    }
                });
        // Верёвка рисует не себя, а то, что на ней висит: см. RopeBlockEntityRenderer.
        BlockEntityRendererFactories.register(ModBlockEntities.ROPE,
                RopeBlockEntityRenderer::new);
        registerCutouts();
        registerItemLooks();
        HandledScreens.register(TownHallScreens.TOWN_HALL, TownHallScreen::new);
        registerViewUpdates();
        registerColonyMap();
        registerQuestScreen();
        registerHologram();
        registerTooltips();
    }

    /**
     * Блоки с прозрачностью.
     * <p>
     * Слой отрисовки в Fabric задаётся <b>кодом клиента</b>, а не полем
     * {@code render_type} в модели: это поле читает Forge, а здесь оно
     * молча ничего не делает. Без этой строки бельё на верёвке рисуется
     * непрозрачным слоем, и вокруг простыней стоят чёрные квадраты.
     */
    private static void registerCutouts() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LAUNDRY, RenderLayer.getCutout());
        // Цветы и бумага с прорезями: прозрачное в текстуре должно быть
        // прозрачным и в мире, иначе лунник стоит чёрным квадратом.
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),
                ModBlocks.PAPER_LANTERN, ModBlocks.MOONFLOWER, ModBlocks.FLOWER_BOX);
    }

    /**
     * С какой стопки монета рисуется грудой, а не парой монет.
     * <p>
     * Шестнадцать — четверть стопки: столько игрок получает за хороший
     * квест, и в руке это уже казна, а не сдача.
     */
    private static final int HEAP = 16;

    /**
     * Значки, которые говорят о содержимом: стопка монет и кошель.
     * <p>
     * Одна монета на значке честна для одной монеты и лжёт для сорока,
     * а полный кошель на значке пустого заставляет открывать подсказку,
     * чтобы узнать главное. Ваниль делает так же со стрелой в луке
     * и с часами: картинку выбирает предикат модели, а сам предмет
     * об этом не знает.
     */
    private static void registerItemLooks() {
        for (Item coin : List.of(ModItems.COIN, ModItems.SILVER_COIN, ModItems.GOLD_COIN)) {
            FabricModelPredicateProviderRegistry.register(coin,
                    new Identifier(VillagePax.MOD_ID, "heap"),
                    (stack, world, holder, seed) -> stack.getCount() >= HEAP ? 1.0f
                            : stack.getCount() > 1 ? 0.5f : 0.0f);
        }
        FabricModelPredicateProviderRegistry.register(ModItems.PURSE,
                new Identifier(VillagePax.MOD_ID, "empty"),
                (stack, world, holder, seed) -> PurseItem.valueOf(stack) <= 0 ? 1.0f : 0.0f);
    }

    /**
     * Экран квестов открывается по пакету, без контейнера: отдать квест
     * можно только стоя рядом с выдающим, и эту проверку делает сервер
     * по расстоянию, а не по открытому экрану.
     */
    private static void registerQuestScreen() {
        ClientPlayNetworking.registerGlobalReceiver(QuestNet.OPEN,
                (client, handler, buf, sender) -> {
                    Optional<QuestView> view = QuestNet.read(buf);
                    client.execute(() -> view.ifPresent(fresh -> QuestScreen.open(client, fresh)));
                });
    }

    /**
     * Карта колонии: подписи над зданиями и граница владений.
     * <p>
     * Своё событие отрисовки, а не общее с голограммой: одно из двух может
     * понадобиться выключить настройкой, и разделять их тогда будет негде.
     */
    private static void registerColonyMap() {
        ClientPlayNetworking.registerGlobalReceiver(ColonyNet.MAP,
                (client, handler, buf, sender) -> {
                    Optional<ColonyMap> map = ColonyNet.read(buf);
                    client.execute(() -> map.ifPresent(fresh -> ColonyRenderer.accept(fresh,
                            client.world == null ? 0L : client.world.getTime())));
                });

        WorldRenderEvents.AFTER_ENTITIES.register(ColonyRenderer::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ColonyRenderer.forget());
    }

    /**
     * Голограмма: план и приговор с сервера, клавиши, отрисовка, подсказка.
     * <p>
     * Режим установки сбрасывается при выходе из мира: призрак, оставшийся
     * от прошлой колонии, показывал бы место, которого больше нет.
     */
    private static void registerHologram() {
        HologramKeys.register();

        ClientPlayNetworking.registerGlobalReceiver(TownHallNet.PLAN,
                (client, handler, buf, sender) -> {
                    GhostPlan plan = GhostPlan.read(buf);
                    client.execute(() -> Placement.acceptPlan(plan));
                });

        ClientPlayNetworking.registerGlobalReceiver(TownHallNet.VERDICT,
                (client, handler, buf, sender) -> {
                    boolean allowed = buf.readBoolean();
                    String reason = buf.readString(128);
                    client.execute(() -> Placement.acceptVerdict(allowed, reason));
                });

        ClientTickEvents.END_CLIENT_TICK.register(HologramKeys::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Placement.cancel());
        // AFTER_ENTITIES, а не AFTER_TRANSLUCENT: приёмники вершин мира
        // существуют только до BEFORE_DEBUG_RENDER, и на AFTER_TRANSLUCENT
        // их уже нет — призрак не рисовался вообще.
        WorldRenderEvents.AFTER_ENTITIES.register(HologramRenderer::render);
        HudRenderCallback.EVENT.register(HologramHud::render);
    }

    /**
     * Новый снимок колонии: экран обновляется, если он открыт.
     * <p>
     * Читать буфер надо в сетевом потоке, а показывать — в клиентском:
     * между ними {@code client.execute}, и буфера к тому времени уже нет.
     */
    private static void registerViewUpdates() {
        ClientPlayNetworking.registerGlobalReceiver(TownHallNet.VIEW,
                (client, handler, buf, sender) -> {
                    TownHallView fresh = TownHallNet.readView(buf);
                    client.execute(() -> TownHallScreen.open(client)
                            .ifPresent(screen -> screen.refresh(fresh)));
                });
    }

    /**
     * Подсказки живут в клиентском наборе исходников, а не в общем коде.
     * Ванильный {@code Item.appendTooltip} тянет за собой клиентский тип
     * в общий код, а это прямой путь к падению выделенного сервера.
     */
    private static void registerTooltips() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (stack.isOf(ModItems.TOWN_HALL_BLUEPRINT)) {
                Identifier culture = TownHallBlueprintItem.cultureOf(stack);
                lines.add(Text.translatable("villagepax.blueprint.culture",
                                Text.translatable("villagepax.culture." + culture.getPath()))
                        .formatted(Formatting.GOLD));
                lines.add(Text.translatable("villagepax.blueprint.hint").formatted(Formatting.GRAY));
                return;
            }

            if (stack.getItem() instanceof BlockItem blockItem && isMarker(blockItem.getBlock())) {
                // Три строки, а не одна: маркеры лежат в творческой вкладке
                // рядом с обычными блоками, и игрок вправе знать, зачем они
                // ему вообще нужны. Одной строкой «служебный блок схемы»
                // это не объясняется.
                lines.add(Text.translatable("villagepax.tooltip.marker")
                        .formatted(Formatting.GOLD));
                lines.add(Text.translatable("villagepax.tooltip.marker.why")
                        .formatted(Formatting.GRAY));
                lines.add(Text.translatable("villagepax.tooltip.marker.hint")
                        .formatted(Formatting.DARK_GRAY));
            }
        });
    }

    private static boolean isMarker(Block block) {
        return block == ModBlocks.MARKER_WORKSTATION
                || block == ModBlocks.MARKER_BED
                || block == ModBlocks.MARKER_STORAGE
                || block == ModBlocks.MARKER_DOOR
                || block == ModBlocks.MARKER_DECOR;
    }
}
