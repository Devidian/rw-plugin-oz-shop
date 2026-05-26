# History / Changelog / Commitlog

<https://www.conventionalcommits.org/en/v1.0.0/>

## [unreleased]

- fix: use a Shop-specific shop-zone shared-indicator icon key
- change: use the dedicated shop-zone shared-indicator icon for shop-zone signals
- feat: add admin-controlled item and recipe definition exports for Shop planning
- feat: add a reserved dynamic-economy enable switch defaulting to disabled
- feat: add Shop radial menu Info/Status entry using the shared Tools info icon
- fix: display system-offer item icons from Rising World item definitions when available
- feat: add shared Tools Info/Status panel content for Shop and route info/status commands to it
- feat: group and localize Shop admin settings metadata
- refactor: route Shop settings logging through the main `OZ.Shop` logger
- feat: add default card layout for Shop offers with a per-player list/card preference
- feat: move the shop-zone HUD signal into the shared Tools indicator panel
- feat: require confirmation before removing shop-area status
- fix: hide the create-shop-area radial action while already inside a shop area
- feat: create `OZ - Shop` plugin from the Maven template
- feat: add plugin offer registration and synchronous purchase callback API
- feat: add Wallet-backed purchase execution with callback-failure refund attempt
- feat: add admin-editable JSON system-shop offer loading
- feat: add `/shop` list, buy, and admin reload command workflow
- feat: change system offers to `itemName` and `itemVariant` based built-in item offers
- feat: add system offer `amount` for multi-item packages
- feat: generate `system-offer-example.json` from Rising World item definitions
- feat: support per-player dynamic plugin offer prices through `ShopPriceResolver`
- feat: add public shop-offer category and source metadata for plugin registrations
- feat: expose runtime plugin-offer registry listing and bulk unregister API
- feat: report callback-failure refunds and failed compensation during purchases
- feat: add shop availability settings and area-based shop access control
- feat: add admin radial action to mark the current existing area as a shop area
- feat: add shop UI with Systemshop, Pluginshop, and admin shop-area tabs
- feat: add global system-shop enablement and per-shop-area `systemShop` overrides
- feat: add shop-zone HUD indicator below LandClaim area info
- docs: document Shop API, install/update scope, and example plugin registration
- fix: send configured welcome message to all players instead of admins only
