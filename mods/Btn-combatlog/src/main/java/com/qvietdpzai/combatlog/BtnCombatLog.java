package com.qvietdpzai.combatlog;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Btn-combatlog: tag combat, chặn lệnh trốn fight, trừng người logout khi đang combat. */
public final class BtnCombatLog extends JavaPlugin implements Listener {

    private final Map<UUID, Long> tagged = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(this, 20L, 10L);
        getLogger().info("Btn-combatlog enabled (tag " + tagSeconds() + "s)");
    }

    @Override
    public void onDisable() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        tagged.clear();
    }

    private int tagSeconds() { return getConfig().getInt("tag-seconds", 15); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = directPlayer(event.getDamager());
        long until = System.currentTimeMillis() + tagSeconds() * 1000L;
        if (victim != null) tag(victim, until);
        if (attacker != null && victim != null && !attacker.equals(victim)) tag(attacker, until);
    }

    private Player directPlayer(org.bukkit.entity.Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof org.bukkit.entity.Projectile pro && pro.getShooter() instanceof Player p) return p;
        return null;
    }

    private void tag(Player p, long until) {
        boolean first = !tagged.containsKey(p.getUniqueId()) || !isTagged(p);
        tagged.put(p.getUniqueId(), until);
        if (first) {
            p.sendMessage(ChatColor.RED + "Ban dang trong combat! Thoat game se bi trung phat.");
            BossBar bar = bars.computeIfAbsent(p.getUniqueId(), k ->
                    Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID));
            bar.addPlayer(p);
            bar.setVisible(true);
        }
    }

    private boolean isTagged(Player p) {
        Long until = tagged.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isTagged(event.getPlayer())) return;
        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        List<String> blocked = getConfig().getStringList("blocked-commands");
        for (String b : blocked) {
            if (cmd.equals(b.toLowerCase()) || cmd.equals("/" + b.toLowerCase())) {
                event.setCancelled(true);
                long left = (tagged.get(event.getPlayer().getUniqueId()) - System.currentTimeMillis()) / 1000;
                event.getPlayer().sendMessage(ChatColor.RED + "Khong the dung lenh nay khi dang combat! Con " + left + "s");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Long until = tagged.get(p.getUniqueId());
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
        if (until == null || until < System.currentTimeMillis()) { tagged.remove(p.getUniqueId()); return; }

        // Trừng người logout khi đang combat
        String name = p.getName();
        boolean broadcast = getConfig().getBoolean("broadcast-combat-log", true);
        if (broadcast) {
            String msg = getConfig().getString("messages.combat-log", "{player} thoat game khi dang combat!")
                    .replace("{player}", name);
            Bukkit.broadcastMessage(ChatColor.RED + msg);
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.hasPermission("btncombatlog.notify")) op.sendMessage(ChatColor.GRAY + "[combatlog] " + name);
            }
        }
        for (String c : getConfig().getStringList("punish-commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{player}", name));
        }
        tagged.remove(p.getUniqueId());
        getLogger().info(name + " logout khi dang combat da bi trung phat");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        tagged.entrySet().removeIf(e -> e.getValue() < now);
        for (UUID id : new HashMap<>(tagged).keySet()) {
            Player p = Bukkit.getPlayer(id);
            BossBar bar = bars.get(id);
            if (p == null || !p.isOnline()) { if (bar != null) { bar.removeAll(); bars.remove(id); } continue; }
            long left = (tagged.get(id) - now) / 1000;
            if (bar != null) {
                bar.setProgress(Math.max(0, Math.min(1, left / (double) tagSeconds())));
                bar.setTitle(ChatColor.RED + "Dang combat - con " + left + "s");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("btncombatlog.admin")) return true;
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Btn-combatlog da reload.");
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Dung trong game."); return true; }
        if (isTagged(p)) {
            long left = (tagged.get(p.getUniqueId()) - System.currentTimeMillis()) / 1000;
            sender.sendMessage(ChatColor.RED + "Ban dang combat, con " + left + "s");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Ban khong trong combat.");
        }
        return true;
    }
}
