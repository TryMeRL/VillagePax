package com.villagepax.sim;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Вечерняя песня: у каждого народа свой напев на площади и свой зов к вечеру.
 * <p>
 * «Чтоб прям хотелось жить в них». Вечерний сбор уже был — жители сходятся
 * к ратуше, — но сходились молча, и площадь в сумерках ничем не отличалась
 * от площади в полдень. Теперь, когда кончается работа, деревня зовёт: у
 * северян трубит рог, у норманнов бьёт колокол, — а пока народ стоит на
 * площади, кто-нибудь играет. Напев у каждого свой, и по нему деревню
 * узнаёшь раньше, чем увидишь: арфа норманнов, флейта и барабан майя,
 * колокольчики пони, бас гномов, арфа эльфов, гул и бой северян.
 * <p>
 * Играет только там, где рядом игрок и где собрались жители: песня для
 * слушателя, а не для пустой площади. Звук — в разделе «нотные блоки»,
 * так что громкость у игрока в ванильных настройках, и отдельный
 * выключатель не нужен.
 */
public final class VillageMusic {

    /** Тиков на шаг напева: пять нот в секунду. */
    private static final int STEP = 4;

    /** С какого расстояния игрок слышит — и ради кого вообще играть. */
    private static final double LISTENER = 24.0;

    /** Сколько жителей на площади — уже сбор, а не прохожий. */
    private static final int CROWD = 2;

    /** Как часто пересчитываются собравшиеся: раз в две секунды. */
    private static final int COUNT_EVERY = 40;

    private static final Map<UUID, Boolean> GATHERED = new HashMap<>();

    private VillageMusic() {
    }

    /** Голос напева: инструмент и ноты по шагам, «.» — пауза. */
    record Voice(RegistryEntry<SoundEvent> instrument, float volume, int[] notes) {
    }

    /** Напев народа и его вечерний зов. */
    record Tune(Voice melody, Voice beat, Call call) {
    }

    /** Зов к вечеру: звук, сколько раз и с каким шагом. */
    record Call(RegistryEntry<SoundEvent> sound, float pitch, int times, int gap) {
    }

    private static final int REST = -1;

