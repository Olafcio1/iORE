package pl.olafcio.iore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryPropertyMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = Minecraft.class, priority = 1500)
public class MinecraftMixin {
    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;save()V"), method = "<init>")
    public void init__saveOptions(Options options, Operation<Void> operation) {
        if (options.onboardAccessibility)  // if (basically) first launched
            operation.call(options);
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/telemetry/TelemetryEventSender;send(Lnet/minecraft/client/telemetry/TelemetryEventType;Ljava/util/function/Consumer;)V"), method = "<init>", require = 0)
    public void init__sendTelemetry(net.minecraft.client.telemetry.TelemetryEventSender a, TelemetryEventType var1, Consumer<TelemetryPropertyMap.Builder> var2) {}
}
