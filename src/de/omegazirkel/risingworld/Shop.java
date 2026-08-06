package de.omegazirkel.risingworld;

import java.nio.file.Path;

import de.omegazirkel.risingworld.shop.ShopPurchaseResult;
import de.omegazirkel.risingworld.tools.FileChangeListener;
import de.omegazirkel.risingworld.tools.OZLogger;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.events.player.PlayerNpcInteractionEvent;
import net.risingworld.api.events.player.ui.PlayerUITextFieldChangeEvent;

/** Rising World entry point; shop behavior lives in {@link ShopRuntime}. */
public final class Shop extends ShopRuntime implements Listener, FileChangeListener {
    public static OZLogger logger() { return ShopRuntime.logger(); }
    public static ShopPurchaseResult unavailable(String message) {
        return ShopRuntime.unavailable(message);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        registerEventListener(this);
    }

    @Override public void onDisable() { super.onDisable(); }
    @Override public void onSettingsChanged(Path settingsPath) { super.onSettingsChanged(settingsPath); }

    @Override @EventMethod
    public void onPlayerSpawnEvent(PlayerSpawnEvent event) { super.onPlayerSpawnEvent(event); }
    @Override @EventMethod
    public void onPlayerUITextFieldChangeEvent(PlayerUITextFieldChangeEvent event) {
        super.onPlayerUITextFieldChangeEvent(event);
    }
    @Override @EventMethod
    public void onPlayerCommand(PlayerCommandEvent event) { super.onPlayerCommand(event); }
    @Override @EventMethod
    public void onPlayerNpcInteractionEvent(PlayerNpcInteractionEvent event) { super.onPlayerNpcInteractionEvent(event); }
}
