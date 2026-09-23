package pl.olafcio.iore.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.device.SurfaceException;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryPropertyMap;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = Minecraft.class, priority = 1500)
public abstract class MinecraftMixin {
    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;save()V"), method = "<init>")
    public void init__saveOptions(Options options, Operation<Void> operation) {
        if (options.onboardAccessibility)  // if (basically) first launched
            operation.call(options);
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/telemetry/TelemetryEventSender;send(Lnet/minecraft/client/telemetry/TelemetryEventType;Ljava/util/function/Consumer;)V"), method = "<init>", require = 0)
    public void init__sendTelemetry(net.minecraft.client.telemetry.TelemetryEventSender a, TelemetryEventType var1, Consumer<TelemetryPropertyMap.Builder> var2) {}

    @Shadow @Final private GpuSurface windowSurface;
    @Shadow @Final public LevelExtractor levelExtractor;
    @Shadow @Final private Window window;
    @Shadow private boolean windowSurfaceNeedsReconfiguring;
    @Shadow private boolean surfaceIsInvalid;
    @Shadow @Final public Options options;
    @Shadow private MetricsRecorder metricsRecorder;
    @Shadow @Final private DeltaTracker.Timer deltaTracker;
    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final public DebugScreenEntryList debugEntries;
    @Shadow @Final private TimerQuery timerQuery;
    @Shadow @Final public Gui gui;
    @Shadow @Final public GameRenderer gameRenderer;
    @Shadow private double gpuUtilization;
    @Shadow @Final public LevelRenderer levelRenderer;
    @Shadow private long frameTimeNs;
    @Shadow @Final private @Nullable TracyFrameCapture tracyFrameCapture;
    @Shadow @Final private FramerateLimitTracker framerateLimitTracker;
    @Shadow public @Nullable ClientLevel level;
    @Shadow private int frames;
    @Shadow private long savedCpuDuration;
    @Shadow private long lastNanoTime;
    @Shadow private static int fps;
    @Shadow private long lastTime;

    @Shadow protected abstract void pauseIfInactive();
    @Shadow public abstract boolean isGameLoadFinished();
    @Shadow protected abstract void pick(float partialTicks);
    @Shadow public abstract DebugScreenOverlay getDebugOverlay();

    /**
     * @author IORE (Intensively Optimized All-In-One Engine) // Olafcio
     * @reason Optimizations
     */
    @Overwrite
    public void renderFrame(boolean advanceGameTime) {
        long renderStartTimer;
        boolean recordGpuUtilization;

        if (this.windowSurface.isAcquired()) {
            return;
        }

        ProfilerFiller profiler = Profiler.get();
        try (Gizmos.TemporaryCollection ignored = this.levelExtractor.collectPerFrameMainThreadGizmos()) {
            profiler.push("update window");

            this.window.updateFullscreenIfChanged();

            if ((this.windowSurfaceNeedsReconfiguring || this.windowSurface.isSuboptimal() && !this.surfaceIsInvalid) && !this.window.isIconified()) {
                Window.FramebufferSize framebufferSize = this.window.queryFramebufferSize();
                GpuSurface.PresentMode presentMode = GpuSurface.PresentMode.getSupportedVsyncMode(this.windowSurface.supportedPresentModes(), this.options.enableVsync().get());
                GpuSurface.Configuration config = new GpuSurface.Configuration(framebufferSize.width(), framebufferSize.height(), presentMode);

                try {
                    this.windowSurface.configure(config);
                    this.surfaceIsInvalid = false;
                    this.windowSurfaceNeedsReconfiguring = false;
                } catch (SurfaceException exception) {
                    LOGGER.warn("Couldn't configure surface to {}", config, exception);
                    this.surfaceIsInvalid = true;
                }
            }

            if (!this.surfaceIsInvalid) {
                try {
                    this.windowSurface.acquireNextTexture();
                } catch (SurfaceException ex) {
                    LOGGER.warn("Couldn't acquire next surface texture with config {}", this.windowSurface.currentConfiguration(), ex);
                    this.surfaceIsInvalid = true;
                    this.windowSurfaceNeedsReconfiguring = true;
                }
            }

            profiler.popPush("update");
            this.deltaTracker.advanceRealTime(Util.getMillis());

            if (this.debugEntries.isCurrentlyEnabled(DebugScreenEntries.GPU_UTILIZATION) || this.metricsRecorder.isRecording()) {
                recordGpuUtilization = this.timerQuery.getStatus() == TimerQuery.Status.NOT_RECORDING;

                if (recordGpuUtilization) {
                    this.timerQuery.beginProfile();
                }
            } else {
                recordGpuUtilization = false;
                this.gpuUtilization = 0.0;
            }

            renderStartTimer = Util.getNanos();
            this.pauseIfInactive();
            this.gui.update();
            if (this.isGameLoadFinished() && advanceGameTime && this.level != null) {
                this.level.update();
            }

            this.gameRenderer.update(this.deltaTracker);
            float worldPartialTicks = this.deltaTracker.getGameTimeDeltaPartialTick(false);
            this.pick(worldPartialTicks);

            profiler.popPush("extract");

            this.gameRenderer.gameRenderState().framerateLimit = this.framerateLimitTracker.getFramerateLimit();
            this.gameRenderer.extract(this.deltaTracker, advanceGameTime);
        }

        try (var ignored = this.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            profiler.popPush("gpuAsync");
            RenderSystem.executePendingTasks();
            profiler.pop();
            this.gameRenderer.render();
        }

        profiler.push("swapchainBlit");
        if (this.windowSurface.isAcquired()) {
            GpuTextureView colorTexture = this.gameRenderer.mainRenderTarget().getColorTextureView();

            //Removed if
//            if (colorTexture == null) {
//                throw new IllegalStateException("Can't blit to screen, color texture doesn't exist yet");
//            }

            //noinspection DataFlowIssue
            this.windowSurface.blitFromTexture(RenderSystem.getDevice().createCommandEncoder(), colorTexture);
        }

        this.frameTimeNs = Util.getNanos() - renderStartTimer;

        if (recordGpuUtilization) {
            this.timerQuery.endProfile();
        }

        if (this.tracyFrameCapture != null) {
            profiler.popPush("tracyCapture");

            this.tracyFrameCapture.upload();
            this.tracyFrameCapture.capture(this.gameRenderer.mainRenderTarget());
        }

        profiler.popPush("submit");
        RenderSystem.getDevice().createCommandEncoder().submit();

        profiler.popPush("present");
        if (this.windowSurface.isAcquired()) {
            this.windowSurface.present();
        }

        profiler.popPush("endFrame");
        if (this.tracyFrameCapture != null) {
            this.tracyFrameCapture.endFrame();
        }

        RenderSystem.getDynamicUniforms().reset();
        this.levelRenderer.endFrame();
        profiler.popPush("frameLimiter");

        int framerateLimit = this.gameRenderer.gameRenderState().framerateLimit;
        if (framerateLimit < 260) {
            FramerateLimiter.limitDisplayFPS(framerateLimit);
        }

        profiler.popPush("fpsUpdate");
        ++this.frames;
        long currentTime = Util.getNanos();
        long frameDuration = currentTime - this.lastNanoTime;
        if (recordGpuUtilization) {
            this.savedCpuDuration = frameDuration;
        }
        this.getDebugOverlay().logFrameDuration(frameDuration);
        this.lastNanoTime = currentTime;
        this.gpuUtilization = (double)this.timerQuery.get() * 100.0 / (double)this.savedCpuDuration;
        while (Util.getMillis() >= this.lastTime + 1000L) {
            fps = this.frames;
            this.lastTime += 1000L;
            this.frames = 0;
        }
        profiler.pop();
    }
}
