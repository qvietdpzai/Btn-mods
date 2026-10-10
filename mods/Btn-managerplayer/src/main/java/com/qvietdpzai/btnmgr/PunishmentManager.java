package com.qvietdpzai.btnmgr;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Quản lý ban (có thời hạn) và mute. Lưu dạng YAML, tự hết hạn. */
public class PunishmentManager {

    public static final long PERMANENT = -1;

    private final BtnManagerPlayer plugin;
    private final File banFile, muteFile;
    private final Map<UUID, Punishment> bans = new HashMap<>();
    private final Map<UUID, Punishment> mutes = new HashMap<>();

    public static final class Punishment {
        public String name, reason, by;
        public long until; // PERMANENT = vĩnh viễn
        public long createdAt;
        public boolean isActive(long now) { return until == PERMANENT || until > now; }
        public String left(long now) {
            if (until == PERMANENT) return "vi~nh vie~n";
            long s = Math.max(0, until - now) / 1000;
            long h = s / 3600, m = (s % 3600) / 60;
            return h > 0 ? h + "h" + m + "p" : m + "p";
        }
    }

    public PunishmentManager(BtnManagerPlayer plugin) {
        this.plugin = plugin;
        this.banFile = new File(plugin.getDataFolder(), "punishments/bans.yml");
        this.muteFile = new File(plugin.getDataFolder(), "punishments/mutes.yml");
        plugin.getDataFolder().mkdirs();
        new File(plugin.getDataFolder(), "punishments").mkdirs();
        load();
    }

    private void load() {
        loadInto(banFile, bans);
        loadInto(muteFile, mutes);
    }

    private void loadInto(File f, Map<UUID, Punishment> map) {
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            Punishment p = new Punishment();
            p.name = s.getString("name", key);
            p.reason = s.getString("reason", "Khong co ly do");
            p.by = s.getString("by", "CONSOLE");
            p.until = s.getLong("until", PERMANENT);
            p.createdAt = s.getLong("created-at", System.currentTimeMillis());
            try { map.put(UUID.fromString(key), p); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveInto(File f, Map<UUID, Punishment> map) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Punishment> e : map.entrySet()) {
            String k = e.getKey().toString();
            Punishment p = e.getValue();
            y.set(k + ".name", p.name);
            y.set(k + ".reason", p.reason);
            y.set(k + ".by", p.by);
            y.set(k + ".until", p.until);
            y.set(k + ".created-at", p.createdAt);
        }
        try { y.save(f); } catch (IOException e) {
            plugin.getLogger().warning("Khong luu duoc punishments: " + e.getMessage());
        }
    }

    public void saveAll() {
        saveInto(banFile, bans);
        saveInto(muteFile, mutes);
    }

    /** Xoá các hình thức đã hết hạn (gọi định kỳ). */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        changed |= bans.entrySet().removeIf(e -> !e.getValue().isActive(now));
        changed |= mutes.entrySet().removeIf(e -> !e.getValue().isActive(now));
        if (changed) saveAll();
    }

    // ---------- BAN ----------
    public String ban(UUID id, String name, long durationMs, String reason, String by) {
        Punishment p = new Punishment();
        p.name = name;
        p.reason = reason;
        p.by = by;
        p.createdAt = System.currentTimeMillis();
        p.until = durationMs == PERMANENT ? PERMANENT : System.currentTimeMillis() + durationMs;
        bans.put(id, p);
        saveAll();
        return p.until == PERMANENT ? "vi~nh vie~n" : durationMs / 1000 + "s";
    }

    public boolean unban(String name) {
        UUID id = resolve(name);
        Punishment removed = id == null ? null : bans.remove(id);
        if (removed != null) { saveAll(); return true; }
        return false;
    }

    public Punishment getBan(UUID id) { return bans.get(id); }

    public String banInfo(UUID id, long now) {
        Punishment p = bans.get(id);
        if (p == null || !p.isActive(now)) return null;
        return "BAN " + p.left(now) + " | " + p.reason + " | boi " + p.by;
    }

    public Map<UUID, Punishment> allBans() { return bans; }

    // ---------- MUTE ----------
    public String mute(UUID id, String name, long durationMs, String reason, String by) {
        Punishment p = new Punishment();
        p.name = name;
        p.reason = reason;
        p.by = by;
        p.createdAt = System.currentTimeMillis();
        p.until = durationMs == PERMANENT ? PERMANENT : System.currentTimeMillis() + durationMs;
        mutes.put(id, p);
        saveAll();
        return p.until == PERMANENT ? "vi~nh vie~n" : durationMs / 1000 + "s";
    }

    public boolean unmute(String name) {
        UUID id = resolve(name);
        Punishment removed = id == null ? null : mutes.remove(id);
        if (removed != null) { saveAll(); return true; }
        return false;
    }

    public String muteInfo(UUID id, long now) {
        Punishment p = mutes.get(id);
        if (p == null || !p.isActive(now)) return null;
        return "MUTE " + p.left(now) + " | " + p.reason + " | boi " + p.by;
    }

    public Map<UUID, Punishment> allMutes() { return mutes; }

    private UUID resolve(String name) {
        for (UUID id : bans.keySet()) if (bans.get(id).name.equalsIgnoreCase(name)) return id;
        for (UUID id : mutes.keySet()) if (mutes.get(id).name.equalsIgnoreCase(name)) return id;
        return org.bukkit.Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    /** Parse "30m", "2h", "3d", "1w", "perm" -> ms; PERMANENT hoặc -1 nếu sai. */
    public static long parseDuration(String s) {
        if (s == null) return -1;
        String v = s.trim().toLowerCase();
        if (v.equals("perm") || v.equals("permanent") || v.equals("-") || v.equals("0")) return PERMANENT;
        try {
            long mult = 1;
            char last = v.charAt(v.length() - 1);
            String num = v;
            switch (last) {
                case 's': mult = 1000; break;
                case 'm': mult = 60000; break;
                case 'h': mult = 3600000; break;
                case 'd': mult = 86400000L; break;
                case 'w': mult = 604800000L; break;
                default: return -1;
            }
            num = v.substring(0, v.length() - 1).trim();
            long n = Long.parseLong(num);
            return n > 0 ? n * mult : PERMANENT;
        } catch (Exception e) { return -1; }
    }
}
