package com.qvietdpzai.veinminer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Btn-veinminer: pha cung loai block lien ket voi nhau trong 1 lan. */
public final class BtnVeinMiner extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Btn-veinminer enabled (max " + getConfig().getInt("max-blocks", 64) + " block)");
    }

    /** Block thuoc nhom duoc dao lien tiep? */
    private boolean isVeinMaterial(Material type) {
        List<String> list = getConfig().getStringList("vein-blocks");
        return list.contains(type.name());
    }

    /** Cuoc thuoc nhom nao? (pickaxe/axe/shovel) tra ve null neu khong tinh */
    private int toolLimit(ItemStack tool) {
        if (tool == null) return 0;
        String name = tool.getType().name();
        if (!name.endsWith("PICKAXE") && !name.endsWith("AXE") && !name.endsWith("SHOVEL")) return 0;
        switch (name) {
            case "WOODEN_PICKAXE": case "WOODEN_AXE": case "WOODEN_SHOVEL": return 32;
            case "STONE_PICKAXE": case "STONE_AXE": case "STONE_SHOVEL": return 64;
            case "IRON_PICKAXE": case "IRON_AXE": case "IRON_SHOVEL": return 128;
            case "GOLDEN_PICKAXE": case "GOLDEN_AXE": case "GOLDEN_SHOVEL": return 64;
            case "DIAMOND_PICKAXE": case "DIAMOND_AXE": case "DIAMOND_SHOVEL": return 256;
            case "NETHERITE_PICKAXE": case "NETHERITE_AXE": case "NETHERITE_SHOVEL": return 512;
            default: return 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!p.hasPermission("btnveinminer.use")) return;
        if (p.getGameMode() == GameMode.CREATIVE && !getConfig().getBoolean("allow-creative", false)) return;
        if (getConfig().getBoolean("disable-when-sneaking", true) && p.isSneaking()) return;

        Block origin = event.getBlock();
        Material type = origin.getType();
        if (!isVeinMaterial(type)) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        int limit = toolLimit(tool);
        if (limit <= 0) return;
        if (getConfig().getBoolean("require-correct-tool", false) && !correctTool(tool, type)) return;
        limit = Math.min(limit, getConfig().getInt("max-blocks", 64));

        // BFS tim block cung loai lien ke (6 huong)
        Set<Block> found = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < limit) {
            Block b = queue.poll();
            for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
                Block n = b.getRelative(d[0], d[1], d[2]);
                if (found.contains(n)) continue;
                if (n.getType() != type) continue;
                found.add(n);
                if (found.size() >= limit) break;
                queue.add(n);
            }
        }
        found.remove(origin);
        if (found.isEmpty()) return;

        int broken = 0;
        for (Block b : found) {
            if (b.getType() != type) continue;
            // gioi han chunk hop le: khong pha block co nguoi dung
            b.breakNaturally(tool);
            broken++;
            if (!damageTool(p, tool)) break;
        }
        final int total = broken + 1;
        if (getConfig().getBoolean("message-count", false)) {
            p.sendMessage(ChatColor.GRAY + "VeinMiner: " + total + " block");
        }
    }

    private boolean correctTool(ItemStack tool, Material type) {
        if (tool == null) return false;
        String name = type.name();
        String t = tool.getType().name();
        if (name.endsWith("LOG") || name.endsWith("STEM") || name.equals("BAMBOO")) return t.endsWith("AXE");
        return t.endsWith("PICKAXE") || t.endsWith("SHOVEL");
    }

    /** Tru do ben cuoc. Tra ve false neu cuoc gan hong. */
    private boolean damageTool(Player p, ItemStack tool) {
        if (tool == null) return false;
        var meta = tool.getItemMeta();
        if (!(meta instanceof Damageable d)) return true;
        int next = d.getDamage() + 1;
        int max = tool.getType().getMaxDurability();
        if (next >= max) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return false;
        }
        d.setDamage(next);
        tool.setItemMeta(meta);
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Btn-veinminer da reload.");
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + "Dang dao lien tiep: " + getConfig().getStringList("vein-blocks").size()
                + " loai block, toi da " + getConfig().getInt("max-blocks", 64) + " block/lan");
        return true;
    }
}
