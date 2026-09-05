# Trader generator

## Objective

Allow administrators to create a configured, stationary NPC trader at their current position through `/shop createtrader` (`/shop ct`) and the Shop radial menu.

## Ownership and dependencies

- Owner: `rw-plugin-oz-shop` only.
- Uses existing Rising World NPC, clothing, and player-position APIs, plus the existing Shop trader registration and Wallet-account routine.
- No Wallet API or persisted trader schema changes are required.

## Constraints and compatibility

- Existing `/shop maketrader` (`/shop mt`) remains the workflow for registering an NPC already in line of sight.
- First-run `traders-config.json` is copied from the packaged default and is never overwritten.
- Generated trader output and radial labels are DE/EN i18n; the localized gender prefix is selected from the creator's language.
- NPC creation uses the creator's position and rotation, randomizes gender/name/outfit from the user-editable configuration, and reapplies the identity, supported skin appearance, locked flag, and invulnerability after native NPC initialization.
- Generated traders are locked rather than static so they remain animated while staying in place.
- Appearance colors are RGB values rather than palette indexes. Light, medium, and dark skin palettes are weighted 76%, 20%, and 4%. Dummy outfits are deliberately added after native initialization, as in the Boss plugin. Male dummy hairstyles are randomized from IDs 50-68 and female styles from IDs 100-119; facial variation uses the available Skin variation field. Hats are configured separately and selected with 25% probability.
- Dissolving a trader is admin-confirmed, settles all scoped stock at base price, transfers every positive account balance to the world account, archives the Wallet account, and then removes only Shop ownership and scoped economy records. The NPC remains.
- On enable, the same settlement is applied automatically only to persisted traders whose NPC is missing or dead. No unrelated NPC is inspected or changed.

## Risks and rollback

- Invalid or missing clothing definitions are skipped; a trader is still created without that garment.
- A missing/invalid configuration falls back to a safe built-in selection so the admin command remains usable.
- Rollback consists of restoring the previous Shop artifact; `traders-config.json` and created NPCs remain server data and are not deleted automatically.
- Wallet settlement uses stable idempotency keys, so a failed dissolution can be retried without duplicating stock-sale credits or world-account transfers.

## Validation and deployment

- [x] Correct generated trader movement from static to locked and retain post-initialization flags.
- [x] Add confirmed trader dissolution, scoped-stock settlement, Wallet world transfer/archive, and persistence cleanup.
- [x] Add configuration loader/default, generator service, command/radial wiring, i18n, documentation, and icon assets.
- [x] Add focused configuration tests and run Maven tests, package, API verification, and entrypoint verification.
- [x] Upload only `OZShop` to the development server, reload plugins, and inspect the post-reload Shop log for errors.
- [x] In-game command/radial and NPC visual/interaction acceptance was confirmed on Development on 2026-09-04.

## 2026-08-07 follow-up

- [x] Expand default generated-trader outfits and randomize configured hats at 25% while preserving existing config files.
- [x] Preserve trader independence from global and Shop-zone system-shop settings.
- [x] Apply randomized supported gender-specific hairstyles and verify the API signature.
