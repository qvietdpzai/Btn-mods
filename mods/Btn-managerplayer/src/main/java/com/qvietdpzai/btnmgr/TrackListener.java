package com.qvietdpzai.btnmgr;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

/** Theo dõi mọi hoạt động của người chơi: ở đâu, làm gì. */
public class TrackListener implements Listener {

    private final BtnManagerPlayer plugin;

    public TrackListener(BtnManagerPlayer plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        PunishmentManager.Punishment ban = plugin.punishments().getBan(event.getPlayer().getUniqueId());
        long now = System.currentTimeMillis();
        if (ban != null && ban.isActive(now)) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    ChatColor.RED + "Ban " + ban.left(now) + ChatColor.GRAY + " | Ly do: " + ban.reason);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        if (d.firstJoin < 0) d.firstJoin = now;
        d.lastJoin = now;
        d.lastSeen = now;
        d.sessionStart = now;
        plugin.broadcastWatch(d.uuid.id(), d.name + " vao game (" + plugin.loc(p) + ")");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        if (d.sessionStart > 0) {
            d.playtimeMs += Math.max(0, now - d.sessionStart);
            d.sessionStart = -1;
        }
        d.lastSeen = now;
        plugin.store().save(d);
        plugin.removeWatchersOf(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        String msg = event.getMessage();
        d.commandsUsed++;
        d.lastCommand = msg;
        d.lastCommandAt = now;
        plugin.broadcastWatch(p.getUniqueId(), "/" + msg);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());

        String mute = plugin.punishments().muteInfo(p.getUniqueId(), now);
        if (mute != null) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Ban dang bi mute (" + mute + ")");
            return;
        }

        d.chatMessages++;
        d.lastChat = event.getMessage().length() > 120
                ? event.getMessage().substring(0, 120) + "..." : event.getMessage();
        d.lastChatAt = now;
        plugin.broadcastWatch(p.getUniqueId(), "chat: " + d.lastChat);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block b = event.getBlock();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.blocksBroken++;
        d.lastBlockBroken = b.getType() + " @ " + plugin.loc(b.getLocation());
        d.lastBlockBrokenAt = now;
        plugin.broadcastWatch(p.getUniqueId(), "pha " + b.getType() + " @ " + plugin.loc(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        Block b = event.getBlock();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.blocksPlaced++;
        d.lastBlockPlaced = b.getType() + " @ " + plugin.loc(b.getLocation());
        d.lastBlockPlacedAt = now;
        plugin.broadcastWatch(p.getUniqueId(), "dat " + b.getType() + " @ " + plugin.loc(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.deaths++;
        d.lastDeathCause = event.getDeathMessage() == null ? "khong ro" : event.getDeathMessage();
        d.lastDeathAt = now;
        plugin.broadcastWatch(p.getUniqueId(), "chet: " + d.lastDeathCause);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        long now = System.currentTimeMillis();
        PlayerData d = plugin.store().get(killer.getUniqueId(), killer.getName());
        if (event.getEntity() instanceof Player) d.playerKills++;
        else d.mobKills++;
        plugin.broadcastWatch(killer.getUniqueId(),
                "giet " + event.getEntityType() + " @ " + plugin.loc(event.getEntity().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.itemsDropped++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Player p = event.getPlayer();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.itemsPicked++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player p = event.getPlayer();
        Block b = event.getClickedBlock();
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.lastInteract = b.getType() + " @ " + plugin.loc(b.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.lastInteract = "mo " + event.getInventory().getType() + " " + plugin.loc(p.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        PlayerData d = plugin.store().get(p.getUniqueId(), p.getName());
        d.lastInteract = "tan cong " + event.getEntityType() + " @ " + plugin.loc(event.getEntity().getLocation());
    }

    // Cập nhật hoạt động để tính AFK
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        plugin.store().get(p.getUniqueId(), p.getName()).lastSeen = now;
    }
}
