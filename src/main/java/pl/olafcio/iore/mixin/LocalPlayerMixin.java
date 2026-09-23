package pl.olafcio.iore.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    @Shadow @Final public ClientPacketListener connection;
    @Shadow private Input lastSentInput;
    @Shadow public ClientInput input;

    @Shadow protected abstract void sendIsSprintingIfNeeded();
    @Shadow protected abstract void sendPosition();

    public LocalPlayerMixin(ClientLevel level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    /**
     * @author IORE (Intensively Optimized All-In-One Engine) // Olafcio
     * @reason Optimizations
     */
    @Overwrite
    public void sendChanges() {
        if (!this.connection.hasClientLoaded()) {
            return;
        }

        if (!this.lastSentInput.equals(this.input.keyPresses)) {
            this.connection.send(new ServerboundPlayerInputPacket(this.input.keyPresses));
            this.lastSentInput = this.input.keyPresses;
        }

        if (this.isPassenger()) {
            this.connection.send(new ServerboundMovePlayerPacket.Rot(this.getYRot(), this.getXRot(), this.onGround(), this.horizontalCollision));

            var vehicle = this.getVehicle();

            //noinspection DataFlowIssue
            if (vehicle.isLocalInstanceAuthoritative()) {
                this.connection.send(ServerboundMoveVehiclePacket.fromEntity(vehicle));
                this.sendIsSprintingIfNeeded();
            }
        } else {
            this.sendPosition();
        }
    }
}
