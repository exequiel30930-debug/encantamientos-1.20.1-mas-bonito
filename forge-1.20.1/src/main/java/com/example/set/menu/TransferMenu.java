package com.example.set.menu;

import com.example.set.ModAdvancements;
import com.example.set.SetConfig;
import com.example.set.SettingsCodec;
import com.example.set.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Segunda pantalla: muestra el objeto (herramienta o libro encantado) y el libro (bloqueados) y deja
 * elegir que encantamientos pasan a un libro nuevo. Dos modos: MOVER (salen del objeto) y COPIAR (el objeto los conserva).
 */
public class TransferMenu extends AbstractContainerMenu {
    // posiciones de las ranuras (la pantalla dibuja sus marcos alrededor de estas coordenadas)
    public static final int TOOL_X = 15, BOOK_X = 63, SLOT_Y = 22;

    /** Recargo fijo (niveles) de los encantamientos "de tesoro" (Reparacion, Paso helado, Velocidad de alma...). */
    public static final int TREASURE_BONUS = 4;

    // ---------------- precios ----------------
    // Precio base de MOVER un encantamiento = peso por rareza x nivel (+ recargo si es de tesoro), escalado por los ajustes.
    //   comun 1.0 · poco comun 1.5 · raro 2.0 · muy raro 3.0
    // Ejemplos: Afilado V = 5, Irrompibilidad III = 5, Eficiencia V = 5, Fortuna III = 6, Toque de seda = 3, Reparacion = 6.
    // COPIAR cuesta (precio de mover) x copyCostMultiplier (3 por defecto) y no admite encantamientos de tesoro.
    private static double basePrice(Enchantment enchantment, int level) {
        double weight = switch (enchantment.getRarity()) {
            case COMMON -> 1.0D;
            case UNCOMMON -> 1.5D;
            case RARE -> 2.0D;
            case VERY_RARE -> 3.0D;
        };
        double price = weight * level;
        if (enchantment.isTreasureOnly() && !enchantment.isCurse()) price += TREASURE_BONUS;
        return price;
    }

    public static int costOf(SetConfig.Settings s, Enchantment enchantment, int level, boolean copy) {
        if (!s.xpCostEnabled || s.costMultiplier <= 0.0D) return 0;
        Double override = s.priceOverrides.get(idOf(enchantment));
        double base;
        if (override != null) {
            if (override.doubleValue() <= 0.0D) return 0;
            base = override.doubleValue() * level;
        } else {
            base = basePrice(enchantment, level);
        }
        int move = Math.max(1, (int) Math.ceil(base * s.costMultiplier));
        return copy ? Math.max(1, (int) Math.ceil(move * s.copyCostMultiplier)) : move;
    }

    public static int totalCost(SetConfig.Settings s, Map<Enchantment, Integer> moving, boolean copy) {
        int total = 0;
        for (Map.Entry<Enchantment, Integer> e : moving.entrySet()) total += costOf(s, e.getKey(), e.getValue(), copy);
        return Math.min(s.maxTotalCost, total);
    }

    // ---------------- bloqueados, desgaste y que se puede seleccionar ----------------
    public static boolean isBlocked(SetConfig.Settings s, Enchantment enchantment) { return s.blocked.contains(idOf(enchantment)); }

    /** % "fuerte" propio del encantamiento (lista copyWear o tesoro automatico), sin el minimo base. */
    private static int strongPercent(SetConfig.Settings s, Enchantment enchantment) {
        Integer listed = s.copyWear.get(idOf(enchantment));
        return listed != null ? listed.intValue() : ((enchantment.isTreasureOnly() && !enchantment.isCurse()) ? s.autoTreasureWear : 0);
    }

    /** Encantamiento "fuerte" (con desgaste propio): no se puede copiar desde un libro, que no tiene durabilidad. */
    public static boolean isStrong(SetConfig.Settings s, Enchantment enchantment) {
        return s.copyWearEnabled && Math.round(strongPercent(s, enchantment) * s.copyWearMultiplier) > 0;
    }

