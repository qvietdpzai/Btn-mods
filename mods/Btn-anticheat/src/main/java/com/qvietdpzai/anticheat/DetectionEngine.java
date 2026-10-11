package com.qvietdpzai.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Btn-anticheat: phat hien cheat (fly/speed/reach/killaura/nofall/velocity/phase/autoclick). */
public final class DetectionEngine implements Listener {

    private final Map<UUID, PlayerState> states = new HashMap<>();
    private BtnAnticheatPlugin plugin;

    // ---------- cau hinh ----------
    private boolean enabled(String check) {
        return plugin.getConfig().getBoolean("checks." + check + ".enabled", true);
    }
    private int threshold(String check, String key, int def) {
        return plugin.getConfig().getInt("checks." + check + "." + key, def);
    }

    public void register(BtnAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    private double tps() {
        try {
            var m = plugin.getServer().getClass().getMethod("getTPS");
            double[] t = (double[]) m.invoke(plugin.getServer());
            return t != null && t.length > 0 ? t[0] : 20;
        } catch (Exception e) { return 20; }
    }

    private PlayerState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new PlayerState());
    }

    /** Nguoi choi co dkien mien kiem tra khong? */
    private boolean exempt(Player p) {
        if (p.hasPermission("btnanticheat.bypass")) return true;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            if (plugin.getConfig().getBoolean("skip-creative", true)) return true;
        }
        if (p.isInsideVehicle()) return true;
        if (p.isGliding()) return true;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION)) return true;
        if (p.isDead()) return true;
        // dang bi knockback ngan gan -> bo qua check van toc mot tick
        PlayerState st = state(p);
        if (System.currentTimeMillis() - st.lastVelocityMs < 500) return true;
        // server dang lag -> bo qua (tranh false positive)
        if (tps() < plugin.getConfig().getDouble("min-tps", 17.0)) return true;
        if (p.getPing() > plugin.getConfig().getInt("max-ping", 400)) return true;
        return false;
    }

    private void violate(Player p, String check, String detail) {
        PlayerState st = state(p);
        st.violations++;
        st.level = Math.min(20, st.level + 2);
        st.lastViolationMs = System.currentTimeMillis();

        plugin.logger().log(p.getName(), check, detail, p.getPing(), tps());

        // thong bao OP trong game
        String prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("alert-prefix", "&8[&c!&8] &7"));
        String msg = prefix + ChatColor.RED + p.getName() + ChatColor.GRAY + " | " + check + " | " + detail;
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.hasPermission("btnanticheat.alerts")) op.sendMessage(msg);
        }

        // hanh dong
        String action = plugin.getConfig().getString("action", "log");
        int maxVio = plugin.getConfig().getInt("max-violations-before-action", 15);
        if (st.violations >= maxVio) {
            if (action.equalsIgnoreCase("kick")) {
                p.kickPlayer(ChatColor.RED + "[Btn-anticheat] Phat hien hanh vi khong hop le: " + check);
            } else if (action.equalsIgnoreCase("ban")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + p.getName() + " [anticheat] " + check);
            }
            if (!action.equalsIgnoreCase("log")) st.violations = 0;
        }
    }

    // ---------- MOVEMENT: speed / fly / phase ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (exempt(p)) { fallTicksReset(event, p); return; }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld()) return;

        PlayerState st = state(p);

        // ---- SPEED ----
        if (enabled("speed")) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            double max = 0.55; // base gioi han 1 tick (sprint+speed cap 1 ~0.45)
            if (p.isSprinting()) max *= 1.3;
            if (p.hasPotionEffect(PotionEffectType.SPEED)) {
                int amp = p.getPotionEffect(PotionEffectType.SPEED).getAmplifier();
                max *= 1.4 + 0.5 * amp;
            }
            if (p.getWalkSpeed() > 0.25f) max *= p.getWalkSpeed() / 0.2f;
            max += 0.15; // buffer cho ice/soulspeed/dolphin
            if (p.getWalkSpeed() <= 0) max = 10;

            if (dist > max && (from.getY() == to.getY() || dist > max + 0.3)) {
                if (dist > max * 2.2) violate(p, "Speed", String.format("di %.2f block/tick (max %.2f) @ %s",
                        dist, max, loc(to)));
            }
        }

        // ---- FLY / AIR ----
        if (enabled("fly")) {
            boolean onGround = isOnGround(p, to);
            if (onGround) {
                st.airTicks = 0;
                st.lastGroundMs = System.currentTimeMillis();
                st.fallTicks = 0;
            } else {
                st.airTicks++;
                double dy = to.getY() - from.getY();
                if (dy < 0) { st.fallTicks++; st.maxFallSpeed = Math.max(st.maxFallSpeed, -dy); }
                else st.fallTicks = 0;

                int airLimit = threshold("fly", "air-ticks", 40);
                if (st.airTicks > airLimit && st.fallTicks > airLimit) {
                    violate(p, "Fly", String.format("bay lien tuc %d tick (roi tu y=%.1f)",
                            st.airTicks, st.maxFallSpeed));
                    st.airTicks = 0;
                }
                // bay nam ngang khong roi (glide spoof)
                if (st.airTicks > airLimit * 2 && Math.abs(dy) < 0.001) {
                    violate(p, "Fly", String.format("dung giua khong khi %d tick", st.airTicks));
                }
            }
        }

        // ---- PHASE (di xuyen tuong) ----
        if (enabled("phase")) {
            Block fromBlock = to.getBlock();
            if (fromBlock.getType().isSolid() && !fromBlock.isLiquid()) {
                // neu chuot vao block do ma khong phai do teleport/an qua portal
                if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()
                        || from.getBlockY() != to.getBlockY()) {
                    if (event.getFrom().distanceSquared(event.getTo()) < 2) {
                        violate(p, "Phase", String.format("di xuyen block %s @ %s",
                                fromBlock.getType(), loc(to)));
                    }
                }
            }
        }
    }

    private void fallTicksReset(PlayerMoveEvent event, Player p) {
        PlayerState st = state(p);
        if (isOnGround(p, event.getTo())) {
            st.airTicks = 0;
            st.fallTicks = 0;
            st.maxFallSpeed = 0;
        }
    }

    private boolean isOnGround(Player p, Location l) {
        if (p.isFlying() || p.getAllowFlight() && p.getGameMode() == GameMode.CREATIVE) return true;
        try {
            return l.clone().subtract(0, 0.05, 0).getBlock().getType().isSolid()
                    || l.getBlock().getType().isSolid();
        } catch (Exception e) { return true; }
    }

    // ---------- COMBAT: reach / killaura / noswing / autoclick ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (exempt(p)) return;

        Entity victim = event.getEntity();
        PlayerState st = state(p);
        long now = System.currentTimeMillis();

        // dem tan cong/giay (auto clicker)
        if (now - st.secondWindowStart > 1000) {
            st.secondWindowStart = now;
            st.attacksThisSecond = 0;
        }
        st.attacksThisSecond++;
        int cpsLimit = threshold("autoclick", "max-cps", 20);
        if (enabled("autoclick") && st.attacksThisSecond > cpsLimit) {
            violate(p, "AutoClick", st.attacksThisSecond + " click/giay");
        }

        // ---- REACH ----
        if (enabled("reach")) {
            Location eye = p.getEyeLocation();
            double dist = eye.distance(victim.getLocation()) - 0.6;
            double distEye = eye.distance(eyeOf(victim));
            double maxReach = threshold("reach", "max-distance", 6) + 0.8;
            if (dist > maxReach && distEye > maxReach) {
                violate(p, "Reach", String.format("danh tu %.2f block (max %.2f)", distEye, maxReach - 0.8));
            }
        }

        // ---- KILLAURA: danh khi khong nhin vao dich ----
        if (enabled("killaura")) {
            Vector dir = p.getEyeLocation().getDirection();
            Vector toVictim = victim.getLocation().toVector()
                    .add(new Vector(0, victim.getHeight() / 2, 0))
                    .subtract(p.getEyeLocation().toVector());
            toVictim = normalize(toVictim);
            double angle = Math.toDegrees(dir.angle(toVictim));
            double maxAngle = threshold("killaura", "max-angle", 70);
            if (angle > maxAngle) {
                violate(p, "KillAura", String.format("danh %.0f do lech huong nhin (%.0f mo dich)", angle, victim.getType()));
            }
        }

        // ---- NO SWING: danh ma khong vay tay ----
        if (enabled("noswing")) {
            long sinceSwing = now - st.lastSwingMs;
            int maxSince = threshold("noswing", "max-ms-since-swing", 350);
            if (sinceSwing > maxSince && st.lastAttackMs != now) {
                violate(p, "NoSwing", "tan cong ma khong vay tay (" + sinceSwing + "ms)");
            }
        }

        st.lastAttackMs = now;
        st.lastVictim = victim.getUniqueId();

        // ---- VELOCITY: bat dau theo doi knockback ----
        st.lastVelocityMs = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnim(PlayerAnimationEvent event) {
        state(event.getPlayer()).lastSwingMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (exempt(p)) return;
        if (!enabled("nofall")) return;

        PlayerState st = state(p);
        // roi tu do cao nhung khong nhan sat thuong / nhan qua it
        double fallDist = event.getFinalDamage() > 0 ? event.getDamage() : p.getFallDistance();
        if (p.getFallDistance() > 12 && event.getFinalDamage() <= 0) {
            violate(p, "NoFall", String.format("roi %.1f block khong nhan sat thuong", p.getFallDistance()));
        }
    }

    // ---------- fast place ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled("fastplace")) return;
        Player p = event.getPlayer();
        if (exempt(p)) return;
        PlayerState st = state(p);
        long now = System.currentTimeMillis();
        if (now - st.secondWindowStart > 1000) {
            st.secondWindowStart = now;
            st.attacksThisSecond = 0;
        }
        st.attacksThisSecond++;
        int limit = threshold("fastplace", "max-placement-cps", 25);
        if (st.attacksThisSecond > limit) {
            violate(p, "FastPlace", st.attacksThisSecond + " block/giay");
        }
    }

    // ---------- teleport ignore ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerState st = state(event.getPlayer());
        st.airTicks = 0;
        st.fallTicks = 0;
        st.lastVelocityMs = System.currentTimeMillis();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        states.put(event.getPlayer().getUniqueId(), new PlayerState());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    // ---------- decay ----------

    public void tickDecay() {
        long now = System.currentTimeMillis();
        for (PlayerState st : states.values()) {
            if (now - st.lastViolationMs > 30000) st.decay();
        }
    }

    public Map<UUID, PlayerState> states() { return states; }

    // ---------- tien ich ----------

    private static Vector normalize(Vector v) {
        double len = v.length();
        if (len == 0) return v.clone();
        return v.clone().multiply(1.0 / len);
    }

    private static Location eyeOf(Entity e) {
        return e.getLocation().add(0, e.getHeight() * 0.85, 0);
    }

    private static String loc(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
