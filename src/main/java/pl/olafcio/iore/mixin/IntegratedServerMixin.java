package pl.olafcio.iore.mixin;

import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.stats.Stats;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pl.olafcio.iore.mixininterface.IPlayerList;

import java.net.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin extends MinecraftServer {
    @Shadow
    private @Nullable UUID uuid;

    public IntegratedServerMixin(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener, boolean propagatesCrashes, NotificationManager notificationManager) {
        super(serverThread, storageSource, packRepository, worldStem, gameRules, proxy, fixerUpper, services, levelLoadListener, propagatesCrashes, notificationManager);
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"), method = "halt")
    public void halt__playerSaving(IntegratedServer loop, Runnable runnable) {
        loop.executeBlocking(() -> {
            var players = Lists.newArrayList(this.getPlayerList().getPlayers());
            var playerList = (IPlayerList) this.getPlayerList();

            try (var executor = Executors.newWorkStealingPool()) {
                for (ServerPlayer player : players) {
                    if (player.getUUID().equals(this.uuid))
                        continue;

                    executor.submit(() -> {
                        playerList.iORE$save(player);
                        playerList.iORE$clean(player);
                    });

                    playerList.iORE$removeNoSave(player);
                }

                executor.shutdown();

                if (!executor.awaitTermination(1000000, TimeUnit.MILLISECONDS))
                    throw new RuntimeException("Player list didn't save in 1mil milliseconds; aborting");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
