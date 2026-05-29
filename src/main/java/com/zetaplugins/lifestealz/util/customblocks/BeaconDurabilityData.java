package com.zetaplugins.lifestealz.util.customblocks;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 存储复活信标的耐久度和所有者信息
 */
public final class BeaconDurabilityData {
    private final Map<Location, BeaconInfo> beaconData;

    public BeaconDurabilityData() {
        this.beaconData = new HashMap<>();
    }

    /**
     * 设置信标数据
     */
    public void setBeaconData(Location location, UUID ownerId, String ownerName, int durability, int maxDurability) {
        Location key = getKey(location);
        beaconData.put(key, new BeaconInfo(ownerId, ownerName, durability, maxDurability));
    }

    /**
     * 获取信标信息
     */
    public BeaconInfo getBeaconInfo(Location location) {
        return beaconData.get(getKey(location));
    }

    /**
     * 减少耐久度
     * @return 剩余耐久度，如果信标不存在返回-1
     */
    public int decreaseDurability(Location location) {
        Location key = getKey(location);
        BeaconInfo info = beaconData.get(key);
        if (info == null) return -1;
        
        info.durability = Math.max(0, info.durability - 1);
        return info.durability;
    }

    /**
     * 移除信标数据
     */
    public void removeBeacon(Location location) {
        beaconData.remove(getKey(location));
    }

    /**
     * 检查信标是否存在
     */
    public boolean hasBeacon(Location location) {
        return beaconData.containsKey(getKey(location));
    }

    /**
     * 清除所有数据
     */
    public void clearAll() {
        beaconData.clear();
    }

    /**
     * 获取所有信标数据
     * @return 所有信标的位置和信息映射
     */
    public Map<Location, BeaconInfo> getAllBeacons() {
        return new HashMap<>(beaconData);
    }

    /**
     * 生成位置键
     */
    private Location getKey(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * 信标信息类
     */
    public static class BeaconInfo {
        private final UUID ownerId;
        private final String ownerName;
        private int durability;
        private final int maxDurability;

        public BeaconInfo(UUID ownerId, String ownerName, int durability, int maxDurability) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.durability = durability;
            this.maxDurability = maxDurability;
        }

        public UUID getOwnerId() {
            return ownerId;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public int getDurability() {
            return durability;
        }

        public int getMaxDurability() {
            return maxDurability;
        }

        public void setDurability(int durability) {
            this.durability = durability;
        }
    }
}
