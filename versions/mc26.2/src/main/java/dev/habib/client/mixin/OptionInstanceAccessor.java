package dev.habib.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("tooltip")
    OptionInstance.TooltipSupplier<Integer> habib$getTooltip();

    @Accessor("toString")
    OptionInstance.CaptionBasedToString<Integer> habib$getCaption();

    @Accessor("onValueUpdate")
    OptionInstance.ValueUpdateListener<? super Integer> habib$getUpdateListener();
}