    private static int[] notes(String score) {
        String[] tokens = score.trim().split("\\s+");
        int[] out = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = tokens[i].equals(".") ? REST : Integer.parseInt(tokens[i]);
        }
        return out;
    }

    private static final Map<String, Tune> TUNES = Map.of(
            "norman", new Tune(
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_HARP, 0.7f, notes(
                            "12 . 14 . 15 . 17 15 14 . 12 . 10 . 12 . 14 . 15 . 17 . 19 17 "
                                    + "15 . 14 . 12 . . .")),
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5f, notes(
                            "12 . . . 7 . . . 12 . . . 10 . . . 12 . . . 7 . . . 5 . . . "
                                    + "12 . . .")),
                    new Call(RegistryEntry.of(SoundEvents.BLOCK_BELL_USE), 1.0f, 3, 12)),
            "maya", new Tune(
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE, 0.6f, notes(
                            "10 . 12 . 15 . 17 . 15 . 12 . 10 . . . 7 . 10 . 12 15 12 10 "
                                    + "7 . . . . .")),
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM, 0.6f, notes(
                            "8 . . 8 . . 8 . 8 . . 8 . . 8 . 8 . . 8 . . 8 . 8 . . 8 . 8 8 .")),
                    new Call(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, 6, 3)),
            "pony", new Tune(
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.6f, notes(
                            "12 16 19 24 19 16 12 . 14 17 21 . 19 . . . 12 16 19 24 21 19 "
                                    + "16 . 14 . 12 . . . . .")),
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.35f, notes(
                            "0 . . . 5 . . . 7 . . . 0 . . . 0 . . . 5 . . . 7 . . . 0 . . .")),
                    new Call(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.26f, 4, 3)),
            "dwarf", new Tune(
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.8f, notes(
                            "0 . 0 . 3 . 5 . 7 . 5 . 3 . 0 . 0 . 3 . 5 . 3 . 0 . . . . .")),
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM, 0.6f, notes(
                            "6 . 6 . 6 . 6 . 6 . 6 . 6 . 6 6 6 . 6 . 6 . 6 . 6 . 6 . 6 6")),
                    new Call(SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.8f, 3, 6)),
            "elf", new Tune(
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_HARP, 0.6f, notes(
                            "12 . . 16 . . 19 . . 18 . . 16 . . . 14 . . 19 . . 23 . . 21 . "
                                    + ". 19 . . .")),
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.3f, notes(
                            "24 . . . . . . . 19 . . . . . . . 21 . . . . . . . 16 . . . . . . .")),
                    new Call(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.5f, 5, 2)),
            "nord", new Tune(
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, notes(
                            "12 . . . . . . . 15 . . . . . . . 17 . . . 15 . . . 12 . . . "
                                    + ". . . .")),
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, notes(
                            "4 . . . 4 . . . 4 . . . 4 . 4 . 4 . . . 4 . . . 4 . . . 4 . 4 .")),
                    new Call(SoundEvents.GOAT_HORN_SOUNDS.get(0), 1.0f, 1, 1)),
            // Ямато: кото на лад «ин» и долгая флейта-сякухати, а к вечеру —
            // низкий удар храмового колокола.
            "yamato", new Tune(
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_HARP, 0.6f, notes(
                            "12 . 13 . 17 . 19 . 20 . 19 17 13 . 12 . . . 8 . 12 . 13 17 "
                                    + "13 12 8 . . . . .")),
                    new Voice(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE, 0.45f, notes(
                            "12 . . . . . . . 17 . . . . . . . 13 . . . . . . . 8 . . . . . . .")),
                    new Call(RegistryEntry.of(SoundEvents.BLOCK_BELL_USE), 0.5f, 2, 20)));

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(VillageMusic::tick);
    }

    private static void tick(ServerWorld world) {
        long time = world.getTime();
        if (time % STEP != 0) {
            return;
        }
        long day = Math.floorMod(world.getTimeOfDay(), 24_000L);
        if (Schedule.at(day) != Schedule.LEISURE) {
            GATHERED.clear();
            return;
        }
        long today = Schedule.dayOf(world.getTimeOfDay());
        for (Settlement settlement : SettlementManager.get(world).all()) {
            Tune tune = TUNES.get(settlement.culture().getPath());
            // В праздник песня играет у сердца ярмарки: народ собрался там.
            BlockPos centre = com.villagepax.sim.festival.Revels.stage(settlement, today)
                    .orElse(settlement.center());
            if (tune == null || !world.isChunkLoaded(centre)
                    || !world.isPlayerInRange(centre.getX(), centre.getY(), centre.getZ(), LISTENER)) {
                continue;
            }
            int step = (int) ((day - Schedule.LEISURE_START) / STEP);
            call(world, centre, tune.call(), step);
            if (time % COUNT_EVERY == 0 || !GATHERED.containsKey(settlement.id())) {
                GATHERED.put(settlement.id(), gathered(world, centre));
            }
            if (GATHERED.getOrDefault(settlement.id(), false) && step > callLength(tune.call())) {
                play(world, centre, tune.melody(), step);
                play(world, centre, tune.beat(), step);
            }
        }
    }

    /** Зов к вечеру: первые шаги досуга, до того как заиграет напев. */
    private static void call(ServerWorld world, BlockPos centre, Call call, int step) {
        if (step % call.gap() == 0 && step / call.gap() < call.times()) {
            world.playSound(null, centre.up(2), call.sound().value(), SoundCategory.RECORDS,
                    2.0f, call.pitch());
        }
    }

    private static int callLength(Call call) {
        return call.times() * call.gap() + 4;
    }

    private static void play(ServerWorld world, BlockPos centre, Voice voice, int step) {
        int note = voice.notes()[Math.floorMod(step, voice.notes().length)];
        if (note == REST) {
            return;
        }
        world.playSound(null, centre.up(), voice.instrument().value(), SoundCategory.RECORDS,
                voice.volume(), pitch(note));
    }

    /** Высота ноты нотного блока: 0 — фа-диез, 12 — октавой выше, как в игре. */
    static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    private static boolean gathered(ServerWorld world, BlockPos centre) {
        Box square = new Box(centre).expand(10, 4, 10);
        return world.getEntitiesByClass(CitizenEntity.class, square, citizen -> true).size() >= CROWD;
    }

    /** Есть ли у народа свой напев — для проверки полноты. */
    public static boolean hasTune(String people) {
        return TUNES.containsKey(people);
    }
}
