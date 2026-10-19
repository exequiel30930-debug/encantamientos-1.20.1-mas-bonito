package com.example.set;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Concede los logros del mod (definidos en data/selective_enchantment_transfer/advancements). */
public final class ModAdvancements {
    private ModAdvancements() {}

    public static void award(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Advancement advancement = server.getAdvancements().getAdvancement(new ResourceLocation(SelectiveEnchantmentTransfer.MODID, name));
        if (advancement != null) player.getAdvancements().award(advancement, "done");
    }
}
