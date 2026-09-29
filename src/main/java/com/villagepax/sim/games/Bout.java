package com.villagepax.sim.games;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.trade.Coins;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Одна живая партия: игрок и житель за столом.
 * <p>
 * Правила — в {@link Ochko} и {@link ArmWrestle}, без мира; здесь — всё,
 * что делает партию живой: соперник бросает при всех с паузой, говорит,
 * досадует, и по итогу монета переходит из рук в руки. Ставка списывается
 * только по итогу: снятая партия никому ничего не стоит.
 */
public final class Bout {

    /** Перевес игрока, на котором соперник кряхтит вслух: раз за партию. */
    static final double STRAIN_AT = 60;

    /** Свой бросок соперник комментирует через раз из стольких: говорить на каждый — тараторить. */
    static final int COMMENT_ODDS = 3;

    /** С какой грани бросок — «большой»: «Шесть!», «Ого!». */
    static final int HIGH_FACE = 5;

    /** До какой — «малый»: «Эх, единица…». */
    static final int LOW_FACE = 2;

    /**
     * Перевес армрестлинга уходит в окно раз в столько тиков: полосу рисует
     * кадр, а снимок каждый тик — это двадцать пакетов в секунду ради
     * сдвига на полпикселя.
     */
    static final int SHOW_EVERY = 2;

    private final RegistryKey<World> world;
    private final UUID player;
    /** Кто начал: подставной игрок проверок в списке игроков сервера не числится. */
    private final PlayerEntity starter;
    private final UUID village;
    private final UUID rival;
    private final Bouts.Kind kind;
    private final int stake;
    private final boolean coins;
    private final boolean withOwner;
    private final long day;
    private final Ochko dice;
    private final ArmWrestle arm;
    private final long markerStart;
    private long nextRoll = Long.MAX_VALUE;
    private boolean strained;
    private Integer settled;

    Bout(ServerWorld world, PlayerEntity player, Settlement settlement, Citizen rival,
         Bouts.Kind kind, int stake, boolean coins, boolean withOwner, long day) {
        this.world = world.getRegistryKey();
        this.player = player.getUuid();
        this.starter = player;
        this.village = settlement.id();
        this.rival = rival.id();
        this.kind = kind;
        this.stake = stake;
        this.coins = coins;
        this.withOwner = withOwner;
        this.day = day;
        this.dice = kind == Bouts.Kind.DICE
                ? new Ochko(com.villagepax.sim.life.Natures.of(rival), Bouts.diceFor(world)) : null;
        this.arm = kind == Bouts.Kind.ARM
                ? new ArmWrestle(Strength.of(rival.profession(),
                com.villagepax.sim.life.Ages.isElder(rival)),
                com.villagepax.sim.life.Natures.of(rival)) : null;
        this.markerStart = world.getTime();
    }

    public UUID player() {
        return player;
    }

    public UUID rival() {
        return rival;
    }

    public UUID village() {
        return village;
    }

    public Bouts.Kind kind() {
        return kind;
    }

    public int stake() {
        return stake;
    }

    public boolean forCoins() {
        return coins;
    }

    /** Кости партии; пусто у армрестлинга. */
    public Optional<Ochko> dice() {
        return Optional.ofNullable(dice);
    }

    /** Борьба партии; пусто у костей. */
    public Optional<ArmWrestle> arm() {
        return Optional.ofNullable(arm);
    }

    /** Тик мира, с которого бежит отметка армрестлинга: клиент рисует её сам от него. */
    public long markerStart() {
        return markerStart;
    }

    /** Сколько медяков по итогу: выиграно (+) или проиграно (−); пусто — партия не кончена. */
    public OptionalInt settled() {
        return settled == null ? OptionalInt.empty() : OptionalInt.of(settled);
    }

    boolean in(ServerWorld world) {
        return this.world.equals(world.getRegistryKey());
    }

    /**
     * Игрок партии: сетевой — по опознавателю с сервера, подставной — тот,
     * что начал, пока не удалён. Ушедший сетевой — никто.
     */
    public PlayerEntity playerOf(ServerWorld world) {
        ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(player);
        if (online != null) {
            return online;
        }
        return starter instanceof ServerPlayerEntity || starter.isRemoved() ? null : starter;
    }

    // --- ходы игрока ---

    /** Бросок игрока. @return выпавшее; пусто — сейчас бросает не он */
    public OptionalInt roll(ServerWorld world) {
        if (dice == null || dice.phase() != Ochko.Phase.PLAYER) {
            return OptionalInt.empty();
        }
        int face = dice.roll();
        world.playSound(null, where(world), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.NEUTRAL,
                0.6f, 1.2f);
        afterPlayer(world);
        return OptionalInt.of(face);
    }

