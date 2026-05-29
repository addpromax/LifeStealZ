package com.zetaplugins.lifestealz.util.revive;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.storage.PlayerData;
import com.zetaplugins.lifestealz.util.MessageUtils;
import com.zetaplugins.lifestealz.util.SlimeWorldHelper;
import com.zetaplugins.lifestealz.util.customblocks.BeaconDurabilityData;
import com.zetaplugins.lifestealz.util.customblocks.CustomBlock;
import com.zetaplugins.lifestealz.util.customitems.customitemdata.CustomReviveBeaconItemData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理玩家的自动复活设置
 */
public final class AutoReviveManager {
    private final LifeStealZ plugin;
    // 玩家UUID -> 复活信标位置
    private final Map<UUID, AutoReviveBeacon> autoReviveBeacons;

    public AutoReviveManager(LifeStealZ plugin) {
        this.plugin = plugin;
        this.autoReviveBeacons = new HashMap<>();
    }

    /**
     * 设置玩家的自动复活信标
     */
    public void setAutoReviveBeacon(UUID playerId, Location beaconLocation, String beaconOwner) {
        boolean isSlimeWorld = false;
        String worldName = "";
        UUID ownerId = null;
        int durability = -1;
        int maxDurability = -1;
        
        if (SlimeWorldHelper.isSlimeWorldAvailable() && beaconLocation.getWorld() != null) {
            isSlimeWorld = SlimeWorldHelper.isSlimeWorld(beaconLocation.getWorld());
            worldName = beaconLocation.getWorld().getName();
        }
        
        // 获取信标的完整数据以便在世界重新加载后恢复
        BeaconDurabilityData.BeaconInfo beaconInfo = plugin.getBeaconDurabilityData().getBeaconInfo(beaconLocation);
        if (beaconInfo != null) {
            ownerId = beaconInfo.getOwnerId();
            durability = beaconInfo.getDurability();
            maxDurability = beaconInfo.getMaxDurability();
        }
        
        AutoReviveBeacon beacon = new AutoReviveBeacon(
            beaconLocation, 
            beaconOwner, 
            System.currentTimeMillis(),
            isSlimeWorld,
            worldName,
            ownerId,
            durability,
            maxDurability
        );
        autoReviveBeacons.put(playerId, beacon);
    }

    /**
     * 移除玩家的自动复活信标设置
     */
    public void removeAutoReviveBeacon(UUID playerId) {
        autoReviveBeacons.remove(playerId);
    }

    /**
     * 获取玩家的自动复活信标
     */
    public AutoReviveBeacon getAutoReviveBeacon(UUID playerId) {
        return autoReviveBeacons.get(playerId);
    }

    /**
     * 检查玩家是否设置了自动复活
     */
    public boolean hasAutoRevive(UUID playerId) {
        return autoReviveBeacons.containsKey(playerId);
    }

    /**
     * 移除指定位置的所有自动复活设置
     */
    public void removeBeaconLocation(Location location) {
        autoReviveBeacons.entrySet().removeIf(entry -> 
            entry.getValue().getLocation().equals(location)
        );
    }

