package com.example.set;

import com.example.set.menu.TransferMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

    /** Hay algun encantamiento de nivel 2 o mas: se puede repartir sus niveles entre varios libros. */
    public static boolean canSplit(ItemStack stack) {
        for (int level : EnchantmentHelper.getEnchantments(stack).values()) if (level >= 2) return true;
        return false;
    }

    /**
     * Un solo objeto a la izquierda y libros normales a la derecha. Vale con N o mas encantamientos (2 por defecto) o,
     * si tiene uno de nivel 2+, con al menos 2 libros (para repartir sus niveles).
     */
    public static boolean isTransferInput(ItemStack left, ItemStack right) {
        if (left.isEmpty() || left.getCount() != 1 || left.is(Items.BOOK) || !right.is(Items.BOOK)) return false;
        int n = EnchantmentHelper.getEnchantments(left).size();
        if (n >= SetConfig.minEnchantments()) return n >= 1;
        return n >= 1 && right.getCount() >= 2 && canSplit(left);
    }

    /**
     * Objeto con encantamientos a la izquierda y OTRO objeto enchantable (de distinto tipo) a la derecha: se pueden pasar encantamientos
     * directamente al segundo objeto. Dos objetos iguales los sigue combinando el yunque vanilla. Hace falta al menos 1 encantamiento transferible.
     */
    public static boolean isTargetInput(ItemStack left, ItemStack right) {
        if (left.isEmpty() || left.getCount() != 1 || right.isEmpty() || right.getCount() != 1) return false;
        if (left.is(Items.BOOK) || right.is(Items.BOOK) || right.is(Items.ENCHANTED_BOOK)) return false;
        if (left.getItem() == right.getItem()) return false;
        if (!(right.isEnchantable() || right.isEnchanted())) return false;
        for (Enchantment e : EnchantmentHelper.getEnchantments(left).keySet())
            if (TransferMenu.targetDeny(e, right) == 0) return true;
        return false;
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
        boolean targetMode = !right.is(Items.BOOK) && isTargetInput(left, right);
        if (!targetMode && !isTransferInput(left, right)) {
            if (log) LOGGER.info("[SET] rejected: left={} right={} (needs at least {} enchantments)", describe(left), describe(right), SetConfig.minEnchantments());
            return;
        }
        if (log) LOGGER.info("[SET] accepted: left={}", describe(left));

        ItemStack preview = new ItemStack(Items.ENCHANTED_BOOK);
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(left).entrySet()) {
            if (targetMode && TransferMenu.targetDeny(entry.getKey(), right) != 0) continue; // solo los que el destino admite
            EnchantedBookItem.addEnchantment(preview, new EnchantmentInstance(entry.getKey(), entry.getValue()));
        }
        preview.getOrCreateTag().putBoolean(PREVIEW_TAG, true);
        preview.setHoverName(Component.translatable("item.selective_enchantment_transfer.preview"));
        event.setOutput(preview);
        event.setCost(1);          // vanilla exige coste > 0; ese nivel se devuelve y el coste real se cobra en la 2a pantalla
        event.setMaterialCost(1);  // solo 1 libro aqui; si al repartir hacen falta mas, se gastan del inventario al confirmar
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
        boolean bookMode = isTransferInput(left, right);
        if (!bookMode && !isTargetInput(left, right)) return;
        event.setBreakChance(0.0F); // esta accion no desgasta el yunque
        removePreviews(player);     // ya, antes de que se pueda guardar o mover
        pending.add(new Pending(player, left.copy(), bookMode ? new ItemStack(Items.BOOK) : right.copy()));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (SetConfig.resetPriorWork()) resetPriorWork(ServerLifecycleHooks.getCurrentServer());
        if (pending.isEmpty()) return;
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

    /** Opcion "sin penalizacion": lo que hay en la ranura de resultado de cualquier yunque sale con la penalizacion por trabajo previo a 0. */
    private static void resetPriorWork(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof AnvilMenu menu)) continue;
            ItemStack out = menu.getSlot(2).getItem();
            if (!out.isEmpty() && !isPreview(out) && out.getBaseRepairCost() != 0) out.setRepairCost(0);
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
