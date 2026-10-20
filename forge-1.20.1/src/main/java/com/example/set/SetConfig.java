package com.example.set;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configuracion del servidor (archivo serverconfig/selective_enchantment_transfer-server.toml).
 * Los ajustes simples tambien se cambian dentro del juego (boton "Ajustes" o atajo de teclado);
 * las listas (precios por encantamiento, bloqueados, desgaste) se editan en el archivo.
 */
public final class SetConfig {
    /** Desgaste por defecto de los encantamientos fuertes (% de la durabilidad maxima por encantamiento copiado; el resto usa copyBaseWear = 30). */
    private static final List<String> DEFAULT_WEAR = Arrays.asList(
            // vanilla: Reparacion y los que estan a su nivel
            "minecraft:mending=35",
            "minecraft:infinity=35",
            "minecraft:soul_speed=35",
            "minecraft:swift_sneak=35",
            "minecraft:frost_walker=35",
            // Apotheosis (los identificadores varian algo entre versiones; los que no existan se ignoran)
            "apotheosis:life_mending=35",
            "apotheosis:knowledge=35",
            "apotheosis:endless_quiver=35",
            "apotheosis:true_infinity=35",
            "apotheosis:berserkers_fury=35",
            "apotheosis:scavenger=35",
            "apotheosis:crescendo=35",
            "apotheosis:chainsaw=35",
            "apotheosis:earths_boon=35");

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue XP_COST_ENABLED;
    private static final ForgeConfigSpec.DoubleValue COST_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue MAX_TOTAL_COST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> PRICE_OVERRIDES;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED;
    private static final ForgeConfigSpec.IntValue MIN_ENCHANTMENTS;
    private static final ForgeConfigSpec.BooleanValue ALLOW_COPY;
    private static final ForgeConfigSpec.DoubleValue COPY_COST_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue ALLOW_COPY_TREASURE;
    private static final ForgeConfigSpec.BooleanValue COPY_WEAR_ENABLED;
    private static final ForgeConfigSpec.DoubleValue COPY_WEAR_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue AUTO_TREASURE_WEAR;
    private static final ForgeConfigSpec.IntValue COPY_BASE_WEAR;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> COPY_WEAR;
    private static final ForgeConfigSpec.BooleanValue SOUNDS;
    private static final ForgeConfigSpec.BooleanValue RESET_PRIOR_WORK;
    private static final ForgeConfigSpec.BooleanValue DEBUG_LOG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("costs");
        XP_COST_ENABLED = b.comment("If false, transferring enchantments costs no experience levels.")
                .define("xpCostEnabled", true);
        COST_MULTIPLIER = b.comment("Multiplier for the price of each enchantment (1.0 = default balance, 0 = free).")
                .defineInRange("costMultiplier", 1.0D, 0.0D, 10.0D);
        MAX_TOTAL_COST = b.comment("Maximum total cost, in levels, of a single transfer.")
                .defineInRange("maxTotalCost", 39, 1, 1000);
        b.pop();
        b.push("enchantments");
        PRICE_OVERRIDES = b.comment("Custom price per level for specific enchantments (works with enchantments from any mod).",
                        "Format: \"namespace:enchantment=price per level\"; the price replaces the automatic one (price = value x level).",
                        "0 = free. Example: [\"minecraft:mending=12\", \"minecraft:sharpness=0.5\"]")
                .defineList("priceOverrides", new ArrayList<String>(), o -> o instanceof String);
        BLOCKED = b.comment("Enchantments that can never be moved or copied (works with enchantments from any mod).",
                        "Example: [\"minecraft:mending\", \"apotheosis:knowledge\"]")
                .defineList("blockedEnchantments", new ArrayList<String>(), o -> o instanceof String);
        b.pop();
        b.push("transfer");
        MIN_ENCHANTMENTS = b.comment("Minimum number of enchantments an item needs for this mod to take over the anvil.")
                .defineInRange("minEnchantments", 2, 1, 10);
        b.pop();
        b.push("copy");
        ALLOW_COPY = b.comment("Enable the 'copy' mode: the source keeps its enchantments and the new book gets a copy (costs more).")
                .define("allowCopy", true);
        COPY_COST_MULTIPLIER = b.comment("Copy mode costs this many times the normal transfer price.")
                .defineInRange("copyCostMultiplier", 3.0D, 1.0D, 10.0D);
        ALLOW_COPY_TREASURE = b.comment("Allow copying treasure-only enchantments (Mending, Frost Walker, Soul Speed, Swift Sneak...).",
                        "They always wear the item down (see copyWear / autoTreasureWear), so copying them is a real sacrifice.")
                .define("allowCopyTreasure", true);
        COPY_WEAR_ENABLED = b.comment("Copying strong enchantments wears down the durability of the source item.")
                .define("copyWearEnabled", true);
        COPY_WEAR_MULTIPLIER = b.comment("Multiplier for all wear values (1.0 = as listed below).")
                .defineInRange("copyWearMultiplier", 1.0D, 0.0D, 5.0D);
        COPY_BASE_WEAR = b.comment("Wear applied to ANY copied enchantment: % of the source item's max durability per enchantment (tools and armor; enchanted books have no durability).",
                        "Strong enchantments (copyWear list / treasure ones) use the LARGER of this value and their own.",
                        "The item must survive with at least 1 durability, otherwise the copy is refused.")
                .defineInRange("copyBaseWear", 30, 0, 100);
        AUTO_TREASURE_WEAR = b.comment("Wear (% of max durability) applied to treasure-only enchantments that are NOT listed in copyWear.",
                        "Covers treasure enchantments added by other mods. 0 = no automatic wear.")
                .defineInRange("autoTreasureWear", 35, 0, 100);
        COPY_WEAR = b.comment("Extra wear for STRONG enchantments, as a percentage of the source item's maximum durability (the larger of this and copyBaseWear is used).",
                        "Format: \"namespace:enchantment=percent\". Items without durability (enchanted books) cannot copy these enchantments.")
                .defineList("copyWear", DEFAULT_WEAR, o -> o instanceof String);
        b.pop();
        b.push("anvil");
        RESET_PRIOR_WORK = b.comment("Whatever you take out of ANY anvil operation gets its 'prior work' penalty reset to 0, so repairs never get more expensive.",
                        "(Vanilla's 'Too Expensive!' limit is hardcoded in the anvil and cannot be removed from here.)")
                .define("resetPriorWork", false);
        b.pop();
        b.push("visuals");
        SOUNDS = b.comment("Play the anvil sound when a transfer is completed.")
                .define("sounds", true);
        b.pop();
        b.push("debug");
        DEBUG_LOG = b.comment("Write to the game log why the anvil accepts or rejects each item + book combination (useful to diagnose conflicts with other mods).")
                .define("debugLog", false);
        b.pop();
        SPEC = b.build();
    }

    private SetConfig() {}

    // Los valores solo se pueden leer con la config cargada (dentro de un mundo); si no, valores por defecto.
    public static boolean xpCostEnabled() { try { return XP_COST_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static double costMultiplier() { try { return COST_MULTIPLIER.get(); } catch (IllegalStateException e) { return 1.0D; } }
    public static int maxTotalCost() { try { return MAX_TOTAL_COST.get(); } catch (IllegalStateException e) { return 39; } }
    public static int minEnchantments() { try { return MIN_ENCHANTMENTS.get(); } catch (IllegalStateException e) { return 2; } }
    public static boolean allowCopy() { try { return ALLOW_COPY.get(); } catch (IllegalStateException e) { return true; } }
    public static double copyCostMultiplier() { try { return COPY_COST_MULTIPLIER.get(); } catch (IllegalStateException e) { return 3.0D; } }
    public static boolean allowCopyTreasure() { try { return ALLOW_COPY_TREASURE.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean debugLog() { try { return DEBUG_LOG.get(); } catch (IllegalStateException e) { return false; } }
    public static boolean resetPriorWork() { try { return RESET_PRIOR_WORK.get(); } catch (IllegalStateException e) { return false; } }
    public static boolean soundsEnabled() { try { return SOUNDS.get(); } catch (IllegalStateException e) { return true; } }

    private static List<? extends String> listValue(ForgeConfigSpec.ConfigValue<List<? extends String>> value, List<? extends String> fallback) {
        try { return value.get(); } catch (IllegalStateException e) { return fallback; }
    }

    // ---------- lectura/escritura de las listas "namespace:id=valor" ----------
    /** "mending" -> "minecraft:mending"; todo en minusculas. */
    public static String normalizeId(String raw) {
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static Map<String, Double> parseDoubles(List<? extends String> list) {
        Map<String, Double> map = new LinkedHashMap<String, Double>();
        for (String entry : list) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            try { map.put(normalizeId(entry.substring(0, eq)), Double.parseDouble(entry.substring(eq + 1).trim())); }
            catch (NumberFormatException ignored) { /* entrada invalida: se ignora */ }
        }
        return map;
    }

    private static Map<String, Integer> parseInts(List<? extends String> list) {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Double> e : parseDoubles(list).entrySet()) map.put(e.getKey(), (int) Math.round(e.getValue()));
        return map;
    }

    private static Set<String> parseIds(List<? extends String> list) {
        Set<String> set = new LinkedHashSet<String>();
        for (String entry : list) if (!entry.trim().isEmpty()) set.add(normalizeId(entry));
        return set;
    }

    private static String num(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    private static List<String> doublesToList(Map<String, Double> map) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Double> e : map.entrySet()) out.add(e.getKey() + "=" + num(e.getValue()));
        return out;
    }

    private static List<String> intsToList(Map<String, Integer> map) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : map.entrySet()) out.add(e.getKey() + "=" + e.getValue());
        return out;
    }

    /** Copia editable de todos los ajustes (se envia por red y la usa el menu de ajustes). */
    public static final class Settings {
        public boolean xpCostEnabled = true;
        public double costMultiplier = 1.0D;
        public int maxTotalCost = 39;
        public int minEnchantments = 2;
        public boolean allowCopy = true;
        public double copyCostMultiplier = 3.0D;
        public boolean allowCopyTreasure = true;
        public boolean copyWearEnabled = true;
        public double copyWearMultiplier = 1.0D;
        public int autoTreasureWear = 35;
        public int copyBaseWear = 30;
        public boolean sounds = true;
        public boolean resetPriorWork = false;
        /** id del encantamiento -> precio por nivel (sustituye al automatico). */
        public Map<String, Double> priceOverrides = new LinkedHashMap<String, Double>();
        /** encantamientos que no se pueden mover ni copiar. */
        public Set<String> blocked = new LinkedHashSet<String>();
        /** id del encantamiento -> % de durabilidad maxima que pierde el objeto al copiarlo. */
        public Map<String, Integer> copyWear = parseInts(DEFAULT_WEAR);

        public static Settings defaults() { return new Settings(); }

        /** Valores actuales de la config. */
        public static Settings fromConfig() {
            Settings s = new Settings();
            s.xpCostEnabled = xpCostEnabled();
            s.costMultiplier = costMultiplier();
            s.maxTotalCost = maxTotalCost();
            s.minEnchantments = minEnchantments();
            s.allowCopy = allowCopy();
            s.copyCostMultiplier = copyCostMultiplier();
            s.allowCopyTreasure = allowCopyTreasure();
            try { s.copyWearEnabled = COPY_WEAR_ENABLED.get(); } catch (IllegalStateException e) { /* por defecto */ }
            try { s.copyWearMultiplier = COPY_WEAR_MULTIPLIER.get(); } catch (IllegalStateException e) { /* por defecto */ }
            try { s.autoTreasureWear = AUTO_TREASURE_WEAR.get(); } catch (IllegalStateException e) { /* por defecto */ }
            try { s.copyBaseWear = COPY_BASE_WEAR.get(); } catch (IllegalStateException e) { /* por defecto */ }
            s.sounds = soundsEnabled();
            s.resetPriorWork = resetPriorWork();
            s.priceOverrides = parseDoubles(listValue(PRICE_OVERRIDES, new ArrayList<String>()));
            s.blocked = parseIds(listValue(BLOCKED, new ArrayList<String>()));
            s.copyWear = parseInts(listValue(COPY_WEAR, DEFAULT_WEAR));
            return s;
        }

        public Settings copy() { Settings s = new Settings(); s.copyFrom(this); return s; }

        public void copyFrom(Settings o) {
            xpCostEnabled = o.xpCostEnabled; costMultiplier = o.costMultiplier; maxTotalCost = o.maxTotalCost;
            minEnchantments = o.minEnchantments; allowCopy = o.allowCopy;
            copyCostMultiplier = o.copyCostMultiplier; allowCopyTreasure = o.allowCopyTreasure;
            copyWearEnabled = o.copyWearEnabled; copyWearMultiplier = o.copyWearMultiplier; autoTreasureWear = o.autoTreasureWear; copyBaseWear = o.copyBaseWear;
            sounds = o.sounds; resetPriorWork = o.resetPriorWork;
            priceOverrides = new LinkedHashMap<String, Double>(o.priceOverrides);
            blocked = new LinkedHashSet<String>(o.blocked);
            copyWear = new LinkedHashMap<String, Integer>(o.copyWear);
        }

        /** Limita los valores a los rangos validos (el servidor nunca se fia de lo que llega por red). */
        public void clamp() {
            costMultiplier = Math.max(0.0D, Math.min(10.0D, costMultiplier));
            copyCostMultiplier = Math.max(1.0D, Math.min(10.0D, copyCostMultiplier));
            copyWearMultiplier = Math.max(0.0D, Math.min(5.0D, copyWearMultiplier));
            maxTotalCost = Math.max(1, Math.min(1000, maxTotalCost));
            minEnchantments = Math.max(1, Math.min(10, minEnchantments));
            autoTreasureWear = Math.max(0, Math.min(100, autoTreasureWear));
            copyBaseWear = Math.max(0, Math.min(100, copyBaseWear));
            for (Map.Entry<String, Double> e : priceOverrides.entrySet()) e.setValue(Math.max(0.0D, Math.min(1000.0D, e.getValue())));
            for (Map.Entry<String, Integer> e : copyWear.entrySet()) e.setValue(Math.max(0, Math.min(100, e.getValue())));
            trim(priceOverrides.keySet().iterator(), 256);
            trim(blocked.iterator(), 256);
            trim(copyWear.keySet().iterator(), 256);
        }

        private static void trim(Iterator<String> it, int max) {
            int n = 0;
            while (it.hasNext()) { it.next(); if (++n > max) it.remove(); }
        }

        /** Escribe los valores en la config del servidor y guarda el archivo. */
        public void apply() {
            try {
                XP_COST_ENABLED.set(xpCostEnabled);
                COST_MULTIPLIER.set(costMultiplier);
                MAX_TOTAL_COST.set(maxTotalCost);
                MIN_ENCHANTMENTS.set(minEnchantments);
                ALLOW_COPY.set(allowCopy);
                COPY_COST_MULTIPLIER.set(copyCostMultiplier);
                ALLOW_COPY_TREASURE.set(allowCopyTreasure);
                COPY_WEAR_ENABLED.set(copyWearEnabled);
                COPY_WEAR_MULTIPLIER.set(copyWearMultiplier);
                AUTO_TREASURE_WEAR.set(autoTreasureWear);
                COPY_BASE_WEAR.set(copyBaseWear);
                SOUNDS.set(sounds);
                RESET_PRIOR_WORK.set(resetPriorWork);
                PRICE_OVERRIDES.set(doublesToList(priceOverrides));
                BLOCKED.set(new ArrayList<String>(blocked));
                COPY_WEAR.set(intsToList(copyWear));
                SPEC.save();
            } catch (RuntimeException e) {
                // config aun no cargada: no hay nada que guardar
            }
        }
    }
}
