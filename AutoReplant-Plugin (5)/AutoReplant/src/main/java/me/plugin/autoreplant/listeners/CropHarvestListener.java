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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CropHarvestListener implements Listener {

    private final AutoReplant plugin;

    // Hoe materials ทุกชนิด
    private static final Set<Material> HOES = Set.of(
        Material.WOODEN_HOE,
        Material.STONE_HOE,
        Material.IRON_HOE,
        Material.GOLDEN_HOE,
        Material.DIAMOND_HOE,
        Material.NETHERITE_HOE
    );

    // พืชที่ยังไม่โตเต็มที่ — ห้ามทำลายด้วย Hoe
    private static final Map<Material, Material> CROP_SEED_MAP = new HashMap<>();

    static {
        CROP_SEED_MAP.put(Material.WHEAT, Material.WHEAT_SEEDS);
        CROP_SEED_MAP.put(Material.CARROTS, Material.CARROT);
        CROP_SEED_MAP.put(Material.POTATOES, Material.POTATO);
        CROP_SEED_MAP.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        CROP_SEED_MAP.put(Material.NETHER_WART, Material.NETHER_WART);
        CROP_SEED_MAP.put(Material.COCOA, Material.COCOA_BEANS);
        CROP_SEED_MAP.put(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES);
        try { CROP_SEED_MAP.put(Material.valueOf("PITCHER_CROP"), Material.valueOf("PITCHER_POD")); }
        catch (IllegalArgumentException ignored) {}
        try { CROP_SEED_MAP.put(Material.valueOf("TORCHFLOWER_CROP"), Material.valueOf("TORCHFLOWER_SEEDS")); }
        catch (IllegalArgumentException ignored) {}
    }

    public CropHarvestListener(AutoReplant plugin) {
        this.plugin = plugin;
    }

    // ========== ป้องกันทำลายพืชที่ยังไม่โตด้วย Hoe ==========
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreakProtect(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // เช็คว่าถือ Hoe อยู่
        if (!isHolding(player)) return;

        // เช็คว่าเป็นพืชที่รองรับ
        if (!CROP_SEED_MAP.containsKey(blockType)) return;

        // ถ้าพืชยังไม่โต → ยกเลิกการทำลาย
        if (!isFullyGrown(block)) {
            event.setCancelled(true);
            if (plugin.getConfig().getBoolean("settings.show-messages", true)) {
                String msg = plugin.getConfig().getString("messages.not-grown",
                        "&eพืชยังไม่โตเต็มที่ รอให้โตก่อนนะ!");
                player.sendMessage(color(msg));
            }
        }
    }

    // ========== Auto Replant เมื่อเก็บพืชด้วย Hoe ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // ต้องถือ Hoe เท่านั้น
        if (!isHolding(player)) return;

        // ตรวจสอบสิทธิ์
        if (!player.hasPermission("autoreplant.use")) return;

        // ตรวจสอบว่าผู้เล่นเปิด auto replant ไว้
        if (!plugin.getPlayerDataManager().isEnabled(player.getUniqueId())) return;

        // ต้องเป็นพืชที่รองรับ
        if (!CROP_SEED_MAP.containsKey(blockType)) return;

        // ต้องโตเต็มที่
        if (!isFullyGrown(block)) return;

        // ต้องเปิดพืชชนิดนี้ใน config
        if (!plugin.getConfig().getBoolean("crops." + blockType.name(), true)) return;

        Material seedMaterial = CROP_SEED_MAP.get(blockType);
        boolean requireSeeds = plugin.getConfig().getBoolean("settings.require-seeds", true);
        Location loc = block.getLocation().clone();
        final Material finalBlockType = blockType;
        final Material finalSeedMaterial = seedMaterial;

        // ตรวจสอบเมล็ดก่อน drop
        if (requireSeeds && !hasSeed(player, seedMaterial)) {
            if (plugin.getConfig().getBoolean("settings.show-messages", true)) {
                String msg = plugin.getConfig().getString("messages.no-seeds",
                        "&cไม่มีเมล็ด &e{crop} &cในกระเป๋า!");
                player.sendMessage(color(msg.replace("{crop}", getCropName(blockType))));
            }
            return;
        }

        // รอ 1 tick ให้บล็อคหายก่อนแล้วค่อยปลูก
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block target = loc.getBlock();

            if (!canReplant(target, finalBlockType)) return;

            if (requireSeeds) {
                // รอรับ drop ก่อน แล้วดึงเมล็ดออก
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    removeSeed(player, finalSeedMaterial), 1L);
            }

            replantCrop(target, finalBlockType);

            if (plugin.getConfig().getBoolean("settings.show-messages", true)) {
                String msg = plugin.getConfig().getString("messages.replanted",
                        "&aปลูกคืน &e{crop} &aแล้ว!");
                player.sendMessage(color(msg.replace("{crop}", getCropName(finalBlockType))));
            }

            loc.getWorld().playSound(loc, Sound.ITEM_CROP_PLANT, 0.8f, 1.0f);
            loc.getWorld().spawnParticle(Particle.COMPOSTER,
                    loc.clone().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0);
        }, 1L);
    }

    // ========== Helper Methods ==========

    private boolean isHolding(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return HOES.contains(item.getType());
    }

    private boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();

        if (block.getType() == Material.SWEET_BERRY_BUSH) {
            return data instanceof Ageable a && a.getAge() >= 3;
        }
        if (block.getType() == Material.COCOA) {
            return data instanceof Ageable a && a.getAge() >= 2;
        }
        if (data instanceof Ageable a) {
            return a.getAge() == a.getMaximumAge();
        }
        return false;
    }

    private boolean canReplant(Block block, Material cropType) {
        if (block.getType() != Material.AIR) return false;

        Block below = block.getRelative(0, -1, 0);
        Material belowType = below.getType();

        return switch (cropType) {
            case NETHER_WART -> belowType == Material.SOUL_SAND;
            case COCOA -> belowType.name().contains("JUNGLE") && belowType.name().contains("LOG");
            case SWEET_BERRY_BUSH -> belowType == Material.GRASS_BLOCK
                    || belowType == Material.DIRT
                    || belowType == Material.PODZOL
                    || belowType == Material.COARSE_DIRT
                    || belowType == Material.FARMLAND;
            default -> belowType == Material.FARMLAND;
        };
    }

    private void replantCrop(Block block, Material cropType) {
        block.setType(cropType);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable);
        }
    }

    private boolean hasSeed(Player player, Material seedMaterial) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == seedMaterial && item.getAmount() > 0) return true;
        }
        return false;
    }

    private void removeSeed(Player player, Material seedMaterial) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == seedMaterial) {
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().setItem(i, null);
                return;
            }
        }
    }

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

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
