package com.example.set.registry;

import com.example.set.SelectiveEnchantmentTransfer;
import com.example.set.menu.TransferMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SelectiveEnchantmentTransfer.MODID);
    public static final RegistryObject<MenuType<TransferMenu>> TRANSFER_MENU = MENUS.register("anvil_transfer", () -> IForgeMenuType.create((id, inv, buf) -> new TransferMenu(id, inv, buf)));
    private ModMenus() {}
}
