package com.qvietdpzai.deathchest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Btn-deathchest: khi chet, do vao ruong tai cho chet, tu xoa sau X phut. */
public final class BtnDeathChest extends JavaPlugin implements Listener {

    private final Map<Location, DeathChestData> chests = new HashMap<>();
    private BukkitTask expireTask;

    public static final class DeathChestData {
        public final UUID owner;
        public final String ownerName;
        public final long expiresAt;
        public DeathChestData(UUID owner, String ownerName, long expiresAt) {
            this.owner = owner; this.ownerName = ownerName; this.expiresAt = expiresAt;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        load();
        getServer().getPluginManager().registerEvents(this, this);
        expireTask = new BukkitRunnable() {
            @Override public void run() { cleanupExpired(); }
        }.runTaskTimer(this, 200L, 1200L);
        getLogger().info("Btn-deathchest enabled - ruong tu xoa sau " + expireMinutes() + " phut");
    }

    @Override
    public void onDisable() {
        if (expireTask != null) expireTask.cancel();
        save();
    }

    private int expireMinutes() { return getConfig().getInt("expire-minutes", 10); }
    private boolean onlyOwner() { return getConfig().getBoolean("only-owner-can-open", true); }
    private boolean message() { return getConfig().getBoolean("message-location", true); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!getConfig().getBoolean("enabled", true)) return;
        Player p = event.getEntity();
        Location loc = p.getLocation();
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) return;

        // tim cho trong xung quanh de dat ruong
        Block target = findSpot(loc);
        if (target == null) {
            if (getConfig().getBoolean("fallback-drop-if-no-space", true)) {
                p.sendMessage(ChatColor.RED + "Khong co cho dat ruong - do rot xuong dat nhu thuong.");
            }
            return;
        }

        target.setType(Material.CHEST, false);
        BlockState state = target.getState();
        if (!(state instanceof Chest chest)) { target.setType(Material.AIR); return; }

        Inventory inv = chest.getInventory();
        for (ItemStack item : drops) if (item != null) inv.addItem(item).values()
                .forEach(left -> p.getWorld().dropItemNaturally(target.getLocation().add(0.5, 1, 0.5), left));
        drops.clear();
        event.getDrops().clear();

        long expiresAt = System.currentTimeMillis() + expireMinutes() * 60000L;
        chests.put(target.getLocation(), new DeathChestData(p.getUniqueId(), p.getName(), expiresAt));

        if (message()) {
            p.sendMessage(ChatColor.YELLOW + "Do cua ban nam trong ruong tai " + fmt(target.getLocation())
                    + ChatColor.GRAY + " (tu xoa sau " + expireMinutes() + " phut)");
        }
        save();
    }

    private Block findSpot(Location loc) {
        Block base = loc.getBlock();
        if (base.getType().isAir() || base.isLiquid() || base.isEmpty()) return base;
        Block above = base.getRelative(0, 1, 0);
        if (above.getType().isAir() || above.isEmpty()) return above;
        // thu 4 huong
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                Block b = base.getRelative(dx, 0, dz);
                if (b.getType().isAir() || b.isEmpty()) return b;
                Block b2 = b.getRelative(0, 1, 0);
                if (b2.getType().isAir() || b2.isEmpty()) return b2;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;
        Location loc = chest.getLocation();
        DeathChestData data = chests.get(loc);
        if (data == null) return;
        if (onlyOwner() && !data.owner.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Ruong death chest cua " + data.ownerName + ", ban khong mo duoc.");
            return;
        }
        event.getPlayer().sendMessage(ChatColor.GRAY + "Ruong tu xoa sau " + fmtLeft(data));
    }

    @EventHandler
    public void onCloseEmpty(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;
        Location loc = chest.getLocation();
        DeathChestData data = chests.get(loc);
        if (data == null) return;
        if (event.getInventory().isEmpty()) {
            removeChest(loc, false);
        }
    }

    private void removeChest(Location loc, boolean expired) {
        DeathChestData data = chests.remove(loc);
        Block block = loc.getBlock();
        if (block.getState() instanceof Chest chest) chest.getInventory().clear();
        block.setType(Material.AIR, false);
        save();
        if (expired && data != null) {
            Player owner = Bukkit.getPlayer(data.owner);
            if (owner != null) owner.sendMessage(ChatColor.RED + "Ruong death chest tai " + fmt(loc) + " da het han va bien mat.");
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        List<Location> toRemove = new ArrayList<>();
        for (Map.Entry<Location, DeathChestData> e : chests.entrySet()) {
            if (e.getValue().expiresAt < now) toRemove.add(e.getKey());
        }
        for (Location loc : toRemove) removeChest(loc, true);
    }

    private String fmtLeft(DeathChestData d) {
        long s = Math.max(0, d.expiresAt - System.currentTimeMillis()) / 1000;
        return (s / 60) + "p" + (s % 60) + "s";
    }

    private static String fmt(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    // ---------- luu tru ----------

    private File file() { return new File(getDataFolder(), "deathchests.yml"); }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, DeathChestData> e : chests.entrySet()) {
            Location l = e.getKey();
            DeathChestData d = e.getValue();
            String p = "chest-" + (i++);
            y.set(p + ".world", l.getWorld().getName());
            y.set(p + ".x", l.getBlockX());
            y.set(p + ".y", l.getBlockY());
            y.set(p + ".z", l.getBlockZ());
            y.set(p + ".owner", d.owner.toString());
            y.set(p + ".owner-name", d.ownerName);
            y.set(p + ".expires", d.expiresAt);
        }
        try { y.save(file()); } catch (IOException e) { getLogger().warning("Khong luu duoc: " + e.getMessage()); }
    }

    private void load() {
        File f = file();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            org.bukkit.World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) continue;
            Location loc = new Location(w, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            try {
                chests.put(loc, new DeathChestData(
                        UUID.fromString(s.getString("owner")),
                        s.getString("owner-name", "?"),
                        s.getLong("expires")));
            } catch (Exception ignored) { }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            save();
            sender.sendMessage(ChatColor.GREEN + "Btn-deathchest da reload.");
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + "Dang theo doi " + chests.size() + " ruong death chest (tu xoa sau "
                + expireMinutes() + " phut)");
        return true;
    }
}
