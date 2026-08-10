# Shop Economy Classification

This document describes the economy classification used by the Shop Plugin default system-offer configuration.

## Metadata Fields

Generated classification metadata uses underscore-prefixed helper fields:

```json
{
  "_economyTier": "T9",
  "_economyCategory": "Workbench Products",
  "_economyDomain": "product"
}
```

Every offer should have all three fields. Valid offers without a more specific rule use the T0 `Trash / Common` loot fallback. Invalid or unsupported items use the separate `invalid` classification and remain disabled.

## Tier Model

| Tier         | Category                     | Domain       | Rule                                                            |
| ------------ | ---------------------------- | ------------ | --------------------------------------------------------------- |
| T0           | Trash / Common               | resource/loot | Very common low-value terrain, pickup, or fallback loot items   |
| T1           | Basic Resources              | resource     | Basic raw resources                                             |
| T2           | Wood and Processing          | resource     | Wood, paper, cloth, leather, and early processed resources      |
| T3           | Ores                         | resource     | Mineable ores and rare raw minerals                             |
| T4           | Processed Metals             | resource     | Ingots, plates, wires, powders, glass/electronic intermediates  |
| T5           | Agriculture                  | resource     | Crops, fruit, food, flour, bread, and simple farm products      |
| T6           | Animal Products              | resource     | Hides, pelts, antlers, shells, meat, eggs, guts                 |
| T7           | Handcrafted Products         | product      | Recipe exists and does not require a crafting station           |
| T8           | Primitive Workbench Products | product      | Recipe requires primitive workbench or equivalent early station |
| T9           | Workbench Products           | product      | Recipe requires the normal workbench                            |
| T10          | Modern Workbench Products    | product      | Recipe requires modern workbench                                |
| T11          | Small Animal Items           | entity       | Small animal items that can be held in inventory                |
| invalid      | Invalid                      | invalid      | Unsupported or invalid items that must never be traded          |
| unclassified | unclassified                 | unclassified | Legacy classification value; valid offers use the T0 fallback   |

## Product Tier Resolution

T7-T10 are derived from recipe station requirements:

1. If the item is already T0-T6, keep the resource tier.
2. If the item is a small animal item, classify it as T11.
3. If the recipe has no required crafting station, classify it as T7.
4. If the recipe requires `primitiveworkbench`, classify it as T8.
5. If the recipe requires `workbench`, classify it as T9.
6. If the recipe requires `workbenchmodern` / `ModernWorkbench`, classify it as T10.
7. If multiple stations are required, the highest relevant tier wins.

## T11 Animal Rule

T11 is only for actual small animal items that can exist in player inventory.

Animal products are not T11. Examples:

- `elephanthide` is T6 because it is hide, not an elephant.
- `deerredpelt` is T6 because it is pelt, not a deer.
- `snakemeatcooked` is T6 because it is meat, not a snake.
- `antlerdeerred`, `antlerdeer`, and `antlermoose` are T6 animal products because they are antlers.

Large animals are not classified as T11 unless the game exposes them as inventory items.

## Stock Defaults

| Tier   | stockMode       | stock | targetStock | stockLimit | buyEnabled | sellEnabled | per-player daily sell limit | global daily sell limit |
| ------ | --------------- | ----: | ----------: | ---------: | ---------- | ----------- | --------------------------: | ----------------------: |
| T0     | LOOT            |     0 |        1000 |     250000 | true       | true        |                       10000 |                   50000 |
| T1     | PLAYER_SUPPLIED |     0 |        2500 |     100000 | true       | true        |                        5000 |                   20000 |
| T2     | PLAYER_SUPPLIED |     0 |        2500 |     100000 | true       | true        |                        5000 |                   20000 |
| T3     | PLAYER_SUPPLIED |     0 |         500 |      50000 | true       | true        |                        2000 |                   10000 |
| T4     | PLAYER_SUPPLIED |     0 |         500 |      25000 | true       | true        |                        1000 |                   10000 |
| T5     | PLAYER_SUPPLIED |     0 |         500 |      50000 | true       | true        |                        2000 |                   10000 |
| T6     | PLAYER_SUPPLIED |     0 |         500 |      25000 | true       | true        |                        1000 |                   10000 |
| T7-T10 | HYBRID          |    10 |          10 |       1000 | unchanged  | unchanged   |                   unchanged |               unchanged |
| T11    | SYSTEM_SUPPLIED |     1 |           2 |          5 | false      | true        |                           0 |                       0 |
| invalid | STATIC          | unchanged | unchanged | unchanged | false      | false       |                   unchanged |               unchanged |

For offer semantics, `buyEnabled=true` means players can sell matching items to the system shop. `sellEnabled=true` means players can buy the offer from the system shop.

`LOOT` is the T0 stock mode: players can both buy and sell, and the normal drain tick removes only stock above target. `LOOT` never performs an automatic restock. Across global, zone, and NPC-trader scopes, restock runs only below target and is capped at target; drain runs only above target and is capped at target. `SYSTEM_SUPPLIED` is restock-only, `HYBRID` uses both directions, and `PLAYER_SUPPLIED`/`STATIC` never run automatic stock movement.

Invalid offers use `basePrice=9999`, `stockMode=STATIC`, and `isEnabled=false`. They remain in `system-offers.complete.json` and are excluded from the generated runtime default and tier files.

## Current Tier Counts

| Tier         | Count |
| ------------ | ----: |
| T0           |   123 |
| T1           |    14 |
| T10          |   233 |
| T11          |    15 |
| T2           |    27 |
| T3           |     6 |
| T4           |    18 |
| T5           |    68 |
| T6           |   117 |
| T7           |    12 |
| T8           |    22 |
| T9           |   286 |
| invalid      |  3505 |

## Current Domain Counts

| Domain       | Count |
| ------------ | ----: |
| entity       |    15 |
| invalid      |  3505 |
| loot         |   105 |
| product      |   553 |
| resource     |   268 |

## Maintenance Notes

- Keep the underscore-prefixed metadata fields in complete and generated default offers.
- Classify valid items without a more specific rule as T0 `Trash / Common` in the `loot` domain.
- Re-run recipe classification after recipe exports change.
- Review T11 manually because animal names are game-specific and can overlap with animal products.
- Fish and meat families use a common T6 baseline: raw `20`, cooked `28`, dried `22`, and burned `15`.
- `branch`, generic `plantitem`, `constructionitem`, and `clothingitem` variants, `blueprint`, `missingitem`, and `missingobject` are invalid and must remain unavailable.
