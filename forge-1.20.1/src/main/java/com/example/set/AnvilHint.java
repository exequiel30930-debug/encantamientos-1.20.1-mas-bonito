package com.example.set;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Si pones en el yunque un objeto con MENOS encantamientos de los necesarios (por defecto 2) y un libro normal,
 * el yunque muestra la X roja sin explicar nada. Este aviso (barra de acciones) dice por que.
 */
public class AnvilHint {
    private final Map<UUID, Integer> shown = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof AnvilMenu menu)) {
            if (!shown.isEmpty()) shown.remove(player.getUUID());
            return;
        }
        ItemStack left = menu.getSlot(0).getItem();
        ItemStack right = menu.getSlot(1).getItem();
        int n = left.isEmpty() ? 0 : EnchantmentHelper.getEnchantments(left).size();
        int min = SetConfig.minEnchantments();
        boolean splittable = !left.isEmpty() && AnvilHandler.canSplit(left);
        boolean tooFew = n >= 1 && n < min && left.getCount() == 1 && !left.is(Items.BOOK)
                && right.is(Items.BOOK) && !menu.getSlot(2).hasItem();
        if (!tooFew) {
            if (!shown.isEmpty()) shown.remove(player.getUUID());
            return;
        }
        int key = n * 100 + min + (splittable ? 100000 : 0);
        Integer prev = shown.get(player.getUUID());
        if (prev == null || prev.intValue() != key) {
            shown.put(player.getUUID(), key);
            if (splittable) player.displayClientMessage(Component.translatable("message.selective_enchantment_transfer.too_few_books"), true);
            else player.displayClientMessage(Component.translatable("message.selective_enchantment_transfer.too_few", n, min), true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        shown.remove(event.getEntity().getUUID());
    }
}
