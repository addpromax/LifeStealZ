package com.zetaplugins.lifestealz.listeners;

import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.util.MessageUtils;
import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

/**
 * 监听玩家命令，限制自动复活旁观模式下的命令使用
 */
@AutoRegisterListener
public final class PlayerCommandListener implements Listener {
    private final LifeStealZ plugin;

    public PlayerCommandListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        // 只检查旁观模式的玩家
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }

        // 检查玩家是否正在自动复活中
        if (!plugin.getReviveTaskManager().isAutoReviving(player.getUniqueId())) {
            return;
        }

        // 获取命令（包含斜杠）
        String command = event.getMessage().toLowerCase();
        
        // 获取白名单命令列表
        List<String> allowedCommands = plugin.getConfig().getStringList("autoReviveAllowedCommands");
        
        // 检查是否允许所有命令
        if (allowedCommands.contains("*")) {
            return;
        }

        // 检查命令是否在白名单中
        for (String allowedCommand : allowedCommands) {
            String normalizedAllowed = allowedCommand.toLowerCase().trim();
            
            // 确保白名单命令以斜杠开头
            if (!normalizedAllowed.startsWith("/")) {
                normalizedAllowed = "/" + normalizedAllowed;
            }
            
            // 检查命令是否匹配（精确匹配或以白名单命令开头）
            if (command.equals(normalizedAllowed) || command.startsWith(normalizedAllowed + " ")) {
                return;
            }
        }

        // 命令不在白名单中，取消并发送消息
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getAndFormatMsg(
                true,
                "commandNotAllowedDuringRevive",
                "&cYou cannot use this command while being revived!"
        ));
    }
}
