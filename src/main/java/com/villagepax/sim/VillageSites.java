package com.villagepax.sim;

import com.villagepax.core.config.Configs;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Где в мире стоят деревни народов.
 * <p>
 * Места <b>считаются по семени мира</b>, а не размечаются при генерации
 * чанков. Разница важная. Своя структура в генераторе означала бы регистрацию
 * типа структуры, набор json-описаний и — главное — что в уже созданном мире
 * деревень не появится никогда. Здесь же место есть у любого мира, включая
 * тот, в который игрок только что добавил мод: сетка клеток от семени даёт
 * один и тот же ответ каждому, кто спросит.
 * <p>
 * Деревня всё равно не «генерируется»: её <b>строят жители</b>. Поэтому
 * резервировать под неё рельеф заранее не нужно — нужно только сказать, где
 * она будет, и это ровно то, что делает этот класс.
 */
public final class VillageSites {

    /**
     * Насколько близко должен подойти игрок, чтобы деревня появилась.
     * <p>
     * Меньше дальности прогрузки мира намеренно: жителям нужно ставить блоки,
     * а в незагруженный чанк ставить нельзя. Игрок при этом деревню уже
     * видит — она встаёт не у него под носом.
     */
    public static final int ACTIVATE_RANGE = 96;

    /** Наименьший шаг сетки: клетка мельче этой сделала бы деревни соседями. */
    private static final int MIN_SPACING_CHUNKS = 12;

    /** Смешивание координат клетки — те же множители, что берёт ваниль. */
    private static final long MIX_X = 341873128712L;
    private static final long MIX_Z = 132897987541L;

    private VillageSites() {
    }

    /** Место деревни вместе с народом, который её поставит. */
    public record Site(Identifier culture, BlockPos where) {
    }

    /**
     * Догадка о месте деревни: только координаты клетки, без проверок.
     * <p>
     * Отличается от {@link Site} тем, что <b>не</b> смотрит в мир: ни биом,
     * ни грунт, ни загруженность чанка. Это ответ на вопрос «куда идти»,
     * а не «можно ли ставить здесь сейчас». Проверить биом и грунт всё
     * равно нельзя, не сгенерировав чанк, — а генерировать полкарты,
     * чтобы ответить на вопрос игрока, недопустимо.
     */
    public record Guess(Identifier culture, BlockPos where) {
    }

