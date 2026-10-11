package com.qvietdpzai.anticheat;

import org.bukkit.configuration.ConfigurationSection;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Ghi log ra file + console + Discord (webhook hoac bot token). */
public final class AlertLogger {

    private final BtnAnticheatPlugin plugin;
    private final File logFile;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String API = "https://discord.com/api/v10/channels/";

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

        // ghi file + gui Discord (async) de khong chan main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            appendFile(line);
            sendDiscord(line);
        });
    }

    /** Ghi log thô (khong theo dinh dang alert). */
    public void logRaw(String line) {
        plugin.getLogger().warning("[ANTICHEAT] " + line);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            appendFile(line);
            sendDiscord(line);
        });
    }

    /** Gui 1 dong len Discord theo cau hinh (webhook uu tien hon bot token). */
    private void sendDiscord(String line) {
        ConfigurationSection dc = plugin.getConfig().getConfigurationSection("discord");
        if (dc == null) return;

        String webhook = dc.getString("webhook-url", "");
        if (webhook != null && !webhook.isBlank()) {
            post(webhook, null, line);
            return;
        }

        String token = dc.getString("bot-token", "");
        String channel = dc.getString("channel-id", "");
        if (token != null && !token.isBlank() && channel != null && !channel.isBlank()) {
            post(API + channel.trim() + "/messages", token, line);
        }
    }

    /**
     * POST noi dung len Discord.
     * @param url   webhook URL hoac API messages URL
     * @param token bot token (null neu dung webhook)
     */
    private void post(String url, String token, String line) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            if (token != null) c.setRequestProperty("Authorization", "Bot " + token);
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);

            String content = "{\"content\": \"[Btn-anticheat] " + escape(line) + "\"}";
            try (OutputStream os = c.getOutputStream()) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
            }

            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                // KHONG log url (co the chua token/webhook)
                plugin.getLogger().warning("Discord tra ve HTTP " + code
                        + " - kiem tra lai token/channel-id hoac quyen gui tin");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Khong gui duoc Discord: "
                    + sanitize(String.valueOf(e.getMessage()), token));
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void appendFile(String line) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(logFile, true))) {
            w.write(line);
            w.newLine();
        } catch (Exception e) {
            plugin.getLogger().warning("Khong ghi duoc log: " + e.getMessage());
        }
    }

    /** Xoa token khoi chuoi truoc khi log (tranh lo secret). */
    private String sanitize(String s, String token) {
        if (s == null) return "(khong ro)";
        if (token != null && !token.isBlank()) s = s.replace(token, "***");
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
