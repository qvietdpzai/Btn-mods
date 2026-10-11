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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Btn-anticheat: phat hien cheat (fly/speed/reach/killaura/nofall/velocity/phase/autoclick). */
public final class DetectionEngine implements Listener {

    private final Map<UUID, PlayerState> states = new HashMap<>();
    private BtnAnticheatPlugin plugin;
    private java.util.Set<String> exemptWorlds = java.util.Collections.emptySet();

    // ---------- cau hinh ----------
    private boolean enabled(String check) {
        return plugin.getConfig().getBoolean("checks." + check + ".enabled", true);
    }
    private int threshold(String check, String key, int def) {
        return plugin.getConfig().getInt("checks." + check + "." + key, def);
    }
    private double thresholdD(String check, String key, double def) {
        return plugin.getConfig().getDouble("checks." + check + "." + key, def);
    }
    private boolean exemptWorld(Player p) {
        return exemptWorlds.contains(p.getWorld().getName());
    }

    public void register(BtnAnticheatPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    /** Nap lai cau hinh dong (goi tu /anticheat reload). */
    public void reloadConfig() {
        List<?> worlds = plugin.getConfig().getList("exempt-worlds");
        if (worlds != null) {
            exemptWorlds = worlds.stream().map(Object::toString)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } else {
            exemptWorlds = java.util.Collections.emptySet();
        }
    }

    public java.util.Set<String> exemptWorldNames() { return exemptWorlds; }

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
        if (exemptWorld(p)) return true;
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
        st.lastViolationMs = System.currentTimeMillis();
        st.level = Math.min(20, st.level + 2); // 2 diem/vi pham, toi da 20

        plugin.logger().log(p.getName(), check, detail, p.getPing(), tps());

        // thong bao OP trong game (truoc khi bi ban ra)
        String prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("alert-prefix", "&8[&c!&8] &7"));
        String msg = prefix + ChatColor.RED + p.getName() + ChatColor.GRAY + " | " + check + " | " + detail;
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.hasPermission("btnanticheat.alerts")) op.sendMessage(msg);
        }

        // trung phat leo thang (ban 1 phut -> 30 phut -> vinh vien)
        plugin.punishments().handle(p, check, detail);
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

        // Nguoi choi dang trong chat long (boi/nuoc/cot khi/dung nham):
        // khong xet bay va khong xet toc do -> tranh bao loi nham nguoi boi bi coi la bay
        boolean inLiquid = inLiquid(p, to);

        // ---- SPEED ----
        if (enabled("speed") && !inLiquid) {
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
            max += thresholdD("speed", "buffer", 0.25); // buffer cho ice/soulspeed/dolphin
            if (p.getWalkSpeed() <= 0) max = 10;

            if (dist > max && (from.getY() == to.getY() || dist > max + 0.3)) {
                if (dist > max * 2.2) violate(p, "Speed", String.format("di %.2f block/tick (max %.2f) @ %s",
                        dist, max, loc(to)));
            }
        }

        // ---- FLY / AIR ----
        if (enabled("fly")) {
            if (inLiquid) {
                // dang boi: reset dem, khong bao bay
                st.airTicks = 0;
                st.fallTicks = 0;
            } else {
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

                    int airLimit = threshold("fly", "air-ticks", 60);
                    // cho phep elytra
                    boolean allowElytra = plugin.getConfig().getBoolean("checks.fly.allow-elytra", true);
                    if (allowElytra && p.isGliding()) {
                        st.airTicks = 0; // reset de khong bao loi
                    } else if (st.airTicks > airLimit && st.fallTicks > airLimit) {
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
        if (isOnGround(p, event.getTo()) || inLiquid(p, event.getTo())) {
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

    /**
     * Nguoi choi co dang o trong chat long (boi nuoc, cot khi, dung nham, lava)?
     * Khi boi, nguoi choi khong dung tren block solid nen isOnGround() = false.
     * Neu khong loai tru, boi 3 giay se bi bao la "bay".
     */
    private boolean inLiquid(Player p, Location l) {
        try {
            if (l.getBlock().isLiquid()) return true;
            Location feet = l.clone().subtract(0, 0.15, 0);
            if (feet.getBlock().isLiquid()) return true;
            // dau/nguc co the chim duoi nuoc
            Location head = l.clone().add(0, 0.9, 0);
            if (head.getBlock().isLiquid()) return true;
        } catch (Exception ignored) { }
        return p.isSwimming();
    }

    // ---------- COMBAT: reach / killaura / autoclick ----------

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
            double maxReach = threshold("reach", "max-distance", 6) + thresholdD("reach", "buffer", 1.0);
            if (dist > maxReach && distEye > maxReach) {
                violate(p, "Reach", String.format("danh tu %.2f block (max %.2f)", distEye, maxReach - thresholdD("reach", "buffer", 1.0)));
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
            double maxAngle = threshold("killaura", "max-angle", 90);
            if (angle > maxAngle) {
                boolean ignoreBehind = plugin.getConfig().getBoolean("checks.killaura.ignore-behind", true);
                if (ignoreBehind && angle > 150) {
                    // bo qua neu dich o phia sau gan 180 do
                } else {
                    violate(p, "KillAura", String.format("danh %.0f do lech huong nhin (%.0f mo dich)", angle, victim.getType()));
                }
            }
        }

        st.lastAttackMs = now;
        st.lastVictim = victim.getUniqueId();

        // ---- VELOCITY: bat dau theo doi knockback ----
        st.lastVelocityMs = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (exempt(p)) return;
        if (!enabled("nofall")) return;

        // roi tu do cao nhung khong nhan sat thuong / nhan qua it
        double minFallDist = threshold("nofall", "min-fall-distance", 15);
        boolean allowFeather = plugin.getConfig().getBoolean("checks.nofall.allow-feather-falling", true);
        if (allowFeather && p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return;
        }
        if (p.getFallDistance() > minFallDist && event.getFinalDamage() <= 0) {
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
        if (now - st.placeWindowStart > 1000) {
            st.placeWindowStart = now;
            st.placesThisSecond = 0;
        }
        st.placesThisSecond++;
        int limit = threshold("fastplace", "max-placement-cps", 25);
        if (st.placesThisSecond > limit) {
            violate(p, "FastPlace", st.placesThisSecond + " block/giay");
        }
    }

    // ---------- XRAY ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (!enabled("xray")) return;
        Player p = event.getPlayer();
        if (p.hasPermission("btnanticheat.bypass")) return;
        if (exemptWorld(p)) return; // world mien kiem tra
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Material mat = event.getBlock().getType();
        PlayerState st = state(p);
        st.totalMined++;

        boolean isOre = isOre(mat);
        if (isOre) st.oreMined++;
        if (isStone(mat)) st.stoneMined++;

        if (isOre) {
            st.lastOreMs = System.currentTimeMillis();
            st.oreStreak++;
            if (isPrecious(mat)) st.preciousMined++;

            if (st.totalMined >= threshold("xray", "min-blocks", 30)
                    && st.stoneMined > 0
                    && st.oreMined >= threshold("xray", "min-ores", 12)) {
                double ratio = (double) st.oreMined / (st.totalMined);
                double limit = plugin.getConfig().getDouble("checks.xray.ore-ratio", 0.4);
                if (ratio >= limit) {
                    violate(p, "Xray", String.format(
                            "ti le quang/da %.0f%% (%d quang / %d block, trong do %d quang quy)",
                            ratio * 100, st.oreMined, st.totalMined, st.preciousMined));
                    return;
                }
            }
            // dao qua nhieu quang quy ma khong co da
            int precious = threshold("xray", "min-precious", 8);
            if (st.preciousMined >= precious && st.stoneMined < threshold("xray", "max-stone-no-alert", 40)) {
                violate(p, "Xray", String.format(
                        "%d quang quy (kim cuong/esmeralda/ancient) nhung chi %d block da",
                        st.preciousMined, st.stoneMined));
            }
        } else if (isStone(mat)) {
            st.oreStreak = 0;
        }
    }

    private static boolean isOre(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS") || n.equals("NETHER_GOLD_ORE");
    }

    private static boolean isStone(Material m) {
        String n = m.name();
        return n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("TUFF") || n.equals("GRANITE")
                || n.equals("DIORITE") || n.equals("ANDESITE") || n.equals("NETHERRACK");
    }

    private static boolean isPrecious(Material m) {
        String n = m.name();
        return n.contains("DIAMOND") || n.contains("EMERALD") || n.equals("ANCIENT_DEBRIS");
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
