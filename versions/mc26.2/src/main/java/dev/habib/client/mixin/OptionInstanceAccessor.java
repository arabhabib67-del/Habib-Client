package dev.habib.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.function.Function;
import net.minecraft.network.chat.Component;

@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("tooltip")
    OptionInstance.TooltipSupplier<Integer> habib$getTooltip();

    @Accessor("toString")
    Function<Integer, Component> habib$getCaption();

    @Accessor("onValueUpdate")
    OptionInstance.ValueUpdateListener<? super Integer> habib$getUpdateListener();
}
