package dev.habib.client.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.text.Text;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("tooltipFactory")
    SimpleOption.TooltipFactory<Integer> habib$getTooltipFactory();

    @Accessor("textGetter")
    Function<Integer, Text> habib$getTextGetter();

    @Accessor("changeCallback")
    Consumer<Integer> habib$getChangeCallback();
}
