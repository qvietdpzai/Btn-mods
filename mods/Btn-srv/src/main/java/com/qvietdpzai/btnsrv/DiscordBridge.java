package com.qvietdpzai.btnsrv;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Cau noi Discord: nhan tin (Gateway WebSocket) va gui tin (webhook hoac bot).
 * Khong phu thuoc thu vien ngoai - dung java.net.http + Json tu chua.
 */
public final class DiscordBridge {

    private static final String GATEWAY = "wss://gateway.discord.gg/?v=10&encoding=json";
    private static final String API = "https://discord.com/api/v10";

    private final BtnSrvPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private java.net.http.WebSocket ws;
    private ScheduledExecutorService heartbeats;
    private ScheduledFuture<?> hbTask;
    private Thread gatewayThread;

    private volatile boolean running;
    private volatile String botId;
    private String sessionId;
    private int seq = -1;
    private volatile String channelId;
    private String heartbeatInterval = "41250";
    private int backoffMs = 1000;
    private long lastAck;

    public DiscordBridge(BtnSrvPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------- vong doi ----------------

    /**
     * Gateway URL: mac dinh Discord that sua URL.
     * (Khong can thay doi, de test dung URL gia lap ws://).) */
    private String gatewayUrl() {
        String cfg = plugin.cfg() != null ? plugin.cfg().getString("discord.gateway-url", "") : "";
        return (cfg == null || cfg.isBlank()) ? GATEWAY : cfg.trim();
    }

    public void start() {
        String token = plugin.cfg().getString("discord.bot-token", "").trim();
        if (token.isBlank()) {
            plugin.getLogger().warning("Btn-srv: chua co bot-token -> chi gui tin len Discord (khong nhan duoc tin tu Discord)");
            return;
        }
        running = true;
        lastAck = System.currentTimeMillis();
        gatewayThread = new Thread(this::runLoop, "Btn-srv-gateway");
        gatewayThread.setDaemon(true);
        gatewayThread.start();
    }

    public void stop() {
        running = false;
        // mo latch va huy heartbeat de connect() (neu dang cho) thoat ngay
        try { gateClose.countDown(); } catch (Exception ignored) { }
        if (hbTask != null) hbTask.cancel(true);
        if (heartbeats != null) heartbeats.shutdownNow();
        try { if (ws != null) ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS); }
        catch (Exception ignored) { }
    }

    /** Vong ket noi + tu dong ket noi lai (exponential backoff, toi da 60s). */
    private void runLoop() {
        while (running) {
            try {
                connect();
                if (!running) break;
            } catch (Exception e) {
                if (running) plugin.getLogger().log(Level.WARNING,
                        "Btn-srv: loi gateway (" + String.valueOf(e.getMessage()) + "), thu lai sau " + backoffMs + "ms", e);
            }
            if (!running) break;
            try { Thread.sleep(backoffMs); } catch (InterruptedException e) { return; }
            backoffMs = Math.min(backoffMs * 2, 60000);
        }
    }

