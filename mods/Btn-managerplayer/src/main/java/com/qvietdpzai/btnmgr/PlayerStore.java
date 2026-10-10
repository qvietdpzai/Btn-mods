package com.qvietdpzai.btnmgr;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lưu/đọc PlayerData dạng YAML, 1 file cho mỗi người chơi. */
public class PlayerStore {

    private final BtnManagerPlayer plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    public PlayerStore(BtnManagerPlayer plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        if (!dir.exists()) dir.mkdirs();
    }

    public PlayerData get(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, k -> load(k, name));
    }

    public PlayerData getByName(String name) {
        UUID id = nameToUuid.get(name.toLowerCase());
        if (id != null) {
            PlayerData d = cache.get(id);
            if (d != null) return d;
        }
        for (PlayerData d : cache.values()) {
            if (d.name.equalsIgnoreCase(name)) return d;
        }
        // chưa có trong cache: thử đọc file theo tên đã biết (offline player)
        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(name);
        if (off != null && off.getUniqueId() != null) {
            return get(off.getUniqueId(), off.getName() != null ? off.getName() : name);
        }
        return null;
    }

    private PlayerData load(UUID uuid, String name) {
        File f = file(uuid);
        PlayerData data = new PlayerData(new UUIDRef(uuid), name);
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            data.firstJoin = y.getLong("first-join", -1);
            data.lastJoin = y.getLong("last-join", -1);
            data.lastSeen = y.getLong("last-seen", -1);
            data.playtimeMs = y.getLong("playtime-ms", 0);
            data.name = y.getString("name", name);
            data.deaths = y.getInt("deaths");
            data.playerKills = y.getInt("player-kills");
            data.mobKills = y.getInt("mob-kills");
            data.blocksBroken = y.getInt("blocks-broken");
            data.blocksPlaced = y.getInt("blocks-placed");
            data.commandsUsed = y.getInt("commands-used");
            data.itemsDropped = y.getInt("items-dropped");
            data.itemsPicked = y.getInt("items-picked");
            data.chatMessages = y.getInt("chat-messages");
            data.lastCommand = y.getString("last-command");
            data.lastBlockBroken = y.getString("last-block-broken");
            data.lastBlockPlaced = y.getString("last-block-placed");
            data.lastChat = y.getString("last-chat");
            data.lastInteract = y.getString("last-interact");
            data.lastDeathCause = y.getString("last-death-cause");
            data.lastCommandAt = y.getLong("last-command-at");
            data.lastBlockBrokenAt = y.getLong("last-block-broken-at");
            data.lastBlockPlacedAt = y.getLong("last-block-placed-at");
            data.lastChatAt = y.getLong("last-chat-at");
            data.lastDeathAt = y.getLong("last-death-at");
        }
        nameToUuid.put(data.name.toLowerCase(), uuid);
        return data;
    }

    public void save(PlayerData d) {
        File f = file(d.uuid.id());
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", d.name);
        y.set("first-join", d.firstJoin);
        y.set("last-join", d.lastJoin);
        y.set("last-seen", d.lastSeen);
        y.set("playtime-ms", d.playtimeMs);
        y.set("deaths", d.deaths);
        y.set("player-kills", d.playerKills);
        y.set("mob-kills", d.mobKills);
        y.set("blocks-broken", d.blocksBroken);
        y.set("blocks-placed", d.blocksPlaced);
        y.set("commands-used", d.commandsUsed);
        y.set("items-dropped", d.itemsDropped);
        y.set("items-picked", d.itemsPicked);
        y.set("chat-messages", d.chatMessages);
        y.set("last-command", d.lastCommand);
        y.set("last-block-broken", d.lastBlockBroken);
        y.set("last-block-placed", d.lastBlockPlaced);
        y.set("last-chat", d.lastChat);
        y.set("last-interact", d.lastInteract);
        y.set("last-death-cause", d.lastDeathCause);
        y.set("last-command-at", d.lastCommandAt);
        y.set("last-block-broken-at", d.lastBlockBrokenAt);
        y.set("last-block-placed-at", d.lastBlockPlacedAt);
        y.set("last-chat-at", d.lastChatAt);
        y.set("last-death-at", d.lastDeathAt);
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Khong luu duoc " + d.name + ": " + e.getMessage());
        }
    }

    public void saveAllAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (PlayerData d : cache.values()) save(d);
        });
    }

    public Map<UUID, PlayerData> all() { return cache; }

    public int countRecords() {
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".yml"));
        return fs == null ? 0 : fs.length;
    }

    private File file(UUID uuid) { return new File(dir, uuid + ".yml"); }
}
