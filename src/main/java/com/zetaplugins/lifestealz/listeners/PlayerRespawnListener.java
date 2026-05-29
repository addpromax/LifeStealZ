package com.zetaplugins.lifestealz.listeners;

import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.storage.PlayerData;
import com.zetaplugins.lifestealz.util.MessageUtils;
import com.zetaplugins.lifestealz.util.customblocks.BeaconDurabilityData;
import com.zetaplugins.lifestealz.util.customblocks.CustomBlock;
import com.zetaplugins.lifestealz.util.customitems.customitemdata.CustomReviveBeaconItemData;
import com.zetaplugins.lifestealz.util.revive.AutoReviveManager;
import com.zetaplugins.lifestealz.util.revive.ReviveTask;
import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

/**
 * 处理玩家重生事件，实现自动复活功能
 */
@AutoRegisterListener
public final class PlayerRespawnListener implements Listener {
    private final LifeStealZ plugin;

    public PlayerRespawnListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // 检查是否有自动复活设置
        if (!plugin.getAutoReviveManager().hasAutoRevive(player.getUniqueId())) {
            return;
        }

        AutoReviveManager.AutoReviveBeacon autoReviveBeacon = plugin.getAutoReviveManager().getAutoReviveBeacon(player.getUniqueId());
        if (autoReviveBeacon == null) {
            return;
        }

        Location beaconLocation = autoReviveBeacon.getLocation();
        
        // 检查信标是否还存在
        if (!CustomBlock.REVIVE_BEACON.is(beaconLocation.getBlock())) {
            plugin.getAutoReviveManager().removeAutoReviveBeacon(player.getUniqueId());
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
            plugin.getAutoReviveManager().removeAutoReviveBeacon(player.getUniqueId());
            return;
        }

        // 检查耐久度
        if (beaconInfo.getDurability() <= 0) {
            plugin.getAutoReviveManager().removeAutoReviveBeacon(player.getUniqueId());
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

        // 设置重生点为信标附近
        Location respawnLoc = beaconLocation.clone().add(0.5, 1, 0.5);
        event.setRespawnLocation(respawnLoc);

        // 延迟执行自动复活逻辑（等待玩家完全重生）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startAutoRevive(player, beaconLocation, itemData, beaconInfo);
        }, 5L);
    }

    /**
     * 开始自动复活流程
     */
    private void startAutoRevive(Player player, Location beaconLocation, CustomReviveBeaconItemData itemData, BeaconDurabilityData.BeaconInfo beaconInfo) {
        int reviveTime = itemData.getReviveTime();
        
        // 设置玩家为旁观模式
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(beaconLocation.clone().add(0.5, 2, 0.5));
        
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
                if (playerLoc.distance(beaconLocation) > 10) {
                    player.teleport(beaconLocation.clone().add(0.5, 2, 0.5));
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
                player.getUniqueId(), // 自己复活自己
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
        player.teleport(beaconLocation.clone().add(0.5, 1, 0.5));

        // 移除复活任务
        plugin.getReviveTaskManager().removeReviveTask(beaconLocation);

        // 减少耐久度
        int remainingDurability = plugin.getBeaconDurabilityData().decreaseDurability(beaconLocation);

        // 清理特效和全息显示
        plugin.getReviveBeaconEffectManager().clearAllEffects(beaconLocation);
        plugin.getReviveBeaconHologramManager().removeHologram(beaconLocation);

        // 发送成功消息
        player.sendMessage(MessageUtils.getAndFormatMsg(
                true,
                "autoReviveSuccess",
                "&aYou have been auto-revived! Welcome back!"
        ));

        // 播放音效
        beaconLocation.getWorld().playSound(beaconLocation, Sound.ENTITY_PLAYER_LEVELUP, 500.0f, 1.0f);

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
}
