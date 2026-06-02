package me.plugin.autoreplant.managers;

import me.plugin.autoreplant.AutoReplant;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final AutoReplant plugin;
    private final Map<UUID, Boolean> playerStates = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public PlayerDataManager(AutoReplant plugin) {
        this.plugin = plugin;
        loadData();
    }

    /**
     * โหลดข้อมูลผู้เล่นจากไฟล์
     */
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("ไม่สามารถสร้างไฟล์ playerdata.yml ได้: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // โหลดข้อมูลจากไฟล์
        if (dataConfig.contains("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean enabled = dataConfig.getBoolean("players." + uuidStr + ".enabled", true);
                    playerStates.put(uuid, enabled);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID ไม่ถูกต้องใน playerdata.yml: " + uuidStr);
                }
            }
        }
        plugin.getLogger().info("โหลดข้อมูลผู้เล่น " + playerStates.size() + " คน");
    }

    /**
     * บันทึกข้อมูลทั้งหมด
     */
    public void saveAll() {
        for (Map.Entry<UUID, Boolean> entry : playerStates.entrySet()) {
            dataConfig.set("players." + entry.getKey().toString() + ".enabled", entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
            plugin.getLogger().info("บันทึกข้อมูลผู้เล่นแล้ว!");
        } catch (IOException e) {
            plugin.getLogger().severe("ไม่สามารถบันทึก playerdata.yml ได้: " + e.getMessage());
        }
    }

    /**
     * ตรวจสอบว่าผู้เล่นเปิด auto replant ไว้หรือไม่
     */
    public boolean isEnabled(UUID uuid) {
        if (!playerStates.containsKey(uuid)) {
            // ค่าเริ่มต้นจาก config
            boolean defaultValue = plugin.getConfig().getBoolean("settings.default-enabled-for-new-players", true);
            playerStates.put(uuid, defaultValue);
        }
        return playerStates.get(uuid);
    }

    /**
     * สลับสถานะ on/off ของผู้เล่น
     * @return สถานะใหม่ (true = เปิด, false = ปิด)
     */
    public boolean toggle(UUID uuid) {
        boolean current = isEnabled(uuid);
        boolean newState = !current;
        playerStates.put(uuid, newState);
        saveAll();
        return newState;
    }

    /**
     * ตั้งค่าสถานะของผู้เล่น
     */
    public void setEnabled(UUID uuid, boolean enabled) {
        playerStates.put(uuid, enabled);
        saveAll();
    }
}
