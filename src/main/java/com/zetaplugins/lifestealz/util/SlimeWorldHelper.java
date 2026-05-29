package com.zetaplugins.lifestealz.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.util.logging.Logger;

/**
 * 处理SlimeWorld相关操作的工具类
 * 提供对AdvancedSlimePaper API的安全访问
 */
public class SlimeWorldHelper {
    private static final Logger logger = Logger.getLogger("LifeStealZ");
    private static Boolean slimeWorldAvailable = null;
    private static Class<?> slimeWorldInstanceClass = null;
    private static Class<?> advancedSlimePaperAPIClass = null;
    private static Class<?> fileLoaderClass = null;
    
    // 默认数据源配置（可通过配置文件修改）
    private static String defaultDataSource = "file";
    private static String defaultWorldsPath = "slime_worlds"; // 默认世界存储路径

    /**
     * 检查服务器是否安装了AdvancedSlimePaper
     */
    public static boolean isSlimeWorldAvailable() {
        if (slimeWorldAvailable == null) {
            try {
                // 尝试加载SlimeWorld API类
                slimeWorldInstanceClass = Class.forName("com.infernalsuite.asp.api.world.SlimeWorldInstance");
                advancedSlimePaperAPIClass = Class.forName("com.infernalsuite.asp.api.AdvancedSlimePaperAPI");
                fileLoaderClass = Class.forName("com.infernalsuite.asp.loaders.file.FileLoader");
                slimeWorldAvailable = true;
                logger.info("检测到AdvancedSlimePaper，已启用SlimeWorld支持");
            } catch (ClassNotFoundException e) {
                slimeWorldAvailable = false;
                logger.info("未检测到AdvancedSlimePaper，使用标准世界处理");
            }
        }
        return slimeWorldAvailable;
    }

