package com.zetaplugins.lifestealz.listeners.revivebeacon;

import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.util.MessageUtils;
import com.zetaplugins.lifestealz.util.customblocks.CustomBlock;
import com.zetaplugins.lifestealz.util.customitems.CustomItemManager;
import com.zetaplugins.lifestealz.util.customitems.CustomItemType;
import com.zetaplugins.lifestealz.util.customitems.customitemdata.CustomReviveBeaconItemData;

@AutoRegisterListener
public final class ReviveBeaconPlaceListener implements Listener {
    private final LifeStealZ plugin;

    public ReviveBeaconPlaceListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!block.getType().equals(Material.BEACON)) return;

        ItemStack itemInHand = event.getItemInHand();
        if (!CustomItemType.REVIVE_BEACON.is(itemInHand)) return;

        Player player = event.getPlayer();
        if (!canPlace(player, block.getLocation(), Material.BEACON)) {
            event.setCancelled(true);
            return;
        }

        String customItemId = CustomItemManager.getCustomItemId(itemInHand);
        CustomReviveBeaconItemData itemData;

        try {
            itemData = new CustomReviveBeaconItemData(customItemId);
        } catch (IllegalArgumentException e) {
            return;
        }

        String world = block.getWorld().getName();
        if (!itemData.isAllowedInWorld(world)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtils.getAndFormatMsg(
                    false,
                    "noItemUseInWorld",
                    "&cYou cannot use this item in this world!"
            ));
            return;
        }

        CustomBlock.REVIVE_BEACON.make(block, customItemId);

        // 初始化耐久度数据
        int durability = itemData.getDurability();
        int maxDurability = durability;
        plugin.getBeaconDurabilityData().setBeaconData(
                block.getLocation(),
                player.getUniqueId(),
                player.getName(),
                durability,
                maxDurability
        );
        
        // 保存到数据库
        if (plugin.getBeaconDataStorage() != null) {
            boolean isSlimeWorld = com.zetaplugins.lifestealz.util.SlimeWorldHelper.isSlimeWorld(block.getWorld());
            
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("准备保存信标到数据库: " + block.getLocation() + 
                    " (所有者: " + player.getName() + 
                    ", UUID: " + player.getUniqueId() +
                    ", 耐久度: " + durability + "/" + maxDurability +
                    ", SlimeWorld: " + isSlimeWorld + ")");
            }
            
            plugin.getBeaconDataStorage().saveBeacon(
                    block.getLocation(),
                    player.getUniqueId(),
                    player.getName(),
                    durability,
                    maxDurability,
                    isSlimeWorld
            );
        } else {
            plugin.getLogger().warning("BeaconDataStorage 未初始化，无法保存信标数据！");
        }

        // 启动空闲特效
        plugin.getReviveBeaconEffectManager().startIdleEffects(
                block.getLocation(),
                itemData.shouldShowEnchantParticles(),
                itemData.getDecoyMaterial()
        );

        // 创建全息显示
        plugin.getReviveBeaconHologramManager().createIdleHologram(
                block.getLocation(),
                player.getName(),
                durability,
                maxDurability
        );
    }

    private boolean canPlace(Player player, Location loc, Material type) {
        Block block = loc.getBlock();
        Block placedAgainst = block.getRelative(BlockFace.DOWN);

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                block,
                block.getState(),
                placedAgainst,
                new ItemStack(type),
                player,
                true,
                EquipmentSlot.HAND
        );

        Bukkit.getPluginManager().callEvent(placeEvent);

        return !placeEvent.isCancelled();
    }
}
