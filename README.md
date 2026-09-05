# OZ - Shop

Shared shop and purchase API plugin for Rising World.

## Responsibilities

- plugin offer registration API
- system-shop offer loading from JSON
- Wallet-backed purchase execution
- quantity buy/sell flows for system offers
- SQLite-backed shop zones, stock, and trade stats
- NPC traders with independent offer files, stock scopes, and Wallet accounts
- synchronous purchase callbacks
- lightweight `/shop` command for listing and buying offers

`rw-plugin-oz-tools` 0.21.0 or newer is a hard runtime dependency. `rw-plugin-oz-wallet` is required for functional purchases and system-shop buyback payouts. If Wallet is missing, Shop loads but purchases are disabled and admins receive a warning on spawn.

## Installation / Update Scope

Install or update these plugin packages together:

- `OZTools`: hard runtime dependency
- `OZWallet`: required for functional purchases and refunds
- `OZShop`: shared system/plugin shop runtime

Feature plugins such as GPS or LandClaim only need an update when they register new or changed Shop offers. Shop does not require Marketplace or LandClaim at runtime. The shop-zone HUD indicator is registered through the shared Tools indicator panel and remains optional through `showShopZoneIndicator`.

## Settings

The plugin copies `settings.default.json` to `settings.<world>.json` on first run.

```properties
logLevel=ALL
reloadOnChange=true
shopCommand=shop
sendPluginWelcome=false
systemOffersFile=system-offers.json
systemShopCurrency=
systemShopEnabled=true
generateDefinitionExports=false
dynamicEconomyEnabled=false
shopEnabled=true
requireShopZone=false
shopZonesFile=shop-zones.json
showShopZoneIndicator=true
exposeShopZones=true
```

`systemOffersFile` points to an admin-editable JSON file in the plugin directory. On first run, `system-offers.default.json` is copied to that path when the file is missing.

The checked-in `src/system-offers.complete.json` is the complete reference catalog. During packaging, `scripts/generate-system-offers.sh` creates the runtime `system-offers.default.json` and numbered tier files from enabled T0-T11 offers only. Valid offers without a more specific classification use the enabled T0 `Trash / Common` loot fallback; invalid offers remain available in the packaged complete reference file but are excluded from the runtime default and tier files.

At server startup, Shop resolves the icons of all active loaded system offers once so the first player opening the shop does not trigger the full icon-loading cost.

`systemShopCurrency` optionally sets one central currency for all system-shop offer files. Leave it empty to use Wallet's default currency or legacy per-offer `currency` values during migration.

`generateDefinitionExports=true` writes generated reference files next to the system offers file when they are missing:

- `system-offer-export.json`: generated item and variant offer reference from `Definitions.getAllItemDefinitions()`, resolving object kits, including large object kits, plus generic construction, clothing, and plant item references to concrete definition names where possible; construction exports merge supported textures with recipe-backed texture variants
- `system-recipes-export.json`: generated crafting recipe reference from `Definitions.getAllRecipes()`

These files are reference exports, not the editable offer file. Existing `system-offer-example.json` files are left untouched; new exports use `system-offer-export.json`.

`systemShopEnabled=false` disables system-shop offers globally while keeping plugin-registered offers available. Shop areas may override that global system-shop setting with `systemShop`: `-1` inherits the global value, `0` disables system-shop offers in the area, and `1` enables them in the area.

System-shop stock state is always maintained for configured offers. Positive scoped stock limits cap system-shop purchases and reject player sales that would exceed the scoped stock limit before items are removed or Wallet payouts happen. Optional per-player and global daily sell limits reject player sales before inventory removal or Wallet payouts. Automatic stock reconciliation has the same rules for global, zone, and NPC-trader scopes: restock runs only below target and never exceeds it; drain runs only above target and never goes below it. `SYSTEM_SUPPLIED` only restocks, `HYBRID` drains and restocks, `PLAYER_SUPPLIED` and `STATIC` do neither, and `LOOT` only drains. An open Shop overlay is rebuilt when its scope completes a stock-changing tick, so its wallet balance, offers, and stock labels remain current. `dynamicEconomyEnabled=false` disables only stock-dependent price multipliers: buy and sell prices are calculated from `basePrice` plus the configured spread, so selling to the system pays less than buying from it, but current stock does not affect the price. When enabled, dynamic system-shop prices use `stock / targetStock` with configured min/max multipliers and the same enforced buy/sell spread; bulk trades are priced as a sequence of per-item stock changes. System-offer definition fields are authoritative from JSON on every load/reload, including mode, target/max stock, price multipliers, spread, tick settings, and sell limits. SQLite stores only scoped runtime stock plus trade and daily sell counters; legacy DB economy override columns remain for compatibility but are ignored for definition behavior. `/shop stock` can set the current scoped runtime stock. Edit the system-offers JSON and run `/shop reload` for mode, limit, tick, pricing, or sell-limit changes. `stock=0` remains unlimited for migration safety.

