package de.omegazirkel.risingworld.shop.web;
import java.util.function.BooleanSupplier;
import com.google.gson.Gson;
import de.omegazirkel.risingworld.OZToolsNativeWebAccess;
import de.omegazirkel.risingworld.shop.exports.ShopTraderExportService;
import net.risingworld.api.callbacks.WebserverHandler;
import net.risingworld.api.events.general.HttpRequestEvent;
import net.risingworld.api.events.general.HttpRequestEvent.HttpMethod;
public final class ShopTraderRoute implements WebserverHandler {
 private static final Gson GSON=new Gson(); private final BooleanSupplier enabled; private final ShopTraderExportService exports;
 public ShopTraderRoute(BooleanSupplier enabled, ShopTraderExportService exports){this.enabled=enabled;this.exports=exports;}
 @Override public void onRequest(HttpRequestEvent event){ event.setContentType("application/json; charset=utf-8"); event.setResponseHeader("Cache-Control","no-store"); if(!enabled.getAsBoolean()){event.setResponseCode(404);event.setResponseBody("{\"error\":\"not_found\"}");return;} if(!OZToolsNativeWebAccess.authorize(event))return; if(event.getMethod()!=HttpMethod.GET){event.setResponseCode(405);event.setResponseHeader("Allow","GET");event.setResponseBody("{\"error\":\"method_not_allowed\"}");return;} try{event.setResponseCode(200);event.setResponseBody(GSON.toJson(exports.exportTraders()));}catch(RuntimeException ex){event.setResponseCode(503);event.setResponseBody("{\"error\":\"shop_traders_unavailable\"}");} }
}
