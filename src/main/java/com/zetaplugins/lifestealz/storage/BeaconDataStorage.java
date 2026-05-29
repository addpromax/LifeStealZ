package com.zetaplugins.lifestealz.storage;

import com.zetaplugins.lifestealz.LifeStealZ;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 信标数据持久化存储
 * 使用现有的 Storage 数据库连接，创建独立的信标表
 */
public class BeaconDataStorage {
    private final LifeStealZ plugin;
    private final Storage storage;
    private final String tablePrefix;

    public BeaconDataStorage(LifeStealZ plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorage();
        this.tablePrefix = "lifestealz_";
        
        initialize();
    }

    /**
     * 初始化信标数据表
     */
    private void initialize() {
        try {
            createTables();
            plugin.getLogger().info("信标数据表已初始化（使用现有数据库连接）");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "无法初始化信标数据表", e);
        }
    }

    /**
     * 创建数据表
     */
    private void createTables() throws SQLException {
        Connection connection = getConnection();
        if (connection == null) {
            throw new SQLException("无法获取数据库连接来创建信标表");
        }
        
        try {
            // 检测是 SQLite 还是 MySQL
            boolean isSQLite = connection.getMetaData().getDriverName().toLowerCase().contains("sqlite");
            
            String sql;
            if (isSQLite) {
                sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "beacons (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "world_name TEXT NOT NULL," +
                        "x REAL NOT NULL," +
                        "y REAL NOT NULL," +
                        "z REAL NOT NULL," +
                        "owner_uuid TEXT NOT NULL," +
                        "owner_name TEXT NOT NULL," +
                        "durability INTEGER NOT NULL," +
                        "max_durability INTEGER NOT NULL," +
                        "is_slime_world INTEGER NOT NULL DEFAULT 0," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL," +
                        "UNIQUE(world_name, x, y, z)" +
                        ")";
            } else {
                sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "beacons (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "world_name VARCHAR(255) NOT NULL," +
                        "x DOUBLE NOT NULL," +
                        "y DOUBLE NOT NULL," +
                        "z DOUBLE NOT NULL," +
                        "owner_uuid VARCHAR(36) NOT NULL," +
                        "owner_name VARCHAR(16) NOT NULL," +
                        "durability INT NOT NULL," +
                        "max_durability INT NOT NULL," +
                        "is_slime_world TINYINT(1) NOT NULL DEFAULT 0," +
                        "created_at BIGINT NOT NULL," +
                        "updated_at BIGINT NOT NULL," +
                        "UNIQUE KEY unique_location (world_name, x, y, z)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            }
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } finally {
            connection.close();
        }
    }
    
    /**
     * 从 Storage 获取数据库连接
     */
    private Connection getConnection() {
        try {
            if (storage instanceof SQLStorage) {
                return ((SQLStorage) storage).getConnection();
            } else {
                plugin.getLogger().severe("Storage 类型不是 SQLStorage！实际类型: " + 
                    (storage != null ? storage.getClass().getName() : "null"));
                plugin.getLogger().severe("信标数据存储需要使用 SQLite 或 MySQL 数据库！");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取数据库连接失败", e);
        }
        return null;
    }

    /**
     * 保存信标数据
     */
    public void saveBeacon(Location location, UUID ownerId, String ownerName, int durability, int maxDurability, boolean isSlimeWorld) {
        Connection connection = getConnection();
        if (connection == null) {
            plugin.getLogger().severe("无法获取数据库连接，信标保存失败！请检查 Storage 是否正确初始化。");
            return;
        }
        
        try {
            boolean isSQLite = connection.getMetaData().getDriverName().toLowerCase().contains("sqlite");
            
            String sql;
            if (isSQLite) {
                sql = "INSERT OR REPLACE INTO " + tablePrefix + "beacons " +
                        "(world_name, x, y, z, owner_uuid, owner_name, durability, max_durability, is_slime_world, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO " + tablePrefix + "beacons " +
                        "(world_name, x, y, z, owner_uuid, owner_name, durability, max_durability, is_slime_world, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "owner_uuid = VALUES(owner_uuid), owner_name = VALUES(owner_name), " +
                        "durability = VALUES(durability), max_durability = VALUES(max_durability), " +
                        "is_slime_world = VALUES(is_slime_world), updated_at = VALUES(updated_at)";
            }
            
            long now = System.currentTimeMillis();
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, location.getWorld().getName());
                stmt.setDouble(2, location.getX());
                stmt.setDouble(3, location.getY());
                stmt.setDouble(4, location.getZ());
                stmt.setString(5, ownerId.toString());
                stmt.setString(6, ownerName);
                stmt.setInt(7, durability);
                stmt.setInt(8, maxDurability);
                stmt.setInt(9, isSlimeWorld ? 1 : 0);
                stmt.setLong(10, now);
                stmt.setLong(11, now);
                
                int affectedRows = stmt.executeUpdate();
                
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("执行信标保存SQL: 影响行数=" + affectedRows);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存信标数据失败", e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "关闭连接失败", e);
            }
        }
    }

    /**
     * 更新信标耐久度
     */
    public void updateBeaconDurability(Location location, int durability) {
        Connection connection = getConnection();
        if (connection == null) return;
        
        String sql = "UPDATE " + tablePrefix + "beacons SET durability = ?, updated_at = ? " +
                "WHERE world_name = ? AND x = ? AND y = ? AND z = ?";
        
        try {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, durability);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, location.getWorld().getName());
                stmt.setDouble(4, location.getX());
                stmt.setDouble(5, location.getY());
                stmt.setDouble(6, location.getZ());
                
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "更新信标耐久度失败", e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "关闭连接失败", e);
            }
        }
    }

    /**
     * 删除信标数据
     */
    public void removeBeacon(Location location) {
        Connection connection = getConnection();
        if (connection == null) return;
        
        String sql = "DELETE FROM " + tablePrefix + "beacons " +
                "WHERE world_name = ? AND x = ? AND y = ? AND z = ?";
        
        try {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, location.getWorld().getName());
                stmt.setDouble(2, location.getX());
                stmt.setDouble(3, location.getY());
                stmt.setDouble(4, location.getZ());
                
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "删除信标数据失败", e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "关闭连接失败", e);
            }
        }
    }

    /**
     * 获取特定世界的所有信标数据
     */
    public List<BeaconData> getBeaconsInWorld(String worldName) {
        List<BeaconData> beacons = new ArrayList<>();
        Connection connection = getConnection();
        if (connection == null) return beacons;
        
        String sql = "SELECT * FROM " + tablePrefix + "beacons WHERE world_name = ?";
        
        try {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, worldName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        beacons.add(parseBeaconData(rs));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "读取世界信标数据失败: " + worldName, e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "关闭连接失败", e);
            }
        }
        
        return beacons;
    }

    /**
     * 获取特定位置的信标数据
     */
    public BeaconData getBeacon(Location location) {
        Connection connection = getConnection();
        if (connection == null) return null;
        
        String sql = "SELECT * FROM " + tablePrefix + "beacons " +
                "WHERE world_name = ? AND x = ? AND y = ? AND z = ?";
        
        try {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, location.getWorld().getName());
                stmt.setDouble(2, location.getX());
                stmt.setDouble(3, location.getY());
                stmt.setDouble(4, location.getZ());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return parseBeaconData(rs);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "读取信标数据失败", e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "关闭连接失败", e);
            }
        }
        
        return null;
    }

    /**
     * 解析 ResultSet 为 BeaconData
     */
    private BeaconData parseBeaconData(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world_name");
        double x = rs.getDouble("x");
        double y = rs.getDouble("y");
        double z = rs.getDouble("z");
        UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
        String ownerName = rs.getString("owner_name");
        int durability = rs.getInt("durability");
        int maxDurability = rs.getInt("max_durability");
        boolean isSlimeWorld = rs.getInt("is_slime_world") == 1;
        
        World world = Bukkit.getWorld(worldName);
        Location location = world != null ? new Location(world, x, y, z) : null;
        
        return new BeaconData(location, worldName, ownerId, ownerName, durability, maxDurability, isSlimeWorld);
    }

    /**
     * 关闭数据库连接（使用连接池时无需操作）
     */
    public void close() {
        // 使用现有的 Storage 连接池，无需关闭
        plugin.getLogger().info("信标数据存储已关闭（使用连接池）");
    }

    /**
     * 信标数据类
     */
    public static class BeaconData {
        private final Location location;
        private final String worldName;
        private final UUID ownerId;
        private final String ownerName;
        private final int durability;
        private final int maxDurability;
        private final boolean isSlimeWorld;

        public BeaconData(Location location, String worldName, UUID ownerId, String ownerName,
                          int durability, int maxDurability, boolean isSlimeWorld) {
            this.location = location;
            this.worldName = worldName;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.durability = durability;
            this.maxDurability = maxDurability;
            this.isSlimeWorld = isSlimeWorld;
        }

        public Location getLocation() {
            return location;
        }

        public String getWorldName() {
            return worldName;
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

        public boolean isSlimeWorld() {
            return isSlimeWorld;
        }
    }
}