`shopEnabled=false` disables player purchases and listing. `requireShopZone=true` restricts non-admin `/shop` access to existing Rising World areas that an admin has marked as shop areas. Shop zones are stored in world-scoped SQLite; `shopZonesFile` is retained as a one-time import source when the SQLite table is empty.

`economyTickIntervalHours=1` configures the automatic stock-reconciliation interval. Drain and restock are calculated together from the elapsed time since the last effective stock change. Fractional changes do not advance that timestamp, so they accumulate until at least one whole item can be added or removed.

`showShopZoneIndicator=true` shows the Shop icon in the shared Tools indicator panel while players are inside a shop area.

`exposeShopZones=true` exposes read-only SQLite shop-zone metadata at `/plugins/oz---shop/zones` for manager views. The export uses `created_at` as the `lastChange` cursor and reads the world-scoped `shop_zones` table.

## System Offers

System-shop offers for built-in game items use JSON:

```json
[
  {
    "id": "example.info",
    "itemName": "ore",
    "itemVariant": 0,
    "amount": 2,
    "basePrice": 40,
    "stockMode": "STATIC",
    "minPriceMultiplier": 0.25,
    "maxPriceMultiplier": 4.0,
    "spreadPercent": 25,
    "drainPercent": 0,
    "drainMax": 0,
    "restockPercent": 0,
    "restockMax": 0,
    "perPlayerDailySellLimit": 0,
    "globalDailySellLimit": 0,
    "isEnabled": false
  }
]
```

`basePrice` is the per-item baseline value. Player purchases round the package price up from `basePrice * amount`; player sales round payouts down from `basePrice * amount`. `isEnabled` is the active flag for the offer. Buy/sell capability is derived from `stockMode`: `STATIC` and `SYSTEM_SUPPLIED` let players buy from the system only, while `PLAYER_SUPPLIED`, `LOOT`, and `HYBRID` also let players sell matching items back to the system. `LOOT` is the drain-only T0 mode, while `SYSTEM_SUPPLIED` is restock-only; `HYBRID` supports both directions. Every automatic drain is limited to stock above target and every restock is limited to the deficit below target. For durable system-shop buybacks, the payout is calculated per item from its remaining durability and modifier: `Normal` is 100%, `Broken` is 10%, and `Godly` is 1000%; the Shop overlay presents the exact breakdown before confirming the sale. Legacy `buyPrice`, `sellPrice`, `price`, `currency`, `enabled`, `sellEnabled`, and `buyEnabled` fields are still read as migration overrides, but generated/default offers now omit them; legacy `sellEnabled || buyEnabled` maps to `isEnabled` when no new flag is present. `perPlayerDailySellLimit` and `globalDailySellLimit` are item-unit limits for player sales into the system shop and only apply to modes that allow player sales; `0` disables the respective limit. `STATIC` stock is unlimited for player purchases. `stockMode=LOOT`, `stockMode=SYSTEM_SUPPLIED`, and `stockMode=HYBRID` enable automatic ticks; `STATIC` and `PLAYER_SUPPLIED` do not tick automatically. `SYSTEM_SUPPLIED` offers have an effective minimum restock of one unit per hourly tick when no restock setting is configured. `drainPercent`/`restockPercent` define the target-stock-relative rate per elapsed economy interval; `drainMax`/`restockMax` cap that rate. Neither is redundant: for example, 1% of a target of 100 is 1 even with a maximum of 5, while 1% of 1,000 is capped at 5. Overdue intervals are reconciled proportionally in one run. Changes to these definition fields take effect after `/shop reload` without deleting old economy DB rows. For system offers, Shop resolves item, object, concrete construction, and concrete clothing definitions where available, displays the corresponding game icon through `getIcon(itemVariant)`, and adds `amount` items to the buyer inventory after successful payment. Generic `constructionitem` and `clothingitem` placeholders remain invalid; resolved construction shapes/textures and clothing names are catalogued and tradeable when their recipe maps to a supported economy tier.

