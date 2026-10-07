package com.villagepax.item.gear;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Часть брони народа — объёмная, моделью GeckoLib.
 * <p>
 * Отрисовщик живёт в клиентском коде и сюда попадает через
 * {@link GearRendering}: серверу о нём знать нечего, а общий код не
 * вправе ссылаться на клиентские классы.
 */
public class GearArmorItem extends ArmorItem implements GeoItem {

    private final Gear gear;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public GearArmorItem(Gear gear, Type type, Settings settings) {
        super(gear.armor(), type, settings);
        this.gear = gear;
    }

    public Gear gear() {
        return gear;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        Object provider = GearRendering.provider(this);
        if (provider != null) {
            consumer.accept(provider);
        }
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return renderProvider;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Броня не движется сама: позу ей даёт тело, на котором она надета.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        tooltip.add(Text.translatable("villagepax.gear.set", Text.translatable(gear.setKey()))
                .formatted(Formatting.GRAY));
        com.villagepax.item.charm.Charm charm = com.villagepax.item.charm.Charm.ofPeople(gear.id());
        if (charm != null) {
            tooltip.add(Text.translatable("villagepax.gear.harmony",
                    Text.translatable("item.villagepax." + charm.id()),
                    Text.translatable("villagepax.charm.harmony." + gear.id()))
                    .formatted(Formatting.GOLD));
        }
    }
}
