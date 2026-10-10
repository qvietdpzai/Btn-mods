package com.qvietdpzai.btnhealth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BtnCheckHealth extends JavaPlugin implements Listener {

    private HealthMonitor monitor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.monitor = new HealthMonitor(this);
        getServer().getPluginManager().registerEvents(this, this);
        monitor.start();
        getLogger().info("Btn-checkhealth enabled (v" + getDescription().getVersion() + ")");
        getLogger().info("Theo doi: TPS/MSPT, entities, chunks, RAM, command spam");
    }

    @Override
    public void onDisable() {
        if (monitor != null) monitor.stop();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.AQUA + "Btn-checkhealth " + getDescription().getVersion()
                    + ChatColor.GRAY + " - /btn health | tps | top [n] | recent [player] | alerts | reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "health": case "report": case "check": case "score":
                monitor.sendReport(sender);
                return true;
            case "tps": case "mspt": case "ping":
                sender.sendMessage(monitor.quickLine());
                return true;
            case "top": case "entities":
                monitor.sendTopEntities(sender, args.length > 1 ? safeInt(args[1], 10) : 10);
                return true;
            case "recent": case "spy": case "cmd":
                monitor.sendCommandReport(sender, args.length > 1 ? args[1] : null);
                return true;
            case "alerts": case "log":
                monitor.sendAlertHistory(sender);
                return true;
            case "reload":
                reloadConfig();
                monitor.reload();
                sender.sendMessage(ChatColor.GREEN + "Btn-checkhealth: da reload config.");
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Subcommand khong ton tai: " + args[0]
                        + ChatColor.GRAY + " (health|tps|top|recent|alerts|reload)");
                return true;
        }
    }

    private int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!getConfig().getBoolean("command-spy.enabled", true)) return;
        Player p = event.getPlayer();
        boolean bypass = p.hasPermission("btncheckhealth.bypass");
        boolean cancel = monitor.onCommand(
                p.getUniqueId(), p.getName(), event.getMessage(),
                getConfig().getInt("command-spy.warn-threshold", 25),
                getConfig().getInt("command-spy.critical-threshold", 60),
                bypass,
                getConfig().getString("command-spy.action", "warn"));
        if (cancel) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "[Btn-checkhealth] Lenh bi chan do spam (" 
                + event.getMessage().split(" ")[0] + ")");
        }
    }
}
