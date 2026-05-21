package de.omegazirkel.risingworld.shop.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.shop.ShopZone;
import net.risingworld.api.Server;
import net.risingworld.api.Timer;
import net.risingworld.api.objects.Player;

public class ShopZoneIndicatorManager {
    private static final String OVERLAY_ATTRIBUTE = "oz.shop.zoneIndicator";

    private final Shop plugin;
    private final Map<Player, ShopZoneIndicatorOverlay> overlays = new HashMap<>();
    private Timer timer;

    public ShopZoneIndicatorManager(Shop plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        timer = new Timer(1, 0, -1, this::updateLoop);
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.kill();
            timer = null;
        }
        for (Map.Entry<Player, ShopZoneIndicatorOverlay> entry : overlays.entrySet()) {
            entry.getValue().hide(entry.getKey());
            entry.getKey().deleteAttribute(OVERLAY_ATTRIBUTE);
        }
        overlays.clear();
    }

    public void refresh() {
        updateLoop();
    }

    private void updateLoop() {
        Player[] players = Server.getAllPlayers();
        if (players == null) {
            clearDisconnected();
            return;
        }
        for (Player player : players) {
            update(player);
        }
        clearDisconnected();
    }

    private void update(Player player) {
        if (player == null || !player.isConnected() || !plugin.showShopZoneIndicator()) {
            hide(player);
            return;
        }
        Optional<ShopZone> zone = plugin.currentShopZone(player);
        if (zone.isEmpty()) {
            hide(player);
            return;
        }
        ShopZoneIndicatorOverlay overlay = overlays.computeIfAbsent(player, ignored -> {
            ShopZoneIndicatorOverlay created = new ShopZoneIndicatorOverlay();
            player.setAttribute(OVERLAY_ATTRIBUTE, created);
            return created;
        });
        overlay.updateText(plugin.shopZoneIndicatorText(player, zone.get()));
        overlay.show(player);
    }

    private void hide(Player player) {
        if (player == null) {
            return;
        }
        ShopZoneIndicatorOverlay overlay = overlays.remove(player);
        if (overlay == null && player.hasAttribute(OVERLAY_ATTRIBUTE, ShopZoneIndicatorOverlay.class)) {
            overlay = (ShopZoneIndicatorOverlay) player.getAttribute(OVERLAY_ATTRIBUTE);
        }
        if (overlay != null) {
            overlay.hide(player);
        }
        player.deleteAttribute(OVERLAY_ATTRIBUTE);
    }

    private void clearDisconnected() {
        Iterator<Map.Entry<Player, ShopZoneIndicatorOverlay>> iterator = overlays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, ShopZoneIndicatorOverlay> entry = iterator.next();
            Player player = entry.getKey();
            if (player == null || !player.isConnected()) {
                if (player != null) {
                    entry.getValue().hide(player);
                    player.deleteAttribute(OVERLAY_ATTRIBUTE);
                }
                iterator.remove();
            }
        }
    }
}
