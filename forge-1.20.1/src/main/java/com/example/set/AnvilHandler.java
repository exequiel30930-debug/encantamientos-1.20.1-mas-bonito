package com.example.set;

import com.example.set.menu.TransferMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Engancha el yunque vanilla (sin reemplazarlo):
 *  - Objeto con 2 o mas encantamientos (herramienta o libro encantado) + libro normal => el yunque muestra un libro "vista previa".
 *  - Al sacar ese resultado, se elimina la vista previa y se abre TransferMenu con el objeto y el libro.
 * Cualquier otra combinacion sigue funcionando exactamente como en vanilla.
 */
public class AnvilHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String PREVIEW_TAG = "SetPreview";

    private record Pending(ServerPlayer player, ItemStack tool, ItemStack book) {}
    private final List<Pending> pending = new ArrayList<>();

    public static boolean isPreview(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) && stack.hasTag() && stack.getTag().getBoolean(PREVIEW_TAG);
    }

    /** Un solo objeto con N o mas encantamientos (2 por defecto) a la izquierda y un libro normal a la derecha. */
    public static boolean isTransferInput(ItemStack left, ItemStack right) {
        return !left.isEmpty() && left.getCount() == 1
                && !left.is(Items.BOOK)
                && right.is(Items.BOOK)
                && EnchantmentHelper.getEnchantments(left).size() >= SetConfig.minEnchantments();
    }

    private static String describe(ItemStack s) {
        if (s.isEmpty()) return "empty";
        return ForgeRegistries.ITEMS.getKey(s.getItem()) + " x" + s.getCount() + " [enchantments=" + EnchantmentHelper.getEnchantments(s).size() + "]";
    }

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        boolean log = SetConfig.debugLog() && !left.isEmpty() && right.is(Items.BOOK);
        if (!event.getOutput().isEmpty()) { // otro mod ya definio un resultado
            if (log) LOGGER.info("[SET] ignored: another mod already set an output ({})", event.getOutput());
            return;
        }
        if (!isTransferInput(left, right)) {
            if (log) LOGGER.info("[SET] rejected: left={} right={} (needs at least {} enchantments)", describe(left), describe(right), SetConfig.minEnchantments());
            return;
        }
        if (log) LOGGER.info("[SET] accepted: left={}", describe(left));

        ItemStack preview = new ItemStack(Items.ENCHANTED_BOOK);
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(left).entrySet())
            EnchantedBookItem.addEnchantment(preview, new EnchantmentInstance(entry.getKey(), entry.getValue()));
        preview.getOrCreateTag().putBoolean(PREVIEW_TAG, true);
        preview.setHoverName(Component.translatable("item.selective_enchantment_transfer.preview"));
        event.setOutput(preview);
        event.setCost(1);          // vanilla exige coste > 0; ese nivel se devuelve y el coste real se cobra en la 2a pantalla
        event.setMaterialCost(1);  // consume solo 1 libro aunque haya una pila
    }

    /** Solo diagnostico (ajuste debugLog): como queda el evento despues de pasar por TODOS los mods. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAnvilUpdateFinal(AnvilUpdateEvent event) {
        if (!SetConfig.debugLog() || event.getLeft().isEmpty() || !event.getRight().is(Items.BOOK)) return;
        LOGGER.info("[SET] final state: canceled={} output={} cost={}", event.isCanceled(), event.getOutput(), event.getCost());
    }

    /**
     * Se dispara al sacar el resultado (clic normal, shift-clic, tecla numerica o Q).
     * Se leen las entradas directamente de las ranuras del yunque (aun intactas en este momento);
     * con shift-clic el "output" del evento llega vacio, asi que no se puede usar.
     */
    @SubscribeEvent
    public void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof AnvilMenu menu)) return;
        ItemStack left = menu.getSlot(0).getItem();
        ItemStack right = menu.getSlot(1).getItem();
        if (!isTransferInput(left, right)) return;
        event.setBreakChance(0.0F); // esta accion no desgasta el yunque
        removePreviews(player);     // ya, antes de que se pueda guardar o mover
        pending.add(new Pending(player, left.copy(), new ItemStack(Items.BOOK)));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) return;
        List<Pending> work = new ArrayList<>(pending);
        pending.clear();
        for (Pending p : work) {
            ServerPlayer player = p.player();
            if (player.isRemoved()) continue; // el logout ya devolvio los objetos
            removePreviews(player);
            if (!player.isAlive()) { // murio justo en ese tick: que no se pierda nada
                TransferMenu.give(player, p.tool());
                TransferMenu.give(player, p.book());
                continue;
            }
            if (!player.getAbilities().instabuild) player.giveExperienceLevels(1); // devuelve el nivel del yunque
            try {
                NetworkHooks.openScreen(player, new SimpleMenuProvider(
                        (id, inventory, pl) -> new TransferMenu(id, inventory, p.tool(), p.book()),
                        Component.translatable("gui.selective_enchantment_transfer.title")),
                        buf -> SettingsCodec.write(buf, SetConfig.Settings.fromConfig()));
            } catch (RuntimeException e) {
                LOGGER.error("Could not open the transfer screen; returning the items to the player", e);
                TransferMenu.give(player, p.tool());
                TransferMenu.give(player, p.book());
            }
        }
    }

    /** Si el jugador se desconecta con algo en juego, no pierde nada. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.player() != player) continue;
            it.remove();
            TransferMenu.give(player, p.tool());
            TransferMenu.give(player, p.book());
            if (!player.getAbilities().instabuild) player.giveExperienceLevels(1);
        }
        if (player.containerMenu instanceof TransferMenu menu) menu.returnItems(player);
    }

    /** Red de seguridad: la vista previa jamas puede existir como objeto suelto en el mundo (por ejemplo con la tecla Q). */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item && isPreview(item.getItem()))
            event.setCanceled(true);
    }

    private static void removePreviews(ServerPlayer player) {
        if (isPreview(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (isPreview(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
    }
}