    /** «Хватит»: ход переходит к сопернику. */
    public boolean stand(ServerWorld world) {
        if (dice == null || dice.phase() != Ochko.Phase.PLAYER || dice.mine().isEmpty()) {
            return false;
        }
        dice.stand();
        afterPlayer(world);
        return true;
    }

    /** Нажатие в армрестлинге. @return попал ли */
    public boolean press(ServerWorld world) {
        if (arm == null || arm.outcome().isPresent()) {
            return false;
        }
        boolean hit = arm.press();
        if (arm.outcome().isPresent()) {
            finish(world);
        } else {
            Bouts.watcher().changed(world, this);
        }
        return hit;
    }

    /**
     * Встать из-за стола до итога — сдать партию.
     * <p>
     * Иначе закрыть окно, увидев перебор соперника на подходе, было бы
     * бесплатным отказом от проигрыша. Сдавший платит одну ставку.
     */
    public void leave(ServerWorld world) {
        if (settled != null) {
            return;
        }
        PlayerEntity who = playerOf(world);
        Settlement settlement = settlement(world).orElse(null);
        Citizen citizen = settlement == null ? null : settlement.citizen(rival).orElse(null);
        int paid = who != null && settlement != null && coins ? payVillage(world, who, settlement, stake) : 0;
        if (who != null) {
            GamesLedger.get(world).record(rival, player, Rivalry.Result.WON, day);
            who.sendMessage(Text.translatable("villagepax.games.forfeit"), true);
        }
        if (citizen != null) {
            Company.body(world, citizen).ifPresent(body -> {
                body.setWrestling(false);
                Lines.sayNow(body, citizen, Say.WIN);
            });
        }
        settled = -paid;
        Bouts.ended(world, this, Optional.of("villagepax.games.forfeit"));
    }

    // --- ход партии ---

    void tick(ServerWorld world) {
        PlayerEntity who = playerOf(world);
        Settlement settlement = settlement(world).orElse(null);
        Citizen citizen = settlement == null ? null : settlement.citizen(rival).orElse(null);
        CitizenEntity body = citizen == null ? null : Company.body(world, citizen).orElse(null);
        if (who == null || !who.isAlive() || who.getWorld() != world
                || body == null || !body.isAlive()) {
            cancel(world);
            return;
        }
        if (world.getTime() % Bouts.CHECK_EVERY == 0
                && who.squaredDistanceTo(body) > Bouts.REACH * Bouts.REACH) {
            leave(world);
            return;
        }
        if (dice != null && dice.phase() == Ochko.Phase.RIVAL && world.getTime() >= nextRoll) {
            rivalRolls(world, citizen, body);
        } else if (arm != null) {
            arm.tick();
            if (!strained && arm.balance() >= STRAIN_AT) {
                strained = true;
                Lines.say(body, citizen, Say.STRAIN);
            }
            if (arm.outcome().isPresent()) {
                finish(world);
            } else if (world.getTime() % SHOW_EVERY == 0) {
                Bouts.watcher().changed(world, this);
            }
        }
    }

    private void afterPlayer(ServerWorld world) {
        if (dice.phase() == Ochko.Phase.DONE) {
            finish(world);
            return;
        }
        if (dice.phase() == Ochko.Phase.RIVAL) {
            nextRoll = world.getTime() + Bouts.ROLL_EVERY;
        }
        Bouts.watcher().changed(world, this);
    }