    /** % de la durabilidad maxima que se pierde al COPIAR este encantamiento: el mayor entre el minimo base (30) y el suyo. */
    public static int wearPercent(SetConfig.Settings s, Enchantment enchantment) {
        if (!s.copyWearEnabled) return 0;
        return (int) Math.round(Math.max(s.copyBaseWear, strongPercent(s, enchantment)) * s.copyWearMultiplier);
    }

    /** El desgaste real: 0 si el objeto de origen no tiene durabilidad (por ejemplo un libro encantado). */
    public static int effectiveWear(SetConfig.Settings s, Enchantment enchantment, ItemStack source) {
        return source.isDamageableItem() ? wearPercent(s, enchantment) : 0;
    }

    /** Puntos de durabilidad que pierde el objeto de origen al copiar estos encantamientos. */
    public static int wearPoints(SetConfig.Settings s, ItemStack source, Map<Enchantment, Integer> moving) {
        if (!source.isDamageableItem()) return 0;
        int percent = 0;
        for (Enchantment e : moving.keySet()) percent += wearPercent(s, e);
        return percent <= 0 ? 0 : (int) Math.ceil(source.getMaxDamage() * percent / 100.0D);
    }

    /** El objeto debe sobrevivir con al menos 1 punto de durabilidad. */
    public static boolean canPayWear(SetConfig.Settings s, ItemStack source, Map<Enchantment, Integer> moving) {
        int points = wearPoints(s, source, moving);
        return points <= 0 || source.getDamageValue() + points < source.getMaxDamage();
    }

    /** 0 = se puede seleccionar; 1 = bloqueado por el servidor; 2 = de tesoro (no se copia); 3 = hace falta un objeto con durabilidad. */
    public static int denyReason(SetConfig.Settings s, Enchantment enchantment, ItemStack source, boolean copy) {
        if (isBlocked(s, enchantment)) return 1;
        if (!copy) return 0;
        if (!s.allowCopyTreasure && enchantment.isTreasureOnly() && !enchantment.isCurse()) return 2;
        if (isStrong(s, enchantment) && !source.isDamageableItem()) return 3;
        return 0;
    }

