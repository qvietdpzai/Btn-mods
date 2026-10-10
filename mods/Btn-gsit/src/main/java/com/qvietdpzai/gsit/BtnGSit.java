package com.qvietdpzai.gsit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Btn-gsit: ngoi tren slab/bac thang/carpet. */
public final class BtnGSit extends JavaPlugin implements Listener {

    private final Map<UUID, ArmorStand> seats = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        // Don seat rac (nguoi choi thoat ma quen go seat)
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID id : Set.copyOf(seats.keySet())) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !p.isOnline()) removeSeat(id);
                }
            }
        }.runTaskTimer(this, 200L, 600L);
        getLogger().info("Btn-gsit enabled");
    }

    @Override
    public void onDisable() {
        for (UUID id : Set.copyOf(seats.keySet())) removeSeat(id);
    }

    private boolean isSittable(Material type) {
        for (String s : getConfig().getStringList("blocks")) {
            if (type.name().equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        Block b = event.getClickedBlock();
        if (b == null) return;

        if (seats.containsKey(p.getUniqueId())) { standUp(p); return; }
        if (!isSittable(b.getType())) return;
        if (!p.hasPermission("btngsit.use")) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (b.getLocation().distanceSquared(p.getLocation()) > 36) return; // xa qua -> bo qua

        if (getConfig().getBoolean("require-empty-hand", true)
                && !p.getInventory().getItemInMainHand().getType().isAir()) {
            p.sendMessage(ChatColor.YELLOW + "Can tay khong de ngoi.");
            return;
        }
        if (!b.getRelative(0, 1, 0).getType().isAir()) return;
        sit(p, b);
    }

    private void sit(Player p, Block b) {
        if (seats.containsKey(p.getUniqueId())) return;
        boolean slab = b.getType().name().endsWith("SLAB");
        Location seatLoc = b.getLocation().add(0.5, slab ? 0.55 : 0.35, 0.5);
        try {
            ArmorStand seat = b.getWorld().spawn(seatLoc, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setBasePlate(false);
                stand.setAI(false);
                stand.setPersistent(false);
                stand.setCanPickupItems(false);
            });
            if (!seat.addPassenger(p)) {
                seat.remove();
                p.sendMessage(ChatColor.RED + "Khong the ngoi o day.");
                return;
            }
            seats.put(p.getUniqueId(), seat);
        } catch (Exception e) {
            getLogger().warning("Loi khi tao cho ngoi: " + e.getMessage());
        }
    }

    /** Cho nguoi choi dung day. */
    public boolean standUp(Player p) {
        ArmorStand seat = seats.remove(p.getUniqueId());
        if (seat == null) return false;
        try {
            seat.eject();
        } catch (Exception ignored) { }
        if (seat.isValid()) seat.remove();
        if (p != null && p.isOnline() && getConfig().getBoolean("message", true)) {
            p.sendMessage(ChatColor.GRAY + "Ban da dung day.");
        }
        return true;
    }

    private void removeSeat(UUID id) {
        standUp(Bukkit.getPlayer(id));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) standUp(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { removeSeat(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { removeSeat(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) { standUp(event.getPlayer()); }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player p) standUp(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Dung trong game."); return true; }
        if (seats.containsKey(p.getUniqueId())) { standUp(p); return true; }
        Block target = p.getTargetBlockExact(6);
        if (target == null || !isSittable(target.getType())) {
            p.sendMessage(ChatColor.YELLOW + "Nhin vao slab/bac thang/carpet roi goi lai lenh.");
            return true;
        }
        if (!p.hasPermission("btngsit.use")) {
            p.sendMessage(ChatColor.RED + "Ban khong co quyen ngoi.");
            return true;
        }
        sit(p, target);
        p.sendMessage(ChatColor.GREEN + "Ban dang ngoi. Nhan Shift hoac /sit de dung day.");
        return true;
    }
}
