package com.qvietdpzai.btnshop;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Test logic Btn-shop khong can server that. */
public final class ShopTest {

    public static void main(String[] args) throws Exception {
        File dataDir = new File("/tmp/shopdata");
        dataDir.mkdirs();
        Logger log = Logger.getLogger("ShopTest");

        // ---- 1. tu dien tieng Viet ----
        VietnameseNames vi = new VietnameseNames(new File(dataDir, "vi.yml"), log);
        vi.load();
        System.out.println("=== 1. TU DIEN TIENG VIET ===");
        System.out.println("tu trong tu dien: " + vi.wordCount());
        System.out.println();
        String[] mats = {
            "DIAMOND_SWORD", "GOLDEN_APPLE", "OAK_PLANKS", "STONE_BRICKS", "SPRUCE_SLAB",
            "WHITE_WOOL", "TIPPED_ARROW", "SPLASH_POTION", "MANGROVE_STAIRS",
            "DIAMOND_HORSE_ARMOR", "NETHERITE_INGOT", "AMETHYST_SHARD", "SCULK_CATALYST",
            "CHERRY_LOG", "TRIAL_KEY", "MUSIC_DISC_CAT", "OBSIDIAN"
        };
        for (String m : mats) System.out.printf("  %-22s -> %s%n", m, vi.apply(m));

        // ---- 2. shops.yml: slot + dem item ----
        System.out.println("\n=== 2. SHOPS.YML ===");
        File sf = new File(dataDir, "shops.yml");
        if (!sf.exists()) {
            System.out.println("  (chua co shops.yml trong " + dataDir + ")");
            System.out.println("  -> copy tu resources bang lenh build truoc khi test");
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(sf);
        Map<Integer, List<String>> bySlot = new TreeMap<>();
        int total = 0;
        for (String sec : y.getConfigurationSection("sections").getKeys(false)) {
            var s = y.getConfigurationSection("sections." + sec);
            bySlot.computeIfAbsent(s.getInt("slot", -1), k -> new ArrayList<>()).add(sec);
            List<?> it = s.getList("items", new ArrayList<>());
            total += it.size();
        }
        boolean dup = bySlot.values().stream().anyMatch(l -> l.size() > 1);
        System.out.println("  section: " + bySlot.values().stream().mapToInt(List::size).sum()
                + " | item: " + total);
        System.out.println("  TRUNG SLOT: " + (dup ? "!!! CO (LOI)" : "khong"));
        for (var e : bySlot.entrySet()) {
            System.out.printf("  slot %2d : %s%n", e.getKey(), e.getKey());
        }

        // ---- 3. ten section tieng Viet ----
        System.out.println("\n=== 3. TEN SECTION (GUI chinh) ===");
        for (String sec : y.getConfigurationSection("sections").getKeys(false)) {
            var s = y.getConfigurationSection("sections." + sec);
            System.out.printf("  %-16s -> '%s'%n", sec, s.getString("title"));
        }
    }
}

