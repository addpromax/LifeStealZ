package com.zetaplugins.lifestealz.util.customitems.customitemdata;

import java.util.List;

/**
 * 信标修复物品数据类
 * 用于为复活信标补充耐久度
 */
public final class CustomBeaconRepairItemData extends CustomItemData {
    private final int repairAmount;
    private final List<String> allowedBeacons;

    public CustomBeaconRepairItemData(String itemId) throws IllegalArgumentException {
        super(itemId);
        this.repairAmount = getConfigurationSection().getInt("repairAmount", 1);
        this.allowedBeacons = getConfigurationSection().getStringList("allowedBeacons");
    }

    /**
     * 获取修复数量
     * @return 修复的耐久度点数
     */
    public int getRepairAmount() {
        return repairAmount;
    }

    /**
     * 获取允许修复的信标ID列表
     * @return 信标ID列表，如果为空则可以修复所有信标
     */
    public List<String> getAllowedBeacons() {
        return allowedBeacons;
    }

    /**
     * 检查是否可以修复指定的信标
     * @param beaconItemId 信标的自定义物品ID
     * @return 如果可以修复返回true，否则返回false
     */
    public boolean canRepairBeacon(String beaconItemId) {
        // 如果列表为空，可以修复所有信标
        if (allowedBeacons == null || allowedBeacons.isEmpty()) {
            return true;
        }
        // 检查信标ID是否在允许列表中
        return allowedBeacons.contains(beaconItemId);
    }
}
