package me.plugin.autoreplant;

import me.plugin.autoreplant.commands.AutoReplantCommand;
import me.plugin.autoreplant.listeners.CropHarvestListener;
import me.plugin.autoreplant.managers.PlayerDataManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoReplant extends JavaPlugin {

    private static AutoReplant instance;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        playerDataManager = new PlayerDataManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new CropHarvestListener(this), this);

        // Register commands
        AutoReplantCommand command = new AutoReplantCommand(this);
        getCommand("autoreplant").setExecutor(command);
        getCommand("autoreplant").setTabCompleter(command);

        getLogger().info("=================================");
        getLogger().info("  AutoReplant v" + getDescription().getVersion() + " เปิดใช้งานแล้ว!");
        getLogger().info("  รองรับ Minecraft 1.20.1");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        getLogger().info("AutoReplant ปิดใช้งานแล้ว!");
    }

    public static AutoReplant getInstance() {
        return instance;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public void reload() {
        reloadConfig();
        getLogger().info("AutoReplant: โหลด config ใหม่แล้ว!");
    }
}
