package me.plugin.autoreplant.listeners;

import me.plugin.autoreplant.AutoReplant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class CropHarvestListener implements Listener {

    private final AutoReplant plugin;

    // Map: Material ของบล็อกพืช -> Material ของเมล็ด/ไอเทมที่ใช้ปลูก
    private static final Map<Material, Material> CROP_SEED_MAP = new HashMap<>();

    static {
        CROP_SEED_MAP.put(Material.WHEAT, Material.WHEAT_SEEDS);
        CROP_SEED_MAP.put(Material.CARROTS, Material.CARROT);
        CROP_SEED_MAP.put(Material.POTATOES, Material.POTATO);
        CROP_SEED_MAP.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        CROP_SEED_MAP.put(Material.NETHER_WART, Material.NETHER_WART);
        CROP_SEED_MAP.put(Material.COCOA, Material.COCOA_BEANS);
        CROP_SEED_MAP.put(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES);
        // 1.20+ crops
        try {
            CROP_SEED_MAP.put(Material.valueOf("PITCHER_CROP"), Material.valueOf("PITCHER_POD"));
        } catch (IllegalArgumentException ignored) {}
        try {
            CROP_SEED_MAP.put(Material.valueOf("TORCHFLOWER_CROP"), Material.valueOf("TORCHFLOWER_SEEDS"));
        } catch (IllegalArgumentException ignored) {}
    }

    public CropHarvestListener(AutoReplant plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // ตรวจสอบว่า plugin เปิดอยู่
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // ตรวจสอบสิทธิ์
        if (!player.hasPermission("autoreplant.use")) return;

        // ตรวจสอบว่าผู้เล่นเปิด auto replant ไว้หรือไม่
        if (!plugin.getPlayerDataManager().isEnabled(player.getUniqueId())) return;

        // ตรวจสอบว่าเป็นพืชที่รองรับ
        if (!CROP_SEED_MAP.containsKey(blockType)) return;

        // ตรวจสอบว่าพืชโตเต็มที่แล้ว
        if (!isFullyGrown(block)) return;

        // ตรวจสอบว่า config เปิดพืชชนิดนี้ไว้
        if (!plugin.getConfig().getBoolean("crops." + blockType.name(), true)) return;

        // ดึง seed material
        Material seedMaterial = CROP_SEED_MAP.get(blockType);

        // ตรวจสอบ require-seeds
        boolean requireSeeds = plugin.getConfig().getBoolean("settings.require-seeds", true);
        Location loc = block.getLocation();

        if (requireSeeds) {
            // หาเมล็ดใน inventory
            if (!hasSeed(player, seedMaterial)) {
                // แจ้งเตือนว่าไม่มีเมล็ด
                if (plugin.getConfig().getBoolean("settings.show-messages", true)) {
                    String msg = plugin.getConfig().getString("messages.no-seeds",
                            "&cไม่มีเมล็ด &e{crop} &cในกระเป๋า ไม่สามารถปลูกคืนได้");
                    msg = msg.replace("{crop}", getCropName(blockType));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
                return;
            }
        }

        // Delay 1 tick เพื่อให้บล็อคถูกทำลายก่อน
        final Material finalBlockType = blockType;
        final Material finalSeedMaterial = seedMaterial;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // ตรวจสอบว่า block ตรงนั้นว่างอยู่ และมี farmland ด้านล่าง
            Block targetBlock = loc.getBlock();

            if (canReplant(targetBlock, finalBlockType)) {
                // ดึงเมล็ดออกจาก inventory (ถ้าจำเป็น)
                if (requireSeeds) {
                    removeSeed(player, finalSeedMaterial);
                }

                // ปลูกพืชใหม่
                replantCrop(targetBlock, finalBlockType);

                // แสดงข้อความ
                if (plugin.getConfig().getBoolean("settings.show-messages", true)) {
                    String msg = plugin.getConfig().getString("messages.replanted",
                            "&aปลูกคืน &e{crop} &aเรียบร้อยแล้ว!");
                    msg = msg.replace("{crop}", getCropName(finalBlockType));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }

                // เล่นเสียงและ particle
                loc.getWorld().playSound(loc, Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);
                loc.getWorld().spawnParticle(Particle.COMPOSTER, loc.add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0);
            }
        }, 1L);
    }

    /**
     * ตรวจสอบว่าพืชโตเต็มที่แล้วหรือยัง
     */
    private boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();

        // Sweet berry bush: age 3 = ผลเต็ม
        if (block.getType() == Material.SWEET_BERRY_BUSH) {
            if (data instanceof Ageable ageable) {
                return ageable.getAge() >= 3;
            }
        }

        // Cocoa: age 2 = โตเต็ม
        if (block.getType() == Material.COCOA) {
            if (data instanceof Ageable ageable) {
                return ageable.getAge() >= 2;
            }
        }

        // พืชทั่วไป: age == maximumAge
        if (data instanceof Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }

        return false;
    }

    /**
     * ตรวจสอบว่าสามารถปลูกคืนได้ไหม
     */
    private boolean canReplant(Block block, Material cropType) {
        // บล็อกต้องว่าง
        if (block.getType() != Material.AIR) return false;

        Block below = block.getRelative(0, -1, 0);

        // Nether Wart ต้องการ Soul Sand
        if (cropType == Material.NETHER_WART) {
            return below.getType() == Material.SOUL_SAND;
        }

        // Cocoa ต้องการ jungle log
        if (cropType == Material.COCOA) {
            return below.getType().name().contains("JUNGLE") && below.getType().name().contains("LOG");
        }

        // Sweet berry bush: ดินทั่วไป
        if (cropType == Material.SWEET_BERRY_BUSH) {
            return below.getType() == Material.GRASS_BLOCK
                    || below.getType() == Material.DIRT
                    || below.getType() == Material.PODZOL
                    || below.getType() == Material.COARSE_DIRT
                    || below.getType() == Material.FARMLAND;
        }

        // พืชทั่วไปต้องการ Farmland
        return below.getType() == Material.FARMLAND;
    }

    /**
     * ปลูกพืชใหม่ในตำแหน่งนั้น
     */
    private void replantCrop(Block block, Material cropType) {
        block.setType(cropType);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable);
        }
    }

    /**
     * ตรวจสอบว่ามีเมล็ดใน inventory
     */
    private boolean hasSeed(Player player, Material seedMaterial) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == seedMaterial && item.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * ดึงเมล็ด 1 ชิ้นออกจาก inventory
     */
    private void removeSeed(Player player, Material seedMaterial) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == seedMaterial) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return;
            }
        }
    }

    /**
     * แปลงชื่อพืชเป็นภาษาไทย/อ่านง่าย
     */
    private String getCropName(Material material) {
        return switch (material) {
            case WHEAT -> "ข้าวสาลี";
            case CARROTS -> "แครอท";
            case POTATOES -> "มันฝรั่ง";
            case BEETROOTS -> "บีทรูท";
            case NETHER_WART -> "วอร์ตนรก";
            case COCOA -> "โกโก้";
            case SWEET_BERRY_BUSH -> "เบอร์รี่หวาน";
            default -> material.name().replace("_", " ").toLowerCase();
        };
    }
}
