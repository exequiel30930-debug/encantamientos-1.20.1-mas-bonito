package com.example.set;

import com.example.set.client.ClientHooks;
import com.example.set.menu.TransferMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.ArrayList;
import java.util.List;

public final class ModNetwork {
    private static final String PROTOCOL = "6";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SelectiveEnchantmentTransfer.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered = false;
    private ModNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;

        // 0: el cliente pide transferir los encantamientos elegidos
        CHANNEL.registerMessage(0, TransferSelectionPacket.class, TransferSelectionPacket::encode,
                TransferSelectionPacket::decode, (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer player = ctx.get().getSender();
                        if (player != null && player.containerMenu instanceof TransferMenu) {
                            TransferMenu menu = (TransferMenu) player.containerMenu;
                            if (menu.containerId == packet.containerId) menu.transferSelected(player, packet.enchantmentIds, packet.copy);
                        }
                    });
                    ctx.get().setPacketHandled(true);
                });

        // 1: el cliente pide guardar nuevos ajustes (solo operadores o el dueño del mundo en un solo jugador)
        CHANNEL.registerMessage(1, ConfigUpdatePacket.class, ConfigUpdatePacket::encode,
                ConfigUpdatePacket::decode, (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer player = ctx.get().getSender();
                        if (player == null) return;
                        MinecraftServer server = player.getServer();
                        boolean allowed = player.createCommandSourceStack().hasPermission(2)
                                || (server != null && server.isSingleplayerOwner(player.getGameProfile()));
                        if (!allowed) {
                            player.displayClientMessage(Component.translatable("message.selective_enchantment_transfer.config_denied"), true);
                            return;
                        }
                        SetConfig.Settings settings = packet.settings;
                        settings.clamp();
                        settings.apply();
                        player.displayClientMessage(Component.translatable("message.selective_enchantment_transfer.config_saved"), true);
                        if (player.containerMenu instanceof TransferMenu) {
                            int id = ((TransferMenu) player.containerMenu).containerId;
                            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SettingsSyncPacket(id, settings));
                        }
                    });
                    ctx.get().setPacketHandled(true);
                });

        // 2: el servidor manda los ajustes nuevos a la pantalla abierta
        CHANNEL.registerMessage(2, SettingsSyncPacket.class, SettingsSyncPacket::encode,
                SettingsSyncPacket::decode, (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> ClientHooks.onSettingsSync(packet.containerId, packet.settings)));
                    ctx.get().setPacketHandled(true);
                });

        // 3: el cliente pide los ajustes actuales (atajo de teclado)
        CHANNEL.registerMessage(3, SettingsRequestPacket.class, SettingsRequestPacket::encode,
                SettingsRequestPacket::decode, (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer player = ctx.get().getSender();
                        if (player != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SettingsOpenPacket(SetConfig.Settings.fromConfig()));
                    });
                    ctx.get().setPacketHandled(true);
                });

        // 4: el servidor responde con los ajustes y el cliente abre el menu de ajustes
        CHANNEL.registerMessage(4, SettingsOpenPacket.class, SettingsOpenPacket::encode,
                SettingsOpenPacket::decode, (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> ClientHooks.openSettings(packet.settings)));
                    ctx.get().setPacketHandled(true);
                });
    }

    public static void sendSelection(int containerId, List<String> ids, boolean copy) {
        CHANNEL.sendToServer(new TransferSelectionPacket(containerId, ids, copy));
    }

    public static void requestSettings() {
        CHANNEL.sendToServer(new SettingsRequestPacket());
    }

    public static void sendConfig(SetConfig.Settings settings) {
        CHANNEL.sendToServer(new ConfigUpdatePacket(settings));
    }

    public static class TransferSelectionPacket {
        final int containerId;
        final List<String> enchantmentIds;
        final boolean copy;
        TransferSelectionPacket(int containerId, List<String> enchantmentIds, boolean copy) {
            this.containerId = containerId;
            this.enchantmentIds = new ArrayList<>(enchantmentIds);
            this.copy = copy;
        }
        static void encode(TransferSelectionPacket p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.containerId);
            buf.writeVarInt(p.enchantmentIds.size());
            for (String id : p.enchantmentIds) buf.writeUtf(id, 256);
            buf.writeBoolean(p.copy);
        }
        static TransferSelectionPacket decode(FriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid enchantment count: " + count);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) ids.add(buf.readUtf(256));
            return new TransferSelectionPacket(containerId, ids, buf.readBoolean());
        }
    }

    public static class ConfigUpdatePacket {
        final SetConfig.Settings settings;
        ConfigUpdatePacket(SetConfig.Settings settings) { this.settings = settings; }
        static void encode(ConfigUpdatePacket p, FriendlyByteBuf buf) { SettingsCodec.write(buf, p.settings); }
        static ConfigUpdatePacket decode(FriendlyByteBuf buf) { return new ConfigUpdatePacket(SettingsCodec.read(buf)); }
    }

    public static class SettingsSyncPacket {
        final int containerId;
        final SetConfig.Settings settings;
        SettingsSyncPacket(int containerId, SetConfig.Settings settings) { this.containerId = containerId; this.settings = settings; }
        static void encode(SettingsSyncPacket p, FriendlyByteBuf buf) { buf.writeVarInt(p.containerId); SettingsCodec.write(buf, p.settings); }
        static SettingsSyncPacket decode(FriendlyByteBuf buf) { return new SettingsSyncPacket(buf.readVarInt(), SettingsCodec.read(buf)); }
    }

    public static class SettingsRequestPacket {
        SettingsRequestPacket() {}
        static void encode(SettingsRequestPacket p, FriendlyByteBuf buf) {}
        static SettingsRequestPacket decode(FriendlyByteBuf buf) { return new SettingsRequestPacket(); }
    }

    public static class SettingsOpenPacket {
        final SetConfig.Settings settings;
        SettingsOpenPacket(SetConfig.Settings settings) { this.settings = settings; }
        static void encode(SettingsOpenPacket p, FriendlyByteBuf buf) { SettingsCodec.write(buf, p.settings); }
        static SettingsOpenPacket decode(FriendlyByteBuf buf) { return new SettingsOpenPacket(SettingsCodec.read(buf)); }
    }
}
