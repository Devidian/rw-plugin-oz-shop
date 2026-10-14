# Faction traders

## Objective and constraints

- [x] Persist `factionId` with an additive migration and enforce faction trader capacity and roles.
- [x] Keep one collision-safe `faction.[factionName].[traderName].json` file per trader, moving it safely on rename.
- [x] Constrain faction offer stock to player-supplied movement; disable automatic ticks and direct stock configuration.
- [x] Settle trade through faction-owned account APIs with stock reservation and compensation.
- [x] Delete stock and offer data on removal without world-account sales.
- [x] Expose gender-specific faction creation in the radial menu and keep trader dummies stationary.
- [x] Confirm all four radial creation actions before spawning.
- [x] Use ASCII-safe offer filenames and reapply name, gender, flags and clothing after native NPC initialization.
- [x] Allow editing faction stock limit while leaving target stock at zero; identify faction ownership in the header and use native confirmations with accurate dissolution text.
- [x] Show the owning faction name in the header, with the stored faction ID when Factions is temporarily unavailable.

## Validation and rollout

Test migration, file rename, roles, capacity, purchase, sale, insufficient funds and stock, item state, modifier and direct edit guards. Run API checks, Maven tests and package. Back up database and offer files before Development upload, then inspect logs and player flows.

Java 25 package and 45 tests passed on 2026-10-14. Development startup and migrated columns were checked after upload. Interactive player trade flows remain open.

The gender-specific menu follow-up passed the same 45 tests. Development reloaded Shop at 16:43 UTC and registered all eight new Classic/Modern trader textures. The current Shop database, jar, and JSON files were backed up at `development-server/backups/faction-npc-menu-20261014-164133/OZShop`. Confirm the four menu choices and stationary NPC behaviour in game.

Player testing found that faction creation raised `InvalidPathException` for filenames such as `faction.QNA Team.Händlerin Mara.json` on the Development host. The exception occurred after spawning and before delayed NPC initialization, leaving unregistered dummies. The follow-up transliterates filename parts to ASCII, persists the intended name independently of native NPC initialization, cleans up failed registrations, repeats clothing setup on delayed initialization, and adds confirmation for all four radial actions. Player acceptance remains open until the four choices are retried in game.

Follow-up validation on 2026-10-14: 46 tests passed, the Java 25 package built, the relevant PluginAPI methods were checked, and `git diff --check` passed. The current Development Shop database, jar and 31 JSON files were backed up at `development-server/backups/faction-trader-fix-20261014-172145/OZShop`. Only Shop was uploaded; its isolated build JAR hash matched the remote JAR. The Development container reported `OZ - Shop Plugin is enabled version:0.12.0` and `RELOADED ALL PLUGINS` at 17:24 UTC without a new Shop exception. In-game placement, clothing and gender still need player verification. The earlier unregistered dummy NPCs were not changed.

The player then confirmed that faction trader creation works. The next Shop follow-up exposed `stockLimit` in faction offer editing and in the direct file edit path while keeping `targetStock` at zero; native game message boxes now confirm creation and dissolution. The faction trader header and account label identify the faction account, and dissolution text explains that the account remains intact. On 2026-10-14, 46 tests, the Java 25 package, PluginAPI checks, JSON parsing and `git diff --check` passed. The live Development Shop database, jar and 33 JSON files were backed up at `development-server/backups/faction-trader-limit-20261014-175831/OZShop` before uploading only Shop. The isolated JAR hash matched Development, which reported `OZ - Shop Plugin is enabled version:0.12.0` and `RELOADED ALL PLUGINS` at 17:59 UTC. The faction trader row for NPC 16623060 remained present. Native dialog choice mapping, max-stock editing and dissolution still need player acceptance in game.

The faction header now resolves the stored faction ID through Factions and shows its name, falling back to `#ID` if Factions cannot supply the name. On 2026-10-14, 46 Shop tests, the Java 25 package, API summary, JSON parsing, and diff check passed. Development Shop data was backed up at `development-server/backups/faction-trader-header-20261014-185724/OZShop`; only Shop was uploaded. Its isolated JAR hash matched the server, which logged successful Shop enable and plugin reload at 18:58 UTC. Faction 9 currently resolves to `QNA Team`; no faction trader was present in the Shop database after reload, so the header still needs a player check on an active faction trader.

## Rollback

Remove faction traders and close their operations before restoring an older jar. Restore the database and offer-file backups together if needed.