    public static String idOf(Enchantment e) { return String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(e)); }

    private final Container inputs = new SimpleContainer(2);
    private boolean finished = false;
    /** Copia de los ajustes del servidor (el cliente la usa para mostrar precios; el servidor cobra con los valores reales). */
    private SetConfig.Settings settings;

    /** Lado cliente: el contenido llega por sincronizacion de ranuras y los ajustes por el buffer de apertura. */
    public TransferMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, ItemStack.EMPTY, ItemStack.EMPTY, SettingsCodec.read(data));
    }

    /** Lado servidor. */
    public TransferMenu(int id, Inventory inventory, ItemStack tool, ItemStack book) {
        this(id, inventory, tool, book, SetConfig.Settings.fromConfig());
    }

    private TransferMenu(int id, Inventory inventory, ItemStack tool, ItemStack book, SetConfig.Settings settings) {
        super(ModMenus.TRANSFER_MENU.get(), id);
        this.settings = settings;
        inputs.setItem(0, tool);
        inputs.setItem(1, book);
        addSlot(new LockedSlot(inputs, 0, TOOL_X, SLOT_Y));
        addSlot(new LockedSlot(inputs, 1, BOOK_X, SLOT_Y));
    }

    private static class LockedSlot extends Slot {
        LockedSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }

    public SetConfig.Settings getSettings() { return settings; }
    public void setSettings(SetConfig.Settings settings) { this.settings = settings; }
    public ItemStack getSource() { return inputs.getItem(0); }

    public List<Map.Entry<Enchantment, Integer>> getEnchantments() {
        ItemStack source = inputs.getItem(0);
        if (source.isEmpty()) return new ArrayList<>();
        List<Map.Entry<Enchantment, Integer>> list = new ArrayList<>(EnchantmentHelper.getEnchantments(source).entrySet());
        list.sort(Comparator.comparing(e -> idOf(e.getKey())));
        return list;
    }

    /** Deja en el objeto solo los encantamientos de "remaining" (los libros guardan los suyos en otra etiqueta). */
    private static void applyRemaining(ItemStack stack, Map<Enchantment, Integer> remaining) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            stack.removeTagKey("StoredEnchantments");
            for (Map.Entry<Enchantment, Integer> e : remaining.entrySet())
                EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(e.getKey(), e.getValue()));
        } else {
            EnchantmentHelper.setEnchantments(remaining, stack);
        }
    }

    public void transferSelected(ServerPlayer player, List<String> selectedIds, boolean copy) {
        if (finished || selectedIds.isEmpty()) return;
        SetConfig.Settings s = SetConfig.Settings.fromConfig(); // siempre los valores reales del servidor
        if (copy && !s.allowCopy) return;
        ItemStack source = inputs.getItem(0);
        ItemStack book = inputs.getItem(1);
        if (source.isEmpty() || !book.is(Items.BOOK)) return;

        Map<Enchantment, Integer> all = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(source));
        Map<Enchantment, Integer> moving = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : all.entrySet()) {
            if (!selectedIds.contains(idOf(entry.getKey()))) continue;
            if (denyReason(s, entry.getKey(), source, copy) != 0) continue;
            moving.put(entry.getKey(), entry.getValue());
        }
        if (moving.isEmpty()) return;
        // un libro origen no puede quedarse sin ningun encantamiento al MOVER (seria un libro vacio)
        if (!copy && source.is(Items.ENCHANTED_BOOK) && moving.size() >= all.size()) return;
        if (copy && !canPayWear(s, source, moving)) {
            player.displayClientMessage(Component.translatable("message.selective_enchantment_transfer.not_enough_durability"), true);
            return;
        }

        int cost = totalCost(s, moving, copy);
        if (!player.getAbilities().instabuild && player.experienceLevel < cost) {
            player.displayClientMessage(Component.translatable("message.selective_enchantment_transfer.not_enough_xp"), true);
            return;
        }
        if (!player.getAbilities().instabuild && cost > 0) player.giveExperienceLevels(-cost);

        ItemStack newBook = new ItemStack(Items.ENCHANTED_BOOK);
        for (Map.Entry<Enchantment, Integer> entry : moving.entrySet())
            EnchantedBookItem.addEnchantment(newBook, new EnchantmentInstance(entry.getKey(), entry.getValue()));

        if (!copy) {
            Map<Enchantment, Integer> remaining = new LinkedHashMap<>(all);
            remaining.keySet().removeIf(moving::containsKey);
            applyRemaining(source, remaining);
        }

        if (copy) {
            int wear = wearPoints(s, source, moving);
            if (wear > 0) source.setDamageValue(source.getDamageValue() + wear);
        }

        inputs.setItem(0, ItemStack.EMPTY);
        inputs.setItem(1, ItemStack.EMPTY);
        finished = true;
        give(player, source);
        give(player, newBook);
        if (s.sounds) player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        ModAdvancements.award(player, "first_transfer");
        if (copy) ModAdvancements.award(player, "copy_cat");
        else if (source.is(Items.ENCHANTED_BOOK)) ModAdvancements.award(player, "split_book");
        if (cost >= 30 && !player.getAbilities().instabuild) ModAdvancements.award(player, "heavy_lifting");
        player.closeContainer();
    }

    /** Entrega un objeto al jugador: inventario si cabe, suelo si no (o si ya murio). */
    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        Inventory inv = player.getInventory();
        if (!player.isAlive() || (stack.isDamaged() && inv.getFreeSlot() < 0)) { player.drop(stack, false); return; }
        if (!inv.add(stack)) player.drop(stack, false);
    }

    /** Devuelve lo que quede dentro al jugador. Es seguro llamarlo varias veces. */
    public void returnItems(Player player) {
        for (int i = 0; i < inputs.getContainerSize(); i++) give(player, inputs.removeItemNoUpdate(i));
    }

    @Override public boolean stillValid(Player player) { return true; }

    /** Si se cierra sin confirmar, todo vuelve al jugador (y el nivel ya fue devuelto). */
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) returnItems(player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
