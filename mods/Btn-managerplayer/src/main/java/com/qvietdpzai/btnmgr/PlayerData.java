package com.qvietdpzai.btnmgr;

import java.util.ArrayDeque;
import java.util.Deque;

/** Dữ liệu theo dõi 1 người chơi (lưu YAML qua PlayerStore). */
public class PlayerData {

    public final UUIDRef uuid;
    public String name;
    public long firstJoin = -1;
    public long lastJoin = -1;
    public long lastSeen = -1;
    public long playtimeMs = 0;
    public int deaths, playerKills, mobKills, blocksBroken, blocksPlaced;
    public int commandsUsed, itemsDropped, itemsPicked, chatMessages;
    public String lastCommand, lastBlockBroken, lastBlockPlaced, lastChat, lastInteract, lastDeathCause;
    public long lastCommandAt, lastBlockBrokenAt, lastBlockPlacedAt, lastChatAt, lastDeathAt;

    // runtime only
    public transient long sessionStart = -1;
    public transient final Deque<String> recentEvents = new ArrayDeque<>(20);

    public PlayerData(UUIDRef uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    /** Tổng thời gian chơi đã tích lũy + session hiện tại. */
    public long totalPlaytime(long now) {
        return playtimeMs + (sessionStart > 0 ? Math.max(0, now - sessionStart) : 0);
    }

    public int afkMinutes(long now, int threshold) {
        long last = Math.max(lastActivityAt(), lastSeen);
        if (last <= 0) return 0;
        return (int) ((now - last) / 60000);
    }

    public boolean isAfk(long now, int thresholdMinutes) {
        return sessionStart > 0 && afkMinutes(now, thresholdMinutes) >= thresholdMinutes;
    }

    public long lastActivityAt() {
        long t = lastSeen;
        t = Math.max(t, lastCommandAt);
        t = Math.max(t, lastBlockBrokenAt);
        t = Math.max(t, lastBlockPlacedAt);
        t = Math.max(t, lastChatAt);
        t = Math.max(t, lastDeathAt);
        return t;
    }

    public void addEvent(String line) {
        if (recentEvents.size() >= 20) recentEvents.pollFirst();
        recentEvents.addLast(line);
    }
}
