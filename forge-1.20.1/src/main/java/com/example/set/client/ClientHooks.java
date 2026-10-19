package com.example.set.client;

import com.example.set.SetConfig;
import com.example.set.menu.TransferMenu;
import net.minecraft.client.Minecraft;

/** Codigo que solo debe ejecutarse en el cliente (se invoca mediante DistExecutor). */
public final class ClientHooks {
    private ClientHooks() {}

    /** Llegan ajustes nuevos del servidor: actualiza la pantalla de transferencia abierta. */
    public static void onSettingsSync(int containerId, SetConfig.Settings settings) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof TransferMenu) {
            TransferMenu menu = (TransferMenu) mc.player.containerMenu;
            if (menu.containerId == containerId) menu.setSettings(settings);
        }
    }

    /** Abre el menu de ajustes con los valores actuales del servidor (respuesta al atajo de teclado). */
    public static void openSettings(SetConfig.Settings settings) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new SetConfigScreen(mc.screen, settings));
    }
}