    /**
     * 检查指定世界是否为SlimeWorld
     */
    public static boolean isSlimeWorld(World world) {
        if (!isSlimeWorldAvailable() || world == null) {
            return false;
        }

        try {
            // 通过API获取已加载的SlimeWorld
            Object api = advancedSlimePaperAPIClass.getMethod("instance").invoke(null);
            Object slimeWorldInstance = advancedSlimePaperAPIClass
                    .getMethod("getLoadedWorld", String.class)
                    .invoke(api, world.getName());
            
            return slimeWorldInstance != null;
        } catch (Exception e) {
            logger.warning("检查SlimeWorld时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 安全地检查SlimeWorld中的区块是否已加载
     * 避免在世界卸载时触发区块加载
     */
    public static boolean isChunkSafelyLoaded(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;

        try {
            // 如果是SlimeWorld，需要额外检查
            if (isSlimeWorld(world)) {
                // 检查世界是否正在卸载
                if (!Bukkit.getWorlds().contains(world)) {
                    return false;
                }
                
                // 使用Paper的isChunkGenerated方法（如果可用）
                try {
                    return (boolean) world.getClass()
                            .getMethod("isChunkGenerated", int.class, int.class)
                            .invoke(world, chunkX, chunkZ);
                } catch (NoSuchMethodException e) {
                    // 降级到标准方法
                    return world.isChunkLoaded(chunkX, chunkZ);
                }
            } else {
                // 标准世界，直接检查
                return world.isChunkLoaded(chunkX, chunkZ);
            }
        } catch (IllegalStateException e) {
            // 区块系统已关闭
            logger.fine("区块系统已关闭，世界: " + world.getName());
            return false;
        } catch (Exception e) {
            logger.warning("检查区块加载状态时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 安全地获取方块类型，处理SlimeWorld特殊情况
     */
    public static String getBlockTypeSafely(Location location) {
        if (location == null || location.getWorld() == null) {
            return "AIR";
        }

        try {
            // 首先检查区块是否安全加载
            if (!isChunkSafelyLoaded(location)) {
                return "AIR";
            }

            // 获取方块类型
            return location.getBlock().getType().toString();
        } catch (IllegalStateException e) {
            // 捕获区块系统关闭异常
            logger.fine("无法获取方块类型，世界可能正在卸载: " + location.getWorld().getName());
            return "AIR";
        } catch (Exception e) {
            logger.warning("获取方块类型时出错: " + e.getMessage());
            return "AIR";
        }
    }

    /**
     * 检查位置是否在有效的世界中
     */
    public static boolean isWorldValid(World world) {
        if (world == null) {
            return false;
        }

        try {
            // 检查世界是否还在服务器的世界列表中
            if (!Bukkit.getWorlds().contains(world)) {
                return false;
            }

            // 如果是SlimeWorld，进行额外检查
            if (isSlimeWorld(world)) {
                try {
                    Object api = advancedSlimePaperAPIClass.getMethod("instance").invoke(null);
                    Object slimeWorldInstance = advancedSlimePaperAPIClass
                            .getMethod("getLoadedWorld", String.class)
                            .invoke(api, world.getName());
                    
                    // 如果SlimeWorld实例为null，说明世界正在卸载或已卸载
                    return slimeWorldInstance != null;
                } catch (Exception e) {
                    logger.warning("检查SlimeWorld有效性时出错: " + e.getMessage());
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.warning("检查世界有效性时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查指定名称的世界是否已加载
     */
    public static boolean isWorldLoaded(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return true;
        }

        // 如果Bukkit找不到，检查是否是未加载的SlimeWorld
        return false;
    }

    /**
     * 检查指定名称的世界是否为SlimeWorld（通过世界名称）
     * 可以检测未加载的SlimeWorld
     */
    public static boolean isSlimeWorldByName(String worldName) {
        if (!isSlimeWorldAvailable() || worldName == null) {
            return false;
        }

        try {
            // 先检查是否已加载
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                return isSlimeWorld(world);
            }

            // 检查是否存在于SlimeWorld数据源中
            // 这里需要检查所有已注册的SlimeLoader
            // 由于无法直接获取所有loader，我们返回false
            // 实际使用时应该在放置信标时记录世界类型
            return false;
        } catch (Exception e) {
            logger.warning("检查SlimeWorld名称时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设置默认数据源
     * @param dataSource 数据源名称（如 "file", "mysql", "mongodb" 等）
     */
    public static void setDefaultDataSource(String dataSource) {
        defaultDataSource = dataSource;
        logger.info("SlimeWorld默认数据源设置为: " + dataSource);
    }
    
    /**
     * 获取默认数据源
     */
    public static String getDefaultDataSource() {
        return defaultDataSource;
    }
    
    /**
     * 设置SlimeWorld文件存储路径
     * @param path 世界文件存储路径
     */
    public static void setWorldsPath(String path) {
        defaultWorldsPath = path;
        logger.info("SlimeWorld文件存储路径设置为: " + path);
    }
    
    /**
     * 获取SlimeWorld文件存储路径
     */
    public static String getWorldsPath() {
        return defaultWorldsPath;
    }

    /**
     * 尝试加载SlimeWorld（使用默认数据源）
     * @param worldName 世界名称
     * @return 加载后的世界，如果失败返回null
     */
    public static World loadSlimeWorld(String worldName) {
        return loadSlimeWorld(worldName, defaultDataSource);
    }
    
    /**
     * 尝试加载SlimeWorld（指定数据源）
     * @param worldName 世界名称
     * @param dataSource 数据源名称
     * @return 加载后的世界，如果失败返回null
     */
    public static World loadSlimeWorld(String worldName, String dataSource) {
        if (!isSlimeWorldAvailable() || worldName == null) {
            return null;
        }

        try {
            // 检查世界是否已经加载
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                logger.info("世界 " + worldName + " 已经加载");
                return existingWorld;
            }

            logger.info("尝试从数据源 '" + dataSource + "' 加载SlimeWorld: " + worldName);
            
            // 获取API实例
            Object api = advancedSlimePaperAPIClass.getMethod("instance").invoke(null);
            
            // 直接创建SlimeLoader（目前仅支持file类型）
            Object loader;
            if ("file".equals(dataSource)) {
                // 创建FileLoader
                File worldDir = new File(defaultWorldsPath);
                loader = fileLoaderClass.getConstructor(File.class).newInstance(worldDir);
                logger.info("使用文件数据源，路径: " + worldDir.getAbsolutePath());
            } else {
                logger.warning("当前仅支持 'file' 数据源");
                logger.warning("如需使用其他数据源，请联系开发者");
                return null;
            }
            
            // 创建默认属性映射
            Class<?> propertyMapClass = Class.forName("com.infernalsuite.asp.api.world.properties.SlimePropertyMap");
            Object propertyMap = propertyMapClass.getDeclaredConstructor().newInstance();
            
            // 从数据源读取世界
            logger.info("正在从数据源读取世界: " + worldName);
            Object slimeWorld = advancedSlimePaperAPIClass
                    .getMethod("readWorld", 
                            Class.forName("com.infernalsuite.asp.api.loaders.SlimeLoader"),
                            String.class,
                            boolean.class,
                            propertyMapClass)
                    .invoke(api, loader, worldName, false, propertyMap);
            
            // 在主线程加载世界
            logger.info("正在将世界加载到服务器: " + worldName);
            Object[] result = new Object[1];
            Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("LifeStealZ"),
                () -> {
                    try {
                        Object worldInstance = advancedSlimePaperAPIClass
                                .getMethod("loadWorld",
                                        Class.forName("com.infernalsuite.asp.api.world.SlimeWorld"),
                                        boolean.class)
                                .invoke(api, slimeWorld, true);
                        
                        // 获取Bukkit世界
                        Object bukkitWorld = slimeWorldInstanceClass
                                .getMethod("getBukkitWorld")
                                .invoke(worldInstance);
                        
                        result[0] = bukkitWorld;
                    } catch (Exception e) {
                        logger.severe("加载世界到服务器时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            );
            
            // 等待加载完成
            Thread.sleep(1000);
            
            if (result[0] instanceof World) {
                logger.info("SlimeWorld " + worldName + " 加载成功！");
                return (World) result[0];
            }
            
            logger.warning("SlimeWorld加载失败: " + worldName);
            return null;
        } catch (ClassNotFoundException e) {
            logger.warning("找不到SlimeWorld相关类: " + e.getMessage());
            return null;
        } catch (Exception e) {
            logger.severe("加载SlimeWorld时出错: " + worldName + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 异步加载SlimeWorld
     * @param worldName 世界名称
     * @param callback 加载完成后的回调，参数为加载的世界（可能为null）
     */
    public static void loadSlimeWorldAsync(String worldName, java.util.function.Consumer<World> callback) {
        if (!isSlimeWorldAvailable() || worldName == null) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(
            Bukkit.getPluginManager().getPlugin("LifeStealZ"),
            () -> {
                World world = loadSlimeWorld(worldName);
                
                // 回调必须在主线程执行
                if (callback != null) {
                    Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("LifeStealZ"),
                        () -> callback.accept(world)
                    );
                }
            }
        );
    }
}
