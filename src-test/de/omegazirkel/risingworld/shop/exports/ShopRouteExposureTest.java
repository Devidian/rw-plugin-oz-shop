package de.omegazirkel.risingworld.shop.exports;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import de.omegazirkel.risingworld.shop.PluginSettings;

public class ShopRouteExposureTest {

    @Test
    public void loadsShopZoneExposureFlagFromSettings() throws Exception {
        Path directory = Files.createTempDirectory("oz-shop-settings-");
        Path settings = directory.resolve("settings.world.json");
        Files.writeString(directory.resolve("settings.default.json"), "{\"exposeShopZones\":true}");
        Files.writeString(settings, "{\"exposeShopZones\":false}");

        PluginSettings pluginSettings = PluginSettings.getInstance();
        pluginSettings.initSettings(settings.toString());

        assertFalse(ShopRouteExposure.from(pluginSettings).zones());

        Files.writeString(settings, "");
        pluginSettings.initSettings(settings.toString());

        assertTrue(ShopRouteExposure.from(pluginSettings).zones());
    }
}
