package com.zetaplugins.lifestealz.listeners.revivebeacon;

import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.util.GuiManager;
import com.zetaplugins.lifestealz.util.MessageUtils;
import com.zetaplugins.lifestealz.util.revive.ReviveTask;
import com.zetaplugins.lifestealz.util.customblocks.CustomBlock;
import com.zetaplugins.lifestealz.util.customblocks.BeaconDurabilityData;
import com.zetaplugins.lifestealz.util.customitems.CustomItemManager;
import com.zetaplugins.lifestealz.util.customitems.customitemdata.CustomReviveBeaconItemData;

@AutoRegisterListener
public final class ReviveBeaconInteractListener implements Listener {
    private final LifeStealZ plugin;

    public ReviveBeaconInteractListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onReviveBeaconInteract(PlayerInteractEvent event) {
        Action action = event.getAction();

        if (!action.equals(Action.RIGHT_CLICK_BLOCK)) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!CustomBlock.REVIVE_BEACON.is(block)) return;
        Player player = event.getPlayer();
        event.setCancelled(true);

        // 检查是否正在复活中
        ReviveTask reviveTask = plugin.getReviveTaskManager().getReviveTask(block.getLocation());
        if (reviveTask != null) {
            long nowSeconds = System.currentTimeMillis() / 1000L;
            int secondsLeft = (int) (reviveTask.start() + reviveTask.durationSeconds() - nowSeconds);
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "beaconInUseInteract",
                    "&7There is already a revive process in progress. Time left: &c%timeLeft% seconds&7.",
                    new MessageUtils.Replaceable("%timeLeft%", String.valueOf(secondsLeft))
            ));
            return;
        }

        // 获取信标数据
        BeaconDurabilityData.BeaconInfo beaconInfo = plugin.getBeaconDurabilityData().getBeaconInfo(block.getLocation());
        if (beaconInfo == null) {
            return;
        }

        // 获取自定义物品数据
        String customItemId = CustomBlock.REVIVE_BEACON.getCustomItemId(block);
        CustomReviveBeaconItemData itemData;
        try {
            itemData = new CustomReviveBeaconItemData(customItemId);
        } catch (IllegalArgumentException e) {
            return;
        }

        // 如果玩家潜行且是信标所有者，并且允许自动复活
        if (player.isSneaking() && player.getUniqueId().equals(beaconInfo.getOwnerId()) && itemData.isAllowAutoRevive()) {
            // 检查是否已设置自动复活
            if (plugin.getAutoReviveManager().hasAutoRevive(player.getUniqueId())) {
                // 移除自动复活设置
                plugin.getAutoReviveManager().removeAutoReviveBeacon(player.getUniqueId());
                player.sendMessage(MessageUtils.getAndFormatMsg(
                        true,
                        "beaconRemoveAutoRevive",
                        "&7You have removed your auto-revive setting!"
                ));
            } else {
                // 设置自动复活
                plugin.getAutoReviveManager().setAutoReviveBeacon(
                        player.getUniqueId(),
                        block.getLocation(),
                        player.getName()
                );
                player.sendMessage(MessageUtils.getAndFormatMsg(
                        true,
                        "beaconSetAutoRevive",
                        "&7You have set this beacon as your auto-revive point!"
                ));
            }
            return;
        }

        // 打开复活GUI
        GuiManager.openReviveBeaconGui(player, 1, plugin, block.getLocation());
    }
}
