package pl.olafcio.iore.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public class HudMixin {
    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractDemoOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"), method = "extractRenderState")
    public void extractRenderState__extractDemoOverlay(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {}
}
