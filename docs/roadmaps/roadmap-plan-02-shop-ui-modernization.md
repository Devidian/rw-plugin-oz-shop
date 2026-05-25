# Roadmap Plan 02 Shop UI Modernization

## Objective
Modernize Shop presentation and area workflows while moving Shop's compact indicator into the shared Tools indicator panel.

## Ownership
Primary repository: `rw-plugin-oz-shop`.

Supporting repositories:
- `rw-plugin-oz-tools` provides shared indicators, player settings, settings UI, dynamic UI helpers, and info/status panels.
- `rw-plugin-oz-wallet` remains required for functional purchases.

## Dependencies
- Tools shared indicator registration should be implemented first.
- Tools player settings support is needed for the list/card display preference.

## Work Packages
- [x] Package 1: Add card-layout rendering for offers using flex layout.
- [x] Package 2: Make card-layout the default and add a per-player setting for card versus list layout.
- [x] Package 3: Replace the Shop zone indicator with an icon registered in the shared Tools indicator panel.
- [x] Package 4: Add a confirmation dialog before removing a shop.
- [x] Package 5: Hide the radial menu action for creating a shop area when the current area is already a shop area.
- [x] Package 6: Add Shop info/status panel content and redirect existing info/status commands to the shared Tools panel.
- [x] Package 7: Complete Shop logger cleanup, settings metadata coverage, grouped settings labels, numeric input behavior, and i18n labels.

## Progress Notes
- Packages 1-5 are complete: Shop offers now render as cards by default with a persisted player list/card preference, shop-zone presence is exposed through the shared Tools indicator panel, shop-area removal requires confirmation, and the create-shop-area radial action is hidden when the current area is already a shop area.
- Root Step 8 logger cleanup is complete for Shop: settings logging now routes through the main `OZ.Shop` logger.
- Root Step 9 settings cleanup is complete for Shop: all safe defaults are exposed, settings are grouped, and English/German setting labels are present.
- Package 6 is complete for Root Step 10: Shop now registers a shared Tools Info/Status provider and routes `/shop status` and `/shop info` to the shared panel.

## Risks
- Card layout must still support system offers and plugin-registered offers without duplicating purchase logic.
- Delete confirmation must protect destructive actions but not leave stale UI state after cancellation.
- Area detection must follow Shop's existing area ownership model.

## Validation Strategy
- Verify card and list layouts both render system and plugin offers.
- Verify player setting persists and updates layout.
- Verify shared indicator appears only when Shop rules say it should.
- Verify deleting a shop requires confirmation.
- Verify Maven package and tests pass with Tools and Wallet available.

## Affected Repositories/Plugins
- `rw-plugin-oz-shop`
- `rw-plugin-oz-tools`
- `rw-plugin-oz-wallet`

## Rollback Considerations
Keep list layout available through player settings so card layout can be disabled if it has runtime UI issues.
