package com.example.set.client;

import com.example.set.SelectiveEnchantmentTransfer;
import com.example.set.registry.ModMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SelectiveEnchantmentTransfer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** Atajo para abrir el menu de ajustes del mod (se cambia en Opciones > Controles). */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping("key." + SelectiveEnchantmentTransfer.MODID + ".open_settings",
            GLFW.GLFW_KEY_K, "key.categories." + SelectiveEnchantmentTransfer.MODID);

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.TRANSFER_MENU.get(), TransferScreen::new));
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
    }
}
