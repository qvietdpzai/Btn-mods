package com.qvietdpzai.btnsrv;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Btn-srv: cau noi chat giua nguoi choi trong game va Discord. */
public final class BtnSrvPlugin extends JavaPlugin implements Listener {

    private DiscordBridge discord;
    private RankProvider ranks;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        this.ranks = new RankProvider(this);
        this.discord = new DiscordBridge(this);

        getServer().getPluginManager().registerEvents(this, this);

        // canh bao neu DiscordSRV cung dang cau noi -> tranh 2 luong thong bao trung nhau
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
            getLogger().warning("Phat hien DiscordSRV dang cai - hai plugin deu cau noi Discord,");
            getLogger().warning("co the bi LAP thong bao. Nen go DiscordSRV-plugin.jar thanh .disabled");
        } else {
            getLogger().info("Btn-srv: khong thay DiscordSRV -> dang la plugin cau noi duy nhat");
        }

        discord.start();

        getLogger().info("Btn-srv bat - che do: "
                + (getConfig().getString("discord.bot-token", "").isBlank() ? "chi gui (webhook)" : "gui + nhan"));
    }

    @Override
    public void onDisable() {
        discord.stop();
        getLogger().info("Btn-srv tat");
    }

    public DiscordBridge discord() { return discord; }
    public RankProvider ranks() { return ranks; }

    /** Config cua plugin (getConfig()). */
    public org.bukkit.configuration.file.FileConfiguration cfg() { return getConfig(); }

    // ---------------- game -> Discord ----------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!getConfig().getBoolean("enabled", true)) return;
        Player p = e.getPlayer();
        if (p.hasPermission("btnsrv.ignore")) return;

        String rank = ranks.rank(p);
        String prefix = rank.isBlank() ? "" : "[" + ChatColor.stripColor(rank) + "] ";
        discord.sendToDiscord(prefix + p.getName(), e.getMessage());
    }

    // ---------------- Discord -> game ----------------

    /** Nguoi choi trong game nhan tin tu Discord. */
    public void broadcastFromDiscord(String author, String rank, String message) {
        String fmt = getConfig().getString("format.discord-to-game",
                "&8[&bDiscord&8]&r %RANK%%NAME%&7: &f%MESSAGE%");

        String rankPart = rank.isBlank() ? "" :
                "&8[&" + color(getConfig().getString("format.rank-color", "6")) + rank + "&8]&r ";
        String colorName = ChatColor.translateAlternateColorCodes('&',
                "&" + color(getConfig().getString("format.name-color", "b")));
        String colorMsg = ChatColor.translateAlternateColorCodes('&',
                "&" + color(getConfig().getString("format.message-color", "f")));

        String line = fmt
                .replace("%RANK%", rankPart)
                .replace("%NAME%", colorName + author + "&r")
                .replace("%MESSAGE%", colorMsg + message);

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', line));
        getLogger().info("[Discord] " + author + ": " + message);
    }

    // ---------------- lenh ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.AQUA + "=== Btn-srv ===");
            sender.sendMessage(ChatColor.WHITE + "Ken thuc: " + describe(discord.channelId()));
            sender.sendMessage(ChatColor.WHITE + "Gui tin: "
                    + (getConfig().getString("discord.webhook-url", "").isBlank() ? "bot token" : "webhook"));
            sender.sendMessage(ChatColor.WHITE + "Nhan tin: "
                    + (getConfig().getString("discord.bot-token", "").isBlank()
                        ? ChatColor.RED + "tat (thieu bot-token)" : ChatColor.GREEN + "bat"));
            sender.sendMessage(ChatColor.GRAY + "Rank cua ban: " + (sender instanceof Player p
                    ? describe(ranks.rank(p)) : ChatColor.GRAY + "(console)"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Da reload config Btn-srv.");
            return true;
        }
        if (args[0].equalsIgnoreCase("say")) {
            if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Dung: /btnsrv say <noi dung>"); return true; }
            String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            discord.sendToDiscord("[Server] Console", msg);
            sender.sendMessage(ChatColor.GREEN + "Da gui len Discord: " + msg);
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Lenh khong ton tai. Dung /btnsrv status|reload|say");
        return true;
    }

    // ---------------- tien ich ----------------

    private String color(String c) {
        if (c == null || c.isBlank()) return "f";
        return c.replace("&", "").replace("§", "").substring(0, 1);
    }

    private String describe(String s) {
        return s == null || s.isBlank() ? ChatColor.GRAY + "(chua xac dinh)" : ChatColor.GREEN + s;
    }

    /** Chay 1 tac vu tren main thread. */
    public void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(this, r);
    }
}
