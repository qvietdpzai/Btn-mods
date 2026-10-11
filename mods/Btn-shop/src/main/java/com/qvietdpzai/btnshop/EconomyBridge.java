package com.qvietdpzai.btnshop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cau noi economy - khong phu thuoc Vault con server chua cai dat!
 *
 * Uu tien:
 *   1. Vault (net.milkbowl.vault.economy.Economy)  - neu server cai Vault
 *   2. EssentialsX (com.earth2me.essentials.IUser) - neu khong co Vault
 *
 * Dung reflection de compile khong can co VaultAPI.jar / EssentialsAPI.jar.
 */
public final class EconomyBridge {

    public enum Provider { VAULT, ESSENTIALS, NONE }

    private final BtnShop plugin;

    // --- Vault ---
    private Object vault;
    private Method vHas, vWithdraw, vDeposit, vBalance;

    // --- EssentialsX ---
    private Object essPlugin;              // plugin cua Essentials (implements IEssentials)
    private Method essGetUser, essGetMoney, essCanAfford, essGive, essTake;

    private Provider provider = Provider.NONE;
    private String status = "chua ket noi";

    public EconomyBridge(BtnShop plugin) {
        this.plugin = plugin;
    }

    /** Kiem tra + ket noi. Tra ve ten provider dang dung. */
    public Provider connect() {
        if (tryVault()) return provider;
        if (tryEssentials()) return provider;
        return Provider.NONE;
    }

    public boolean available() { return provider != Provider.NONE; }

    public Provider provider() { return provider; }

    /** Mo ta trang thai de hien log/lệnh. */
    public String status() { return status; }

    // ---------------- Vault ----------------

    private boolean tryVault() {
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = Bukkit.getServicesManager().getRegistration(ecoClass);
            if (rsp == null) return false;
            Object p = rsp.getClass().getMethod("getProvider").invoke(rsp);
            if (p == null) return false;

            vault = p;
            vHas = findAny(p.getClass(), "has", OfflinePlayer.class, double.class);
            vWithdraw = findAny(p.getClass(), "withdrawPlayer", OfflinePlayer.class, double.class);
            vDeposit = findAny(p.getClass(), "depositPlayer", OfflinePlayer.class, double.class);
            vBalance = findAny(p.getClass(), "getBalance", OfflinePlayer.class);
            if (vHas == null || vBalance == null) return false;

            provider = Provider.VAULT;
            status = "Vault (" + vault.getClass().getSimpleName() + ")";
            return true;
        } catch (Throwable t) {
            vault = null;
            return false;
        }
    }

    // ---------------- EssentialsX ----------------

    private boolean tryEssentials() {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Essentials");
            if (p == null) return false;
            essPlugin = p;

            essGetUser = findAny(p.getClass(), "getUser", UUID.class);
            if (essGetUser == null) return false;

            // thu lay 1 user de xac nhan duoc cac method tien
            Class<?> userClass = Class.forName("com.earth2me.essentials.IUser");
            essGetMoney = userClass.getMethod("getMoney");
            essCanAfford = userClass.getMethod("canAfford", BigDecimal.class);
            essGive = userClass.getMethod("giveMoney", BigDecimal.class);
            essTake = userClass.getMethod("takeMoney", BigDecimal.class);

            provider = Provider.ESSENTIALS;
            status = "EssentialsX (khong can Vault)";
            return true;
        } catch (Throwable t) {
            essPlugin = null;
            return false;
        }
    }

    // ---------------- giao dich ----------------

    public BigDecimal balance(UUID id) {
        try {
            if (provider == Provider.VAULT) {
                Object val = vBalance.invoke(vault, Bukkit.getOfflinePlayer(id));
                return BigDecimal.valueOf(val == null ? 0 : ((Number) val).doubleValue());
            }
            if (provider == Provider.ESSENTIALS) {
                Object user = essGetUser.invoke(essPlugin, id);
                if (user == null) return BigDecimal.ZERO;
                Object m = essGetMoney.invoke(user);
                return m == null ? BigDecimal.ZERO : (BigDecimal) m;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Loi lay so du: " + root(t).getMessage());
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal balance(Player p) { return balance(p.getUniqueId()); }

    /** Co du tien khong? */
    public boolean has(Player p, BigDecimal amount) {
        try {
            if (provider == Provider.VAULT) {
                Object res = vHas.invoke(vault, p, amount.doubleValue());
                return res instanceof Boolean b && b;
            }
            if (provider == Provider.ESSENTIALS) {
                Object user = essGetUser.invoke(essPlugin, p.getUniqueId());
                if (user == null) return false;
                Object res = essCanAfford.invoke(user, amount);
                return res instanceof Boolean b && b;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Loi kiem tra tien: " + root(t).getMessage());
        }
        return false;
    }

    /** Tru tien. Tra ve true neu thanh cong. */
    public boolean withdraw(Player p, BigDecimal amount) {
        try {
            if (provider == Provider.VAULT) {
                Object res = vWithdraw.invoke(vault, p, amount.doubleValue());
                if (res instanceof Boolean b) return b;        // Vault cu
                return Double.parseDouble(String.valueOf(res)) > 0; // Vault moi: EconomyResponse
            }
            if (provider == Provider.ESSENTIALS) {
                Object user = essGetUser.invoke(essPlugin, p.getUniqueId());
                if (user == null) return false;
                essTake.invoke(user, amount);
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Loi tru tien cua " + p.getName() + ": " + root(t).getMessage());
        }
        return false;
    }

    /** Cong tien. */
    public void deposit(Player p, BigDecimal amount) {
        try {
            if (provider == Provider.VAULT) {
                vDeposit.invoke(vault, p, amount.doubleValue());
                return;
            }
            if (provider == Provider.ESSENTIALS) {
                Object user = essGetUser.invoke(essPlugin, p.getUniqueId());
                if (user != null) essGive.invoke(user, amount);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Loi cong tien cua " + p.getName() + ": " + root(t).getMessage());
        }
    }

    // ---------------- tien ich ----------------

    private static Throwable root(Throwable t) {
        while (t instanceof InvocationTargetException ite && ite.getCause() != null) t = ite.getCause();
        return t;
    }

    /** Tim method dau tien khop ten (dung cho Vault khac phien ban). */
    private static Method findAny(Class<?> c, String name, Class<?>... args) {
        try { return c.getMethod(name, args); } catch (Throwable ignored) { }
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                Class<?>[] ps = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < ps.length; i++) if (!ps[i].isAssignableFrom(args[i])) { ok = false; break; }
                if (ok) return m;
            }
        }
        return null;
    }
}