    /**
     * 尝试自动复活玩家
     * @return 是否成功启动自动复活
     */
    public boolean tryAutoRevive(Player player) {
        UUID playerId = player.getUniqueId();
        AutoReviveBeacon beacon = autoReviveBeacons.get(playerId);
        
        if (beacon == null) {
            return false;
        }

        // 如果是SlimeWorld且世界未加载，尝试自动加载
        if (beacon.isSlimeWorld()) {
            String worldName = beacon.getWorldName();
            
            if (!SlimeWorldHelper.isWorldLoaded(worldName)) {
                boolean debug = plugin.getConfig().getBoolean("debug", false);
                if (debug) {
                    plugin.getLogger().info("SlimeWorld未加载，尝试自动加载: " + worldName);
                }
                
                // 异步加载世界
                SlimeWorldHelper.loadSlimeWorldAsync(worldName, loadedWorld -> {
                    if (loadedWorld != null) {
                        if (debug) {
                            plugin.getLogger().info("SlimeWorld加载成功: " + worldName + "，继续复活流程");
                        }
                        
                        // 恢复信标数据（世界重新加载后方块数据会丢失）
                        if (beacon.hasBeaconData()) {
                            if (debug) {
                                plugin.getLogger().info("恢复信标数据: " + beacon.getLocation());
                            }
                            plugin.getBeaconDurabilityData().setBeaconData(
                                beacon.getLocation(),
                                beacon.getOwnerId(),
                                beacon.getOwnerName(),
                                beacon.getDurability(),
                                beacon.getMaxDurability()
                            );
                        }
                        
                        // 世界加载成功，在主线程中启动复活流程
                        if (player.isOnline()) {
                            player.sendMessage("§a复活信标所在的世界已加载，正在执行复活...");
                            // 在主线程中执行复活逻辑
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                executeAutoRevive(player, beacon);
                            });
                        }
                    } else {
                        plugin.getLogger().warning("SlimeWorld加载失败: " + worldName);
                        
                        if (player.isOnline()) {
                            player.sendMessage("§c自动复活失败: 无法加载复活信标所在的世界");
                            player.sendMessage("§e世界: " + worldName);
                            player.sendMessage("§e提示: 请联系管理员检查SlimeWorld配置");
                        }
                    }
                });
                
                // 返回true表示已开始处理
                return true;
            }
        }

        Location beaconLoc = beacon.getLocation();
        
        // 检查信标是否还存在
        if (!isBeaconValid(beaconLoc)) {
            autoReviveBeacons.remove(playerId);
            return false;
        }

        // 检查信标是否正在使用中
        if (plugin.getReviveTaskManager().isReviving(beaconLoc)) {
            return false;
        }

        return true;
    }


    /**
     * 执行自动复活流程
     * 用于世界加载完成后手动触发复活
     */
    private void executeAutoRevive(Player player, AutoReviveBeacon beacon) {
        Location beaconLocation = beacon.getLocation();
        
        // 检查信标是否还存在
        if (!CustomBlock.REVIVE_BEACON.is(beaconLocation.getBlock())) {
            removeAutoReviveBeacon(player.getUniqueId());
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "autoReviveBeaconNotFoundMessage",
                    "&cYour auto-revive beacon no longer exists!"
            ));
            return;
        }

        // 检查信标是否正在使用中
        if (plugin.getReviveTaskManager().isReviving(beaconLocation)) {
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "autoReviveBeaconInUseMessage",
                    "&cYour auto-revive beacon is currently in use! You will respawn normally."
            ));
            return;
        }

        // 获取信标数据
        BeaconDurabilityData.BeaconInfo beaconInfo = plugin.getBeaconDurabilityData().getBeaconInfo(beaconLocation);
        if (beaconInfo == null) {
            removeAutoReviveBeacon(player.getUniqueId());
            return;
        }

        // 检查耐久度
        if (beaconInfo.getDurability() <= 0) {
            removeAutoReviveBeacon(player.getUniqueId());
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "autoReviveBeaconNoDurability",
                    "&cYour auto-revive beacon has no durability left!"
            ));
            return;
        }

        // 获取自定义物品数据
        String customItemId = CustomBlock.REVIVE_BEACON.getCustomItemId(beaconLocation.getBlock());
        CustomReviveBeaconItemData itemData;
        try {
            itemData = new CustomReviveBeaconItemData(customItemId);
        } catch (IllegalArgumentException e) {
            return;
        }

        int reviveTime = itemData.getReviveTime();
        
        // 传送玩家到信标位置并设置为旁观模式
        Location respawnLoc = beaconLocation.clone().add(0.5, 2, 0.5);
        player.teleport(respawnLoc);
        player.setGameMode(GameMode.SPECTATOR);
        
        // 发送消息
        player.sendMessage(MessageUtils.getAndFormatMsg(
                true,
                "autoReviveStartedMessage",
                "&aAuto-revive activated! You will be revived in &e%time% &aseconds...",
                new MessageUtils.Replaceable("%time%", String.valueOf(reviveTime))
        ));

        // 启动特效
        plugin.getReviveBeaconEffectManager().startRevivingEffects(
                beaconLocation,
                player.getName(),
                itemData.shouldShowLaser(),
                itemData.shouldShowParticleRing(),
                itemData.getParticleColor(),
                itemData.getInnerLaser(),
                itemData.getOuterLaser(),
                reviveTime
        );

        // 创建复活中的全息显示
        plugin.getReviveBeaconHologramManager().createRevivingHologram(
                beaconLocation,
                player.getName(),
                reviveTime,
                beaconInfo.getDurability(),
                beaconInfo.getMaxDurability()
        );

        // 创建倒计时任务
        BukkitTask countdownTask = new BukkitRunnable() {
            int timeLeft = reviveTime;
            
            @Override
            public void run() {
                if (!player.isOnline() || player.getGameMode() != GameMode.SPECTATOR) {
                    this.cancel();
                    return;
                }

                // 限制玩家移动范围
                Location playerLoc = player.getLocation();
                
                // 刷新信标位置的世界引用（防止世界重载后引用过期）
                World currentWorld = Bukkit.getWorld(beaconLocation.getWorld().getName());
                if (currentWorld == null) {
                    this.cancel();
                    return;
                }
                
                // 创建使用当前世界实例的位置
                Location currentBeaconLoc = new Location(currentWorld, 
                    beaconLocation.getX(), beaconLocation.getY(), beaconLocation.getZ());
                
                // 检查玩家是否在同一个世界
                if (!playerLoc.getWorld().equals(currentWorld)) {
                    player.teleport(currentBeaconLoc.clone().add(0.5, 2, 0.5));
                    return;
                }
                
                // 检查距离
                if (playerLoc.distance(currentBeaconLoc) > 10) {
                    player.teleport(currentBeaconLoc.clone().add(0.5, 2, 0.5));
                }

                // 显示倒计时 Title
                int minutes = timeLeft / 60;
                int seconds = timeLeft % 60;
                String timeStr = String.format("%02d:%02d", minutes, seconds);
                
                player.showTitle(Title.title(
                        Component.text(ChatColor.translateAlternateColorCodes('&', "&e&l复活中")),
                        Component.text(ChatColor.translateAlternateColorCodes('&', "&7剩余时间: &e" + timeStr)),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)
                ));

                timeLeft--;
                
                if (timeLeft < 0) {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // 创建复活任务
        BukkitTask reviveTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 取消倒计时任务
                countdownTask.cancel();
                
                // 执行复活
                completeAutoRevive(player, beaconLocation, beaconInfo);
            }
        }.runTaskLater(plugin, reviveTime * 20L);

        // 注册复活任务
        plugin.getReviveTaskManager().addReviveTask(beaconLocation, new ReviveTask(
                beaconLocation,
                reviveTask,
                player.getUniqueId(),
                player.getUniqueId(),
                System.currentTimeMillis() / 1000L,
                reviveTime
        ));
    }

    /**
     * 完成自动复活
     */
    private void completeAutoRevive(Player player, Location beaconLocation, BeaconDurabilityData.BeaconInfo beaconInfo) {
        if (!player.isOnline()) {
            return;
        }
        
        // 刷新信标位置的世界引用（防止世界重载后引用过期）
        World currentWorld = Bukkit.getWorld(beaconLocation.getWorld().getName());
        if (currentWorld == null) {
            plugin.getLogger().warning("无法完成自动复活：世界不存在 " + beaconLocation.getWorld().getName());
            return;
        }
        Location currentBeaconLoc = new Location(currentWorld, 
            beaconLocation.getX(), beaconLocation.getY(), beaconLocation.getZ());

        // 恢复玩家数据
        PlayerData data = plugin.getStorage().load(player.getUniqueId());
        double respawnHP = plugin.getConfig().getInt("reviveHearts") * 2;
        data.setMaxHealth(respawnHP);
        data.setHasBeenRevived(data.getHasBeenRevived() + 1);
        plugin.getStorage().save(data);

        // 设置玩家生命值
        LifeStealZ.setMaxHealth(player, respawnHP);
        player.setHealth(respawnHP);

        // 恢复游戏模式
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(currentBeaconLoc.clone().add(0.5, 1, 0.5));

        // 移除复活任务
        plugin.getReviveTaskManager().removeReviveTask(currentBeaconLoc);

        // 减少耐久度
        int remainingDurability = plugin.getBeaconDurabilityData().decreaseDurability(currentBeaconLoc);

        // 清理特效和全息显示
        plugin.getReviveBeaconEffectManager().clearAllEffects(currentBeaconLoc);
        plugin.getReviveBeaconHologramManager().removeHologram(currentBeaconLoc);

        // 发送成功消息
        player.sendMessage(MessageUtils.getAndFormatMsg(
                true,
                "autoReviveSuccess",
                "&aYou have been auto-revived! Welcome back!"
        ));

        // 播放音效
        currentWorld.playSound(currentBeaconLoc, Sound.ENTITY_PLAYER_LEVELUP, 500.0f, 1.0f);

        // 执行复活命令
        String[] location = {
                String.valueOf(beaconLocation.getBlockX()),
                String.valueOf(beaconLocation.getBlockY()),
                String.valueOf(beaconLocation.getBlockZ())
        };

        for (String command : plugin.getConfig().getStringList("reviveCommands")) {
            String finalCommand = command
                    .replace("&player&", player.getName())
                    .replace("&target&", player.getName())
                    .replace("&location&", location[0] + ", " + location[1] + ", " + location[2])
                    .replace("&locationX&", location[0])
                    .replace("&locationY&", location[1])
                    .replace("&locationZ&", location[2]);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        }

        // 如果耐久度为0，销毁信标
        if (remainingDurability == 0) {
            beaconLocation.getBlock().setType(Material.AIR);
            plugin.getBeaconDurabilityData().removeBeacon(beaconLocation);
            plugin.getAutoReviveManager().removeBeaconLocation(beaconLocation);
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "beaconDurabilityZero",
                    "&cThe beacon's durability has been depleted and it has been destroyed!"
            ));
        } else if (remainingDurability > 0) {
            // 重新创建空闲状态的全息显示
            plugin.getReviveBeaconHologramManager().createIdleHologram(
                    beaconLocation,
                    beaconInfo.getOwnerName(),
                    remainingDurability,
                    beaconInfo.getMaxDurability()
            );

            // 如果耐久度低，发送警告
            if (remainingDurability <= 3) {
                player.sendMessage(MessageUtils.getAndFormatMsg(
                        true,
                        "beaconDurabilityLow",
                        "&cWarning: The beacon only has &e%durability%&c uses left!",
                        new MessageUtils.Replaceable("%durability%", String.valueOf(remainingDurability))
                ));
            }
        }
    }

    /**
     * 检查信标是否有效
     * 使用SlimeWorldHelper安全处理SlimeWorld区块系统关闭的情况
     */
    private boolean isBeaconValid(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        
        try {
            // 使用SlimeWorldHelper检查世界是否有效
            if (!SlimeWorldHelper.isWorldValid(location.getWorld())) {
                return false;
            }
            
            // 使用SlimeWorldHelper安全地检查区块是否已加载
            if (!SlimeWorldHelper.isChunkSafelyLoaded(location)) {
                return false;
            }
            
            // 使用SlimeWorldHelper安全地获取方块类型
            String blockType = SlimeWorldHelper.getBlockTypeSafely(location);
            return "BEACON".equals(blockType);
        } catch (Exception e) {
            // 捕获其他可能的异常
            plugin.getLogger().warning("检查信标时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从数据库恢复该世界中所有信标的数据
     * @param worldName 世界名称
     * @return 恢复的信标数量
     */
    public int restoreBeaconsFromDatabase(String worldName) {
        if (plugin.getBeaconDataStorage() == null) {
            plugin.getLogger().warning("信标数据存储未初始化，无法恢复数据");
            return 0;
        }
        
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        if (debug) {
            plugin.getLogger().info("正在从数据库查询世界 " + worldName + " 的信标数据...");
        }
        
        int count = 0;
        List<com.zetaplugins.lifestealz.storage.BeaconDataStorage.BeaconData> beacons = 
            plugin.getBeaconDataStorage().getBeaconsInWorld(worldName);
        
        if (debug) {
            plugin.getLogger().info("数据库中找到 " + beacons.size() + " 个信标记录");
        }
        
        // 收集所有需要创建全息的信标数据
        java.util.List<com.zetaplugins.lifestealz.storage.BeaconDataStorage.BeaconData> validBeacons = new java.util.ArrayList<>();
        
        for (com.zetaplugins.lifestealz.storage.BeaconDataStorage.BeaconData beaconData : beacons) {
            Location location = beaconData.getLocation();
            
            // 检查位置是否有效
            if (location == null || location.getWorld() == null) {
                if (debug) {
                    plugin.getLogger().warning("信标位置无效，跳过: " + worldName + 
                        " (" + beaconData.getOwnerId() + ")");
                }
                continue;
            }
            
            // 检查信标方块是否存在
            if (!CustomBlock.REVIVE_BEACON.is(location.getBlock())) {
                if (debug) {
                    plugin.getLogger().warning("信标方块不存在，跳过: " + location);
                }
                continue;
            }
            
            if (debug) {
                plugin.getLogger().info("从数据库恢复信标数据: " + location + 
                    " (所有者: " + beaconData.getOwnerName() + 
                    ", 耐久度: " + beaconData.getDurability() + "/" + beaconData.getMaxDurability() + ")");
            }
            
            // 恢复数据到 BeaconDurabilityData
            plugin.getBeaconDurabilityData().setBeaconData(
                location,
                beaconData.getOwnerId(),
                beaconData.getOwnerName(),
                beaconData.getDurability(),
                beaconData.getMaxDurability()
            );
            
            // 确保区块已加载
            if (!location.getChunk().isLoaded()) {
                location.getChunk().load();
                if (debug) {
                    plugin.getLogger().info("已加载信标所在区块: " + location.getChunk());
                }
            }
            
            // 直接创建全息显示
            plugin.getReviveBeaconHologramManager().createIdleHologram(
                location,
                beaconData.getOwnerName(),
                beaconData.getDurability(),
                beaconData.getMaxDurability()
            );
            
            validBeacons.add(beaconData);
            count++;
        }
        
        if (debug && !validBeacons.isEmpty()) {
            plugin.getLogger().info("批量创建了 " + count + " 个全息显示");
        }
        
        return count;
    }
    
    /**
     * 当世界加载时，恢复该世界中所有已知信标的数据（内存版本，备用）
     * @param worldName 世界名称
     * @return 恢复的信标数量
     */
    public int restoreBeaconsForWorld(String worldName) {
        int count = 0;
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        
        for (AutoReviveBeacon beacon : autoReviveBeacons.values()) {
            // 检查是否是该世界的信标
            if (!beacon.isSlimeWorld() || !worldName.equals(beacon.getWorldName())) {
                continue;
            }
            
            // 检查是否有保存的数据
            if (!beacon.hasBeaconData()) {
                continue;
            }
            
            Location location = beacon.getLocation();
            
            // 检查信标方块是否存在
            if (location.getWorld() == null || !CustomBlock.REVIVE_BEACON.is(location.getBlock())) {
                continue;
            }
            
            if (debug) {
                plugin.getLogger().info("恢复信标数据: " + location + 
                    " (所有者: " + beacon.getOwnerName() + 
                    ", 耐久度: " + beacon.getDurability() + "/" + beacon.getMaxDurability() + ")");
            }
            
            // 恢复数据到 BeaconDurabilityData
            plugin.getBeaconDurabilityData().setBeaconData(
                location,
                beacon.getOwnerId(),
                beacon.getOwnerName(),
                beacon.getDurability(),
                beacon.getMaxDurability()
            );
            
            // 重新创建全息显示
            plugin.getReviveBeaconHologramManager().createIdleHologram(
                location,
                beacon.getOwnerName(),
                beacon.getDurability(),
                beacon.getMaxDurability()
            );
            
            count++;
        }
        
        return count;
    }
    
    /**
     * 清除所有自动复活设置
     */
    public void clearAll() {
        autoReviveBeacons.clear();
    }

    /**
     * 自动复活信标数据类
     */
    public static class AutoReviveBeacon {
        private final Location location;
        private final String ownerName;
        private final long setTime;
        private final boolean isSlimeWorld;
        private final String worldName;
        private final UUID ownerId;
        private final int durability;
        private final int maxDurability;

        public AutoReviveBeacon(Location location, String ownerName, long setTime) {
            this(location, ownerName, setTime, false, location != null && location.getWorld() != null ? location.getWorld().getName() : "", null, -1, -1);
        }

        public AutoReviveBeacon(Location location, String ownerName, long setTime, boolean isSlimeWorld, String worldName) {
            this(location, ownerName, setTime, isSlimeWorld, worldName, null, -1, -1);
        }

        public AutoReviveBeacon(Location location, String ownerName, long setTime, boolean isSlimeWorld, String worldName, UUID ownerId, int durability, int maxDurability) {
            this.location = location;
            this.ownerName = ownerName;
            this.setTime = setTime;
            this.isSlimeWorld = isSlimeWorld;
            this.worldName = worldName;
            this.ownerId = ownerId;
            this.durability = durability;
            this.maxDurability = maxDurability;
        }

        public Location getLocation() {
            return location;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public long getSetTime() {
            return setTime;
        }

        public boolean isSlimeWorld() {
            return isSlimeWorld;
        }

        public String getWorldName() {
            return worldName;
        }

        public UUID getOwnerId() {
            return ownerId;
        }

        public int getDurability() {
            return durability;
        }

        public int getMaxDurability() {
            return maxDurability;
        }

        public boolean hasBeaconData() {
            return ownerId != null && durability > 0 && maxDurability > 0;
        }
    }
}
