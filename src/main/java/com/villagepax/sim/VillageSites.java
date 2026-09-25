package com.villagepax.sim;

import com.villagepax.core.config.Configs;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.build.Footing;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * Насколько место деревни можно подвинуть, чтобы найти ровную землю.
     * <p>
     * Восемь блоков: дерево или валун на самой середине клетки не должны
     * отменять деревню, к которой поиск уже привёл игрока. Дальше сдвигать
     * незачем — иначе ратуша уедет от того места, которое было названо.
     */
    private static final int NUDGE = 8;

    /** Наименьший шаг сетки: клетка мельче этой сделала бы деревни соседями. */
    private static final int MIN_SPACING_CHUNKS = 12;

    /**
     * Сколько мест пробуют в одной клетке сетки.
     * <p>
     * Одного было мало, и это была <b>главная причина</b>, по которой мод
     * начинался с часовой прогулки. Клетка — это пятьсот блоков в стороне,
     * биомы в ней разные, а годилась она или нет решала <b>одна точка</b>:
     * попала в реку или в холм не того биома — и деревни в клетке нет
     * вовсе. На настоящем мире до ближайшей деревни выходило больше
     * километра, и первый игрок так её и не нашёл.
     * <p>
     * Восемь точек — это восемь выборок шума вместо одной там, где клетка
     * пустая, и почти всегда одна там, где деревня есть: перебор
     * прекращается на первом же годном месте. Поиск при этом стал
     * <b>быстрее</b>, а не медленнее: он находит деревню в ближнем кольце
     * и не обходит все шесть.
     */
    private static final int TRIES = 8;

    /** Смешивание координат клетки — те же множители, что берёт ваниль. */
    private static final long MIX_X = 341873128712L;
    private static final long MIX_Z = 132897987541L;

    private VillageSites() {
    }

    /** Место деревни вместе с народом, который её поставит. */
    public record Site(Identifier culture, BlockPos where) {
    }

    /**
     * Найденное место деревни: то же, что {@link Site}, но проверенное
     * <b>без генерации чанков</b>.
     */
    public record Guess(Identifier culture, BlockPos where) {
    }

    /**
     * Насколько далеко ищется деревня, в клетках сетки в каждую сторону.
     * <p>
     * Шесть клеток — это около четырёх с половиной тысяч блоков. Дальше
     * искать незачем: столько игрок пешком не пойдёт, а обход стоит
     * сотни выборок шума.
     */
    private static final int SEARCH_CELLS = 6;

    /**
     * Куда идти за ближайшей деревней.
     * <p>
     * <b>Проверяется по-настоящему.</b> Прежняя версия называла середину
     * клетки, ничего не проверяя, — и уводила игрока за семь сотен блоков
     * в пустоту: биом не тот, деревня там не встанет никогда. Игрок так
     * и сказал: «ведёт в неизвестные места где пусто».
     * <p>
     * Биом и высота спрашиваются у <b>генератора</b>, а не у мира: тем же
     * способом ваниль расставляет свои структуры, и чанк для этого
     * генерировать не надо. Ответ поэтому и дальний, и правдивый.
     */
    public static Guess guessNearest(ServerWorld world, BlockPos from) {
        Guess best = null;
        double bestAway = Double.MAX_VALUE;

        for (Guess guess : guessEach(world, from)) {
            double away = guess.where().getSquaredDistance(from.getX(), guess.where().getY(),
                    from.getZ());
            if (away < bestAway) {
                bestAway = away;
                best = guess;
            }
        }
        return best;
    }

    /**
     * Ближайшая деревня <b>каждого</b> народа.
     * <p>
     * Игроку нужен не один ответ, а выбор: норманны дают чертёж ратуши
     * и торгуют камнем, майя — своим. Сказать только про ближайших значит
     * скрыть половину мира, а идти к ним обоим игрок волен сам.
     */
    public static List<Guess> guessEach(ServerWorld world, BlockPos from) {
        List<Guess> found = new ArrayList<>();

        for (Map.Entry<Identifier, Culture> entry : CultureManager.all().entrySet()) {
            int spacing = spacing(entry.getValue());
            int cellX = Math.floorDiv(new ChunkPos(from).x, spacing);
            int cellZ = Math.floorDiv(new ChunkPos(from).z, spacing);

            // Своя находка на каждый народ. Общая обрывала поиск второму
            // народу на первом же кольце, если первый уже что-то нашёл, —
            // и норманны переставали находиться, стоило майя попасться
            // хоть где-то в пределах четырёх с половиной тысяч блоков.
            Guess mine = null;
            double mineAway = Double.MAX_VALUE;

            for (int ring = 0; ring <= SEARCH_CELLS; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }

                        BlockPos where = plannedSite(world, entry.getKey(), entry.getValue(),
                                cellX + dx, cellZ + dz);
                        if (where == null) {
                            continue;
                        }

                        double away = where.getSquaredDistance(from.getX(), where.getY(),
                                from.getZ());
                        if (away < mineAway) {
                            mineAway = away;
                            mine = new Guess(entry.getKey(), where);
                        }
                    }
                }

                // Нашли в этом кольце — дальше по этому народу не ищем:
                // следующее кольцо заведомо дальше.
                if (mine != null) {
                    break;
                }
            }

            if (mine != null) {
                found.add(mine);
            }
        }

        // По удалённости: первым называют того, до кого ближе идти.
        found.sort(Comparator.comparingDouble(guess -> guess.where()
                .getSquaredDistance(from.getX(), guess.where().getY(), from.getZ())));
        return found;
    }

    /**
     * Место деревни в клетке, проверенное по генератору, или {@code null},
     * если этой клетке деревня не полагается.
     * <p>
     * Ни один чанк при этом не генерируется. Высота берётся расчётом
     * поверхности, биом — выборкой шума: ровно так ваниль решает, где
     * поставить деревню или крепость, ещё до того как игрок туда придёт.
     */
    public static BlockPos plannedSite(ServerWorld world, Identifier cultureId, Culture culture,
                                       int cellX, int cellZ) {
        for (BlockPos column : tries(world, cultureId, culture, cellX, cellZ)) {
            BlockPos fits = fitsByGenerator(world, culture, column);
            if (fits != null) {
                return fits;
            }
        }
        return null;
    }

    /**
     * Годится ли эта колонна: высота и биом — по генератору, без загрузки чанка.
     * <p>
     * Ровно так ваниль решает, где поставить деревню или крепость, ещё до
     * того как игрок туда придёт.
     */
    private static BlockPos fitsByGenerator(ServerWorld world, Culture culture, BlockPos column) {
        ServerChunkManager chunks = world.getChunkManager();
        ChunkGenerator generator = chunks.getChunkGenerator();
        NoiseConfig noise = chunks.getNoiseConfig();

        int surface = generator.getHeight(column.getX(), column.getZ(),
                Heightmap.Type.WORLD_SURFACE_WG, world, noise);
        if (surface <= world.getBottomY() || surface >= world.getTopY()) {
            return null;
        }

        BlockPos where = new BlockPos(column.getX(), surface, column.getZ());
        if (!matchesBiome(generator.getBiomeSource().getBiome(
                BiomeCoords.fromBlock(where.getX()),
                BiomeCoords.fromBlock(where.getY()),
                BiomeCoords.fromBlock(where.getZ()),
                noise.getMultiNoiseSampler()), culture)) {
            return null;
        }

        // Под водой деревню не ставят: жителям надо где стоять.
        if (surface <= world.getSeaLevel()) {
            return null;
        }
        return vanillaStructureNear(world, where, Configs.get().structureDistanceChunks())
                .isPresent() ? null : where;
    }

    /**
     * Ванильная постройка у земли будущей деревни — если генератор её туда
     * поставит.
     * <p>
     * Деревня народа, вставшая в ванильную деревню, аванпост или храм, —
     * не соседство, а наложение: ратуша врастает в чужой дом, улица идёт
     * сквозь колокольню, и на глаз это поломка обоих. Дизайн-документ
     * обещал обходить их с первого дня; обходить начали только теперь.
     * <p>
     * Спрашивается <b>генератор</b>, а не мир — тем же способом, каким
     * ваниль сама решает, где встанет её деревня: сетка расстановки,
     * частота и биом. Чанки не загружаются, и потому проверка одна и та же
     * для указателя «куда идти» и для самого основания: иначе игрока
     * привели бы туда, где деревня потом откажется встать.
     * <p>
     * В счёт идут только постройки <b>у поверхности</b>. Шахты, крепость,
     * древний город лежат глубоко и деревне не мешают, а отказывать из-за
     * них значило бы отказывать почти везде. Кольца крепостей по той же
     * причине не смотрятся вовсе.
     *
     * @param distance на сколько чанков держаться; 0 — не держаться
     * @return какая постройка мешает; пусто, если никакая
     */
    public static Optional<Identifier> vanillaStructureNear(ServerWorld world, BlockPos where,
                                                           int distance) {
        if (distance <= 0 || !world.getStructureAccessor().shouldGenerateStructures()) {
            return Optional.empty();
        }
        ServerChunkManager chunks = world.getChunkManager();
        StructurePlacementCalculator calculator = chunks.getStructurePlacementCalculator();
        ChunkGenerator generator = chunks.getChunkGenerator();
        NoiseConfig noise = chunks.getNoiseConfig();
        ChunkPos centre = new ChunkPos(where);

        for (RegistryEntry<StructureSet> set : calculator.getStructureSets()) {
            if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            List<Structure> surface = new ArrayList<>();
            for (StructureSet.WeightedEntry entry : set.value().structures()) {
                Structure structure = entry.structure().value();
                if (structure.getFeatureGenerationStep()
                        == GenerationStep.Feature.SURFACE_STRUCTURES) {
                    surface.add(structure);
                }
            }
            if (surface.isEmpty()) {
                continue;
            }
            int reach = distance;
            for (Structure structure : surface) {
                reach = Math.max(reach, distance + extent(structure));
            }

            // В каждой области сетки у набора одно начало, и областей
            // вокруг места — от одной до четырёх: перебирать чанки
            // по одному незачем.
            int spacing = spread.getSpacing();
            for (int regionX = Math.floorDiv(centre.x - reach, spacing);
                 regionX <= Math.floorDiv(centre.x + reach, spacing); regionX++) {
                for (int regionZ = Math.floorDiv(centre.z - reach, spacing);
                     regionZ <= Math.floorDiv(centre.z + reach, spacing); regionZ++) {
                    ChunkPos start = spread.getStartChunk(calculator.getStructureSeed(),
                            regionX * spacing, regionZ * spacing);
                    int away = Math.max(Math.abs(start.x - centre.x),
                            Math.abs(start.z - centre.z));
                    if (away > reach || !spread.shouldGenerate(calculator, start.x, start.z)) {
                        continue;
                    }
                    int x = start.getCenterX();
                    int z = start.getCenterZ();
                    int y = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
                    RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome(
                            BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y),
                            BiomeCoords.fromBlock(z), noise.getMultiNoiseSampler());
                    for (Structure structure : surface) {
                        if (away <= distance + extent(structure)
                                && structure.getValidBiomes().contains(biome)) {
                            return Optional.ofNullable(world.getRegistryManager()
                                    .get(RegistryKeys.STRUCTURE).getId(structure));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * На сколько чанков постройка раскидывается от своего начала.
     * <p>
     * Отступ меряется от <b>домов</b>, а не от точки, из которой ваниль
     * начала их раскладывать: деревня собирается кусками до восьмидесяти
     * блоков от начала, и замер «от начала» подпускал ратушу народа
     * вплотную к её крайнему дому. Храм, хижина, портал — в пределах
     * своего чанка.
     */
    private static int extent(Structure structure) {
        StructureType<?> type = structure.getType();
        if (type == StructureType.JIGSAW) {
            return 5;
        }
        return type == StructureType.WOODLAND_MANSION ? 4 : 1;
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
        // Где именно в клетке — решает генератор, и решает один раз на всех:
        // и для «куда идти», и для «где встанет». Мир после этого только
        // подтверждает, что тут есть на что встать <b>сейчас</b>. Спрашивать
        // биом дважды, у генератора и у мира, значило бы завести два ответа
        // на один вопрос.
        BlockPos column = plannedSite(world, cultureId, culture, cellX, cellZ);
        if (column == null) {
            return Optional.empty();
        }

        if (!world.isChunkLoaded(column)) {
            // Высоту поверхности в незагруженном чанке спрашивать нельзя:
            // это заставило бы мир его сгенерировать здесь и сейчас.
            return Optional.empty();
        }

        // Место ищется <b>рядом</b>, а не только в самой колонне. Дерево,
        // валун или высокая трава ровно на середине клетки — не повод
        // отменить деревню: поиск уже привёл сюда игрока, и «пришёл,
        // а тут ничего» было бы обманом.
        //
        // Землю ищет {@link Ground}, а не карта высот мира. Карта высот
        // считает верхушку листвы поверхностью, и под пологом джунглей
        // опоры не находилось ни в одной из двухсот восьмидесяти девяти
        // колонн: деревня майя не могла встать нигде.
        for (int radius = 0; radius <= NUDGE; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    BlockPos nearby = column.add(dx, 0, dz);
                    Optional<BlockPos> stand = standFor(world, culture, nearby);
                    if (stand.isPresent()) {
                        return stand;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Где в этой колонне встанет середина поселения.
     * <p>
     * У народа с земли это земля, у народа из горы — отметка пола чертога,
     * у народа из крон — настил над лесом. Спрашивается через одну дверь
     * ({@link Footing}), и это ровно та разница, ради которой заводились
     * черты: поиск места не должен знать ни слова «гномы», ни слова
     * «эльфы».
     */
    private static Optional<BlockPos> standFor(ServerWorld world, Culture culture,
                                               BlockPos column) {
        return Footing.centreUnder(world, culture, column.getX(), column.getZ());
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
    private static List<BlockPos> tries(ServerWorld world, Identifier cultureId, Culture culture,
                                        int cellX, int cellZ) {
        int spacing = spacing(culture);
        int inset = Math.max(1, spacing / 4);
        int room = Math.max(1, spacing - 2 * inset);

        Random random = new Random(world.getSeed()
                ^ (cellX * MIX_X + cellZ * MIX_Z)
                ^ cultureId.toString().hashCode());

        List<BlockPos> spots = new ArrayList<>(TRIES);
        for (int attempt = 0; attempt < TRIES; attempt++) {
            int chunkX = cellX * spacing + inset + random.nextInt(room);
            int chunkZ = cellZ * spacing + inset + random.nextInt(room);
            spots.add(new ChunkPos(chunkX, chunkZ).getStartPos().add(8, 0, 8));
        }
        return spots;
    }

    private static boolean matchesBiome(ServerWorld world, BlockPos where, Culture culture) {
        return matchesBiome(world.getBiome(where), culture);
    }

    /**
     * Тот ли биом. Строка культуры — либо тег с решёткой, либо сам биом:
     * тем же языком, каким биомы задаются в ванильных описаниях структур.
     * <p>
     * Принимает запись реестра, а не позицию, потому что спрашивают
     * двое: загруженный мир и генератор. Правило одно, и разойтись ему
     * негде — иначе поиск указывал бы туда, где деревня не встанет.
     */
    private static boolean matchesBiome(RegistryEntry<Biome> biome, Culture culture) {
        String wanted = culture.spawn().biomes();
        if (wanted == null || wanted.isEmpty()) {
            return true;
        }

        if (wanted.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(wanted.substring(1));
            return tagId != null && biome.isIn(TagKey.of(RegistryKeys.BIOME, tagId));
        }

        Identifier biomeId = Identifier.tryParse(wanted);
        return biomeId != null && biome.matchesId(biomeId);
    }

    private static boolean isNear(BlockPos around, BlockPos site) {
        double dx = around.getX() - site.getX();
        double dz = around.getZ() - site.getZ();
        return dx * dx + dz * dz <= (double) ACTIVATE_RANGE * ACTIVATE_RANGE;
    }
}
