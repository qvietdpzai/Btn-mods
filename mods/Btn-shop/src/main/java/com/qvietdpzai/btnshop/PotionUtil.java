package com.qvietdpzai.btnshop;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Áp dụng / so khớp potion type ("healing", "strong_healing", "long_swiftness"...). */
public final class PotionUtil {

    private PotionUtil() {}

    /** Tạo item từ material + loại potion (dùng khi mua). */
    public static ItemStack apply(ItemStack item, String potion) {
        if (potion == null || item == null) return item;
        PotionMeta meta = item.getItemMeta() instanceof PotionMeta pm ? pm : null;
        if (meta == null) return item;

        String p = potion.toLowerCase();
        boolean strong = p.startsWith("strong_");
        boolean longer = p.startsWith("long_");
        String base = p.replace("strong_", "").replace("long_", "").trim();
        int amp = strong ? 1 : 0;
        int dur = strong ? 1800 : longer ? 9600 : 3600; // ticks

        PotionEffectType type = effectOf(base);
        if (type == null) return item;

        boolean show = true;
        if (base.equals("healing") || base.equals("harming")) {
            meta.addCustomEffect(new PotionEffect(type, 1, amp, true, true, show), true);
        } else if (base.equals("turtle_master")) {
            meta.addCustomEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur, 2, true, true, show), true);
            meta.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 3, true, true, show), true);
        } else {
            meta.addCustomEffect(new PotionEffect(type, dur, amp, true, true, show), true);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Item này có khớp loại potion cấu hình không (dùng khi bán). */
    public static boolean matches(ItemStack it, String potion) {
        if (!(it.getItemMeta() instanceof PotionMeta pm) || pm.getCustomEffects().isEmpty()) return false;

        String p = potion.toLowerCase();
        boolean strong = p.startsWith("strong_");
        boolean longer = p.startsWith("long_");
        String base = p.replace("strong_", "").replace("long_", "").trim();
        int wantAmp = strong ? 1 : 0;

        PotionEffectType type = effectOf(base);
        if (type == null) return false;

        boolean effectOk = false, ampOk = false, durOk = false;
        for (PotionEffect e : pm.getCustomEffects()) {
            if (base.equals("turtle_master")) {
                if (e.getType().equals(PotionEffectType.RESISTANCE)) { effectOk = true; ampOk = true; }
                if (e.getType().equals(PotionEffectType.SLOWNESS)) durOk = true;
                continue;
            }
            if (e.getType().equals(type)) {
                effectOk = true;
                ampOk = e.getAmplifier() == wantAmp;
                if (base.equals("healing") || base.equals("harming")) durOk = true;
                else if (longer) durOk = e.getDuration() > 6000;
                else durOk = e.getDuration() <= 6000;
            }
        }
        return effectOk && ampOk && durOk;
    }

    /** Tên hiển thị cho potion. */
    public static String displayName(String potion) {
        String p = potion.toLowerCase();
        String tier = "";
        if (p.startsWith("strong_")) { tier = "Cấp 2 "; p = p.substring(7); }
        else if (p.startsWith("long_")) { tier = "Dài "; p = p.substring(5); }
        String name;
        switch (base(p)) {
            case "fire_resistance" -> name = "Chống lửa";
            case "harming" -> name = "Sát thương";
            case "healing" -> name = "Hồi máu";
            case "invisibility" -> name = "Tàng hình";
            case "leaping" -> name = "Nhảy cao";
            case "night_vision" -> name = "Nhìn đêm";
            case "poison" -> name = "Độc";
            case "regeneration" -> name = "Hồi sinh";
            case "slow_falling" -> name = "Rơi chậm";
            case "slowness" -> name = "Làm chậm";
            case "strength" -> name = "Sức mạnh";
            case "swiftness" -> name = "Tốc chạy";
            case "turtle_master" -> name = "Rùa thế";
            case "water_breathing" -> name = "Thở nước";
            case "weakness" -> name = "Suy yếu";
            default -> name = base(p);
        }
        return tier + name;
    }

    private static String base(String p) {
        if (p.startsWith("strong_")) return p.substring(7);
        if (p.startsWith("long_")) return p.substring(5);
        return p;
    }

    public static PotionEffectType effectOf(String base) {
        return switch (base) {
            case "fire_resistance" -> PotionEffectType.FIRE_RESISTANCE;
            case "harming" -> PotionEffectType.INSTANT_DAMAGE;
            case "healing" -> PotionEffectType.INSTANT_HEALTH;
            case "invisibility" -> PotionEffectType.INVISIBILITY;
            case "leaping" -> PotionEffectType.JUMP_BOOST;
            case "night_vision" -> PotionEffectType.NIGHT_VISION;
            case "poison" -> PotionEffectType.POISON;
            case "regeneration" -> PotionEffectType.REGENERATION;
            case "slow_falling" -> PotionEffectType.SLOW_FALLING;
            case "slowness" -> PotionEffectType.SLOWNESS;
            case "strength" -> PotionEffectType.STRENGTH;
            case "swiftness" -> PotionEffectType.SPEED;
            case "turtle_master" -> PotionEffectType.RESISTANCE;
            case "water_breathing" -> PotionEffectType.WATER_BREATHING;
            case "weakness" -> PotionEffectType.WEAKNESS;
            default -> null;
        };
    }
}
