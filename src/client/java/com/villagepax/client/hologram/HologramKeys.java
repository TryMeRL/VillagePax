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

    private HologramKeys() {
    }

    public static void register() {
        rotate = bind("rotate", GLFW.GLFW_KEY_R);
        confirm = bind("confirm", GLFW.GLFW_KEY_ENTER);
        cancel = bind("cancel", GLFW.GLFW_KEY_X);
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
        while (rotate.wasPressed()) {
            // накопленные нажатия выбрасываются
        }
        while (confirm.wasPressed()) {
            // то же
        }
        while (cancel.wasPressed()) {
            // и то же
        }
    }

    public static String rotateKey() {
        return rotate.getBoundKeyLocalizedText().getString();
    }

    public static String confirmKey() {
        return confirm.getBoundKeyLocalizedText().getString();
    }

    public static String cancelKey() {
        return cancel.getBoundKeyLocalizedText().getString();
    }
}
