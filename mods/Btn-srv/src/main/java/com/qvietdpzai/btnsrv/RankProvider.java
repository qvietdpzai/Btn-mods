package com.qvietdpzai.btnsrv;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

/**
 * Lay rank/prefix cua nguoi choi.
 *
 * Thu tu uu tien:
 *  1. LuckPerms API (qua reflection - khong can bundle thu vien)
 *  2. Permission node "group.<ten>" (LuckPerms tu gan cac node nay)
 *  3. Prefix tuy chinh theo permission trong config
 *  4. Rong -> hien ten tran
 */
public final class RankProvider {

    private final BtnSrvPlugin plugin;
    private Object luckPerms;
    private Method lpGetUserManager;
    private boolean lpFailed;

    public RankProvider(BtnSrvPlugin plugin) {
        this.plugin = plugin;
    }

    /** Rank hien thi cho nguoi choi (vi du: "Admin", "VIP"). Rong neu khong xac dinh duoc. */
    public String rank(Player p) {
        if (!plugin.cfg().getBoolean("discord.show-rank", true)) return "";
        String r = fromLuckPerms(p);
        if (r != null && !r.isBlank()) return r;
        r = fromGroups(p);
        if (r != null && !r.isBlank()) return r;
        return fromConfigPrefix(p);
    }

    // ---------- 1. LuckPerms ----------

    private String fromLuckPerms(Player p) {
        if (lpFailed) return null;
        try {
            if (luckPerms == null) {
                Class<?> prov = Class.forName("net.luckperms.api.LuckPermsProvider");
                luckPerms = prov.getMethod("get").invoke(null);
                if (luckPerms == null) { lpFailed = true; return null; }
                lpGetUserManager = luckPerms.getClass().getMethod("getUserManager");
            }
            // user = luckPerms.getUserManager().getUser(uuid)
            Object um = lpGetUserManager.invoke(luckPerms);
            Object user = um.getClass().getMethod("getUser", java.util.UUID.class).invoke(um, p.getUniqueId());
            if (user == null) return null;

            // uu tien prefix cua group chinh
            Object groupName = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            String group = groupName == null ? "" : String.valueOf(groupName);

            String prefix = metaData(user).getString("prefix");
            if (prefix == null || prefix.isBlank()) return prettify(group);
            return strip(prefix);
        } catch (Throwable t) {
            if (!lpFailed) plugin.getLogger().log(Level.FINE, "Khong doc duoc LuckPerms API", t);
            lpFailed = true;   // khong thu lai, tranh spam log
            return null;
        }
    }

    private org.bukkit.configuration.ConfigurationSection metaData(Object user) {
        try {
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object meta = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefixObj = meta.getClass().getMethod("getPrefix").invoke(meta);
            org.bukkit.configuration.ConfigurationSection sec =
                    new org.bukkit.configuration.MemoryConfiguration();
            // tra ve dang "prefix" de goi nay doc duoc
            if (prefixObj instanceof String s && !s.isBlank()) sec.set("prefix", s);
            return sec;
        } catch (Throwable t) {
            return new org.bukkit.configuration.MemoryConfiguration();
        }
    }

    // ---------- 2. group.* permission ----------

    private String fromGroups(Player p) {
        List<String> groups = plugin.cfg().getStringList("rank.groups");
        for (String g : groups) {
            if (p.hasPermission("group." + g)) return prettify(g);
        }
        return null;
    }

    // ---------- 3. prefix theo permission trong config ----------

    private String fromConfigPrefix(Player p) {
        var sec = plugin.cfg().getConfigurationSection("rank.map");
        if (sec == null) return null;
        for (var e : sec.getValues(false).entrySet()) {
            if (p.hasPermission(e.getKey())) return String.valueOf(e.getValue());
        }
        return null;
    }

    // ---------- tien ich ----------

    /** "default" -> "Default", "vip_plus" -> "Vip Plus". */
    public static String prettify(String s) {
        if (s == null || s.isBlank()) return "";
        String[] parts = s.replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Bo ma mau hex/legacy + khoang trang. */
    public static String strip(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").replaceAll("§#[0-9a-fA-F]{6}", "");
        return t.replaceAll("\\s+", " ").trim();
    }

    /** Nguoi choi co online khong (de tranh lay rank khi da thoat). */
    public static Player online(String name) {
        return Bukkit.getPlayerExact(name);
    }
}
