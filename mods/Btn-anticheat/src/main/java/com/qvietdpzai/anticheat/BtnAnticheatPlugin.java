package com.qvietdpzai.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Btn-anticheat: phat hien + ghi log cheat. */
public final class BtnAnticheatPlugin extends JavaPlugin {

    private DetectionEngine engine;
    private AlertLogger logger;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        this.logger = new AlertLogger(this);
        this.engine = new DetectionEngine();
        engine.register(this);
        getServer().getPluginManager().registerEvents(engine, this);
        new BukkitRunnable() {
            @Override public void run() { engine.tickDecay(); }
        }.runTaskTimer(this, 1200L, 1200L);
        getLogger().info("Btn-anticheat enabled - che do: " + getConfig().getString("action", "log"));
        getLogger().info("Cac check: " + String.join(", ", getConfig().getConfigurationSection("checks").getKeys(false)));
    }

    @Override
    public void onDisable() {
        getLogger().info("Btn-anticheat disabled");
    }

    public AlertLogger logger() { return logger; }
    public DetectionEngine engine() { return engine; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.AQUA + "=== Btn-anticheat ===");
            sender.sendMessage(ChatColor.WHITE + "/anticheat" + ChatColor.GRAY + " - trang thai + so nguoi bi phat hien");
            sender.sendMessage(ChatColor.WHITE + "/anticheat info <ng>" + ChatColor.GRAY + " - chi tiet nguoi choi");
            sender.sendMessage(ChatColor.WHITE + "/anticheat reset <ng>|all" + ChatColor.GRAY + " - xoa diem vi pham");
            sender.sendMessage(ChatColor.WHITE + "/anticheat reload" + ChatColor.GRAY + " - nap lai config");
            sender.sendMessage(ChatColor.GRAY + "Log: plugins/Btn-anticheat/logs/anticheat.log");
            if (getConfig().getString("discord.webhook-url", "").isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Chua cau hinh Discord webhook (plugins/Btn-anticheat/config.yml)");
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Da reload config.");
                return true;
            case "info": {
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/anticheat info <ten>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(ChatColor.RED + "Nguoi choi khong online"); return true; }
                PlayerState st = engine.states().get(t.getUniqueId());
                if (st == null) { sender.sendMessage(ChatColor.GRAY + "Chua co du lieu"); return true; }
                sender.sendMessage(ChatColor.YELLOW + t.getName() + ": " + st.violations + " lan vi pham, diem "
                        + String.format("%.1f", st.level) + "/20, ping " + t.getPing() + "ms");
                return true;
            }
            case "reset":
                if (args.length > 1 && !args[1].equalsIgnoreCase("all")) {
                    PlayerState st = null;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().equalsIgnoreCase(args[1])) st = engine.states().get(p.getUniqueId());
                    }
                    if (st != null) { st.violations = 0; st.level = 0; sender.sendMessage(ChatColor.GREEN + "Da reset " + args[1]); }
                    else sender.sendMessage(ChatColor.RED + "Khong tim thay");
                    return true;
                }
                engine.states().values().forEach(st -> { st.violations = 0; st.level = 0; });
                sender.sendMessage(ChatColor.GREEN + "Da reset tat ca.");
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Lenh khong ton tai.");
                return true;
        }
    }
}
