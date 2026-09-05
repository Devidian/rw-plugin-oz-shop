package de.omegazirkel.risingworld.shop.web;

import java.sql.SQLException;
import java.util.function.BooleanSupplier;
import com.google.gson.Gson;
import de.omegazirkel.risingworld.OZToolsNativeWebAccess;
import de.omegazirkel.risingworld.shop.exports.ShopZoneExportService;
import net.risingworld.api.callbacks.WebserverHandler;
import net.risingworld.api.events.general.HttpRequestEvent;
import net.risingworld.api.events.general.HttpRequestEvent.HttpMethod;

/** Native read-only Shop-zone export. */
public final class ShopZoneRoute implements WebserverHandler {
    private static final Gson GSON = new Gson(); private final BooleanSupplier enabled; private final ShopZoneExportService exports;
    public ShopZoneRoute(BooleanSupplier enabled, ShopZoneExportService exports) { this.enabled = enabled; this.exports = exports; }
    @Override public void onRequest(HttpRequestEvent event) {
        event.setContentType("application/json; charset=utf-8"); event.setResponseHeader("Cache-Control", "no-store");
        if (!enabled.getAsBoolean()) { event.setResponseCode(404); event.setResponseBody("{\"error\":\"not_found\"}"); return; }
        if (!OZToolsNativeWebAccess.authorize(event)) return;
        if (event.getMethod() != HttpMethod.GET) { event.setResponseCode(405); event.setResponseHeader("Allow", "GET"); event.setResponseBody("{\"error\":\"method_not_allowed\"}"); return; }
        try { event.setResponseCode(200); event.setResponseBody(GSON.toJson(exports.exportZones(lastChange(event.getQueryParameters().get("lastChange"))))); }
        catch (IllegalArgumentException ex) { event.setResponseCode(400); event.setResponseBody("{\"error\":\"invalid_last_change\"}"); }
        catch (SQLException | RuntimeException ex) { event.setResponseCode(503); event.setResponseBody("{\"error\":\"shop_zones_unavailable\"}"); }
    }
    static Long lastChange(String value) { if (value == null) return null; if (!value.matches("\\d+")) throw new IllegalArgumentException(); try { return Long.valueOf(value); } catch (NumberFormatException ex) { throw new IllegalArgumentException(ex); } }
}
