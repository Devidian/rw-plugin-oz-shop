# OZ - Shop

Shared shop and purchase API plugin for Rising World.

## Responsibilities

- plugin offer registration API
- system-shop offer loading from JSON
- Wallet-backed purchase execution
- synchronous purchase callbacks
- lightweight `/shop` command for listing and buying offers

`rw-plugin-oz-tools` is a hard runtime dependency. `rw-plugin-oz-wallet` is required for functional purchases. If Wallet is missing, Shop loads but purchases are disabled and admins receive a warning on spawn.

## Installation / Update Scope

Install or update these plugin packages together:

- `OZTools`: hard runtime dependency
- `OZWallet`: required for functional purchases and refunds
- `OZShop`: shared system/plugin shop runtime

Feature plugins such as GPS or LandClaim only need an update when they register new or changed Shop offers. Shop does not require Marketplace or LandClaim at runtime. The shop-zone HUD indicator is registered through the shared Tools indicator panel and remains optional through `showShopZoneIndicator`.

## Settings

The plugin copies `settings.default.properties` to `settings.properties` on first run.

```properties
logLevel=ALL
reloadOnChange=true
shopCommand=shop
sendPluginWelcome=false
systemOffersFile=system-offers.json
systemShopEnabled=true
generateDefinitionExports=false
dynamicEconomyEnabled=false
shopEnabled=true
requireShopZone=false
shopZonesFile=shop-zones.json
showShopZoneIndicator=true
```

`systemOffersFile` points to an admin-editable JSON file in the plugin directory. On first run, `system-offers.default.json` is copied to that path when the file is missing.

`generateDefinitionExports=true` writes generated reference files next to the system offers file when they are missing:

- `system-offer-export.json`: generated item and variant offer reference from `Definitions.getAllItemDefinitions()`
- `system-recipes-export.json`: generated crafting recipe reference from `Definitions.getAllRecipes()`

These files are reference exports, not the editable offer file. Existing `system-offer-example.json` files are left untouched; new exports use `system-offer-export.json`.

`systemShopEnabled=false` disables system-shop offers globally while keeping plugin-registered offers available. Shop areas may override that global system-shop setting with `systemShop`: `-1` inherits the global value, `0` disables system-shop offers in the area, and `1` enables them in the area.

`dynamicEconomyEnabled=false` is a reserved gate for the optional future dynamic stock/pricing extension. Static system and plugin offers are unchanged while this work is disabled. Dynamic-economy defaults should be chosen only after a real `system-recipes-export.json` has been generated and inspected.

`shopEnabled=false` disables player purchases and listing. `requireShopZone=true` restricts non-admin `/shop` access to existing Rising World areas that an admin has marked as shop areas. Shop zones are stored in `shopZonesFile` by `areaId`.

`showShopZoneIndicator=true` shows the Shop icon in the shared Tools indicator panel while players are inside a shop area.

## System Offers

System-shop offers for built-in game items use JSON:

```json
[
  {
    "id": "example.info",
    "itemName": "ore",
    "itemVariant": 0,
    "amount": 1,
    "price": 100,
    "currency": "",
    "enabled": false
  }
]
```

An empty `currency` uses Wallet's configured default currency. For system offers, Shop resolves `itemName` with `Definitions.getItemDefinition(name)`, reads the selected variant with `getVariant(itemVariant)`, displays the game's item icon through `getIcon(itemVariant)` when available, and adds `amount` items to the buyer inventory after successful payment.

## Public API

Other plugins can look up `OZ - Shop` and call these methods on the main plugin class:

```java
public ShopOfferRegistrationResult registerOffer(
    String id,
    String title,
    String description,
    long price,
    String currencyIdentifier,
    String icon,
    String pluginIdentifier,
    ShopPurchaseCallback callback
);

public ShopOfferRegistrationResult registerOffer(
    String id,
    String title,
    String description,
    long price,
    String currencyIdentifier,
    String icon,
    String category,
    String source,
    String pluginIdentifier,
    ShopPurchaseCallback callback
);

public ShopOfferRegistrationResult registerOffer(
    String id,
    String title,
    String description,
    long fallbackPrice,
    String currencyIdentifier,
    String icon,
    String pluginIdentifier,
    ShopPurchaseCallback callback,
    ShopPriceResolver priceResolver
);

public ShopOfferRegistrationResult registerOffer(
    String id,
    String title,
    String description,
    long fallbackPrice,
    String currencyIdentifier,
    String icon,
    String category,
    String source,
    String pluginIdentifier,
    ShopPurchaseCallback callback,
    ShopPriceResolver priceResolver
);

public ShopOfferRegistrationResult unregisterOffer(String id, String pluginIdentifier);

public int unregisterOffers(String pluginIdentifier);

public ShopPurchaseResult purchase(Player player, String offerId);

public ShopOffer findOffer(String offerId);

public List<ShopOffer> listOffers();

public List<ShopOffer> listPluginOffers();

public List<ShopOffer> listSystemOffers();

public int reloadSystemOffers();
```

