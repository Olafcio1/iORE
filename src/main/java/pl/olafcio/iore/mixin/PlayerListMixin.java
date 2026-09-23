package pl.olafcio.iore.mixin;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import pl.olafcio.iore.mixininterface.IPlayerList;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin implements IPlayerList {
    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private List<ServerPlayer> players;
    @Shadow @Final private Map<UUID, ServerPlayer> playersByUUID;
    @Shadow @Final private Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
    @Shadow @Final private Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();

    @Shadow protected abstract void save(ServerPlayer player);
    @Shadow public abstract void broadcastAll(Packet<?> packet);

    @Override
    public void iORE$save(ServerPlayer player) {
        this.save(player);
    }

    @Override
    public void iORE$clean(ServerPlayer player) {
        var uuid = player.getUUID();

        this.stats.remove(uuid);
        this.advancements.remove(uuid);
    }

    public void iORE$removeNoSave(ServerPlayer player) {
        Object vehicle;
        ServerLevel level = player.level();
        player.awardStat(Stats.LEAVE_GAME);
        if (player.isPassenger() && ((Entity)(vehicle = player.getRootVehicle())).hasExactlyOnePlayerPassenger()) {
            LOGGER.debug("Removing player mount");
            player.stopRiding();
            ((Entity)vehicle).getPassengersAndSelf().forEach(e -> e.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
        }
        player.unRide();
        for (ThrownEnderpearl enderpearl : player.getEnderPearls()) {
            enderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        level.removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        player.getAdvancements().clearTriggers();
        this.players.remove(player);
        this.server.getCustomBossEvents().onPlayerDisconnect(player);
        UUID uuid = player.getUUID();
        ServerPlayer serverPlayer = this.playersByUUID.get(uuid);
        if (serverPlayer == player) {
            this.playersByUUID.remove(uuid);
            this.server.notificationManager().playerLeft(player);
        }
        this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
    }
}
