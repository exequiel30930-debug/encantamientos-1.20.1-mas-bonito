package com.example.set;

import com.example.set.registry.ModMenus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SelectiveEnchantmentTransfer.MODID)
public class SelectiveEnchantmentTransfer {
    public static final String MODID = "selective_enchantment_transfer";

    public SelectiveEnchantmentTransfer() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModMenus.MENUS.register(bus);
        ModNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SetConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new AnvilHandler());
        MinecraftForge.EVENT_BUS.register(new AnvilHint());
    }
}
