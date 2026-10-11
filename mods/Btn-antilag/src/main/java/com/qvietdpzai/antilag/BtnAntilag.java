package com.qvietdpzai.antilag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;

/** Btn-antilag: giam tai server. */
public final class BtnAntilag extends JavaPlugin {

    private AntilageEngine engine;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        this.engine = new AntilageEngine(this);
        engine.start();
        getLogger().info("Btn-antilag enabled - don item moi " + getConfig().getLong("cleanup.interval-seconds", 120)
                + "s, item ton tai it nhat " + getConfig().getInt("cleanup.items.min-age-seconds", 60) + "s");
    }

    @Override
    public void onDisable() {
        getLogger().info("Btn-antilag disabled");
    }

    private static final DecimalFormat F = new DecimalFormat("#.##");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            int items = 0, mobs = 0, vehicles = 0;
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e instanceof org.bukkit.entity.Item) items++;
                    else if (e instanceof LivingEntity le && !(le instanceof Player)) mobs++;
                    else if (e instanceof Vehicle) vehicles++;
                }
            }
            sender.sendMessage(ChatColor.DARK_AQUA + "=== Btn-antilag ===");
            sender.sendMessage(ChatColor.WHITE + "TPS: " + ChatColor.YELLOW + F.format(engine.tps())
                    + ChatColor.WHITE + " | Item tren dat: " + items + " | Quai: " + mobs + " | Xe: " + vehicles);
            sender.sendMessage(ChatColor.WHITE + "Da don tich luy: " + ChatColor.YELLOW + engine.clearedItems()
                    + ChatColor.WHITE + " item, " + engine.clearedMobs() + " quai, " + engine.cleanedVehicles() + " xe");
            sender.sendMessage(ChatColor.GRAY + "View-distance: " + Bukkit.getWorlds().get(0).getViewDistance()
                    + (getConfig().getBoolean("view-distance.dynamic") ? ChatColor.GREEN + " (dong)" : ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("btnantilag.admin")) {
                sender.sendMessage(ChatColor.RED + "Khong co quyen.");
                return true;
            }
            engine.clearNow();
            sender.sendMessage(ChatColor.GREEN + "Da don item rac (van giu item co ten + item bao ve).");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("btnantilag.admin")) {
                sender.sendMessage(ChatColor.RED + "Khong co quyen.");
                return true;
            }
            reloadConfig();
            getLogger().info("Da reload config (chu ky don item tinh tu lan toi).");
            sender.sendMessage(ChatColor.GREEN + "Da reload config.");
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.AQUA + "/antilag" + ChatColor.GRAY + " - thong ke hien tai");
            sender.sendMessage(ChatColor.AQUA + "/antilag clear" + ChatColor.GRAY + " - don item rac ngay");
            sender.sendMessage(ChatColor.AQUA + "/antilag reload" + ChatColor.GRAY + " - nap lai config");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Lenh khong ton tai.");
        return true;
    }
}
