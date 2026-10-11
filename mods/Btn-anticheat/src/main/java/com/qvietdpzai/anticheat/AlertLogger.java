package com.qvietdpzai.anticheat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Ghi log ra file + console + Discord webhook. */
public final class AlertLogger {

    private final BtnAnticheatPlugin plugin;
    private final File logFile;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AlertLogger(BtnAnticheatPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "logs");
        dir.mkdirs();
        this.logFile = new File(dir, "anticheat.log");
    }

    public void log(String player, String check, String detail, int ping, double tps) {
        String line = String.format("[%s] %s | %s | %s | ping=%dms tps=%.1f",
                LocalDateTime.now().format(FMT), player, check, detail, ping, tps);

        plugin.getLogger().warning("[ANTICHEAT] " + player + " | " + check + " | " + detail);

        // ghi file + webhook (async) de khong chan main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            appendFile(line);
            String webhook = plugin.getConfig().getString("discord.webhook-url", "");
            if (webhook != null && !webhook.isBlank()) sendWebhook(webhook, line);
        });
    }

    /** Ghi log thô (khong theo dinh dang alert). */
    public void logRaw(String line) {
        plugin.getLogger().warning("[ANTICHEAT] " + line);
        String webhook = plugin.getConfig().getString("discord.webhook-url", "");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            appendFile(line);
            if (webhook != null && !webhook.isBlank()) sendWebhook(webhook, line);
        });
    }

    private void appendFile(String line) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(logFile, true))) {
            w.write(line);
            w.newLine();
        } catch (Exception e) {
            plugin.getLogger().warning("Khong ghi duoc log: " + e.getMessage());
        }
    }

    private void sendWebhook(String url, String line) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            String content = "{\"content\": \"[Btn-anticheat] " + escape(line) + "\"}";
            try (OutputStream os = c.getOutputStream()) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {
            // webhook loi thi bo qua, khong lam anh huong server
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
