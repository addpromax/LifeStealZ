package com.zetaplugins.lifestealz.util.customitems.customitemdata;

import org.bukkit.Material;
import com.zetaplugins.lifestealz.util.customblocks.ParticleColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.Color;

import java.util.Objects;

public final class CustomReviveBeaconItemData extends CustomItemData {
    private final int reviveTime;
    private final int durability;
    private final boolean allowAutoRevive;
    private final boolean allowBreakingBeaconWhileReviving;
    private final boolean showLaser;
    private final Material innerLaser;
    private final Material outerLaser;
    private final boolean showParticleRing;
    private final ParticleColor particleColor;
    private final boolean showEnchantParticles;
    private final Material decoyMaterial;
    private final boolean enableHologram;
    private final String hologramTitle;
    private final String hologramOwnerFormat;
    private final String hologramDurabilityFormat;
    private final String hologramRevivingTitle;
    private final String hologramTargetFormat;
    private final String hologramTimeFormat;
    private final Color hologramBackground;
    private final double hologramOffsetX;
    private final double hologramOffsetY;
    private final double hologramOffsetZ;

    public CustomReviveBeaconItemData(String itemId) throws IllegalArgumentException {
        super(itemId);
        this.reviveTime = getConfigurationSection().getInt("reviveTime", 30);
        this.durability = getConfigurationSection().getInt("durability", -1);
        this.allowAutoRevive = getConfigurationSection().getBoolean("allowAutoRevive", true);
        this.allowBreakingBeaconWhileReviving = getConfigurationSection().getBoolean("allowBreakingBeaconWhileReviving", true);
        
        // 全息显示配置
        this.enableHologram = getConfigurationSection().getBoolean("hologram.enabled", true);
        this.hologramTitle = getConfigurationSection().getString("hologram.title", "&c&l复活信标");
        this.hologramOwnerFormat = getConfigurationSection().getString("hologram.ownerFormat", "&7所有者: &e%owner%");
        this.hologramDurabilityFormat = getConfigurationSection().getString("hologram.durabilityFormat", "&7耐久: &a%durability%&7/&a%maxDurability%");
        this.hologramRevivingTitle = getConfigurationSection().getString("hologram.revivingTitle", "&e&l复活进行中");
        this.hologramTargetFormat = getConfigurationSection().getString("hologram.targetFormat", "&7目标: &e%target%");
        this.hologramTimeFormat = getConfigurationSection().getString("hologram.timeFormat", "&7剩余时间: &e%time%");
        
        // 全息图背景颜色配置（默认为透明）
        this.hologramBackground = parseBackgroundColor(
            getConfigurationSection().getString("hologram.background", "transparent")
        );
        
        // 全息图位置偏移配置（相对于方块中心）
        this.hologramOffsetX = getConfigurationSection().getDouble("hologram.offsetX", 0.5);
        this.hologramOffsetY = getConfigurationSection().getDouble("hologram.offsetY", 2.5);
        this.hologramOffsetZ = getConfigurationSection().getDouble("hologram.offsetZ", 0.5);
        this.showLaser = getConfigurationSection().getBoolean("showLaser", true);
        this.innerLaser = parseMaterial(
                Objects.requireNonNullElse(getConfigurationSection().getString("innerLaserMaterial"), "RED_GLAZED_TERRACOTTA"),
                Material.RED_GLAZED_TERRACOTTA
        );
        this.outerLaser = parseMaterial(
                Objects.requireNonNullElse(getConfigurationSection().getString("outerLaserMaterial"), "RED_STAINED_GLASS"),
                Material.RED_STAINED_GLASS
        );
        this.showParticleRing = getConfigurationSection().getBoolean("showParticleRing", true);
        this.particleColor = ParticleColor.fromString(
                getConfigurationSection().getString("particleColor", "RED")
        );
        this.showEnchantParticles = getConfigurationSection().getBoolean("showEnchantParticles", true);
        this.decoyMaterial = parseMaterial(
                getConfigurationSection().getString("decoyMaterial", "RED_STAINED_GLASS"),
                Material.RED_STAINED_GLASS
        );
    }

    public int getReviveTime() {
        return reviveTime;
    }

    public boolean isAllowBreakingBeaconWhileReviving() {
        return allowBreakingBeaconWhileReviving;
    }

    public boolean shouldShowLaser() {
        return showLaser;
    }

    public boolean shouldShowParticleRing() {
        return showParticleRing;
    }

    public boolean shouldShowEnchantParticles() {
        return showEnchantParticles;
    }

    public Material getDecoyMaterial() {
        return decoyMaterial;
    }

    public Material getInnerLaser() {
        return innerLaser;
    }

    public Material getOuterLaser() {
        return outerLaser;
    }

    public ParticleColor getParticleColor() {
        return particleColor;
    }

    public int getDurability() {
        return durability;
    }

    public boolean isAllowAutoRevive() {
        return allowAutoRevive;
    }

    public boolean isEnableHologram() {
        return enableHologram;
    }

    public String getHologramTitle() {
        return hologramTitle;
    }

    public String getHologramOwnerFormat() {
        return hologramOwnerFormat;
    }

    public String getHologramDurabilityFormat() {
        return hologramDurabilityFormat;
    }

    public String getHologramRevivingTitle() {
        return hologramRevivingTitle;
    }

    public String getHologramTargetFormat() {
        return hologramTargetFormat;
    }

    public String getHologramTimeFormat() {
        return hologramTimeFormat;
    }

    public Color getHologramBackground() {
        return hologramBackground;
    }

    public double getHologramOffsetX() {
        return hologramOffsetX;
    }

    public double getHologramOffsetY() {
        return hologramOffsetY;
    }

    public double getHologramOffsetZ() {
        return hologramOffsetZ;
    }

    /**
     * Parses a material from a string, returning a fallback material if the string is invalid.
     * @param materialName the name of the material to parse
     * @param fallbackMaterial the material to return if the string is invalid
     * @return the parsed material, or the fallback material if the string is invalid
     */
    private Material parseMaterial(String materialName, Material fallbackMaterial) {
        Material material = Material.getMaterial(materialName.toUpperCase());
        return material != null ? material : fallbackMaterial;
    }

    /**
     * Parses a background color from a string.
     * Supports:
     * - "transparent" for transparent background
     * - "#AARRGGBB" for ARGB hex color (8 digits with alpha)
     * - "#RRGGBB" for RGB hex color (6 digits, alpha set to 255)
     * @param colorString the color string to parse
     * @return the parsed color, or null for transparent
     */
    private Color parseBackgroundColor(String colorString) {
        if (colorString == null || colorString.equalsIgnoreCase("transparent")) {
            return null; // null表示透明
        }
        
        if (colorString.startsWith("#")) {
            try {
                String hex = colorString.substring(1);
                if (hex.length() == 8) {
                    // ARGB格式
                    return Color.fromARGB((int) Long.parseLong(hex, 16));
                } else if (hex.length() == 6) {
                    // RGB格式，alpha设为255（不透明）
                    int rgb = Integer.parseInt(hex, 16);
                    return Color.fromARGB(0xFF000000 | rgb);
                }
            } catch (NumberFormatException e) {
                // 解析失败，返回透明
            }
        }
        
        return null; // 默认透明
    }
}
