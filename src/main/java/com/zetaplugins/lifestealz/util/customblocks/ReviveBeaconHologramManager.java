package com.zetaplugins.lifestealz.util.customblocks;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.data.TextHologramData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.util.customitems.CustomItemManager;
import com.zetaplugins.lifestealz.util.customitems.customitemdata.CustomReviveBeaconItemData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理复活信标的全息显示
 */
public final class ReviveBeaconHologramManager {
    private final LifeStealZ plugin;
    private final Map<Location, String> hologramNames;
    private final Map<Location, BukkitTask> updateTasks;
    private boolean fancyHologramsEnabled;

    public ReviveBeaconHologramManager(LifeStealZ plugin) {
        this.plugin = plugin;
        this.hologramNames = new HashMap<>();
        this.updateTasks = new HashMap<>();
        this.fancyHologramsEnabled = plugin.getServer().getPluginManager().isPluginEnabled("FancyHolograms");
        
        // 输出检测结果
        if (fancyHologramsEnabled) {
            plugin.getLogger().info("FancyHolograms detected! Hologram features enabled.");
        } else {
            plugin.getLogger().warning("FancyHolograms not found! Hologram features will be disabled.");
            plugin.getLogger().warning("Download FancyHolograms from: https://modrinth.com/plugin/fancyholograms");
        }
    }

