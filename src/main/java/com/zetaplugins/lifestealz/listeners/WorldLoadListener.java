package com.zetaplugins.lifestealz.listeners;

import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * 监听世界加载事件，用于在SlimeWorld重新加载时恢复信标数据
 */
@AutoRegisterListener
public final class WorldLoadListener implements Listener {
    private final LifeStealZ plugin;

    public WorldLoadListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        String worldName = event.getWorld().getName();
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        
        if (debug) {
            plugin.getLogger().info("检测到世界加载: " + worldName + "，正在从数据库恢复信标数据...");
        }
        
        // 延迟1秒后恢复，确保世界完全加载
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 从数据库恢复该世界中所有信标数据
            int restoredCount = plugin.getAutoReviveManager().restoreBeaconsFromDatabase(worldName);
            
            if (debug) {
                if (restoredCount > 0) {
                    plugin.getLogger().info("成功从数据库恢复 " + restoredCount + " 个信标的数据");
                } else {
                    plugin.getLogger().info("该世界没有需要恢复的信标数据");
                }
            }
        }, 20L); // 20 ticks = 1秒
    }
}
