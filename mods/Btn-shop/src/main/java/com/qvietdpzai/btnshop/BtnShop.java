package com.qvietdpzai.btnshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;

/** Btn-shop: shop nhe, 1 file shops.yml, ket noi Vault hoac EssentialsX. */
public final class BtnShop extends JavaPlugin {

    private ShopConfig shopConfig;
    private VietnameseNames vi;
    private EconomyBridge econ;
    private final DecimalFormat money = new DecimalFormat("#,##0.##");

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        this.shopConfig = new ShopConfig(this);
        shopConfig.load();
        // copy vi.yml tu jar neu chua co (admin sua duoc sau do)
        if (!new java.io.File(getDataFolder(), "vi.yml").exists()) {
            try { saveResource("vi.yml", false); } catch (IllegalArgumentException ignored) { }
        }
        this.vi = new VietnameseNames(new java.io.File(getDataFolder(), "vi.yml"), getLogger());
        vi.load();
        this.econ = new EconomyBridge(this);
        EconomyBridge.Provider p = econ.connect();
        if (p == EconomyBridge.Provider.NONE) {
            getLogger().severe("Khong tim thay economy!");
            getLogger().severe("Can cai Vault HOAC EssentialsX. Neu chi co EssentialsX thi khong sao.");
        } else {
            getLogger().info("Economy: " + econ.status());
        }
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getLogger().info("Btn-shop enabled: " + shopConfig.sections().size() + " section, "
                + shopConfig.totalItems() + " item (ten tieng Viet)");
    }

    @Override
    public void onDisable() {
        // KHONG save() shops.yml o day: viec do se ghi de file neu admin chinh tay
        // (su dung /btnshop set|add tu luu ngay khi thao tac)
    }

    public ShopConfig shopConfig() { return shopConfig; }
    public VietnameseNames vi() { return vi; }
    public EconomyBridge econ() { return econ; }
    public boolean hasEconomy() { return econ.available(); }
    public String money(BigDecimal v) { return money.format(v); }
    public String money(double v) { return money.format(v); }

    /** Ghi log dinh dang "Shop >> ..." (LangChillBridge doc tiền tố này). */
    public void shopLog(String line) {
        String prefix = getConfig().getString("log-prefix", "Shop >> ");
        getServer().getConsoleSender().sendMessage(prefix + line);
    }

    // ---------------- lệnh ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("btnshop")) return adminCommand(sender, args);

        // /shop [section|sell]
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này dùng trong game.");
            return true;
        }
        // Shop mo cho TAT CA (khong kiem tra permission):
        // PlugManX nap plugin sau LuckPerms nen node 'btnshop.use' khong nhan
        // duoc default tu plugin.yml -> moi nguoi choi deu bi tu choi.
        // Muon chan nguoi nao -> them permission 'btnshop.deny' cho ho.
        if (player.hasPermission("btnshop.deny")) {
            player.sendMessage(ChatColor.RED + "Ban khong duoc dung shop.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            ShopListener.openMain(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("sellgui")) {
            ShopListener.openSell(player);
            return true;
        }
        ShopConfig.Section sec = shopConfig.section(args[0]);
        if (sec == null) {
            player.sendMessage(ChatColor.RED + "Khong co section: " + args[0]
                    + ChatColor.GRAY + " (dung /shop de xem menu)");
            return true;
        }
        ShopListener.openSection(player, sec, 0);
        return true;
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("btnshop.admin")) {
            sender.sendMessage(ChatColor.RED + "Khong co quyen (btnshop.admin).");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.AQUA + "=== Btn-shop ===");
            sender.sendMessage("/btnshop info <material>      - xem giá + thuộc section");
            sender.sendMessage("/btnshop set <mat> buy|sell <gia>");
            sender.sendMessage("/btnshop add <section> <mat> <buy> <sell> [stack]");
            sender.sendMessage("/btnshop give <player> <mat> [số lượng]");
            sender.sendMessage("/btnshop sections            - danh sách section");
            sender.sendMessage("/btnshop total               - tổng item");
            sender.sendMessage("/btnshop reload              - nạp lại shops.yml");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                shopConfig.load();
                vi.load();
                sender.sendMessage(ChatColor.GREEN + "Da reload shops.yml (" + shopConfig.totalItems() + " item) + vi.yml");
                return true;
            case "total":
                sender.sendMessage(ChatColor.GRAY + "Tong: " + shopConfig.totalItems()
                        + " item / " + shopConfig.sections().size() + " section");
                return true;
            case "sections":
                for (Map.Entry<String, ShopConfig.Section> e : shopConfig.sections().entrySet())
                    sender.sendMessage(ChatColor.WHITE + e.getKey() + ChatColor.GRAY
                            + " - " + e.getValue().items.size() + " item");
                return true;
            case "info": {
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/btnshop info <material>"); return true; }
                ShopEntry e = shopConfig.findEntry(args[1].toUpperCase());
                ShopConfig.Section s = shopConfig.findSection(args[1].toUpperCase());
                if (e == null) { sender.sendMessage(ChatColor.RED + "Khong co trong shop: " + args[1]); return true; }
                sender.sendMessage(ChatColor.YELLOW + e.material + ChatColor.GRAY + " | section: " + s.id);
                sender.sendMessage("  mua vao: " + (e.buy >= 0 ? money(e.buy) : "khong ban"));
                sender.sendMessage("  ban ra: " + (e.sell >= 0 ? money(e.sell) : "khong mua"));
                if (e.stackSize > 0) sender.sendMessage("  ban theo chong: " + e.stackSize);
                return true;
            }
            case "set": {
                if (args.length < 4) { sender.sendMessage(ChatColor.RED + "/btnshop set <mat> buy|sell <gia>"); return true; }
                ShopEntry e = shopConfig.findEntry(args[1].toUpperCase());
                if (e == null) { sender.sendMessage(ChatColor.RED + "Khong co: " + args[1]); return true; }
                double price;
                try { price = Double.parseDouble(args[3]); } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Gia khong hop le"); return true; }
                if (args[2].equalsIgnoreCase("buy")) e.buy = price;
                else if (args[2].equalsIgnoreCase("sell")) e.sell = price;
                else { sender.sendMessage(ChatColor.RED + "Chi buy hoac sell"); return true; }
                shopConfig.save();
                sender.sendMessage(ChatColor.GREEN + "Da dat " + args[2] + " cua " + e.material + " = " + money(price));
                return true;
            }
            case "add": {
                if (args.length < 5) { sender.sendMessage(ChatColor.RED + "/btnshop add <section> <mat> <buy> <sell> [stack]"); return true; }
                ShopConfig.Section s = shopConfig.section(args[1]);
                if (s == null) { sender.sendMessage(ChatColor.RED + "Khong co section: " + args[1] + " (dung /btnshop sections)"); return true; }
                Material mat = Material.matchMaterial(args[2].toUpperCase());
                if (mat == null) { sender.sendMessage(ChatColor.RED + "Material khong ton tai: " + args[2]); return true; }
                ShopEntry e = shopConfig.findEntry(mat.name());
                if (e == null) { e = new ShopEntry(); e.material = mat.name(); s.items.add(e); }
                e.buy = Double.parseDouble(args[3]);
                e.sell = Double.parseDouble(args[4]);
                if (args.length > 5) e.stackSize = Integer.parseInt(args[5]);
                shopConfig.save();
                sender.sendMessage(ChatColor.GREEN + "Da them " + mat.name() + " vao section " + s.id);
                return true;
            }
            case "give": {
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "/btnshop give <player> <mat> [so luong]"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(ChatColor.RED + "Player khong online"); return true; }
                Material mat = Material.matchMaterial(args[2].toUpperCase());
                if (mat == null) { sender.sendMessage(ChatColor.RED + "Material khong ton tai"); return true; }
                int amount = args.length > 3 ? Integer.parseInt(args[3]) : 1;
                t.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount));
                sender.sendMessage(ChatColor.GREEN + "Da tra " + amount + "x " + mat.name() + " cho " + t.getName());
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Lenh khong ton tai: " + args[0]);
                return true;
        }
    }

    public String prefix() { return getConfig().getString("prefix", "&8[&aShop&8] &7"); }
}