    /**
     * 调试日志输出
     */
    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[Hologram Debug] " + message);
        }
    }

    /**
     * 创建空闲状态的全息显示（带配置参数）
     */
    public void createIdleHologram(Location location, String ownerName, int durability, int maxDurability, 
                                   boolean enableHologram, String title, String ownerFormat, String durabilityFormat,
                                   org.bukkit.Color background, double offsetX, double offsetY, double offsetZ) {
        debug("Creating idle hologram at " + location + " for owner: " + ownerName);
        
        if (!fancyHologramsEnabled) {
            debug("FancyHolograms not enabled, skipping hologram creation");
            return;
        }
        
        if (!enableHologram) {
            debug("Hologram disabled in config, skipping creation");
            return;
        }

        Location hologramLoc = location.clone().add(offsetX, offsetY, offsetZ);
        String hologramName = "revive_beacon_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            
            // 删除已存在的全息图
            manager.getHologram(hologramName).ifPresent(manager::removeHologram);

            TextHologramData data = new TextHologramData(hologramName, hologramLoc);
            data.setPersistent(false);  // 不使用FancyHolograms的持久化，由插件自己管理
            data.setBillboard(Display.Billboard.CENTER);
            
            // 设置背景颜色
            if (background != null) {
                data.setBackground(background);
            }
            
            List<String> lines = List.of(
                ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', ownerFormat.replace("%owner%", ownerName)),
                ChatColor.translateAlternateColorCodes('&', durabilityFormat
                    .replace("%durability%", String.valueOf(durability))
                    .replace("%maxDurability%", String.valueOf(maxDurability)))
            );
            data.setText(lines);

            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);

            hologramNames.put(getKey(location), hologramName);
            debug("Successfully created idle hologram '" + hologramName + "' with " + lines.size() + " lines");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create hologram: " + e.getMessage());
            debug("Error details: " + e.getClass().getName() + " - " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 创建空闲状态的全息显示（自动从配置读取）
     */
    public void createIdleHologram(Location location, String ownerName, int durability, int maxDurability) {
        debug("Creating idle hologram (auto-config) at " + location);
        
        // 从方块获取信标的自定义物品ID
        String beaconItemId = CustomBlock.REVIVE_BEACON.getCustomItemId(location.getBlock());
        if (beaconItemId == null) {
            debug("Beacon item ID not found, using default config");
            // 如果无法获取ID，使用默认配置
            createIdleHologram(location, ownerName, durability, maxDurability, 
                true, "&c&l复活信标", "&7所有者: &e%owner%", "&7耐久: &a%durability%&7/&a%maxDurability%",
                null, 0.5, 2.5, 0.5);
            return;
        }

        try {
            debug("Loading hologram config for beacon ID: " + beaconItemId);
            CustomReviveBeaconItemData itemData = new CustomReviveBeaconItemData(beaconItemId);
            debug("Config loaded - enabled: " + itemData.isEnableHologram() + ", title: " + itemData.getHologramTitle());
            createIdleHologram(
                location, ownerName, durability, maxDurability,
                itemData.isEnableHologram(),
                itemData.getHologramTitle(),
                itemData.getHologramOwnerFormat(),
                itemData.getHologramDurabilityFormat(),
                itemData.getHologramBackground(),
                itemData.getHologramOffsetX(),
                itemData.getHologramOffsetY(),
                itemData.getHologramOffsetZ()
            );
        } catch (IllegalArgumentException e) {
            // 配置错误时使用默认值
            plugin.getLogger().warning("Failed to load hologram config for beacon " + beaconItemId + ": " + e.getMessage());
            debug("Using default config due to error");
            createIdleHologram(location, ownerName, durability, maxDurability, 
                true, "&c&l复活信标", "&7所有者: &e%owner%", "&7耐久: &a%durability%&7/&a%maxDurability%",
                null, 0.5, 2.5, 0.5);
        }
    }

    /**
     * 创建复活中的全息显示（自动从配置读取）
     */
    public void createRevivingHologram(Location location, String targetName, int reviveTime, int durability, int maxDurability) {
        debug("Creating reviving hologram at " + location + " for target: " + targetName + ", time: " + reviveTime + "s");
        
        // 从方块获取信标的自定义物品ID
        String beaconItemId = CustomBlock.REVIVE_BEACON.getCustomItemId(location.getBlock());
        if (beaconItemId == null) {
            // 使用默认配置
            createRevivingHologramWithConfig(location, targetName, reviveTime, durability, maxDurability,
                true, "&e&l复活进行中", "&7目标: &e%target%", "&7剩余时间: &e%time%",
                null, 0.5, 2.5, 0.5);
            return;
        }

        try {
            CustomReviveBeaconItemData itemData = new CustomReviveBeaconItemData(beaconItemId);
            createRevivingHologramWithConfig(
                location, targetName, reviveTime, durability, maxDurability,
                itemData.isEnableHologram(),
                itemData.getHologramRevivingTitle(),
                itemData.getHologramTargetFormat(),
                itemData.getHologramTimeFormat(),
                itemData.getHologramBackground(),
                itemData.getHologramOffsetX(),
                itemData.getHologramOffsetY(),
                itemData.getHologramOffsetZ()
            );
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Failed to load hologram config for beacon " + beaconItemId + ": " + e.getMessage());
            createRevivingHologramWithConfig(location, targetName, reviveTime, durability, maxDurability,
                true, "&e&l复活进行中", "&7目标: &e%target%", "&7剩余时间: &e%time%",
                null, 0.5, 2.5, 0.5);
        }
    }

    /**
     * 创建复活中的全息显示（带配置参数）
     */
    private void createRevivingHologramWithConfig(Location location, String targetName, int reviveTime, 
                                                   int durability, int maxDurability, boolean enableHologram,
                                                   String title, String targetFormat, String timeFormat,
                                                   org.bukkit.Color background, double offsetX, double offsetY, double offsetZ) {
        if (!fancyHologramsEnabled) {
            debug("FancyHolograms not enabled, skipping reviving hologram creation");
            return;
        }
        
        if (!enableHologram) {
            debug("Hologram disabled in config, skipping reviving hologram creation");
            return;
        }

        Location hologramLoc = location.clone().add(offsetX, offsetY, offsetZ);
        String hologramName = "revive_beacon_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            
            // 删除已存在的全息图
            manager.getHologram(hologramName).ifPresent(manager::removeHologram);

            TextHologramData data = new TextHologramData(hologramName, hologramLoc);
            data.setPersistent(false);  // 不使用FancyHolograms的持久化，由插件自己管理
            data.setBillboard(Display.Billboard.CENTER);
            
            // 设置背景颜色
            if (background != null) {
                data.setBackground(background);
            }

            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);

            hologramNames.put(getKey(location), hologramName);

            // 启动更新任务
            startUpdateTask(location, targetName, reviveTime, durability, maxDurability, title, targetFormat, timeFormat);
            debug("Successfully created reviving hologram '" + hologramName + "' with update task");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create reviving hologram: " + e.getMessage());
            debug("Error details: " + e.getClass().getName() + " - " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 启动全息图更新任务
     */
    private void startUpdateTask(Location location, String targetName, int totalTime, int durability, int maxDurability,
                                 String title, String targetFormat, String timeFormat) {
        String hologramName = hologramNames.get(getKey(location));
        if (hologramName == null) return;

        // 获取耐久度格式
        String beaconItemId = CustomBlock.REVIVE_BEACON.getCustomItemId(location.getBlock());
        String durabilityFormat = "&7耐久: &a%durability%&7/&a%maxDurability%";
        if (beaconItemId != null) {
            try {
                CustomReviveBeaconItemData itemData = new CustomReviveBeaconItemData(beaconItemId);
                durabilityFormat = itemData.getHologramDurabilityFormat();
            } catch (IllegalArgumentException ignored) {}
        }
        final String finalDurabilityFormat = durabilityFormat;

        long startTime = System.currentTimeMillis() / 1000L;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
                    Hologram hologram = manager.getHologram(hologramName).orElse(null);
                    
                    if (hologram == null) {
                        this.cancel();
                        return;
                    }

                    long currentTime = System.currentTimeMillis() / 1000L;
                    int elapsed = (int) (currentTime - startTime);
                    int remaining = Math.max(0, totalTime - elapsed);

                    if (remaining <= 0) {
                        this.cancel();
                        return;
                    }

                    int minutes = remaining / 60;
                    int seconds = remaining % 60;
                    String timeStr = String.format("%02d:%02d", minutes, seconds);

                    List<String> lines = List.of(
                        ChatColor.translateAlternateColorCodes('&', title),
                        ChatColor.translateAlternateColorCodes('&', targetFormat.replace("%target%", targetName)),
                        ChatColor.translateAlternateColorCodes('&', timeFormat.replace("%time%", timeStr)),
                        ChatColor.translateAlternateColorCodes('&', finalDurabilityFormat
                            .replace("%durability%", String.valueOf(durability))
                            .replace("%maxDurability%", String.valueOf(maxDurability)))
                    );

                    if (hologram.getData() instanceof TextHologramData textData) {
                        textData.setText(lines);
                        hologram.forceUpdate();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to update hologram: " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        updateTasks.put(getKey(location), task);
    }

    /**
     * 更新耐久度显示
     */
    public void updateDurability(Location location, int durability, int maxDurability) {
        if (!fancyHologramsEnabled) return;

        String hologramName = hologramNames.get(getKey(location));
        if (hologramName == null) return;

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            Hologram hologram = manager.getHologram(hologramName).orElse(null);
            
            if (hologram != null && hologram.getData() instanceof TextHologramData textData) {
                List<String> currentLines = textData.getText();
                // 更新耐久度行
                if (currentLines.size() >= 3) {
                    List<String> newLines = new java.util.ArrayList<>(currentLines);
                    newLines.set(newLines.size() - 1, ChatColor.translateAlternateColorCodes('&', 
                        "&7耐久: &a" + durability + "&7/&a" + maxDurability));
                    textData.setText(newLines);
                    hologram.forceUpdate();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update hologram durability: " + e.getMessage());
        }
    }

    /**
     * 移除全息显示
     */
    public void removeHologram(Location location) {
        if (!fancyHologramsEnabled) return;

        debug("Removing hologram at " + location);
        Location key = getKey(location);
        
        // 停止更新任务
        BukkitTask task = updateTasks.remove(key);
        if (task != null) {
            task.cancel();
        }

        // 删除全息图
        String hologramName = hologramNames.remove(key);
        if (hologramName != null) {
            try {
                HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
                manager.getHologram(hologramName).ifPresent(hologram -> {
                    manager.removeHologram(hologram);
                    debug("Successfully removed hologram '" + hologramName + "'");
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to remove hologram: " + e.getMessage());
                debug("Error removing hologram: " + e.getMessage());
            }
        } else {
            debug("No hologram found at location to remove");
        }
    }

    /**
     * 清除所有全息显示
     */
    public void clearAll() {
        for (Location location : new HashMap<>(hologramNames).keySet()) {
            removeHologram(location);
        }
    }

    /**
     * 重新加载所有信标的全息显示
     * 用于 reload 命令
     */
    public void reloadAllHolograms() {
        if (!fancyHologramsEnabled) {
            debug("FancyHolograms not enabled, skipping hologram reload");
            return;
        }

        plugin.getLogger().info("Reloading all beacon holograms...");
        debug("Starting hologram reload process");
        
        // 清除现有全息显示
        clearAll();
        
        // 遍历所有世界的所有信标方块，重新创建全息显示
        plugin.getServer().getWorlds().forEach(world -> {
            world.getLoadedChunks();
            world.getBlockAt(0, 0, 0); // 确保世界已加载
        });
        
        // 从耐久度数据中重建全息显示
        BeaconDurabilityData durabilityData = plugin.getBeaconDurabilityData();
        int totalBeacons = durabilityData.getAllBeacons().size();
        debug("Found " + totalBeacons + " beacons in data");
        
        int[] reloadedCount = {0};
        int[] skippedCount = {0};
        
        durabilityData.getAllBeacons().forEach((location, beaconInfo) -> {
            debug("Processing beacon at " + location + " (owner: " + beaconInfo.getOwnerName() + ")");
            
            // 检查方块是否仍然是信标
            if (location.getBlock().getType() == org.bukkit.Material.BEACON) {
                // 检查是否正在复活中
                if (plugin.getReviveTaskManager().getReviveTask(location) == null) {
                    debug("Creating hologram for beacon at " + location);
                    // 创建空闲状态的全息显示
                    createIdleHologram(
                        location,
                        beaconInfo.getOwnerName(),
                        beaconInfo.getDurability(),
                        beaconInfo.getMaxDurability()
                    );
                    reloadedCount[0]++;
                } else {
                    debug("Skipping beacon at " + location + " - revive in progress");
                    skippedCount[0]++;
                }
            } else {
                debug("Skipping location " + location + " - not a beacon block (type: " + location.getBlock().getType() + ")");
                skippedCount[0]++;
            }
        });
        
        plugin.getLogger().info("Beacon holograms reloaded! Created: " + reloadedCount[0] + ", Skipped: " + skippedCount[0]);
        debug("Reload complete - Total: " + totalBeacons + ", Created: " + reloadedCount[0] + ", Skipped: " + skippedCount[0]);
    }

    /**
     * 生成位置键
     */
    private Location getKey(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * 检查 FancyHolograms 是否可用
     */
    public boolean isFancyHologramsEnabled() {
        return fancyHologramsEnabled;
    }
}
