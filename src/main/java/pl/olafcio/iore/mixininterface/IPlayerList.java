package pl.olafcio.iore.mixininterface;

import net.minecraft.server.level.ServerPlayer;

public interface IPlayerList {
    void iORE$save(ServerPlayer player);
    void iORE$clean(ServerPlayer player);
    void iORE$removeNoSave(ServerPlayer player);
}
