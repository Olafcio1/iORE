package pl.olafcio.iore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.gui.font.AtlasGlyphProvider;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Mixin(FontManager.class)
public class FontManagerMixin {
    @Shadow @Final @Mutable
    private Map<Identifier, AtlasGlyphProvider> atlasProviders;

    @Unique
    private final Object2ObjectMap<Identifier, AtlasGlyphProvider> result
                = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());

    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/sprite/AtlasManager;forEach(Ljava/util/function/BiConsumer;)V"), method = "apply")
    public void apply__forEach__atlases(AtlasManager mgr, java.util.function.BiConsumer<net.minecraft.resources.Identifier, net.minecraft.client.renderer.texture.TextureAtlas> output, Operation<Void> operation) {
        try (var executor = Executors.newWorkStealingPool()) {
            operation.call(mgr, (BiConsumer<Identifier, TextureAtlas>) ((id, atlas) -> executor.submit(() -> {
                output.accept(id, atlas);
            })));

            executor.shutdown();

            if (!executor.awaitTermination(400000, TimeUnit.MILLISECONDS))
                throw new RuntimeException("Font atlas glyph providers didn't load in 400k milliseconds; aborting");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        atlasProviders = result;
    }
}
