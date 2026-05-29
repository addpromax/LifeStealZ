package com.zetaplugins.lifestealz.listeners.revivebeacon;

import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.util.MessageUtils;
import com.zetaplugins.lifestealz.util.customblocks.BeaconDurabilityData;
import com.zetaplugins.lifestealz.util.customblocks.CustomBlock;
import com.zetaplugins.lifestealz.util.customitems.CustomItemManager;
import com.zetaplugins.lifestealz.util.customitems.CustomItemType;
import com.zetaplugins.lifestealz.util.customitems.customitemdata.CustomBeaconRepairItemData;
import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 监听玩家使用信标修复物品
 */
@AutoRegisterListener
public final class BeaconRepairListener implements Listener {
    private final LifeStealZ plugin;

    public BeaconRepairListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBeaconRepair(PlayerInteractEvent event) {
        // 只处理右键点击方块
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        // 只处理主手
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 检查是否是信标修复物品
        if (!CustomItemType.BEACON_REPAIR.is(item)) return;

        Block block = event.getClickedBlock();
        if (block == null || !block.getType().equals(Material.BEACON)) return;
        
        // 检查是否是复活信标
        if (!CustomBlock.REVIVE_BEACON.is(block)) return;

        event.setCancelled(true);

        // 获取修复物品数据
        String repairItemId = CustomItemManager.getCustomItemId(item);
        CustomBeaconRepairItemData repairData;
        try {
            repairData = new CustomBeaconRepairItemData(repairItemId);
        } catch (IllegalArgumentException e) {
            return;
        }

        // 获取信标的自定义物品ID
        String beaconItemId = CustomBlock.REVIVE_BEACON.getCustomItemId(block);
        
        // 检查是否可以修复此信标
        if (!repairData.canRepairBeacon(beaconItemId)) {
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "beaconRepairNotAllowed",
                    "&cThis repair item cannot be used on this beacon!"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 获取信标数据
        BeaconDurabilityData.BeaconInfo beaconInfo = plugin.getBeaconDurabilityData().getBeaconInfo(block.getLocation());
        if (beaconInfo == null) {
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "beaconDataNotFound",
                    "&cBeacon data not found!"
            ));
            return;
        }

        // 检查是否已满耐久
        if (beaconInfo.getDurability() >= beaconInfo.getMaxDurability()) {
            player.sendMessage(MessageUtils.getAndFormatMsg(
                    true,
                    "beaconAlreadyFullDurability",
                    "&cThis beacon is already at full durability!"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 计算修复后的耐久度
        int repairAmount = repairData.getRepairAmount();
        int currentDurability = beaconInfo.getDurability();
        int maxDurability = beaconInfo.getMaxDurability();
        int newDurability = Math.min(currentDurability + repairAmount, maxDurability);
        int actualRepaired = newDurability - currentDurability;

        // 更新耐久度
        beaconInfo.setDurability(newDurability);

        // 更新全息显示
        plugin.getReviveBeaconHologramManager().removeHologram(block.getLocation());
        plugin.getReviveBeaconHologramManager().createIdleHologram(
                block.getLocation(),
                beaconInfo.getOwnerName(),
                newDurability,
                maxDurability
        );

        // 消耗物品
        item.setAmount(item.getAmount() - 1);

        // 播放音效
        player.playSound(block.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);

        // 发送消息
        player.sendMessage(MessageUtils.getAndFormatMsg(
                true,
                "beaconRepairSuccess",
                "&aSuccessfully repaired the beacon! &7(&e+%amount% &7durability, now &e%current%&7/&e%max%&7)",
                new MessageUtils.Replaceable("%amount%", String.valueOf(actualRepaired)),
                new MessageUtils.Replaceable("%current%", String.valueOf(newDurability)),
                new MessageUtils.Replaceable("%max%", String.valueOf(maxDurability))
        ));
    }
}
