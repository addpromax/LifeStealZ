package com.zetaplugins.lifestealz.listeners;

import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.storage.CachedStorage;
import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@AutoRegisterListener
public class PlayerQuitListener implements Listener {
    private final LifeStealZ plugin;

    public PlayerQuitListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getStorage() instanceof CachedStorage cachedStorage) {
            cachedStorage.evict(event.getPlayer().getUniqueId());
        }
    }
}
