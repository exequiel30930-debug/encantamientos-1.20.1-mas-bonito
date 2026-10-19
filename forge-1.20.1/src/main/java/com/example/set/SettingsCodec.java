package com.example.set;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;

/** Lectura/escritura de los ajustes por red. */
public final class SettingsCodec {
    private SettingsCodec() {}

    public static void write(FriendlyByteBuf buf, SetConfig.Settings s) {
        buf.writeBoolean(s.xpCostEnabled);
        buf.writeDouble(s.costMultiplier);
        buf.writeVarInt(s.maxTotalCost);
        buf.writeVarInt(s.minEnchantments);
        buf.writeBoolean(s.allowCopy);
        buf.writeDouble(s.copyCostMultiplier);
        buf.writeBoolean(s.allowCopyTreasure);
        buf.writeBoolean(s.sounds);
        buf.writeBoolean(s.copyWearEnabled);
        buf.writeDouble(s.copyWearMultiplier);
        buf.writeVarInt(s.autoTreasureWear);
        buf.writeVarInt(s.copyBaseWear);
        buf.writeVarInt(s.priceOverrides.size());
        for (Map.Entry<String, Double> e : s.priceOverrides.entrySet()) { buf.writeUtf(e.getKey(), 128); buf.writeDouble(e.getValue()); }
        buf.writeVarInt(s.blocked.size());
        for (String id : s.blocked) buf.writeUtf(id, 128);
        buf.writeVarInt(s.copyWear.size());
        for (Map.Entry<String, Integer> e : s.copyWear.entrySet()) { buf.writeUtf(e.getKey(), 128); buf.writeVarInt(e.getValue()); }
    }

    public static SetConfig.Settings read(FriendlyByteBuf buf) {
        SetConfig.Settings s = new SetConfig.Settings();
        s.xpCostEnabled = buf.readBoolean();
        s.costMultiplier = buf.readDouble();
        s.maxTotalCost = buf.readVarInt();
        s.minEnchantments = buf.readVarInt();
        s.allowCopy = buf.readBoolean();
        s.copyCostMultiplier = buf.readDouble();
        s.allowCopyTreasure = buf.readBoolean();
        s.sounds = buf.readBoolean();
        s.copyWearEnabled = buf.readBoolean();
        s.copyWearMultiplier = buf.readDouble();
        s.autoTreasureWear = buf.readVarInt();
        s.copyBaseWear = buf.readVarInt();
        s.priceOverrides.clear();
        int prices = limit(buf.readVarInt());
        for (int i = 0; i < prices; i++) s.priceOverrides.put(buf.readUtf(128), buf.readDouble());
        s.blocked.clear();
        int blocked = limit(buf.readVarInt());
        for (int i = 0; i < blocked; i++) s.blocked.add(buf.readUtf(128));
        s.copyWear.clear();
        int wear = limit(buf.readVarInt());
        for (int i = 0; i < wear; i++) s.copyWear.put(buf.readUtf(128), buf.readVarInt());
        s.clamp();
        return s;
    }

    private static int limit(int n) {
        if (n < 0 || n > 512) throw new IllegalArgumentException("Invalid list size: " + n);
        return n;
    }
}
