package pl.olafcio.iore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;save()V"), method = "<init>")
    public void init__saveOptions(Options options, Operation<Void> operation) {
        if (options.onboardAccessibility)  // if (basically) first launched
            operation.call(options);
    }
}
