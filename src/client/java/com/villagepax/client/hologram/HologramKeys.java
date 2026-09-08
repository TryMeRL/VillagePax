package com.villagepax.client.hologram;

import com.villagepax.screen.BuildOrders;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши режима установки.
 * <p>
 * Назначаемые клавиши, а не перехват правого щелчка: щелчок в режиме
 * установки — это ещё и «поставить блок в руке», и разнимать два смысла
 * надёжно не выйдет. Заодно игрок может переназначить их в настройках,
 * как всё остальное.
 * <p>
 * Пока режим включён, подсказка с клавишами висит над полосой предметов:
 * иначе про них никто не узнает.
 */
public final class HologramKeys {

    private static final String CATEGORY = "villagepax.key.category";

    private static KeyBinding rotate;
    private static KeyBinding confirm;
    private static KeyBinding cancel;
    private static KeyBinding pin;
    private static KeyBinding higher;
    private static KeyBinding lower;
    private static KeyBinding farther;
    private static KeyBinding closer;

    /** Все клавиши разом — чтобы вычитывать накопленные нажатия одним списком. */
    private static KeyBinding[] all;

    private HologramKeys() {
    }

    public static void register() {
        rotate = bind("rotate", GLFW.GLFW_KEY_R);
        confirm = bind("confirm", GLFW.GLFW_KEY_ENTER);
        cancel = bind("cancel", GLFW.GLFW_KEY_X);
        pin = bind("pin", GLFW.GLFW_KEY_V);
        higher = bind("higher", GLFW.GLFW_KEY_PAGE_UP);
        lower = bind("lower", GLFW.GLFW_KEY_PAGE_DOWN);
        farther = bind("farther", GLFW.GLFW_KEY_EQUAL);
        closer = bind("closer", GLFW.GLFW_KEY_MINUS);

        all = new KeyBinding[] {rotate, confirm, cancel, pin, higher, lower, farther, closer};
    }

    private static KeyBinding bind(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "villagepax.key." + name, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    /**
     * Один тик режима установки: призрак идёт за взглядом, клавиши
     * разбираются здесь же.
     */
    public static void tick(MinecraftClient client) {
        Placement placement = Placement.active();
        if (placement == null) {
            // Нажатия вне режима надо всё равно вычитать, иначе они
            // накопятся и сработают все разом при следующем включении.
            drain();
            return;
        }

        placement.follow(client);

        boolean rotated = false;
        while (rotate.wasPressed()) {
            placement.rotate();
            rotated = true;
        }
        if (rotated && client.player != null) {
            client.player.sendMessage(Text.translatable("villagepax.hologram.rotated",
                    Text.literal(BuildOrders.nameOf(placement.rotation()))), true);
        }

        while (higher.wasPressed()) {
            placement.raise(1);
        }
        while (lower.wasPressed()) {
            placement.raise(-1);
        }
        while (farther.wasPressed()) {
            placement.pushAway(1);
        }
        while (closer.wasPressed()) {
            placement.pushAway(-1);
        }
        while (pin.wasPressed()) {
            placement.togglePin();
        }

        while (cancel.wasPressed()) {
            Placement.cancel();
            if (client.player != null) {
                client.player.sendMessage(
                        Text.translatable("villagepax.hologram.cancelled").formatted(Formatting.GRAY),
                        true);
            }
            drain();
            return;
        }

        while (confirm.wasPressed()) {
            placement.confirm();
            drain();
            return;
        }
    }

    private static void drain() {
        for (KeyBinding key : all) {
            while (key.wasPressed()) {
                // Накопленные вне режима нажатия выбрасываются: иначе они
                // сработают все разом при следующем включении.
            }
        }
    }

    public static String rotateKey() {
        return name(rotate);
    }

    public static String confirmKey() {
        return name(confirm);
    }

    public static String cancelKey() {
        return name(cancel);
    }

    public static String pinKey() {
        return name(pin);
    }

    public static String liftKeys() {
        return name(higher) + "/" + name(lower);
    }

    public static String rangeKeys() {
        return name(farther) + "/" + name(closer);
    }

    private static String name(KeyBinding key) {
        return key.getBoundKeyLocalizedText().getString();
    }
}
