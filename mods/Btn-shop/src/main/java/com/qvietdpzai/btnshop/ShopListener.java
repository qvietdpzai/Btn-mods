package com.qvietdpzai.btnshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** GUI shop: menu section, trang item, mua/ban. */
public final class ShopListener implements Listener {

    private static final int ROWS = 6;          // 6 hang -> 45 slot item + hang nav
    private static final int PER_PAGE = 45;
    private static final String MAIN_TITLE = "  Shop";
    private static final String SELL_TITLE = "  Ban do";

    private static BtnShop plugin;
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");

    /** holder de nhan dien GUI cua plugin (khong nham voi chest khac) */
    private static final class ShopHolder implements InventoryHolder {
        final String kind;             // "main" | "section" | "sell"
        final String sectionId;
        final int page;
        ShopHolder(String kind, String sectionId, int page) {
            this.kind = kind; this.sectionId = sectionId; this.page = page;
        }
        @Override public Inventory getInventory() { return null; }
    }

    public ShopListener(BtnShop plugin) { ShopListener.plugin = plugin; }

    // ---------------- mo GUI ----------------

    public static void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(new ShopHolder("main", null, 0), ROWS * 9, MAIN_TITLE);
        for (ShopConfig.Section s : new ArrayList<>(plugin.shopConfig().sections().values())) {
            if (s.items.isEmpty() || s.slot >= 54) continue;
            inv.setItem(s.slot, icon(s));
        }
        inv.setItem(4, balanceItem(p));
        inv.setItem(48, button(Material.CHEST, ChatColor.YELLOW + "Ban do", "Ban item ban dang co", "tra gia shop"));
        inv.setItem(50, button(Material.PAPER, ChatColor.GRAY + "Thong tin",
                "Mua: click trai 1 / phai = 1 chong", "Shift = 1 chong (64)"));
        inv.setItem(53, button(Material.BARRIER, ChatColor.RED + "Dong", null, null));
        p.openInventory(inv);
    }

    /** Item hien so du cua nguoi choi trong GUI shop. */
    private static ItemStack balanceItem(Player p) {
        ItemStack i = new ItemStack(Material.SUNFLOWER);
        ItemMeta m = i.getItemMeta();
        double bal = plugin.hasEconomy() ? plugin.economy().getBalance(p) : 0;
        m.setDisplayName(ChatColor.GOLD + "So du cua ban");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "$" + String.format("%,.2f", bal));
        lore.add("");
        lore.add(ChatColor.GRAY + "Mua: click trai = 1, phai/shift = 1 chong");
        lore.add(ChatColor.GRAY + "Ban: " + ChatColor.YELLOW + "/shop sell");
        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    public static void openSection(Player p, ShopConfig.Section sec, int page) {
        int pages = Math.max(1, (sec.items.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(pages - 1, page));
        Inventory inv = Bukkit.createInventory(new ShopHolder("section", sec.id, page), ROWS * 9, sec.title);
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(from + PER_PAGE, sec.items.size()); i++) {
            inv.setItem(i - from, shopItem(sec.items.get(i)));
        }
        inv.setItem(4, balanceItem(p));
        // hang nav cuoi
        inv.setItem(45, button(Material.ARROW, ChatColor.YELLOW + "< Ve menu", null, null));
        if (pages > 1) {
            if (page > 0) inv.setItem(48, button(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Trang truoc", null, null));
            if (page < pages - 1) inv.setItem(52, button(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Trang sau", null, null));
            inv.setItem(49, button(Material.PAPER, ChatColor.GRAY + "Trang " + (page + 1) + "/" + pages, null, null));
        }
        inv.setItem(53, button(Material.BARRIER, ChatColor.RED + "Dong", null, null));
        p.openInventory(inv);
    }

    public static void openSell(Player p) {
        Inventory inv = Bukkit.createInventory(new ShopHolder("sell", null, 0), 54, SELL_TITLE);
        Map<String, int[]> totals = new HashMap<>();   // material -> [so luong, gia 1 mon]
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            ShopEntry e = plugin.shopConfig().findEntryFor(it);
            if (e == null || !e.canSell()) continue;
            int[] cur = totals.computeIfAbsent(it.getType().name(), k -> new int[2]);
            cur[0] += it.getAmount();
            cur[1] = (int) (100 * e.sell);
        }
        for (Map.Entry<String, int[]> e : totals.entrySet()) {
            ItemStack icon = new ItemStack(Material.valueOf(e.getKey()));
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + pretty(e.getKey()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Dang co: " + ChatColor.WHITE + e.getValue()[0]);
            lore.add(ChatColor.GRAY + "Gia: " + ChatColor.GREEN + "$" + MONEY.format(e.getValue()[1] / 100.0) + ChatColor.GRAY + " / mon");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click trai: ban tat ca loai nay");
            lore.add(ChatColor.YELLOW + "Click phai: ban 1 mon");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.addItem(icon);
        }
        inv.setItem(45, button(Material.GOLD_BLOCK, ChatColor.GOLD + "BAN TAT CA",
                "Ban moi item co gia", "trong tui do cua ban"));
        inv.setItem(48, balanceItem(p));
        inv.setItem(49, button(Material.ARROW, ChatColor.YELLOW + "< Ve shop", null, null));
        inv.setItem(53, button(Material.BARRIER, ChatColor.RED + "Dong", null, null));
        p.openInventory(inv);
    }

    // ---------------- click ----------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) return;
        event.setCancelled(true);   // khong the lay item trong GUI

        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return; // chi tu dong GUI

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = event.getSlot();
        ShopConfig.Section sec = holder.sectionId == null ? null : plugin.shopConfig().section(holder.sectionId);

        switch (holder.kind) {
            case "main" -> {
                if (slot == 48) { openSell(p); return; }
                if (slot == 53) { p.closeInventory(); return; }
                for (ShopConfig.Section s : plugin.shopConfig().sections().values()) {
                    if (s.slot == slot && !s.items.isEmpty()) { openSection(p, s, 0); return; }
                }
            }
            case "section" -> {
                if (slot == 45) { openMain(p); return; }
                if (slot == 53) { p.closeInventory(); return; }
                if (sec == null) return;
                int idx = slot < 45 ? holder.page * PER_PAGE + slot : -1;
                if (idx >= 0 && idx < sec.items.size()) {
                    ShopEntry entry = sec.items.get(idx);
                    int qty = 1;
                    if (event.isShiftClick() || event.getClick() == ClickType.RIGHT) qty = entry.buyUnit() > 1 ? entry.buyUnit() : 64;
                    if (event.getClick() == ClickType.MIDDLE) qty = 16;
                    buy(p, entry, qty);
                    return;
                }
                if (slot == 48 && holder.page > 0) { openSection(p, sec, holder.page - 1); return; }
                if (slot == 52) { openSection(p, sec, holder.page + 1); return; }
            }
            case "sell" -> {
                if (slot == 49) { openMain(p); return; }
                if (slot == 53) { p.closeInventory(); return; }
                if (slot == 45) { sellAll(p); return; }
                ItemStack real = findInInventory(p, clicked.getType());
                if (real == null) return;
                int qty = event.getClick() == ClickType.RIGHT ? 1 : countItem(p, clicked.getType().name());
                sell(p, clicked.getType(), qty);
            }
        }
    }

    // ---------------- giao dich ----------------

    private void buy(Player p, ShopEntry entry, int qty) {
        if (!plugin.hasEconomy()) { p.sendMessage(ChatColor.RED + "Shop chua ket noi economy!"); return; }
        if (entry.buy < 0) { p.sendMessage(ChatColor.RED + "Mon nay khong ban."); return; }

        int perUnit = entry.buyUnit();                   // >= 1
        int units = (int) Math.ceil(qty / (double) perUnit);
        qty = units * perUnit;                            // chinh ve boi so chong
        double total = entry.buy * units;

        Economy eco = plugin.economy();
        if (!eco.has(p, total)) {
            p.sendMessage(ChatColor.RED + "Khong du tien! Can " + ChatColor.YELLOW + "$" + plugin.money(total)
                    + ChatColor.RED + ", ban co " + ChatColor.YELLOW + "$" + plugin.money(eco.getBalance(p)));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // cho trong tui
        int free = freeSpace(p, entry.material);
        if (free < qty) {
            p.sendMessage(ChatColor.RED + "Tui do khong du cho! Can " + qty + " slot item.");
            return;
        }

        eco.withdrawPlayer(p, total);
        ItemStack give = new ItemStack(Material.valueOf(entry.material), qty);
        if (entry.potion != null) PotionUtil.apply(give, entry.potion);
        p.getInventory().addItem(give);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        p.sendMessage(ChatColor.GREEN + "Da mua " + qty + "x " + pretty(entry.material)
                + ChatColor.GRAY + " -$" + plugin.money(total));

        refreshBalance(p);
        plugin.shopLog(String.format(Locale.US, "%s bought %d x %s for %.2f with the buy screen.",
                p.getName(), qty, entry.material.toLowerCase(Locale.US), total));
    }

    private void sell(Player p, Material material, int qty) {
        if (!plugin.hasEconomy()) return;
        ItemStack sample = null;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getType() == material) { sample = it; break; }
        if (sample == null) return;
        ShopEntry entry = plugin.shopConfig().findEntryFor(sample);
        if (entry == null || !entry.canSell()) { p.sendMessage(ChatColor.RED + "Mon nay shop khong mua."); return; }
        int have = countItem(p, entry.material);
        if (have <= 0) return;
        // ban dung loai potion: loc item khop
        qty = Math.min(qty, countMatching(p, entry, sample));
        if (qty <= 0) return;
        double total = entry.sell * qty / (entry.buyUnit() > 1 ? entry.buyUnit() : 1);
        if (total <= 0) { p.sendMessage(ChatColor.RED + "Mon nay khong co gia ban."); return; }

        removeMatching(p, entry, sample, qty);
        plugin.economy().depositPlayer(p, total);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        p.sendMessage(ChatColor.GREEN + "Da ban " + qty + "x " + pretty(entry.material)
                + ChatColor.GRAY + " +$" + plugin.money(total));

        plugin.shopLog(String.format(Locale.US, "%s sold %d x %s for %.2f with the sell gui.",
                p.getName(), qty, entry.material.toLowerCase(Locale.US), total));
        openSell(p); // refresh
    }

    private void sellAll(Player p) {
        if (!plugin.hasEconomy()) return;
        double total = 0; int items = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            ShopEntry e = plugin.shopConfig().findEntryFor(it);
            if (e == null || !e.canSell()) continue;
            int per = e.buyUnit() > 1 ? e.buyUnit() : 1;
            double money = e.sell * it.getAmount() / per;
            if (money <= 0) continue;
            total += money; items += it.getAmount();
            it.setAmount(0);
        }
        if (items == 0) { p.sendMessage(ChatColor.YELLOW + "Khong co gi de ban."); return; }
        plugin.economy().depositPlayer(p, total);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        p.sendMessage(ChatColor.GREEN + "Da ban " + items + " item, nhan duoc "
                + ChatColor.YELLOW + "$" + plugin.money(total));
        plugin.shopLog(String.format(Locale.US, "%s sold %d items for %.2f with the sell all button.",
                p.getName(), items, total));
        openSell(p);
    }

    /** Cap nhat o so du trong GUI dang mo (sau khi mua/ban). */
    private static void refreshBalance(Player p) {
        Inventory open = p.getOpenInventory().getTopInventory();
        if (open != null && open.getHolder() instanceof ShopHolder) {
            open.setItem(4, balanceItem(p));
            p.updateInventory();
        }
    }

    /** Số item khớp entry (theo potion type nếu có). */
    private static int countMatching(Player p, ShopEntry entry, ItemStack sample) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (!it.getType().name().equalsIgnoreCase(entry.material)) continue;
            if (entry.potion != null && !PotionUtil.matches(it, entry.potion)) continue;
            n += it.getAmount();
        }
        return n;
    }

    private static void removeMatching(Player p, ShopEntry entry, ItemStack sample, int qty) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (!it.getType().name().equalsIgnoreCase(entry.material)) continue;
            if (entry.potion != null && !PotionUtil.matches(it, entry.potion)) continue;
            int take = Math.min(qty, it.getAmount());
            it.setAmount(it.getAmount() - take);
            qty -= take;
            if (qty <= 0) break;
        }
    }

    // ---------------- tien ich ----------------

    private int freeSpace(Player p, String material) {
        int free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) free += 64;
            else if (it.getType().name().equals(material)) free += Math.max(0, it.getMaxStackSize() - it.getAmount());
        }
        return free;
    }

    private int countItem(Player p, String material) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getType().name().equals(material)) n += it.getAmount();
        return n;
    }

    private ItemStack findInInventory(Player p, Material m) {
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getType() == m) return it;
        return null;
    }

    private void removeItems(Player p, String material, int qty) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.getType().name().equals(material)) continue;
            int take = Math.min(qty, it.getAmount());
            it.setAmount(it.getAmount() - take);
            qty -= take;
            if (qty <= 0) break;
        }
    }

    private static ItemStack icon(ShopConfig.Section s) {
        ItemStack i = new ItemStack(s.iconMaterial());
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', s.iconName != null ? s.iconName : s.title));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "So mon hang: " + ChatColor.WHITE + s.items.size());
        lore.add(ChatColor.YELLOW + "Click de mo");
        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    private static ItemStack shopItem(ShopEntry e) {
        ItemStack i = new ItemStack(Material.valueOf(e.material));
        if (e.potion != null) PotionUtil.apply(i, e.potion);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(ChatColor.YELLOW + (e.potion != null ? PotionUtil.displayName(e.potion)
                + ChatColor.DARK_GRAY + " (" + form(e.material) + ")" : pretty(e.material)));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Mua: " + (e.buy >= 0
                ? ChatColor.GREEN + "$" + MONEY.format(e.buy) + (e.stackSize > 0 ? ChatColor.GRAY + " / chong " + e.stackSize : "")
                : ChatColor.RED + "khong ban"));
        lore.add(ChatColor.GRAY + "Ban: " + (e.sell >= 0 ? ChatColor.GREEN + "$" + MONEY.format(e.sell) : ChatColor.RED + "khong mua"));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Trai: 1" + (e.buyUnit() > 1 ? " chong" : "") + " | Phai: " + e.buyUnit() + " | Shift: "
                + (e.buyUnit() > 1 ? e.buyUnit() : 64));
        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    private static String form(String material) {
        return switch (material) {
            case "POTION" -> "thuong";
            case "SPLASH_POTION" -> "nem";
            case "LINGERING_POTION" -> "luu lang";
            case "TIPPED_ARROW" -> "mui ten";
            default -> pretty(material);
        };
    }

    private static ItemStack button(Material mat, String name, String l1, String l2) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        if (l1 != null) lore.add(ChatColor.GRAY + l1);
        if (l2 != null) lore.add(ChatColor.GRAY + l2);
        if (!lore.isEmpty()) m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    private static String pretty(String material) {
        String s = material.toLowerCase(Locale.US).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }


}
