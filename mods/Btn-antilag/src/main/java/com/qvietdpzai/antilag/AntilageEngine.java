package com.qvietdpzai.antilag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Btn-antilag: giam tai server - don item rac, gioi han quai, view-distance linh hoat. */
public final class AntilageEngine implements Listener {

    private final BtnAntilag plugin;
    private int clearedItems, clearedMobs, cleanedVehicles;
    private double tps = 20;
    private int currentViewDistance = -1;
    private long lowTpsSince = 0;

    public AntilageEngine(BtnAntilag plugin) { this.plugin = plugin; }

    public void start() {
        long interval = Math.max(20, plugin.getConfig().getLong("cleanup.interval-seconds", 120)) * 20L;
        new BukkitRunnable() {
            @Override public void run() { cycle(); }
        }.runTaskTimer(plugin, interval, interval);

        if (plugin.getConfig().getBoolean("view-distance.dynamic", false)) {
            new BukkitRunnable() {
                @Override public void run() { dynamicViewDistance(); }
            }.runTaskTimer(plugin, 100L, 100L);
        }
    }

    private void cycle() {
        tps = tps();
        clearOldItems();
        limitMobs();
        cleanVehicles();
        report();
    }

    // ---------- 1. Don item rac tren dat ----------

    private void clearOldItems() {
        if (!plugin.getConfig().getBoolean("cleanup.items.enabled", true)) return;
        int minAge = plugin.getConfig().getInt("cleanup.items.min-age-seconds", 60);
        boolean broadcast = plugin.getConfig().getBoolean("cleanup.items.broadcast-warning", true);
        String warnRaw = plugin.getConfig().getString("cleanup.items.warning-message",
                "&e[Antilag] &fSe don item rac tren dat sau &c10 giay&f!");
        String doneRaw = plugin.getConfig().getString("cleanup.items.done-message",
                "&e[Antilag] &fDa don &c{N} &fitem rac.");

        new BukkitRunnable() {
            @Override public void run() { doClearItems(minAge, broadcast, doneRaw); }
        }.runTaskLater(plugin, 200L);   // 10 giay sau thi don

        if (broadcast) {
            String msg = ChatColor.translateAlternateColorCodes('&', warnRaw);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("btnantilag.alerts")) p.sendMessage(msg);
            }
        }
    }

    private void doClearItems(int minAge, boolean broadcast, String doneRaw) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof Item item)) continue;
                if (item.getTicksLived() < minAge * 20) continue;
                if (isProtected(item)) continue;
                item.remove();
                removed++;
            }
        }
        clearedItems += removed;
        if (removed > 0) {
            plugin.getLogger().info("[Antilag] Da don " + removed + " item rac tren dat");
            if (broadcast) {
                String msg = ChatColor.translateAlternateColorCodes('&',
                        doneRaw.replace("{N}", String.valueOf(removed)));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("btnantilag.alerts")) p.sendMessage(msg);
                }
            }
        }
    }

    private boolean isProtected(Item item) {
        if (plugin.getConfig().getBoolean("cleanup.items.protect-named-items", true)
                && item.getItemStack().hasItemMeta()
                && item.getItemStack().getItemMeta().hasDisplayName()) return true;
        return plugin.getConfig().getStringList("cleanup.items.protect-items")
                .contains(item.getItemStack().getType().name());
    }

    // ---------- 2. Gioi han quai ----------

    private void limitMobs() {
        if (!plugin.getConfig().getBoolean("cleanup.mobs.enabled", false)) return;
        int limit = plugin.getConfig().getInt("cleanup.mobs.per-world-limit", 300);
        for (World w : Bukkit.getWorlds()) {
            List<LivingEntity> mobs = new ArrayList<>();
            for (Entity e : w.getEntities()) {
                if (e instanceof LivingEntity le && !(e instanceof Player) && le.isValid()) mobs.add(le);
            }
            if (mobs.size() <= limit) continue;
            // xoa quai xa nguoi choi nhat truoc
            mobs.sort((a, b) -> Double.compare(nearestPlayerDist(b), nearestPlayerDist(a)));
            int toRemove = mobs.size() - limit;
            for (int i = 0; i < toRemove && i < mobs.size(); i++) {
                mobs.get(i).remove();
                clearedMobs++;
            }
            plugin.getLogger().info("[Antilag] World " + w.getName() + ": xoa " + toRemove
                    + " quai vuot gioi han " + limit);
        }
    }

    private double nearestPlayerDist(Entity e) {
        double best = Double.MAX_VALUE;
        for (Player p : e.getWorld().getPlayers()) {
            best = Math.min(best, e.getLocation().distanceSquared(p.getLocation()));
        }
        return best;
    }

    // ---------- 3. Gom xe/boat bo trong ----------

    private void cleanVehicles() {
        if (!plugin.getConfig().getBoolean("cleanup.vehicles.enabled", true)) return;
        int maxAge = plugin.getConfig().getInt("cleanup.vehicles.max-age-ticks", 24000);
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Vehicle v && v.getPassengers().isEmpty() && e.getTicksLived() > maxAge) {
                    e.remove();
                    cleanedVehicles++;
                }
            }
        }
    }

    // ---------- 4. View distance linh hoat ----------

    private void dynamicViewDistance() {
        double lowAt = plugin.getConfig().getDouble("view-distance.low-tps", 17.0);
        int lowVd = plugin.getConfig().getInt("view-distance.low", 3);
        int normalVd = plugin.getConfig().getInt("view-distance.normal", 6);
        if (currentViewDistance < 0) currentViewDistance = normalVd;
        double t = tps();

        if (t < lowAt) {
            if (lowTpsSince == 0) lowTpsSince = System.currentTimeMillis();
            long after = plugin.getConfig().getLong("view-distance.switch-after-seconds", 30) * 1000;
            if (System.currentTimeMillis() - lowTpsSince > after && currentViewDistance != lowVd) {
                for (World w : Bukkit.getWorlds()) setViewDistance(w, lowVd);
                currentViewDistance = lowVd;
                plugin.getLogger().warning("[Antilag] TPS thap (" + fmt(t) + ") -> giam view-distance xuong " + lowVd);
            }
        } else {
            lowTpsSince = 0;
            if (currentViewDistance != normalVd) {
                for (World w : Bukkit.getWorlds()) setViewDistance(w, normalVd);
                currentViewDistance = normalVd;
                plugin.getLogger().info("[Antilag] TPS phuc hoi -> tra view-distance ve " + normalVd);
            }
        }
    }

    /** World#setViewDistance chi co tren Paper - goi bang reflection. */
    private void setViewDistance(World w, int vd) {
        try {
            w.getClass().getMethod("setViewDistance", int.class).invoke(w, vd);
        } catch (Exception e) {
            plugin.getLogger().warning("Khong the doi view-distance (chi chay tren Paper): " + e.getCause());
        }
    }

    // ---------- tien ich ----------

    public double tps() {
        try {
            var m = plugin.getServer().getClass().getMethod("getTPS");
            double[] arr = (double[]) m.invoke(plugin.getServer());
            return arr != null && arr.length > 0 ? Math.min(20, arr[0]) : 20;
        } catch (Exception e) { return 20; }
    }

    private void report() {
        if (!plugin.getConfig().getBoolean("report.enabled", true)) return;
        int items = 0, mobs = 0, vehicles = 0;
        Map<Material, Integer> ground = new HashMap<>();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Item it) { items++; ground.merge(it.getItemStack().getType(), 1, Integer::sum); }
                else if (e instanceof LivingEntity le && !(le instanceof Player)) mobs++;
                else if (e instanceof Vehicle) vehicles++;
            }
        }
        plugin.getLogger().info("[Antilag] " + fmt(tps) + " TPS | item=" + items + " quai=" + mobs
                + " xe=" + vehicles + " | da don tich luy: " + clearedItems + " item, "
                + clearedMobs + " quai, " + cleanedVehicles + " xe");
    }

    private static final DecimalFormat F = new DecimalFormat("#.##");
    private static String fmt(double v) { return F.format(v); }

    public int clearedItems() { return clearedItems; }
    public int clearedMobs() { return clearedMobs; }
    public int cleanedVehicles() { return cleanedVehicles; }

    /** Don ngay lap tuc (goi tu lenh /antilag clear). */
    public void clearNow() {
        int minAge = plugin.getConfig().getInt("cleanup.items.min-age-seconds", 60) * 20;
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof Item item)) continue;
                if (item.getTicksLived() < minAge) continue;
                if (isProtected(item)) continue;
                item.remove();
                removed++;
            }
        }
        clearedItems += removed;
        plugin.getLogger().info("[Antilag] Don thu cong: " + removed + " item");
    }
}
