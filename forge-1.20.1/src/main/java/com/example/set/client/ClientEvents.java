package com.example.set.client;

import com.example.set.ModNetwork;
import com.example.set.SelectiveEnchantmentTransfer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del cliente: detecta el atajo de teclado y pide al servidor los ajustes actuales. */
@Mod.EventBusSubscriber(modid = SelectiveEnchantmentTransfer.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean pressed = false;
        while (ClientSetup.OPEN_SETTINGS.consumeClick()) pressed = true; // varias pulsaciones = una sola peticion
        Minecraft mc = Minecraft.getInstance();
        if (pressed && mc.player != null && mc.screen == null) ModNetwork.requestSettings();
    }
}