    private void connect() throws Exception {
        plugin.getLogger().info("Btn-srv: dang ket noi Discord gateway...");

        // tat heartbeat executor cu (tranh ro ri thread khi ket noi lai)
        if (heartbeats != null) heartbeats.shutdownNow();

        heartbeats = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Btn-srv-heartbeat");
            t.setDaemon(true);
            return t;
        });

        // latch moi cho moi ket noi: onClose se mo -> biet khi nao bi ngat
        gateClose = new java.util.concurrent.CountDownLatch(1);
        lastAck = System.currentTimeMillis();

        ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(gatewayUrl()), new wsListener())
                .get(20, TimeUnit.SECONDS);   // chi cho toi khi HANDSHAKE xong

        // QUAN TRONG: drain o day toi khi ket noi bi dong.
        // Neu khong, vong lap se tao ket noi moi trong khi ket noi cu van mo
        // -> hang tram session cung luc, Discord rate-limit roi dong tat ca.
        while (running) {
            if (gateClose.await(1, TimeUnit.SECONDS)) break;
        }
    }

    // ---------------- gateway events ----------------

    private final class wsListener implements java.net.http.WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(java.net.http.WebSocket w) {
            w.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String msg = buf.toString();
                buf.setLength(0);
                handle(msg);
            }
            w.request(1);
            return null;
        }

        @Override
        public void onError(java.net.http.WebSocket w, Throwable error) {
            if (running) plugin.getLogger().log(Level.WARNING, "Btn-srv: loi websocket", error);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket w, int code, String reason) {
            if (running) plugin.getLogger().info("Btn-srv: gateway dong (code " + code + ") - se tu ket noi lai");
            if (hbTask != null) hbTask.cancel(true);
            try { gateClose.countDown(); } catch (Exception ignored) { }
            return null;
        }
    }

    /** Mo khi WebSocket bi dong, de connect() biet phai ket noi lai. Khong final vi doi moi khi ket noi. */
    private volatile java.util.concurrent.CountDownLatch gateClose = new java.util.concurrent.CountDownLatch(1);

    /** Xu ly 1 frame JSON tu gateway. */
    private void handle(String raw) {
        try {
            Map<?, ?> m = Json.parseObj(raw);
            Object opObj = m.get("op");
            if (!(opObj instanceof Number opNum)) return;
            int op = opNum.intValue();
            Object d = m.get("d");
            Object s = m.get("s");
            if (s instanceof Number sn) seq = sn.intValue();

            switch (op) {
                case 10 -> { // Hello -> bat dau heartbeat + IDENTIFY
                    if (d instanceof Map<?, ?> hello) {
                        Object hb = hello.get("heartbeat_interval");
                        if (hb instanceof Number n) heartbeatInterval = String.valueOf(n.longValue());
                    }
                    startHeartbeats();
                    identify();
                    // KHONG reset backoff o day: reset nhieu lan lam backoff khong bao gio tang,
                    // ket noi lai mai moi 1s -> bi Discord rate-limit.
                }
                case 11 -> lastAck = System.currentTimeMillis(); // Heartbeat ACK
                case 7, 9 -> { // Reconnect / Invalid Session
                    if (running) {
                        try { ws.sendClose(1000, "reconnect"); } catch (Exception ignored) { }
                    }
                }
                case 0 -> {
                    Object t = m.get("t");
                    if ("READY".equals(t) && d instanceof Map<?, ?> ready) {
                        Object u = ready.get("user");
                        if (u instanceof Map<?, ?> um) {
                            botId = String.valueOf(um.get("id"));
                        }
                        Object sess = ready.get("session_id");
                        if (sess != null) sessionId = String.valueOf(sess);
                        plugin.getLogger().info("Btn-srv: da ket noi Discord, bot id=" + botId);

                        // *** Bot co nam trong server Discord nao khong? ***
                        // READY.d.guilds rong = bot CHUA duoc moi vao server nao
                        // -> khong nghe/gui duoc tin o kenh nao ca.
                        Object guilds = ready.get("guilds");
                        int n = (guilds instanceof List<?> gl) ? gl.size() : 0;
                        plugin.getLogger().info("Btn-srv: bot nam trong " + n + " server Discord");
                        if (n == 0) {
                            if (botId != null) {
                                plugin.getLogger().warning("=================================================");
                                plugin.getLogger().warning("Bot CHUA duoc moi vao server Discord nao!");
                                plugin.getLogger().warning("Mo link nay de moi bot vao:");
                                plugin.getLogger().warning("  https://discord.com/oauth2/authorize?client_id="
                                        + botId + "&permissions=84992&scope=bot");
                                plugin.getLogger().warning("Neu link loi, lay lai Client ID tai:");
                                plugin.getLogger().warning("  https://discord.com/developers/applications -> chon app -> OAuth2");
                                plugin.getLogger().warning("=================================================");
                            }
                        }

                        resolveChannelAsync();
                    } else if ("MESSAGE_CREATE".equals(t) && d instanceof Map<?, ?> msg) {
                        onMessage(msg);
                    }
                }
                default -> { }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Btn-srv: khong doc duoc goi gateway: " + e.getMessage(), e);
        }
    }

    private void startHeartbeats() {
        long iv;
        try { iv = Long.parseLong(heartbeatInterval); } catch (NumberFormatException e) { iv = 41250; }
        long period = Math.max(5000, iv);
        if (hbTask != null) hbTask.cancel(true);
        hbTask = heartbeats.scheduleAtFixedRate(() -> {
            try {
                if (ws != null && ws.isOutputClosed()) return;
                if (System.currentTimeMillis() - lastAck > period * 3) {
                    // mat heartbeat -> tu ket noi lai
                    if (running) try { ws.sendClose(1000, "no ack"); } catch (Exception ignored) { }
                    return;
                }
                send(Json.obj("op", 1, "d", seq));
            } catch (Exception ignored) { }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    private void identify() {
        String token = plugin.cfg().getString("discord.bot-token", "").trim();
        int intents = (1 << 0)   // GUILDS
                   | (1 << 9)   // GUILD_MESSAGES
                   | (1 << 15); // MESSAGE_CONTENT (privileged - phai bat tren Developer Portal)
        String props = Json.obj("os", "linux", "browser", "Btn-srv", "device", "Btn-srv");
        String d = Json.obj("token", token, "intents", intents, "properties", Json.raw(props));
        String body = Json.obj("op", 2, "d", Json.raw(d));
        send(body);
    }

    private void send(String json) {
        try {
            if (ws != null && !ws.isOutputClosed()) ws.sendText(json, true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Btn-srv: gui loi", e);
        }
    }

    // ---------------- nhan tin tu Discord ----------------

    private void onMessage(Map<?, ?> d) {
        // Bo qua tin do webhook/bot tu gui -> chong lap vo han
        if (d.containsKey("webhook_id") && d.get("webhook_id") != null) return;
        if (botId != null && botId.equals(String.valueOf(d.get("author_id")))) return;

        String ch = str(d.get("channel_id"));
        String target = channelId();
        if (target != null && !target.isBlank() && !ch.equals(target)) return; // chi lay 1 kenh

        Object authorObj = d.get("author");
        if (!(authorObj instanceof Map<?, ?> author)) return;
        String name = str(author.get("global_name"));
        if (name.isBlank()) name = str(author.get("username"));
        if (name.isBlank()) name = "Discord";

        String content = str(d.get("content"));
        if (content.isBlank()) return;

        // tin nho -> bo qua
        Object type = d.get("type");
        if (type instanceof Number tn && tn.intValue() != 0) return;

        // loai bo mention voi @everyone/@here
        content = content.replace("@everyone", "@ everyone").replace("@here", "@ here");

        rankAndBroadcast(name, content, d);
    }

    private void rankAndBroadcast(String author, String content, Map<?, ?> raw) {
        // rank Discord = role dau tien trong config mapping hoac mac dinh
        String rank = discordRank(raw);
        plugin.runSync(() -> plugin.broadcastFromDiscord(author, rank, content));
    }

    /** Lay rank Discord theo mapping role-id -> ten rank trong config. */
    private String discordRank(Map<?, ?> raw) {
        List<?> roles = null;
        if (raw.get("member") instanceof Map<?, ?> member && member.get("roles") instanceof List<?> r) {
            roles = r;
        }
        Map<String, String> map = plugin.cfg().getConfigurationSection("discord.role-map").getValues(false)
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue())));
        if (roles != null) {
            for (Object r : roles) {
                String id = String.valueOf(r);
                String mapped = map.get(id);
                if (mapped != null) return mapped;
            }
        }
        String def = plugin.cfg().getString("discord.default-discord-rank", "Member");
        return def;
    }

    // ---------------- gui tin len Discord ----------------

    /** Gui tin nguoi choi len Discord (webhook uu tien, hien thi rank + ten). */
    public void sendToDiscord(String displayName, String message) {
        String webhook = plugin.cfg().getString("discord.webhook-url", "").trim();
        String content = message.length() > 1900 ? message.substring(0, 1900) + "..." : message;

        if (!webhook.isBlank()) {
            // webhook cho phep dat ten tuy y -> hien thi rank + ten
            String body = Json.obj("username", displayName.substring(0, Math.min(80, displayName.length())),
                    "content", content);
            httpPostAsync(webhook, null, body);
        } else {
            String ch = channelId();
            String token = plugin.cfg().getString("discord.bot-token", "").trim();
            if (ch.isBlank() || token.isBlank()) {
                plugin.getLogger().warning("Btn-srv: chua co webhook-url hoac bot-token -> khong gui duoc tin len Discord");
                return;
            }
            String body = Json.obj("content", content);
            httpPostAsync(API + "/channels/" + ch + "/messages", token, body);
        }
    }

    private void httpPostAsync(String url, String token, String json) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null) b.header("Authorization", "Bot " + token);
        http.sendAsync(b.build(), HttpResponse.BodyHandlers.discarding())
                .thenAccept(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        plugin.getLogger().warning("Btn-srv: Discord tra ve HTTP " + resp.statusCode());
                    }
                })
                .exceptionally(t -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Btn-srv: gui Discord that bai - " + sanitize(String.valueOf(t.getMessage())), t);
                    return null;
                });
    }

    // ---------------- tu dong tim kenh ----------------

    /** Neu chua cau hinh channel-id, tu dong tim kenh text dau tien bot gui duoc. */
    public void resolveChannelAsync() {
        String cfg = plugin.cfg().getString("discord.channel-id", "").trim();
        if (!cfg.isBlank()) { channelId = cfg; return; }
        if (botId == null) return;

        String token = plugin.cfg().getString("discord.bot-token", "").trim();
        http.sendAsync(HttpRequest.newBuilder(URI.create(API + "/users/@me/guilds"))
                        .header("Authorization", "Bot " + token)
                        .timeout(Duration.ofSeconds(10))
                        .GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        plugin.getLogger().warning("Btn-srv: khong lay duoc danh sach server (HTTP " + resp.statusCode() + ")");
                        return;
                    }
                    try {
                        Object parsed = Json.parse(resp.body());
                        if (!(parsed instanceof List<?> guilds) || guilds.isEmpty()) return;
                        for (Object g : guilds) {
                            if (!(g instanceof Map<?, ?> gm)) continue;
                            String gid = str(gm.get("id"));
                            findChannel(gid, token);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Btn-srv: loi doc danh sach server", e);
                    }
                })
                .exceptionally(t -> null);
    }

    private void findChannel(String guildId, String token) {
        http.sendAsync(HttpRequest.newBuilder(URI.create(API + "/guilds/" + guildId + "/channels"))
                        .header("Authorization", "Bot " + token)
                        .timeout(Duration.ofSeconds(10))
                        .GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) return;
                    try {
                        Object parsed = Json.parse(resp.body());
                        if (!(parsed instanceof List<?> chans)) return;
                        for (Object c : chans) {
                            if (!(c instanceof Map<?, ?> cm)) continue;
                            // type 0 = text channel
                            if (cm.get("type") instanceof Number tn && tn.intValue() == 0) {
                                if (channelId == null) {
                                    channelId = String.valueOf(cm.get("id"));
                                    plugin.getLogger().info("Btn-srv: tu dong chon kenh #"
                                            + cm.get("name") + " (" + channelId + ")");
                                }
                                return;
                            }
                        }
                    } catch (Exception ignored) { }
                })
                .exceptionally(t -> null);
    }

    // ---------------- tien ich ----------------

    public String channelId() {
        String c = plugin.cfg().getString("discord.channel-id", "").trim();
        if (!c.isBlank()) return c;
        return channelId == null ? "" : channelId;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private String sanitize(String s) {
        String token = plugin.cfg().getString("discord.bot-token", "").trim();
        if (s == null) return "(khong ro)";
        if (!token.isBlank()) s = s.replace(token, "***");
        String hook = plugin.cfg().getString("discord.webhook-url", "").trim();
        if (!hook.isBlank()) s = s.replace(hook, "***");
        return s.length() > 150 ? s.substring(0, 150) : s;
    }
}
