package com.zetaplugins.lifestealz.storage;

import com.zetaplugins.lifestealz.LifeStealZ;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CachedStorage extends Storage {
    private final Storage delegate;
    private final ConcurrentHashMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public CachedStorage(LifeStealZ plugin, Storage delegate) {
        super(plugin);
        this.delegate = delegate;
    }

    public void evict(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null && data.hasChanges()) {
            delegate.save(data);
        }
    }

    public boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    @Override
    public PlayerData load(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        PlayerData data = delegate.load(uuid);
        if (data != null) cache.put(uuid, data);
        return data;
    }

    @Override
    public PlayerData load(String uuid) {
        return load(UUID.fromString(uuid));
    }

    @Override
    public void save(PlayerData playerData) {
        delegate.save(playerData);
        try {
            cache.put(UUID.fromString(playerData.getUuid()), playerData);
        } catch (IllegalArgumentException e) {
            getPlugin().getLogger().warning(
                    "CachedStorage: skipping cache put for invalid UUID: " + playerData.getUuid()
            );
        }
    }

    @Override
    public int reviveAllPlayers(int minHearts, int reviveHearts, int maxRevives, boolean bypassReviveLimit) {
        int affected = delegate.reviveAllPlayers(minHearts, reviveHearts, maxRevives, bypassReviveLimit);
        if (affected > 0) cache.clear();
        return affected;
    }

    @Override
    public void importData(String fileName) {
        delegate.importData(fileName);
        cache.clear();
    }

    @Override
    public void clearDatabase() {
        delegate.clearDatabase();
        cache.clear();
    }

    @Override
    public void init() {
        delegate.init();
    }

    @Override
    public List<UUID> getEliminatedPlayers() {
        return delegate.getEliminatedPlayers();
    }

    @Override
    public String export(String fileName) {
        return delegate.export(fileName);
    }

    @Override
    public List<String> getPlayerNames() {
        return delegate.getPlayerNames();
    }

    @Override
    public List<String> getEliminatedPlayerNames() {
        return delegate.getEliminatedPlayerNames();
    }

    @Override
    protected void migrateDatabase() {
        delegate.migrateDatabase();
    }
}
