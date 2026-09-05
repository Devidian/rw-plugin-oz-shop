# Ingame Offer Editor

## Objective
Let administrators create and maintain non-packaged system-offer files directly in the Shop and NPC-trader overlays, while preserving scoped stock and Wallet settlement rules.

## Ownership
Owning repository/plugin: `rw-plugin-oz-shop`
Supporting repositories/plugins: `rw-plugin-oz-wallet` (existing trader-system-account settlement only)

## Dependencies
- Runtime: Rising World item-selection UI, OZ Tools UI/i18n, optional OZ Wallet for trader stock settlement.
- Build: Java 20 and the existing Shop Maven dependencies.
- Optional integrations: Wallet must be available to settle a removed trader offer's remaining stock.

## Risks
- User-entered file names must remain inside the plugin directory.
- JSON writes must preserve only supported offer configuration and must never select a packaged default file for editing.
- Removing an offer must clear only its current shop/trader economy scope; trader stock is settled before removal.

## Validation Strategy
- [ ] Add focused file/editor unit tests where the existing JSON parser permits it. In-game behavior was accepted on 2026-09-04.
- [x] `mvn -B test`
- [x] `mvn -B -DskipTests package`
- [x] `scripts/verify-plugin-api.sh --summary`
- [x] `git diff --check`
- [x] Scoped Shop-only Dev upload and reload/log verification.

## Affected Repositories/Plugins
- `rw-plugin-oz-shop`

## Rollback Considerations
Revert the Shop artifact. Administrator-created offer JSON files and their scoped SQLite stock remain server data and can be restored from backup or edited manually.

## Implementation Checklist
- [x] Confirm creation of a missing configured offer file before persisting it.
- [x] Add/remove selected offers from a non-default selected file.
- [x] Add a localized editable offer overlay and persist supported configuration fields.
- [x] Reconcile/reload affected scopes and settle removed trader stock.
- [x] Update README, HISTORY, and i18n.
- [x] Validate and deploy only OZ Shop to Dev.