Offer ids are trimmed and normalized to lowercase. Prices are whole-number non-negative `long` values. Static offers use `price`; dynamic plugin offers can pass `ShopPriceResolver`, which Shop calls with the current `Player` when listing and purchasing. `category` and `source` are public metadata for UI grouping and display; `pluginIdentifier` remains the owner key for unregistering and duplicate protection.

Plugin-registered offers live only in the runtime registry. Shop does not persist them to disk or database, so registering plugins should register on enable and can call `unregisterOffer(id, pluginIdentifier)` for one offer or `unregisterOffers(pluginIdentifier)` during shutdown/reload. System offers are reloaded from the configured JSON file and replaced independently, while plugin offers stay registered across `/shop reload`.

Purchase flow validates the offer, requires Wallet, resolves the current price, withdraws that amount, and executes the callback synchronously. If payment fails, the callback is not called. If the callback fails, throws, or returns no result after a successful payment, Shop attempts a Wallet refund and returns `CALLBACK_FAILED` with a refund note. If that compensation also fails, Shop returns `REFUND_FAILED` so admins can investigate the charged-but-not-delivered purchase.

### Example Plugin Registration

For a compile-time integration, add Shop's API classes to the consuming plugin build and register offers during the consuming plugin's enable flow:

```java
import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopOfferRegistrationResult;
import de.omegazirkel.risingworld.shop.ShopPurchaseResult;
import net.risingworld.api.Plugin;

public final class ExampleShopIntegration {
    private static final String OWNER = "OZ - Example";

    private final Plugin ownerPlugin;

    public ExampleShopIntegration(Plugin ownerPlugin) {
        this.ownerPlugin = ownerPlugin;
    }

    public void registerExampleOffer() {
        Plugin plugin = ownerPlugin.getPluginByName("OZ - Shop");
        if (!(plugin instanceof Shop shop)) {
            warn("OZ - Shop is not loaded; example offer was not registered.");
            return;
        }

        ShopOfferRegistrationResult result = shop.registerOffer(
                "example.teleport-token",
                "Example teleport token",
                "Adds one example token to the buyer.",
                250L,
                "",
                "shop-icon",
                "Pluginshop",
                OWNER,
                OWNER,
                (player, offer) -> grantExampleToken(player.getDbID(), offer));

        if (!result.success()) {
            warn("Could not register Shop offer: " + result.message());
        }
    }

    public void unregisterExampleOffers() {
        Plugin plugin = ownerPlugin.getPluginByName("OZ - Shop");
        if (plugin instanceof Shop shop) {
            shop.unregisterOffers(OWNER);
        }
    }

    private ShopPurchaseResult grantExampleToken(int playerDbId, ShopOffer offer) {
        // Fulfillment remains owned by the registering plugin.
        // Return failure if delivery cannot be completed; Shop will refund the payment.
        return ShopPurchaseResult.success("Example token delivered.", offer);
    }

    private void warn(String message) {
        System.out.println("[OZ - Example] " + message);
    }
}
```

For optional integrations where the consuming plugin must still compile and run without Shop classes, use `ownerPlugin.getPluginByName("OZ - Shop")`, `Class.forName("de.omegazirkel.risingworld.shop.ShopPurchaseCallback")`, and reflection to invoke the same `registerOffer(...)` overload. GPS and LandClaim use this optional style so Shop can remain an optional integration partner for feature plugins.

## Shop UI

`/shop`, `/shop list`, and the Shop radial menu entry open the shop UI. The Shop radial menu also includes an `Info / Status` entry using the shared Tools info icon. The UI has separate tabs for `Systemshop` and `Pluginshop`. Offers use the card layout by default; players can switch between card and list layout in the player plugin settings. Admins additionally see an `Admin` tab that lists configured shop areas and can remove the shop status from an area after confirmation.

Direct command purchases remain available for fast workflows.

## Player Commands

- `/shop` or `/shop list`: open the shop UI
- `/shop buy <offer-id>`: buy an offer
- `/shop status` or `/shop info`: open the shared Tools Info/Status panel
- `/shop reload`: admin-only reload of system offers and settings-backed file path

## Admin Shop Zones

Admins can open the Shop entry in the plugin radial menu to mark the current existing area as a shop area. This create action is hidden when the current area is already a shop area. If the admin is not standing inside an area, no shop area is created. The admin tab lists shop areas, removes shop status after confirmation, and cycles each area's system-shop override through inherit, disabled, and enabled. Plugin-registered offers are not persisted in the zone file; zones only control where the shared shop may be opened when `requireShopZone=true` and how system-shop offers are enabled in that area.

Players see the Shop icon in the shared Tools indicator panel while they are in a configured shop area. Disabling `showShopZoneIndicator` hides this HUD indication without changing shop access or purchases.

## Validation

- `scripts/verify-plugin-api.sh --summary`
- `mvn -B -DskipTests package`
- `mvn -B test`
