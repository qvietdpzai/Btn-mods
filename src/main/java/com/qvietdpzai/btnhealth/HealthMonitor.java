package com.qvietdpzai.btnhealth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class HealthMonitor {

    private final Plugin plugin;
    private BukkitTask task;

    // Running stats
    private double tps1m = 20, tps5m = 20, tps15m = 20;
    private double msptAvg = 0, msptPeak = 0;
    private long usedMem, maxMem;
    private int totalEntities, totalChunks, players;
    private final Map<EntityType, Integer> entityByType = new HashMap<>();
    private final Map<String, Integer> entitiesPerWorld = new LinkedHashMap<>();
    private final Map<String, Integer> chunksPerWorld = new LinkedHashMap<>();
    private int items, mobs, arrows, xpOrbs, vehicles, other;

    // Tick timing fallback (khi Paper API không khả dụng)
    private final Deque<Double> recentMspt = new ArrayDeque<>();
    private long lastTickCheck = System.nanoTime();

    // Alert history
    private final Deque<String> alerts = new ArrayDeque<>();
    private int alertCount = 0;
    private long lastAlertAt = 0;
    private int score = 100;
    private String grade = "EXCELLENT";

    public HealthMonitor(Plugin plugin) { this.plugin = plugin; }

    // ---------- config ----------
    private int intervalSeconds() { return plugin.getConfig().getInt("monitor.interval-seconds", 10); }
    private int historySize() { return plugin.getConfig().getInt("monitor.history-size", 60); }
    private double tpsThreshold() { return plugin.getConfig().getDouble("monitor.alert.tps-threshold", 18.0); }
    private double msptThreshold() { return plugin.getConfig().getDouble("monitor.alert.mspt-threshold-ms", 45.0); }
    private int entityThreshold() { return plugin.getConfig().getInt("monitor.alert.entity-threshold", 1500); }
    private int alertCooldown() { return plugin.getConfig().getInt("monitor.alert.cooldown-seconds", 300) * 1000; }
    private boolean notifyOps() { return plugin.getConfig().getBoolean("monitor.alert.notify-ops", true); }
    private boolean notifyConsole() { return plugin.getConfig().getBoolean("monitor.alert.notify-console", true); }

    public void start() {
        long period = Math.max(1, intervalSeconds()) * 20L;
        task = new BukkitRunnable() {
            @Override public void run() { sample(); }
        }.runTaskTimer(plugin, 20L, period);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void reload() { stop(); start(); }

    // ---------- sampling ----------
    private void sample() {
        try {
            samplePaperMetrics();
            sampleWorlds();
            sampleMemory();
            updateFallbackTiming();
            evaluate();
            checkAlerts();
            commandCounters.clear();
        } catch (Exception e) {
            plugin.getLogger().warning("HealthMonitor sample error: " + e.getMessage());
        }
    }

    private void samplePaperMetrics() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] tps = (double[]) m.invoke(Bukkit.getServer());
            if (tps != null && tps.length >= 3) { tps1m = tps[0]; tps5m = tps[1]; tps15m = tps[2]; }
        } catch (Exception ignored) { }

        try {
            Method m = Bukkit.getServer().getClass().getMethod("getMSPT");
            Object r = m.invoke(Bukkit.getServer());
            if (r instanceof double[]) {
                double[] mspt = (double[]) r;
                double sum = 0;
                for (double v : mspt) sum += v;
                msptAvg = sum / mspt.length;
                double peak = 0;
                for (double v : mspt) peak = Math.max(peak, v);
                msptPeak = peak;
            }
        } catch (Exception ignored) { }
    }

    private void updateFallbackTiming() {
        long now = System.nanoTime();
        double elapsedMs = (now - lastTickCheck) / 1_000_000.0;
        double expectedMs = intervalSeconds() * 1000.0;
        lastTickCheck = now;
        if (msptAvg <= 0) {
            // Không có Paper API: ước lượng MSPT = elapsed / số tick kỳ vọng
            double est = elapsedMs / (expectedMs / 50.0);
            recentMspt.add(est);
            while (recentMspt.size() > historySize()) recentMspt.pollFirst();
            double sum = 0, peak = 0;
            for (double v : recentMspt) { sum += v; peak = Math.max(peak, v); }
            msptAvg = sum / recentMspt.size();
            msptPeak = peak;
        }
    }

    private void sampleWorlds() {
        entityByType.clear(); entitiesPerWorld.clear(); chunksPerWorld.clear();
        items = mobs = arrows = xpOrbs = vehicles = other = 0;
        totalEntities = totalChunks = 0;
        players = Bukkit.getOnlinePlayers().size();

        for (World w : Bukkit.getWorlds()) {
            int we = 0;
            for (Entity e : w.getEntities()) {
                we++; totalEntities++;
                entityByType.merge(e.getType(), 1, Integer::sum);
                if (e instanceof Item) items++;
                else if (e instanceof LivingEntity || e.getType().isAlive()) mobs++;
                else if (e.getType() == EntityType.ARROW || e.getType() == EntityType.SPECTRAL_ARROW) arrows++;
                else if (e.getType() == EntityType.EXPERIENCE_ORB) xpOrbs++;
                else if (e instanceof org.bukkit.entity.Vehicle) vehicles++;
                else other++;
            }
            int wc = w.getLoadedChunks().length;
            totalChunks += wc;
            entitiesPerWorld.put(w.getName(), we);
            chunksPerWorld.put(w.getName(), wc);
        }
    }

    private void sampleMemory() {
        Runtime rt = Runtime.getRuntime();
        maxMem = rt.maxMemory();
        usedMem = rt.totalMemory() - rt.freeMemory();
    }

    // ---------- evaluation ----------
    private void evaluate() {
        double s = 100;

        if (tps1m < 18) s += (tps1m - 18) * 3.0;
        if (tps5m < 18) s += (tps5m - 18) * 1.5;

        if (msptAvg > msptThreshold()) s -= (msptAvg - msptThreshold()) * 0.4;
        if (msptPeak > 100) s -= Math.min(15, (msptPeak - 100) * 0.1);

        if (totalEntities > entityThreshold()) s -= (totalEntities - entityThreshold()) / 100.0 * 3.0;

        double memPct = maxMem == 0 ? 0 : (double) usedMem / maxMem;
        if (memPct > 0.85) s -= (memPct - 0.85) * 200;

        int recentBursts = commandBurstPenalty();
        s -= Math.min(20, recentBursts * 5.0);

        score = (int) Math.max(0, Math.min(100, Math.round(s)));
        grade = score >= 90 ? "EXCELLENT" : score >= 75 ? "GOOD" : score >= 60 ? "FAIR"
                : score >= 40 ? "POOR" : "CRITICAL";
    }

    private int commandBurstPenalty() {
        int heavy = commandCounters.values().stream().mapToInt(Integer::intValue).sum();
        return heavy > 200 ? 3 : heavy > 100 ? 2 : heavy > 40 ? 1 : 0;
    }

    // ---------- alerts ----------
    private void checkAlerts() {
        long now = System.currentTimeMillis();
        if (now - lastAlertAt < alertCooldown()) return;

        StringBuilder reason = new StringBuilder();
        if (tps1m < tpsThreshold()) reason.append(String.format("TPS=%.1f ", tps1m));
        if (msptAvg > msptThreshold()) reason.append(String.format("MSPT=%.0fms ", msptAvg));
        if (totalEntities > entityThreshold()) reason.append("entities=").append(totalEntities).append(' ');
        if (reason.isEmpty()) return;

        lastAlertAt = now;
        alertCount++;
        String msg = String.format("[Btn-checkhealth] ALERT #%d: %s(score=%d %s)",
                alertCount, reason, score, grade);
        alerts.addFirst(now + " | " + msg);
        while (alerts.size() > 20) alerts.pollLast();

        plugin.getLogger().warning(msg);
        if (notifyConsole()) Bukkit.getConsoleSender().sendMessage(ChatColor.RED + msg);
        if (notifyOps()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("btncheckhealth.admin")) p.sendMessage(ChatColor.RED + msg);
            }
        }
    }

    public void recordCommandAlert(String playerName, String detail, int rate) {
        long now = System.currentTimeMillis();
        String msg = String.format("[Btn-checkhealth] COMMAND SPAM: %s (%d cmd/s) %s",
                playerName, rate, detail);
        alerts.addFirst(now + " | " + msg);
        while (alerts.size() > 20) alerts.pollLast();
        plugin.getLogger().warning(msg);
        if (notifyConsole()) Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + msg);
    }

    // ---------- reports ----------
    public void sendReport(CommandSender sender) {
        ChatColor gradeColor = score >= 90 ? ChatColor.GREEN : score >= 60 ? ChatColor.YELLOW : ChatColor.RED;
        sender.sendMessage(ChatColor.DARK_AQUA + "===== Btn-checkhealth: đánh giá hiệu suất =====");
        sender.sendMessage(ChatColor.WHITE + "Điểm sức khỏe: " + gradeColor + score + "/100 [" + grade + "]");

        ChatColor tpsColor = tps1m >= 19 ? ChatColor.GREEN : tps1m >= tpsThreshold() ? ChatColor.YELLOW : ChatColor.RED;
        sender.sendMessage(ChatColor.WHITE + "TPS: " + tpsColor + fmt(tps1m) + " (1m) / " + fmt(tps5m)
                + " (5m) / " + fmt(tps15m) + " (15m)");
        sender.sendMessage(ChatColor.WHITE + "MSPT: " + fmt(msptAvg) + " avg / " + fmt(msptPeak) + " peak"
                + ChatColor.GRAY + " (ngưỡng " + fmt(msptThreshold()) + "ms)");

        double memPct = maxMem == 0 ? 0 : (double) usedMem / maxMem * 100;
        ChatColor memColor = memPct > 90 ? ChatColor.RED : memPct > 75 ? ChatColor.YELLOW : ChatColor.GREEN;
        sender.sendMessage(ChatColor.WHITE + "RAM: " + memColor + mb(usedMem) + "/" + mb(maxMem)
                + " MB (" + fmt(memPct) + "%)");
        sender.sendMessage(ChatColor.WHITE + "Entities: " + entityColor() + totalEntities
                + ChatColor.GRAY + " | Chunks: " + totalChunks + " | Players: " + players);
        sender.sendMessage(ChatColor.WHITE + "Phân loại: items=" + items + " mobs=" + mobs
                + " arrows=" + arrows + " xp=" + xpOrbs + " vehicles=" + vehicles + " khác=" + other);
        sender.sendMessage(ChatColor.GRAY + "Theo world: " + entitiesPerWorld + " chunks=" + chunksPerWorld);
        sender.sendMessage(ChatColor.WHITE + "Alerts (10p gần): " + alertCount
                + ChatColor.GRAY + " - /btn alerts xem lịch sử");

        int bursts = commandCounters.values().stream().mapToInt(Integer::intValue).sum();
        if (bursts > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Lệnh nặng gần đây: " + bursts
                    + ChatColor.GRAY + " (fill/setblock/clone...) - /btn recent");
        }
    }

    public String quickLine() {
        return ChatColor.WHITE + "TPS: " + fmt(tps1m) + "/" + fmt(tps5m) + ChatColor.GRAY
                + " | MSPT: " + fmt(msptAvg) + "ms | Score: " + score + " (" + grade + ")";
    }

    public void sendTopEntities(CommandSender sender, int max) {
        Map<EntityType, Integer> sorted = new TreeMap<>((a, b) -> entityByType.get(b) - entityByType.get(a));
        sorted.putAll(entityByType);
        StringBuilder sb = new StringBuilder(ChatColor.DARK_AQUA + "Top entities (" + totalEntities + " total):\n");
        int i = 0;
        for (Map.Entry<EntityType, Integer> e : sorted.entrySet()) {
            if (i++ >= max) break;
            sb.append(ChatColor.WHITE).append(" ").append(e.getKey()).append(": ")
              .append(ChatColor.YELLOW).append(e.getValue()).append('\n');
        }
        sender.sendMessage(sb.toString().trim());
    }

    public void sendCommandReport(CommandSender sender, String playerFilter) {
        StringBuilder sb = new StringBuilder(ChatColor.DARK_AQUA + "Tần suất lệnh (5s gần nhất):\n");
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Map.Entry<UUID, Tracker> e : trackers.entrySet()) {
            Tracker t = e.getValue();
            if (t.messages.isEmpty()) continue;
            if (playerFilter != null && !t.name.equalsIgnoreCase(playerFilter)) continue;
            any = true;
            sb.append(ChatColor.WHITE).append(" ").append(t.name)
              .append(ChatColor.GRAY).append(": ")
              .append(ChatColor.YELLOW).append(t.ratePerSecond(now)).append(" cmd/s, heavy=")
              .append(t.heavy).append(" (fill=").append(t.fills)
              .append(" setblock=").append(t.setblocks).append(" clone=").append(t.clones).append(")\n");
        }
        if (!any) sb.append(ChatColor.GRAY).append(" (không có dữ liệu)");
        sender.sendMessage(sb.toString().trim());
    }

    public void sendAlertHistory(CommandSender sender) {
        if (alerts.isEmpty()) { sender.sendMessage(ChatColor.GREEN + "Không có cảnh báo nào."); return; }
        StringBuilder sb = new StringBuilder(ChatColor.DARK_AQUA + "Lịch sử cảnh báo:\n");
        for (String a : alerts) sb.append(ChatColor.GRAY).append(" ").append(stripTime(a)).append('\n');
        sender.sendMessage(sb.toString().trim());
    }

    // ---------- command tracker integration ----------
    final Map<UUID, Tracker> trackers = new HashMap<>();
    final Map<UUID, Integer> commandCounters = new HashMap<>();

    static class Tracker {
        String name;
        final Deque<Long> messages = new ArrayDeque<>();
        int heavy, fills, setblocks, clones;
        long lastAlertAt;
        Tracker(String name) { this.name = name; }

        int ratePerSecond(long now) {
            prune(now);
            return (int) messages.stream().filter(x -> now - x <= 1000).count();
        }

        void prune(long now) {
            while (!messages.isEmpty() && now - messages.peekFirst() > 5000) messages.pollFirst();
        }
    }

    /**
     * Gọi từ PlayerCommandPreprocessEvent. Trả về true nếu nên chặn lệnh (spam).
     */
    public boolean onCommand(UUID id, String name, String message, int warnThreshold, int criticalThreshold,
                             boolean bypass, String action) {
        long now = System.currentTimeMillis();
        Tracker t = trackers.computeIfAbsent(id, k -> new Tracker(name));
        t.name = name;
        t.messages.add(now);
        t.prune(now);

        String lowered = message.toLowerCase();
        boolean heavy = isHeavy(lowered);
        if (heavy) {
            t.heavy++;
            commandCounters.merge(id, 1, Integer::sum);
            if (lowered.startsWith("/fill")) t.fills++;
            else if (lowered.startsWith("/setblock")) t.setblocks++;
            else if (lowered.startsWith("/clone")) t.clones++;
        }

        if (bypass || warnThreshold <= 0) return false;

        int rate = t.ratePerSecond(now);
        if (rate >= warnThreshold && now - t.lastAlertAt >= 10000) {
            t.lastAlertAt = now;
            String detail = heavy ? "heavy-command " + message.split(" ")[0] : "cmd " + message.split(" ")[0];
            recordCommandAlert(name, detail, rate);
            if (criticalThreshold > 0 && rate >= criticalThreshold && "cancel".equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeavy(String lowered) {
        String first = lowered.split(" ")[0];
        for (String h : plugin.getConfig().getStringList("command-spy.heavy-commands")) {
            if (first.equals(h.startsWith("/") ? h : "/" + h)) return true;
        }
        return false;
    }

    // ---------- utils ----------
    private ChatColor entityColor() {
        return totalEntities > entityThreshold() ? ChatColor.RED
                : totalEntities > entityThreshold() / 2 ? ChatColor.YELLOW : ChatColor.GREEN;
    }
    private static String fmt(double v) { return String.format("%.1f", v); }
    private static long mb(long b) { return b / 1048576L; }
    private static String stripTime(String s) {
        int i = s.indexOf("| ");
        return i >= 0 ? s.substring(i + 2) : s;
    }
}
