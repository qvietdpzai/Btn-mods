package com.qvietdpzai.btnmods;

import org.bukkit.plugin.java.JavaPlugin;

public final class BtnMods extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Btn-mods enabled (v" + getDescription().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        getLogger().info("Btn-mods disabled");
    }
}