    /**
     * Куда идти за ближайшей деревней.
     * <p>
     * Обходятся клетки сетки вокруг игрока — по кольцу шириной в две клетки
     * в каждую сторону, этого хватает: клетка сама шириной в семь с половиной
     * сотен блоков. Высота берётся с потолка мира, потому что настоящую можно
     * узнать только сгенерировав чанк; игроку нужны X и Z.
     */
    public static Guess guessNearest(ServerWorld world, BlockPos from) {
        Guess best = null;
        double bestAway = Double.MAX_VALUE;

        for (Map.Entry<Identifier, Culture> entry : CultureManager.all().entrySet()) {
            int spacing = spacing(entry.getValue());
            int cellX = Math.floorDiv(new ChunkPos(from).x, spacing);
            int cellZ = Math.floorDiv(new ChunkPos(from).z, spacing);

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos where = cellCentre(world, entry.getKey(), entry.getValue(),
                            cellX + dx, cellZ + dz);
                    double away = where.getSquaredDistance(from.getX(), where.getY(), from.getZ());
                    if (away < bestAway) {
                        bestAway = away;
                        best = new Guess(entry.getKey(), where);
                    }
                }
            }
        }
        return best;
    }

    /**
     * Места деревень рядом с точкой — по одному на клетку сетки.
     * <p>
     * Проверяется и биом, и грунт, и загруженность чанка: ответ должен быть
     * пригоден к немедленной постройке, а не «примерно тут где-то».
     */
    public static List<Site> near(ServerWorld world, BlockPos around) {
        List<Site> found = new ArrayList<>();

        for (Map.Entry<Identifier, Culture> entry : CultureManager.all().entrySet()) {
            int spacing = spacing(entry.getValue());
            int cellX = Math.floorDiv(new ChunkPos(around).x, spacing);
            int cellZ = Math.floorDiv(new ChunkPos(around).z, spacing);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    candidate(world, entry.getKey(), entry.getValue(), cellX + dx, cellZ + dz)
                            .filter(site -> isNear(around, site))
                            .ifPresent(site -> found.add(new Site(entry.getKey(), site)));
                }
            }
        }
        return found;
    }

    /**
     * Шаг сетки в чанках.
     * <p>
     * Настройка игрока перебивает культуру: густоту деревень он хочет
     * решать сам, а датапак говорит, какой она задумана. Ноль в настройке
     * значит «как задумано».
     */
    public static int spacing(Culture culture) {
        int wanted = Configs.get().villageSpacingChunks();
        return Math.max(MIN_SPACING_CHUNKS,
                wanted > 0 ? wanted : culture.spawn().minDistanceChunks());
    }

    /**
     * Кандидат в клетке сетки.
     * <p>
     * Позиция выводится из семени мира и номера клетки, поэтому она
     * <b>одна и та же</b> при каждом вызове и у каждого игрока на сервере.
     * Хранить её негде и не надо.
     */
    public static Optional<BlockPos> candidate(ServerWorld world, Identifier cultureId,
                                               Culture culture, int cellX, int cellZ) {
        BlockPos column = cellCentre(world, cultureId, culture, cellX, cellZ);

        if (!world.isChunkLoaded(column)) {
            // Высоту поверхности в незагруженном чанке спрашивать нельзя:
            // это заставило бы мир его сгенерировать здесь и сейчас.
            return Optional.empty();
        }

        BlockPos surface = new BlockPos(column.getX(),
                world.getTopY(Heightmap.Type.WORLD_SURFACE, column.getX(), column.getZ()),
                column.getZ());

        if (!matchesBiome(world, surface, culture)) {
            return Optional.empty();
        }
        return ColonyFounder.isBuildable(world, surface) ? Optional.of(surface) : Optional.empty();
    }

    /**
     * Колонна в середине клетки сетки — от семени мира и номера клетки.
     * <p>
     * Один расчёт на две задачи: и «где кандидат», и «куда идти». Разойдись
     * они — команда указывала бы игроку не туда, где деревня в итоге встанет.
     * <p>
     * Кандидат держится в середине клетки: у краёв две соседние деревни
     * могли бы оказаться вплотную, и обе отказались бы возникать.
     */
    private static BlockPos cellCentre(ServerWorld world, Identifier cultureId, Culture culture,
                                       int cellX, int cellZ) {
        int spacing = spacing(culture);
        int inset = Math.max(1, spacing / 4);
        int room = Math.max(1, spacing - 2 * inset);

        Random random = new Random(world.getSeed()
                ^ (cellX * MIX_X + cellZ * MIX_Z)
                ^ cultureId.toString().hashCode());

        int chunkX = cellX * spacing + inset + random.nextInt(room);
        int chunkZ = cellZ * spacing + inset + random.nextInt(room);
        return new ChunkPos(chunkX, chunkZ).getStartPos().add(8, 0, 8);
    }

    /**
     * Тот ли биом. Строка культуры — либо тег с решёткой, либо сам биом:
     * тем же языком, каким биомы задаются в ванильных описаниях структур.
     */
    private static boolean matchesBiome(ServerWorld world, BlockPos where, Culture culture) {
        String wanted = culture.spawn().biomes();
        if (wanted == null || wanted.isEmpty()) {
            return true;
        }

        if (wanted.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(wanted.substring(1));
            if (tagId == null) {
                return false;
            }
            return world.getBiome(where).isIn(TagKey.of(RegistryKeys.BIOME, tagId));
        }

        return world.getBiome(where).matchesId(Identifier.tryParse(wanted));
    }

    private static boolean isNear(BlockPos around, BlockPos site) {
        double dx = around.getX() - site.getX();
        double dz = around.getZ() - site.getZ();
        return dx * dx + dz * dz <= (double) ACTIVATE_RANGE * ACTIVATE_RANGE;
    }
}
