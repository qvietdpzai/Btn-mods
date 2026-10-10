package com.qvietdpzai.btnmgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Btn-managerplayer: quản lý người chơi + theo dõi họ ở đâu, làm gì. */
public final class BtnManagerPlayer extends JavaPlugin {

    private PlayerStore store;
    private PunishmentManager punishments;
    private BukkitTask cleanupTask;

    private final Map<UUID, Watch> watches = new HashMap<>();
    private final Deque<String> serverFeed = new ArrayDeque<>(200);

    private static final class Watch {
        final UUID targetId;
        final long expiresAt;
        Watch(UUID targetId, long expiresAt) { this.targetId = targetId; this.expiresAt = expiresAt; }
    }

    /** UUID đại diện console khi admin watch từ console. */
    private static final java.util.UUID CONSOLE_WATCHER =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        this.store = new PlayerStore(this);
        this.punishments = new PunishmentManager(this);
        getServer().getPluginManager().registerEvents(new TrackListener(this), this);

        // Nạp dữ liệu người chơi đang online (cho trường hợp reload plugin)
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = store.get(p.getUniqueId(), p.getName());
            if (d.firstJoin < 0) d.firstJoin = now;
            d.sessionStart = now;
        }

        cleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            punishments.cleanupExpired();
            store.saveAllAsync();
        }, 6000L, 6000L);

        getLogger().info("Btn-managerplayer enabled (v" + getDescription().getVersion() + ")");
        getLogger().info("Quan ly nguoi choi: ban/mute/kick/heal/fly/gm/inv + theo doi hoat dong");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) cleanupTask.cancel();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = store.get(p.getUniqueId(), p.getName());
            if (d.sessionStart > 0) d.playtimeMs += Math.max(0, now - d.sessionStart);
        }
        store.saveAllAsync();
    }

    public PlayerStore store() { return store; }
    public PunishmentManager punishments() { return punishments; }

    // ---------- helpers dùng chung ----------

    public String loc(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public String loc(Player p) {
        return loc(p.getLocation());
    }

    public void broadcastWatch(UUID targetId, String message) {
        long now = System.currentTimeMillis();
        watches.entrySet().removeIf(e -> now > e.getValue().expiresAt);
        String line = ChatColor.DARK_GRAY + "[" + time() + "] " + ChatColor.GRAY + message;
        serverFeed.addLast(line);
        if (serverFeed.size() > 200) serverFeed.pollFirst();
        for (Map.Entry<UUID, Watch> e : watches.entrySet()) {
            if (!e.getValue().targetId.equals(targetId)) continue;
            UUID watcher = e.getKey();
            if (CONSOLE_WATCHER.equals(watcher)) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.AQUA + "[watch] " + line);
            } else {
                Player admin = Bukkit.getPlayer(watcher);
                if (admin != null && admin.isOnline()) {
                    admin.sendMessage(ChatColor.AQUA + "[watch] " + line);
                }
            }
        }
    }

    public void removeWatchersOf(UUID targetId) {
        watches.entrySet().removeIf(e -> e.getValue().targetId.equals(targetId));
    }

    private static String time() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    private static String fmtDuration(long ms) {
        if (ms < 60000) return (ms / 1000) + "s";
        long m = ms / 60000;
        long h = m / 60, d = h / 24;
        if (d > 0) return d + "d" + (h % 24) + "h";
        if (h > 0) return h + "h" + (m % 60) + "p";
        return m + "p";
    }

    private static String fmtTime(long ms) {
        if (ms < 60000) return Math.max(0, ms / 1000) + "s";
        long m = ms / 60000, h = m / 60;
        if (h > 0) return h + "h" + (m % 60) + "p";
        return m + "p";
    }

    // ---------- commands ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && !sender.isPermissionSet("btnmp.admin")) {
            // console luôn được dùng
        }
        if (!sender.hasPermission("btnmp.admin")) {
            sender.sendMessage(ChatColor.RED + "Ban khong co quyen (btnmp.admin).");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        switch (sub) {
            case "list": case "online":      cmdList(sender); return true;
            case "info": case "i":           cmdInfo(sender, rest, false); return true;
            case "seen": case "s":           cmdInfo(sender, rest, true); return true;
            case "kick":                     cmdKick(sender, rest); return true;
            case "ban":                      cmdBan(sender, rest); return true;
            case "unban":                    cmdUnban(sender, rest); return true;
            case "mute":                     cmdMute(sender, rest); return true;
            case "unmute":                   cmdUnmute(sender, rest); return true;
            case "tp": case "goto":          cmdTp(sender, rest); return true;
            case "bring": case "tphere":     cmdBring(sender, rest); return true;
            case "heal":                     cmdHeal(sender, rest); return true;
            case "feed":                     cmdFeed(sender, rest); return true;
            case "kill":                     cmdKill(sender, rest); return true;
            case "fly":                      cmdFly(sender, rest); return true;
            case "gm": case "gamemode":      cmdGm(sender, rest); return true;
            case "inv": case "invsee":       cmdInv(sender, rest, false); return true;
            case "ec": case "enderchest":    cmdInv(sender, rest, true); return true;
            case "watch":                    cmdWatch(sender, rest); return true;
            case "log": case "feedlog":      cmdLog(sender, rest); return true;
            case "bans":                     cmdListPunishments(sender, punishments.allBans(), "BAN"); return true;
            case "mutes":                    cmdListPunishments(sender, punishments.allMutes(), "MUTE"); return true;
            case "reload":                   reloadConfig(); sender.sendMessage(ChatColor.GREEN + "Da reload config."); return true;
            default:
                sender.sendMessage(ChatColor.RED + "Lenh khong ton tai: " + sub);
                usage(sender);
                return true;
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== Btn-managerplayer ===");
        sender.sendMessage(ChatColor.WHITE + "/btnmp list" + ChatColor.GRAY + " - danh sach nguoi choi dang online");
        sender.sendMessage(ChatColor.WHITE + "/btnmp info <nguoi>" + ChatColor.GRAY + " - chi tiet nguoi choi (dang online)");
        sender.sendMessage(ChatColor.WHITE + "/btnmp seen <nguoi>" + ChatColor.GRAY + " - nguoi choi offline");
        sender.sendMessage(ChatColor.WHITE + "/btnmp watch <nguoi>" + ChatColor.GRAY + " - theo doi truc tiep (off = tat)");
        sender.sendMessage(ChatColor.WHITE + "/btnmp log [n]" + ChatColor.GRAY + " - nhat ky hoat dong gan day");
        sender.sendMessage(ChatColor.WHITE + "--- quan ly ---");
        sender.sendMessage(ChatColor.WHITE + "/btnmp kick|ban|unban|mute|unmute <nguoi> [thoi gian] [ly do]");
        sender.sendMessage(ChatColor.WHITE + "/btnmp heal|feed|kill|inv|ec <nguoi> | fly [on|off] | gm <che do>");
        sender.sendMessage(ChatColor.WHITE + "/btnmp tp <nguoi> | /btnmp bring <nguoi>");
        sender.sendMessage(ChatColor.WHITE + "/btnmp bans | mutes" + ChatColor.GRAY + " - danh sach phat");
    }

    private void cmdList(CommandSender sender) {
        long now = System.currentTimeMillis();
        int afkMin = getConfig().getInt("tracking.afk-minutes", 10);
        sender.sendMessage(ChatColor.DARK_AQUA + "=== Nguoi choi online (" + Bukkit.getOnlinePlayers().size() + "/"
                + Bukkit.getMaxPlayers() + ") ===");
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = store.get(p.getUniqueId(), p.getName());
            boolean afk = d.isAfk(now, afkMin);
            sender.sendMessage(ChatColor.WHITE + p.getName()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + p.getGameMode()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW + loc(p)
                    + ChatColor.DARK_GRAY + " | hp " + ChatColor.RED + (int) p.getHealth() + "/" + (int) p.getMaxHealth()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.AQUA + p.getPing() + "ms"
                    + ChatColor.DARK_GRAY + " | choi " + fmtTime(d.totalPlaytime(now))
                    + (afk ? ChatColor.YELLOW + " | AFK " + d.afkMinutes(now, afkMin) + "p" : ""));
        }
    }

    private void cmdInfo(CommandSender sender, String[] args, boolean offline) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp " + (offline ? "seen" : "info") + " <nguoi>"); return; }
        String name = args[0];
        long now = System.currentTimeMillis();

        Player target = offline ? null : Bukkit.getPlayerExact(name);
        PlayerData d = target != null ? store.get(target.getUniqueId(), target.getName())
                : store.getByName(name);
        if (d == null) { sender.sendMessage(ChatColor.RED + "Khong tim thay du lieu nguoi choi: " + name); return; }

        sender.sendMessage(ChatColor.DARK_AQUA + "=== " + d.name + " " + (target != null ? ChatColor.GREEN + "[online]" : ChatColor.GRAY + "[offline]") + ChatColor.DARK_AQUA + " ===");
        sender.sendMessage(ChatColor.WHITE + "UUID: " + ChatColor.GRAY + d.uuid.id());
        sender.sendMessage(ChatColor.WHITE + "Thoi gian choi: " + ChatColor.YELLOW + fmtTime(d.totalPlaytime(now))
                + ChatColor.GRAY + " | vao lan dau: " + (d.firstJoin > 0
                    ? new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(d.firstJoin)) : "?")
                + ChatColor.GRAY + " | cuoi: " + (d.lastSeen > 0
                    ? new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(d.lastSeen)) : "?"));

        if (target != null) {
            sender.sendMessage(ChatColor.WHITE + "Vi tri: " + ChatColor.YELLOW + loc(target)
                    + ChatColor.WHITE + " | che do: " + target.getGameMode()
                    + ChatColor.WHITE + " | hp " + (int) target.getHealth() + "/20 | thuc an " + target.getFoodLevel()
                    + ChatColor.WHITE + " | ping " + target.getPing() + "ms"
                    + ChatColor.WHITE + " | bay: " + (target.getAllowFlight() ? "co" : "khong"));
            try {
                org.bukkit.block.Block look = target.getTargetBlockExact(6);
                if (look != null) {
                    sender.sendMessage(ChatColor.WHITE + "Dang nhin: " + ChatColor.YELLOW
                            + look.getType() + " @ " + loc(look.getLocation()));
                }
            } catch (Exception ignored) { }
        }

            sender.sendMessage(ChatColor.WHITE + "Thong ke: " + ChatColor.GRAY + "block pha=" + d.blocksBroken
                    + " dat=" + d.blocksPlaced + " | chet=" + d.deaths + " gietPlayer=" + d.playerKills
                    + " gietMob=" + d.mobKills + " | lenh=" + d.commandsUsed
                    + " | chat=" + d.chatMessages + " drop=" + d.itemsDropped + " nhat=" + d.itemsPicked);

            if (d.lastBlockBroken != null)
                sender.sendMessage(ChatColor.WHITE + "Block gan nhat: " + ChatColor.GRAY
                        + "pha " + d.lastBlockBroken + (d.lastBlockPlaced != null ? " | dat " + d.lastBlockPlaced : ""));
            if (d.lastCommand != null)
                sender.sendMessage(ChatColor.WHITE + "Lenh gan nhat: " + ChatColor.GRAY
                        + d.lastCommand + ChatColor.DARK_GRAY + " (" + fmtTime(now - d.lastCommandAt) + " truoc)");
            if (d.lastChat != null)
                sender.sendMessage(ChatColor.WHITE + "Chat gan nhat: " + ChatColor.GRAY
                        + "\"" + d.lastChat + "\"" + ChatColor.DARK_GRAY + " (" + fmtTime(now - d.lastChatAt) + " truoc)");
            if (d.lastDeathCause != null)
                sender.sendMessage(ChatColor.WHITE + "Chet gan nhat: " + ChatColor.GRAY + d.lastDeathCause
                        + " @ " + ChatColor.DARK_GRAY + fmtTime(now - d.lastDeathAt) + " truoc");
            if (d.lastInteract != null)
                sender.sendMessage(ChatColor.WHITE + "Tuong tac gan nhat: " + ChatColor.GRAY + d.lastInteract);

            String banInfo = punishments.banInfo(d.uuid.id(), now);
            if (banInfo != null) sender.sendMessage(ChatColor.RED + "Dang bi: " + banInfo);
            String muteInfo = punishments.muteInfo(d.uuid.id(), now);
            if (muteInfo != null) sender.sendMessage(ChatColor.RED + "Dang bi: " + muteInfo);
    }

    private void cmdKick(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp kick <nguoi> [ly do]"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Nguoi choi khong online: " + args[0]); return; }
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Bi kick boi quan tri vien";
        t.kickPlayer(ChatColor.RED + "Kicked: " + reason);
        sender.sendMessage(ChatColor.GREEN + "Da kick " + t.getName() + " | ly do: " + reason);
    }

    private void cmdBan(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp ban <nguoi> [thoi gian: 30m|2h|3d|perm] [ly do]"); return; }
        String name = args[0];
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        UUID id = off.getUniqueId();
        String dName = off.getName() != null ? off.getName() : name;

        long dur = PunishmentManager.PERMANENT;
        int reasonStart = 1;
        if (args.length > 1) {
            long parsed = PunishmentManager.parseDuration(args[1]);
            if (parsed != -1 || args[1].equalsIgnoreCase("perm")) { dur = parsed; reasonStart = 2; }
        }
        String reason = args.length > reasonStart
                ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length)) : "Vi pham";

        punishments.ban(id, dName, dur, reason, sender.getName());
        Player online = Bukkit.getPlayerExact(dName);
        if (online != null) online.kickPlayer(ChatColor.RED + "Ban: " + reason
                + (dur == PunishmentManager.PERMANENT ? "" : " (" + dur / 1000 + "s)"));
        sender.sendMessage(ChatColor.GREEN + "Da ban " + dName + " ("
                + punishments.banInfo(id, System.currentTimeMillis()) + ")");
    }

    private void cmdUnban(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp unban <nguoi>"); return; }
        sender.sendMessage(punishments.unban(args[0])
                ? ChatColor.GREEN + "Da unban " + args[0]
                : ChatColor.RED + "Khong thay ban cho " + args[0]);
    }

    private void cmdMute(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp mute <nguoi> [thoi gian] [ly do]"); return; }
        String name = args[0];
        Player on = Bukkit.getPlayerExact(name);
        OfflinePlayer off = on != null ? on : Bukkit.getOfflinePlayer(name);
        UUID id = off.getUniqueId();
        String dName = off.getName() != null ? off.getName() : name;

        long dur = PunishmentManager.PERMANENT;
        int reasonStart = 1;
        if (args.length > 1) {
            long parsed = PunishmentManager.parseDuration(args[1]);
            if (parsed != -1 || args[1].equalsIgnoreCase("perm")) { dur = parsed; reasonStart = 2; }
        }
        String reason = args.length > reasonStart
                ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length)) : "Spam/violate";

        punishments.mute(id, dName, dur, reason, sender.getName());
        if (on != null) on.sendMessage(ChatColor.RED + "Ban bi mute: " + reason);
        sender.sendMessage(ChatColor.GREEN + "Da mute " + dName + " (" + punishments.muteInfo(id, System.currentTimeMillis()) + ")");
    }

    private void cmdUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp unmute <nguoi>"); return; }
        sender.sendMessage(punishments.unmute(args[0])
                ? ChatColor.GREEN + "Da unmute " + args[0]
                : ChatColor.RED + "Khong thay mute cho " + args[0]);
    }

    private void cmdTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player self)) { sender.sendMessage(ChatColor.RED + "Lenh nay phai goi trong game (console khong the tu dich chuyen / mo tui do)."); return; }
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp tp <nguoi>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Khong online: " + args[0]); return; }
        self.teleport(t.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Da dich chuyen toi " + t.getName() + " @ " + loc(t));
    }

    private void cmdBring(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp bring <nguoi>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Khong online: " + args[0]); return; }
        Location dest = (sender instanceof Player self) ? self.getLocation() : t.getWorld().getSpawnLocation();
        t.teleport(dest);
        sender.sendMessage(ChatColor.GREEN + "Da keo " + t.getName() + " toi ban @ " + loc(dest));
    }

    private void cmdHeal(CommandSender sender, String[] args) {
        Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player s ? s : null);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp heal [nguoi]"); return; }
        t.setHealth(t.getMaxHealth());
        t.setFoodLevel(20);
        t.setSaturation(20f);
        t.setFireTicks(0);
        t.sendMessage(ChatColor.GREEN + "Ban da duoc hoi mau.");
        sender.sendMessage(ChatColor.GREEN + "Da hoi mau cho " + t.getName());
    }

    private void cmdFeed(CommandSender sender, String[] args) {
        Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player s ? s : null);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp feed [nguoi]"); return; }
        t.setFoodLevel(20);
        t.setSaturation(20f);
        t.sendMessage(ChatColor.GREEN + "Ban da duoc no bung.");
        sender.sendMessage(ChatColor.GREEN + "Da feed " + t.getName());
    }

    private void cmdKill(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp kill <nguoi>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Khong online: " + args[0]); return; }
        t.setHealth(0);
        sender.sendMessage(ChatColor.GREEN + "Da giet " + t.getName());
    }

    private void cmdFly(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp fly <nguoi> [on|off]"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Khong online: " + args[0]); return; }
        boolean enable;
        if (args.length > 1 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            enable = args[1].equalsIgnoreCase("on");
        } else {
            enable = !t.getAllowFlight();
        }
        t.setAllowFlight(enable);
        if (!enable) t.setFlying(false);
        sender.sendMessage(ChatColor.GREEN + "Fly " + t.getName() + ": " + (enable ? "BAT" : "TAT"));
        t.sendMessage(ChatColor.YELLOW + "Che do bay cua ban: " + (enable ? "bat" : "tat"));
    }

    private void cmdGm(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp gm <nguoi> <survival|creative|adventure|spectator>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Khong online: " + args[0]); return; }
        GameMode gm;
        try { gm = GameMode.valueOf(args[1].toUpperCase()); }
        catch (IllegalArgumentException e) { sender.sendMessage(ChatColor.RED + "Che do khong hop le: " + args[1]); return; }
        t.setGameMode(gm);
        sender.sendMessage(ChatColor.GREEN + t.getName() + " -> " + gm);
        t.sendMessage(ChatColor.YELLOW + "Che do choi cua ban: " + gm);
    }

    private void cmdInv(CommandSender sender, String[] args, boolean enderChest) {
        if (!(sender instanceof Player self)) { sender.sendMessage(ChatColor.RED + "Lenh nay phai goi trong game (console khong the tu dich chuyen / mo tui do)."); return; }
        if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Dung: /btnmp " + (enderChest ? "ec" : "inv") + " <nguoi>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Khong online: " + args[0]); return; }
        Inventory inv = enderChest ? t.getEnderChest() : t.getInventory();
        self.openInventory(inv);
        sender.sendMessage(ChatColor.GREEN + "Dang xem " + (enderChest ? "ender chest" : "tui do") + " cua " + t.getName());
    }

    private void cmdWatch(CommandSender sender, String[] args) {
        java.util.UUID watcher = sender instanceof Player p ? p.getUniqueId() : CONSOLE_WATCHER;
        if (args.length < 1 || args[0].equalsIgnoreCase("off")) {
            watches.remove(watcher);
            sender.sendMessage(ChatColor.YELLOW + "Da tat theo doi.");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(ChatColor.RED + "Khong online: " + args[0]); return; }
        long expiry = System.currentTimeMillis() + getConfig().getInt("tracking.watch-duration-minutes", 15) * 60000L;
        watches.put(watcher, new Watch(t.getUniqueId(), expiry));
        sender.sendMessage(ChatColor.GREEN + "Dang theo doi " + t.getName() + " @ " + loc(t));
        sender.sendMessage(ChatColor.GRAY + "Se thay: di chuyen, chat, lenh, block pha/dat, chet, giet, mo tui do, vao/ra game");
        sender.sendMessage(ChatColor.GRAY + "Tu tat: /btnmp watch off (tu dong tat sau "
                + getConfig().getInt("tracking.watch-duration-minutes", 15) + " phut)");
    }

    private void cmdLog(CommandSender sender, String[] args) {
        int n = 20;
        try {
            if (args.length > 0) n = Math.min(100, Math.max(1, Integer.parseInt(args[0])));
        } catch (NumberFormatException ignored) { n = 20; }

        sender.sendMessage(ChatColor.DARK_AQUA + "=== Nhat ky hoat dong (" + n + " su kien gan nhat) ===");
        Iterator<String> it = serverFeed.descendingIterator();
        int shown = 0;
        while (it.hasNext() && shown < n) {
            sender.sendMessage(it.next());
            shown++;
        }
        if (shown == 0) sender.sendMessage(ChatColor.GRAY + "(chua co su kien nao)");
    }

    private void cmdListPunishments(CommandSender sender, Map<UUID, PunishmentManager.Punishment> map, String type) {
        long now = System.currentTimeMillis();
        sender.sendMessage(ChatColor.DARK_AQUA + "=== Danh sach " + type + " (" + map.size() + ") ===");
        if (map.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "(trong)"); return; }
        int i = 0;
        for (Map.Entry<UUID, PunishmentManager.Punishment> e : map.entrySet()) {
            if (i++ >= 30) { sender.sendMessage(ChatColor.GRAY + "... con " + (map.size() - 30)); break; }
            PunishmentManager.Punishment p = e.getValue();
            sender.sendMessage(ChatColor.WHITE + p.name + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW
                    + (p.until == PunishmentManager.PERMANENT ? "vinh vien" : fmtDuration(p.until - now))
                    + ChatColor.DARK_GRAY + " | " + p.reason + ChatColor.DARK_GRAY + " | boi " + p.by);
        }
    }
}
