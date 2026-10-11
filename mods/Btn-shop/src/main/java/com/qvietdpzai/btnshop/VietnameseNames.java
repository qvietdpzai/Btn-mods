package com.qvietdpzai.btnshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Dich ten item sang tieng Viet giong ten goc: DIAMOND_SWORD -> "Kiem kim cuong".
 *
 * Uu tien:
 *   1. TEN_CHINH_XAC (ten goc -> ten Viet)
 *   2. TIEN_TO (GILDED -> "Cuong vang" o dau)
 *   3. TU_DIEN (tung tu dich roi ghep)
 *   4. khong co -> giu nguyen tieng Anh
 *
 * File tu dien: plugins/Btn-shop/vi.yml (sua duoc, /btnshop reload khong can build lai).
 */
public final class VietnameseNames {

    private final File file;
    private final Logger log;
    private final Map<String, String> words = new HashMap<>();
    private final Map<String, String> prefixes = new HashMap<>();
    private final Map<String, String> exact = new HashMap<>();
    private final java.util.Set<String> front = new java.util.HashSet<>();

    public VietnameseNames(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Nap tu dien tu file (da duoc copy tu jar). */
    public void load() {
        words.clear();
        prefixes.clear();
        exact.clear();
        if (!file.exists()) {
            if (log != null) log.warning("Khong thay vi.yml - giu ten tieng Anh.");
            return;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection w = y.getConfigurationSection("words");
        if (w != null) {
            for (String k : w.getKeys(false)) words.put(k.toUpperCase(), w.getString(k, ""));
        }
        ConfigurationSection p = y.getConfigurationSection("from-tien-to");
        if (p != null) {
            for (String k : p.getKeys(false)) prefixes.put(k.toUpperCase(), p.getString(k, ""));
        }
        ConfigurationSection f = y.getConfigurationSection("tu-dau");
        if (f != null) {
            for (String k : f.getKeys(false)) front.add(k.toUpperCase());
        }
        ConfigurationSection e = y.getConfigurationSection("tim-ten");
        if (e != null) {
            for (String k : e.getKeys(false)) exact.put(k.toUpperCase(), e.getString(k, ""));
        }
        if (log != null) log.info("Da nap tu dien tieng Viet: " + words.size() + " tu");
    }

    /** So tu trong tu dien (dung de test/debug). */
    public int wordCount() { return words.size(); }

    /** Ten hien thi cua material (vi du: DIAMOND_SWORD -> "Kiem kim cuong"). */
    public String apply(String material) {
        if (material == null || material.isBlank()) return "";
        String m = material.toUpperCase();

        // 1. ten chinh xac
        String e = exact.get(m);
        if (e != null && !e.isBlank()) return e;

        String[] parts = m.split("_");
        StringBuilder prefix = new StringBuilder();
        StringBuilder rest = new StringBuilder();

        // 2. tim danh tu chinh (TU_DAU) de dat len dau tien theo tieng Viet.
        //    VD: DIAMOND_SWORD -> "Kiem kim cuong" (sword la danh tu chinh)
        int headIdx = -1;
        for (int i = parts.length - 1; i >= 0; i--) {
            if (front.contains(parts[i])) { headIdx = i; break; }
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];

            // tien to o dau (GILDED, ENCHANTED...) - khong phai danh tu chinh
            if (i == 0) {
                String pre = prefixes.get(w);
                if (pre != null) { prefix.append(capitalize(pre)).append(' '); continue; }
            }

            String v = words.get(w);
            if (v == null) {
                v = lower(w);                 // khong co: giu nguyen tieng Anh
            } else if (v.isBlank()) {
                continue;                     // tu vung "the/of/and..." -> bo qua
            }
            v = capitalize(v);

            if (i == headIdx) {
                // danh tu chinh: ghi vao dau (neu chua co gi phia truoc)
                if (out.length() == 0) out.append(v);
                else { String cur = out.toString(); out.setLength(0); out.append(v).append(' ').append(cur); }
            } else if (out.length() > 0) {
                out.append(' ').append(v);
            } else {
                out.append(v);
            }
        }
        return prefix.toString() + out;
    }

    /** "kim cuong" -> "Kim cuong" (chi hoa chu dau). */
    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String lower(String s) {
        return s.substring(0, 1) + s.substring(1).toLowerCase();
    }
}
