package com.qvietdpzai.anticheat;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quan ly diem vi pham leo thang: lan 1 -> ban 1 phut, lan 2 -> 30 phut, lan 3 -> vinh vien.
 * Moi loai cheat co bo dem rieng (xray, speed, fly...). Luu duong ra file de khong mat khi restart.
 */
public final class PunishmentManager {

    private final BtnAnticheatPlugin plugin;
    private final File file;
    /** uuid -> (check -> so lan vi pham). ConcurrentHashMap: main thread ghi, task async doc khi save. */
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    public PunishmentManager(BtnAnticheatPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "violations.yml");
        load();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("punishments.enabled", true);
    }

    /** Xu ly 1 lan vi pham: tang dem va trung phat theo muc. Tra ve mo ta hinh phat (da ap dung) hoac null neu tat/chi dem. */
    public String handle(Player p, String check, String detail) {
        // van dem so lan vi pham de thong ke (giu du lieu de tinh chinh ve sau)
        int count = violations
                .computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>())
                .merge(check, 1, Integer::sum);
        names.put(p.getUniqueId(), p.getName());
        save();

        // che do chi ghi log: KHONG ban/kick bat ke action la gi
        if (!enabled()) {
            plugin.getLogger().info("[anticheat] " + p.getName() + " | " + check
                    + " | phat hien lan " + count + " (chi ghi log): " + detail);
            return null;
        }

        // chi ban khi du so lan bi phat hien; neu chua du -> chi ghi log
        int minFlags = Math.max(1, plugin.getConfig().getInt("punishments.flags-before-ban", 5));
        if (count < minFlags) {
            plugin.getLogger().info("[anticheat] " + p.getName() + " | " + check + " | phat hien lan " + count
                    + "/" + minFlags + " (chi ghi log): " + detail);
            return null;
        }
        int level = count - (minFlags - 1);   // 1 = lan phat thu nhat -> 1 phut

        String duration = durationFor(level);
        String serverName = plugin.getConfig().getString("punishments.ban-reason", "&cBan boi Btn-anticheat");
        String reason = ChatColor.translateAlternateColorCodes('&', serverName)
                + ChatColor.GRAY + " (" + check + "): " + detail
                + ChatColor.GRAY + " | Lan " + count;

        String prettyDuration;
        if (duration.equalsIgnoreCase("perm") || duration.equalsIgnoreCase("permanent")) {
            ban(p, reason, null);
            prettyDuration = "vinh vien";
        } else {
            long ms = parseDuration(duration);
            if (ms <= 0) { ban(p, reason, null); prettyDuration = "vinh vien"; }
            else {
                Date until = new Date(System.currentTimeMillis() + ms);
                ban(p, reason, until);
                prettyDuration = duration;
            }
        }

        String msg = ChatColor.RED + "[Btn-anticheat] Da ban " + ChatColor.YELLOW + p.getName()
                + ChatColor.RED + " (" + prettyDuration + ") - " + check + " lan " + level;
        plugin.getLogger().warning("BAN " + p.getName() + " " + prettyDuration + " | " + check + " lan phat " + level + " | " + detail);
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.hasPermission("btnanticheat.alerts")) op.sendMessage(msg);
        }
        plugin.logger().logRaw("[PUNISH] " + p.getName() + " | " + check + " | lan phat " + level
                + " (flag " + count + ") | ban " + prettyDuration + " | " + detail);
        return prettyDuration;
    }

    private void ban(Player p, String reason, Date expiry) {
        BanList list = Bukkit.getBanList(BanList.Type.NAME);
        list.addBan(p.getName(), ChatColor.stripColor(reason), expiry, "Btn-anticheat");
        p.kickPlayer(reason);
    }

    /** Mo ta thoi hạn cho so lan vi pham tu config. */
    public String durationFor(int count) {
        List<?> rules = plugin.getConfig().getList("punishments.rules");
        if (rules == null) return count >= 3 ? "perm" : count == 2 ? "30m" : "1m";
        for (Object o : rules) {
            if (o instanceof Map<?, ?> m) {
                Object c = m.get("count");
                Object d = m.get("duration");
                if (c instanceof Number n && d != null && n.intValue() == count) {
                    return String.valueOf(d);
                }
            }
        }
        return count >= 3 ? "perm" : count == 2 ? "30m" : "1m";
    }

    public static long parseDuration(String s) {
        if (s == null) return -1;
        String v = s.trim().toLowerCase();
        if (v.equals("perm") || v.equals("permanent") || v.equals("0")) return -1;
        try {
            char last = v.charAt(v.length() - 1);
            long mult;
            switch (last) {
                case 's': mult = 1000; break;
                case 'm': mult = 60000; break;
                case 'h': mult = 3600000; break;
                case 'd': mult = 86400000L; break;
                default: return -1;
            }
            return Long.parseLong(v.substring(0, v.length() - 1).trim()) * mult;
        } catch (Exception e) { return -1; }
    }

    public int count(UUID id, String check) {
        Map<String, Integer> m = violations.get(id);
        return m == null ? 0 : m.getOrDefault(check, 0);
    }

    public Map<String, Integer> allCounts(UUID id) {
        return violations.getOrDefault(id, Map.of());
    }

    public void reset(UUID id, String check) {
        Map<String, Integer> m = violations.get(id);
        if (m == null) return;
        if (check == null) m.clear();
        else m.remove(check);
        save();
    }

    public Map<UUID, Map<String, Integer>> all() { return violations; }

    // ---------- luu tru ----------

    private void save() {
        // snapshot: main thread van ghi vao map trong khi task async doc
        Map<UUID, Map<String, Integer>> snap = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> e : violations.entrySet()) {
            snap.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        Map<UUID, String> namesSnap = new HashMap<>(names);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<UUID, Map<String, Integer>> e : snap.entrySet()) {
                String base = e.getKey().toString();
                for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
                    y.set(base + "." + c.getKey(), c.getValue());
                }
                String name = namesSnap.get(e.getKey());
                if (name != null) y.set(base + ".name", name);
            }
            try { y.save(file); } catch (IOException ignored) { }
        });
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String uuidKey : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uuidKey);
                ConfigurationSection sec = y.getConfigurationSection(uuidKey);
                if (sec == null) continue;
                String name = sec.getString("name");
                if (name != null) names.put(id, name);
                Map<String, Integer> m = new ConcurrentHashMap<>();
                for (String k : sec.getKeys(false)) {
                    if (k.equals("name")) continue;
                    m.put(k, sec.getInt(k));
                }
                violations.put(id, m);
            } catch (IllegalArgumentException ignored) { }
        }
        plugin.getLogger().info("Da nap " + violations.size() + " ho so vi pham");
    }
}