Default economy classification and raw material pricing rules are documented in `docs/economy/`.

Admins can assign a different system-offer file to the current shop area with `/shop zoneoffers <file>` and reset to the global default with `/shop zoneoffers default`.
When an administrator saves a missing `.json` file name in the Zone or Trader settings, Shop asks before creating an empty `[]` file. For a non-packaged offer file, the Systemshop tab then provides **Add item** beside the owned-offers filter and **Edit item** / **Remove item from offers** beside the selected offer's economy controls. Adding uses the native item picker and copies the matching definition from `system-offers.complete.json`; the editor keeps ID, item name, variant, and enabled state read-only. Removing clears only the active Zone or Trader economy scope. Remaining Trader stock is credited to that Trader's Wallet account at the stepped player-purchase price before removal.

## NPC traders

An administrator can create a new locked (but non-static) NPC trader at their current position with
`/shop createtrader` (or `/shop ct`) or through the Shop radial menu. The new trader
uses the admin's facing direction, a random configured gender/name/outfit, weighted
RGB skin colours, hair/eye colours, and a
facial-variation/tattoo selection, plus a localized `Trader`/`Händler` prefix.
`traders-config.json` is copied from the packaged
`traders-config.default.json` on first run and contains editable outfit combinations,
a separate `hats` list (one selected with 25% probability), plus 20 male and 20
female names. Invalid clothing definitions are skipped. NPC traders are independent
of global and Shop-zone system-shop settings.
Generated traders apply `Skin.setHairstyle(...)`: male dummies use a random ID from
50 through 68 and female dummies a random ID from 100 through 119.
If a registered trader NPC is deleted or killed, Shop dissolves that trader on its
next enable: scoped stock is settled at the stepped player-purchase price, positive account balances move to
the world account, and the Wallet account and Shop records are archived/removed.

`/shop maketrader` (or `/shop mt`) remains available to turn an NPC in line of sight
into a trader. Both workflows make the NPC interactable and invincible and assign the
deterministic Wallet account `trader::<npcId>` with 1000 initial units of the Shop
currency. The first-run `default-trader.json` contains no offers. Each trader can use a
separate offer file and enable or disable plugin offers through its admin-only
Trader settings tab. Purchases credit, and player sales debit, only that
trader's account; automatic trader drain is settled at the stepped player-purchase price,
while restock is settled at base price.

Administrators can dissolve a trader shop from the trader settings after an explicit
confirmation. The trader's scoped stock is settled at the stepped player-purchase price, all positive Wallet
balances move to the world account, and the trader account is archived. The NPC remains
in the world but no longer opens a Shop trader overlay.

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

public ShopPurchaseResult purchase(Player player, String offerId, int quantity);

public ShopPurchaseResult sell(Player player, String offerId, int quantity);

public ShopOffer findOffer(String offerId);

public List<ShopOffer> listOffers();

public List<ShopOffer> listPluginOffers();

public List<ShopOffer> listSystemOffers();

