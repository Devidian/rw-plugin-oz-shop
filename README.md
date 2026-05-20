# OZ - Shop

Shared shop and purchase API plugin for Rising World.

## Responsibilities

- plugin offer registration API
- system-shop offer loading from JSON
- Wallet-backed purchase execution
- synchronous purchase callbacks
- lightweight `/shop` command for listing and buying offers

`rw-plugin-oz-tools` is a hard runtime dependency. `rw-plugin-oz-wallet` is required for functional purchases. If Wallet is missing, Shop loads but purchases are disabled and admins receive a warning on spawn.

## Settings

The plugin copies `settings.default.properties` to `settings.properties` on first run.

```properties
logLevel=ALL
reloadOnChange=true
shopCommand=shop
sendPluginWelcome=false
systemOffersFile=system-offers.json
```

`systemOffersFile` points to an admin-editable JSON file in the plugin directory. On first run, `system-offers.default.json` is copied to that path when the file is missing.

## System Offers

System-shop offers use JSON:

```json
[
  {
    "id": "example.info",
    "title": "Example system offer",
    "description": "Shown in /shop list",
    "price": 100,
    "currency": "",
    "icon": "shop-icon",
    "enabled": false
  }
]
```

An empty `currency` uses Wallet's configured default currency. System offers are listed, but they are not purchasable until a purchase action exists. Plugin-registered offers provide that action through a synchronous callback.

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

public ShopOfferRegistrationResult unregisterOffer(String id, String pluginIdentifier);

public ShopPurchaseResult purchase(Player player, String offerId);

public ShopOffer findOffer(String offerId);

public List<ShopOffer> listOffers();

public int reloadSystemOffers();
```

Offer ids are trimmed and normalized to lowercase. Prices are whole-number non-negative `long` values. Purchase flow validates the offer, requires Wallet, withdraws the price, executes the callback synchronously, and attempts a Wallet refund when the callback fails.

## Player Commands

- `/shop` or `/shop list`: list enabled offers
- `/shop buy <offer-id>`: buy an offer
- `/shop reload`: admin-only reload of system offers and settings-backed file path

## Validation

- `scripts/verify-plugin-api.sh --summary`
- `mvn -B -DskipTests package`
- `mvn -B test`
