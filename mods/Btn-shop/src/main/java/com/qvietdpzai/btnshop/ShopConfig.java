package com.qvietdpzai.btnshop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Đọc/ghi shops.yml: sections -> items phẳng (plugin tự phân trang). */
public final class ShopConfig {

    private final BtnShop plugin;
    private final File file;
    private final Map<String, Section> sections = new LinkedHashMap<>();

    public static final class Section {
        public String id;
        public String title;
        public String icon = "CHEST";
        public String iconName;
        public int slot = 10;
        public final List<ShopEntry> items = new ArrayList<>();

        Material iconMaterial() {
            Material m = Material.matchMaterial(icon);
            return m == null ? Material.CHEST : m;
        }
    }

    public ShopConfig(BtnShop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
    }

    public Map<String, Section> sections() { return sections; }

    public Section section(String id) {
        return sections.get(id);
    }

    /** Tìm section chứa item này. */
    public Section findSection(String material) {
        for (Section s : sections.values()) {
            for (ShopEntry e : s.items) {
                if (e.material.equalsIgnoreCase(material)) return s;
            }
        }
        return null;
    }

    public ShopEntry findEntry(String material) {
        Section s = findSection(material);
        if (s == null) return null;
        for (ShopEntry e : s.items) if (e.material.equalsIgnoreCase(material)) return e;
        return null;
    }

    public int totalItems() {
        int n = 0;
        for (Section s : sections.values()) n += s.items.size();
        return n;
    }

    public void load() {
        sections.clear();
        if (!file.exists()) {
            try { plugin.saveResource("shops.yml", false); } catch (IllegalArgumentException ignored) {}
        }
        if (!file.exists()) { plugin.getLogger().warning("Khong thay shops.yml - shop trong!"); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = y.getConfigurationSection("sections");
        if (root == null) { plugin.getLogger().warning("shops.yml thieu khoa 'sections'"); return; }

        for (String sid : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(sid);
            if (s == null) continue;
            Section sec = new Section();
            sec.id = sid;
            sec.title = s.getString("title", sid);
            sec.icon = s.getString("icon", "CHEST");
            sec.iconName = s.getString("icon-name", sec.title);
            sec.slot = s.getInt("slot", 10);
            List<?> raw = s.getList("items", new ArrayList<>());
            for (Object o : raw) {
                if (o instanceof Map<?, ?> map) {
                    Object mat = map.get("material");
                    if (mat == null) continue;
                    ShopEntry e = new ShopEntry();
                    e.material = String.valueOf(mat).toUpperCase();
                    Object b = map.get("buy"), sl = map.get("sell"), ss = map.get("stack-size");
                    e.buy = toDouble(b, -1);
                    e.sell = toDouble(sl, -1);
                    e.stackSize = (int) toDouble(ss, 0);
                    if (Material.matchMaterial(e.material) == null) continue; // bo item khong ton tai
                    sec.items.add(e);
                }
            }
            sections.put(sid, sec);
        }
        plugin.getLogger().info("Da tai " + sections.size() + " section / " + totalItems() + " item tu shops.yml");
    }

    private double toDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        }
        return def;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Section s : sections.values()) {
            String base = "sections." + s.id + ".";
            y.set(base + "title", s.title);
            y.set(base + "icon", s.icon);
            y.set(base + "icon-name", s.iconName);
            y.set(base + "slot", s.slot);
            List<Map<String, Object>> items = new ArrayList<>();
            for (ShopEntry e : s.items) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("material", e.material);
                m.put("buy", e.buy);
                m.put("sell", e.sell);
                if (e.stackSize > 0) m.put("stack-size", e.stackSize);
                items.add(m);
            }
            y.set(base + "items", items);
        }
        try { y.save(file); } catch (IOException e) {
            plugin.getLogger().warning("Khong luu duoc shops.yml: " + e.getMessage());
        }
    }
}