public int reloadSystemOffers();
```

Offer ids are trimmed and normalized to lowercase. Prices are whole-number non-negative `long` values. Static offers use `price`; dynamic plugin offers can pass `ShopPriceResolver`, which Shop calls with the current `Player` when listing and purchasing. `category` and `source` are public metadata for UI grouping and display; `pluginIdentifier` remains the owner key for unregistering and duplicate protection.

Plugin-registered offers live only in the runtime registry. Shop does not persist them to disk or database, so registering plugins should register on enable and can call `unregisterOffer(id, pluginIdentifier)` for one offer or `unregisterOffers(pluginIdentifier)` during shutdown/reload. System offers are reloaded from the configured JSON file and replaced independently, while plugin offers stay registered across `/shop reload`.

Purchase flow validates the offer, requires Wallet, resolves the current price, and executes the callback synchronously. With Wallet's system-account API, the charge is transferred atomically into the current world account; older Wallet versions retain the compatible withdrawal behavior. If payment fails, the callback is not called. If the callback fails, throws, or returns no result after a successful payment, Shop reverses the world-account transfer idempotently (or uses the legacy deposit refund) and returns `CALLBACK_FAILED` with a refund note. If that compensation also fails, Shop returns `REFUND_FAILED` so admins can investigate the charged-but-not-delivered purchase. Shop buy-back payouts remain system-created money and do not debit the world account.

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
                "oz-shop",
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

`/shop` and `/shop list` open the shop UI. The shared `/ozt` and inventory shortcut entry opens the admin radial menu for admins and the direct shop overlay for normal players. The Shop radial menu also includes an `Info / Status` entry using the shared Tools info icon. The UI has separate tabs for `Systemshop` and `Pluginshop`. The Systemshop tab includes a name search, an optional filter for offers whose items the player currently holds, and selecting an offer updates the detail panel without rebuilding the full offer list. Offers use the card layout by default; players can switch between card and list layout in the player plugin settings. Players can also hide the Shop shortcut from `/ozt` and the inventory shortcut panel. Admins standing inside an existing area additionally see a `Zone` tab for configuring the current area as a Shop-Zone, editing the stored zone name, syncing it from the Rising World area name, setting/resetting the zone system-offer file, choosing the zone systemshop mode, removing the zone, and resetting current-zone stocks to target. Admins also see an `Admin` tab that lists configured shop areas and can remove the shop status from an area after confirmation. Use explicit close controls until Rising World exposes custom-overlay Escape handling.

Direct command purchases remain available for fast workflows.

## Player Commands

- `/shop` or `/shop list`: open the shop UI
- `/shop buy <offer-id> [quantity]`: buy a system offer quantity; plugin offers accept only quantity `1`
- `/shop sell <offer-id> [quantity]`: sell matching items to a buy-enabled system offer
- `/shop status` or `/shop info`: open the shared Tools Info/Status panel
- `/shop reload`: admin-only reload of system offers and settings-backed file path
- `/shop zoneoffers <file|default>`: admin-only set/reset the current shop area's system-offer file
- `/shop stock <offer-id> <stock>`: admin-only configure scoped dynamic stock
- `/shop economy <offer-id> key=value...`: deprecated. System-offer economy definitions are JSON-authoritative; edit the system-offers file and run `/shop reload`.

## Admin Shop Zones

Admins can open the Shop entry in the plugin radial menu to mark the current existing area as a shop area. This create action is hidden when the current area is already a shop area. If the admin is not standing inside an area, no shop area is created. The `Zone` tab is available only to admins standing in an existing area and focuses on the current area. For a non-Shop area it shows the area name/id and a mark-as-shop action. For an existing Shop-Zone it edits the stored display name, syncs that name from the current Rising World area, updates or clears the zone-specific system-offer file, switches systemshop mode between inherit/disabled/enabled, removes the current zone after confirmation, and resets current-zone system-offer stocks to target after confirmation. The admin tab remains the global shop-area list, removes shop status after confirmation, and cycles each area's system-shop override through inherit, disabled, and enabled. Plugin-registered offers are not persisted in the zone file; zones only control where the shared shop may be opened when `requireShopZone=true` and how system-shop offers are enabled in that area.

Players see the Shop icon in the shared Tools indicator panel while they are in a configured shop area. Disabling `showShopZoneIndicator` hides this HUD indication without changing shop access or purchases.

## Validation

- `scripts/verify-plugin-api.sh --summary`
- `mvn -B -DskipTests package`
- `mvn -B test`

## JSON-only distribution

Settings defaults (`settings.default.json`) and translations (`i18n/*.json`)
are shipped only as JSON. Legacy default and translation `.properties` files
are no longer included. Runtime settings remain world-scoped as
`settings.<world>.json`; migration of an existing `settings.properties` and
its backup remains supported. Updating the package does not delete old files
already present on the server. Use `mvn clean package` for a fresh local
package; ZIP assembly also excludes stale legacy settings and translations.

Admin-added items outside the enabled catalog use stock 0, target 10, limit 100, HYBRID mode, drain 50% (max 5), restock 25% (max 2), base price 10, price multipliers 0.25–4, spread 25%, and daily sell limits 1000 per player / 10000 global. Existing enabled catalog presets remain available. Removing an NPC trader offer sells its remaining stock through Wallet using the configured or default currency.
