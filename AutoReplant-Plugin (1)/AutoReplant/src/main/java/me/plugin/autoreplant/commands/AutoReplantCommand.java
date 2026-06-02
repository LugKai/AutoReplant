package me.plugin.autoreplant.commands;

import me.plugin.autoreplant.AutoReplant;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class AutoReplantCommand implements CommandExecutor, TabCompleter {

    private final AutoReplant plugin;

    public AutoReplantCommand(AutoReplant plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cคำสั่งนี้ใช้ได้เฉพาะในเกมเท่านั้น"));
                    return true;
                }
                if (!player.hasPermission("autoreplant.toggle")) {
                    player.sendMessage(color(plugin.getConfig().getString("messages.no-permission",
                            "&cคุณไม่มีสิทธิ์ใช้คำสั่งนี้")));
                    return true;
                }
                boolean newState = plugin.getPlayerDataManager().toggle(player.getUniqueId());
                if (newState) {
                    player.sendMessage(color(plugin.getConfig().getString("messages.toggle-on",
                            "&aเปิด Auto Replant แล้ว!")));
                } else {
                    player.sendMessage(color(plugin.getConfig().getString("messages.toggle-off",
                            "&cปิด Auto Replant แล้ว!")));
                }
            }

            case "reload" -> {
                if (!sender.hasPermission("autoreplant.admin")) {
                    sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission",
                            "&cคุณไม่มีสิทธิ์ใช้คำสั่งนี้")));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(color(plugin.getConfig().getString("messages.reloaded",
                        "&aโหลด config ใหม่แล้ว!")));
            }

            case "info" -> {
                sender.sendMessage(color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                sender.sendMessage(color("&a&lAutoReplant &7v" + plugin.getDescription().getVersion()));
                sender.sendMessage(color("&7ปลูกพืชคืนอัตโนมัติเมื่อเก็บเกี่ยว"));
                sender.sendMessage(color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                sender.sendMessage(color("&eพืชที่รองรับ:"));
                sender.sendMessage(color("&7ข้าวสาลี, แครอท, มันฝรั่ง, บีทรูท"));
                sender.sendMessage(color("&7วอร์ตนรก, โกโก้, เบอร์รี่หวาน"));
                sender.sendMessage(color("&7Pitcher Crop, Torchflower (1.20+)"));
                sender.sendMessage(color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));

                if (sender instanceof Player player) {
                    boolean enabled = plugin.getPlayerDataManager().isEnabled(player.getUniqueId());
                    sender.sendMessage(color("&7สถานะของคุณ: " + (enabled ? "&aเปิด ✓" : "&cปิด ✗")));
                    sender.sendMessage(color("&7ใช้ &e/ar toggle &7เพื่อสลับสถานะ"));
                }
                sender.sendMessage(color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color("&a&lAutoReplant &7- คำสั่ง"));
        sender.sendMessage(color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color("&e/ar toggle &7- เปิด/ปิด auto replant"));
        sender.sendMessage(color("&e/ar info &7- ดูข้อมูล plugin"));
        if (sender.hasPermission("autoreplant.admin")) {
            sender.sendMessage(color("&e/ar reload &7- โหลด config ใหม่ (Admin)"));
        }
        sender.sendMessage(color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("autoreplant.admin")) {
                return Arrays.asList("toggle", "info", "reload");
            }
            return Arrays.asList("toggle", "info");
        }
        return List.of();
    }
}