    /**
     * Шаг соперника: бросок при всех, стук костей, и изредка — слово.
     * Большое и малое он комментирует через раз: говорить на каждый бросок —
     * значит тараторить.
     */
    private void rivalRolls(ServerWorld world, Citizen citizen, CitizenEntity body) {
        OptionalInt face = dice.rivalStep();
        if (face.isPresent()) {
            body.throwDice();
            world.playSound(null, body.getBlockPos(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.NEUTRAL,
                    0.6f, 1.2f);
            if (world.getRandom().nextInt(COMMENT_ODDS) == 0) {
                if (face.getAsInt() >= HIGH_FACE) {
                    Lines.say(body, citizen, Say.ROLL_HIGH);
                } else if (face.getAsInt() <= LOW_FACE) {
                    Lines.say(body, citizen, Say.ROLL_LOW);
                }
            }
        }
        if (dice.phase() == Ochko.Phase.DONE) {
            finish(world);
            return;
        }
        nextRoll = world.getTime() + Bouts.ROLL_EVERY;
        Bouts.watcher().changed(world, this);
    }

    /**
     * Итог: монета из кошелька соперника игроку или от игрока на склад
     * деревни, счёт — в память, слово — вслух.
     */
    private void finish(ServerWorld world) {
        PlayerEntity who = playerOf(world);
        Settlement settlement = settlement(world).orElse(null);
        Citizen citizen = settlement == null ? null : settlement.citizen(rival).orElse(null);
        if (who == null || citizen == null) {
            cancel(world);
            return;
        }
        Result result = result();
        int amount = stake * times();
        int moved = 0;
        GamesLedger ledger = GamesLedger.get(world);
        if (coins && amount > 0 && result == Result.PLAYER) {
            ledger.spend(rival, day, amount);
            PlayerInventory inventory = who.getInventory();
            for (ItemStack rest : Coins.earn(inventory, amount)) {
                inventory.offerOrDrop(rest);
            }
            moved = amount;
        } else if (coins && amount > 0 && result == Result.RIVAL) {
            moved = -payVillage(world, who, settlement, amount);
        }
        ledger.record(rival, player, switch (result) {
            case PLAYER -> Rivalry.Result.LOST;
            case RIVAL -> Rivalry.Result.WON;
            case NOBODY -> Rivalry.Result.EVEN;
        }, day);
        if (!coins && withOwner) {
            ledger.markCheer(rival, day);
        }
        Say line = line(result);
        Company.body(world, citizen).ifPresent(body -> {
            body.setWrestling(false);
            Lines.sayNow(body, citizen, line, who.getName());
        });
        settled = moved;
        Bouts.ended(world, this, Optional.empty());
    }

    /** Снять без выплаты: игрок ушёл из игры, соперник пропал, сервер встаёт. */
    void cancel(ServerWorld world) {
        if (settled != null) {
            return;
        }
        settled = 0;
        settlement(world).flatMap(s -> s.citizen(rival)).flatMap(c -> Company.body(world, c))
                .ifPresent(body -> body.setWrestling(false));
        PlayerEntity who = playerOf(world);
        if (who != null) {
            who.sendMessage(Text.translatable("villagepax.games.cancelled"), true);
        }
        Bouts.ended(world, this, Optional.of("villagepax.games.cancelled"));
    }

    /** Чем кончилось — кто победил. */
    private enum Result { PLAYER, RIVAL, NOBODY }

    private Result result() {
        if (dice != null) {
            return switch (dice.outcome().orElse(Ochko.Outcome.PUSH)) {
                case WIN -> Result.PLAYER;
                case LOSE -> Result.RIVAL;
                case PUSH -> Result.NOBODY;
            };
        }
        return switch (arm.outcome().orElse(ArmWrestle.Outcome.DRAW)) {
            case WIN -> Result.PLAYER;
            case LOSE -> Result.RIVAL;
            case DRAW -> Result.NOBODY;
        };
    }

    /** Во сколько ставок итог: у костей «очко» вдвойне, у армрестлинга всегда одна. */
    private int times() {
        if (dice != null) {
            return dice.stakes();
        }
        return result() == Result.NOBODY ? 0 : 1;
    }

    /**
     * Что скажет соперник по итогу.
     * <p>
     * У себя в колонии, с хозяином, — спасибо за вечер: там играют
     * на интерес, и победа здесь не главное.
     */
    private Say line(Result result) {
        if (!coins && withOwner) {
            return Say.THANKS;
        }
        if (dice != null && result == Result.RIVAL && dice.theirTotal() == Ochko.LIMIT) {
            return Say.OCHKO;
        }
        if (dice != null && dice.theirTotal() > Ochko.LIMIT) {
            return Say.BUST;
        }
        return switch (result) {
            case PLAYER -> Say.LOSE;
            case RIVAL -> Say.WIN;
            case NOBODY -> Say.PUSH;
        };
    }

    /**
     * Заплатить деревне столько, сколько есть, но не больше долга.
     * <p>
     * Меньше долга бывает только у «очка» соперника: оно вдвое, а ставка
     * была по монетам игрока. Сдача от размена кладётся обратно в руки.
     *
     * @return сколько заплачено
     */
    private static int payVillage(ServerWorld world, PlayerEntity who, Settlement settlement,
                                  int amount) {
        PlayerInventory inventory = who.getInventory();
        int paid = Math.min(amount, Coins.total(inventory));
        if (paid <= 0) {
            return 0;
        }
        for (ItemStack change : Coins.pay(inventory, paid)) {
            inventory.offerOrDrop(change);
        }
        Warehouse warehouse = Warehouse.of(world, settlement);
        net.minecraft.util.math.BlockPos spot = GameSpot.of(world, SettlementManager.get(world), settlement);
        for (ItemStack stack : Coins.stacksFor(paid)) {
            warehouse.addOrScatter(world, spot, stack);
        }
        return paid;
    }

    private Optional<Settlement> settlement(ServerWorld world) {
        return SettlementManager.get(world).byId(village);
    }

    private net.minecraft.util.math.BlockPos where(ServerWorld world) {
        return settlement(world).map(s -> GameSpot.of(world, SettlementManager.get(world), s))
                .orElse(starter.getBlockPos());
    }
}
