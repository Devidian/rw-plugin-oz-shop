# Shop Economy Raw Material Price List

This document explains the baseline prices for the resource economy of the Shop Plugin.

T0-T6 are manually anchored economy tiers. T7-T10 product prices should be calculated from recipes, not maintained as isolated static prices.

## Pricing Principles

- `basePrice` is the per-item baseline value.
- Raw resources have direct baseline prices.
- Processed resources must include the material value plus a processing markup.
- Processing markup represents fuel, time, station requirements, handling effort, and production complexity.
- Recipe products from T7-T10 are calculated from ingredient costs plus station-specific markup.

## Processing Markup

Processed materials must never be priced as only the sum of their ingredients.

Examples:

- Leather = hide or pelt value + processing markup.
- Ingots = ore value + fuel cost + smelting time + processing markup.
- Plates and wires = metal value + station usage + processing markup.
- Cloth, rope, and paper = raw fiber/wood value + processing effort.

Recommended formula:

```text
processedBasePrice = ingredientCost + fuelCost + timeCost + processingMarkup
```

Recommended processing markup ranges:

| Material Type                   | Recommended Markup |
| ------------------------------- | -----------------: |
| Simple hand processing          |        +5% to +15% |
| Primitive station processing    |       +10% to +25% |
| Workbench processing            |       +25% to +60% |
| Modern processing / electronics |      +60% to +150% |

## Recipe Product Pricing

T7-T10 products should be calculated from the recipe tree.

```text
recipePrice = sum(ingredientBasePrice * ingredientAmount) + processingMarkup
```

When a recipe depends on processed ingredients, use the processed ingredient's own calculated base price.

## T0-T6 Baseline Prices

## T0 — Trash / Common

| Item   | Base Price |
| ------ | ---------: |
| branch |        0.3 |
| dirt   |        0.1 |
| grass  |        0.1 |
| gravel |        0.2 |
| mud    |       0.15 |
| sand   |       0.25 |
| snow   |       0.25 |
| stick  |        0.6 |

## T1 — Basic Resources

| Item         | Base Price |
| ------------ | ---------: |
| coal         |          3 |
| cobble       |        1.2 |
| cotton       |          4 |
| fibers       |          2 |
| forestground |        1.2 |
| hempfibers   |          3 |
| redclay      |          2 |
| stone        |          1 |
| sulfur       |          4 |
| wool         |          5 |

## T2 — Wood and Processing

| Item        | Base Price |
| ----------- | ---------: |
| cloth       |         15 |
| coarsecloth |        250 |
| leather     |         20 |
| lumber      |        1.9 |
| nails       |         14 |
| paper       |          8 |
| rags        |          2 |
| rope        |       1000 |
| treelog     |          6 |
| yarn        |          8 |

## T3 — Ores

| Item         | Base Price |
| ------------ | ---------: |
| aluminiumore |         18 |
| goldore      |         30 |
| hellstone    |         80 |
| ironore      |         12 |
| obsidian     |         50 |
| tungstenore  |         45 |

## T4 — Processed Metals

| Item           | Base Price |
| -------------- | ---------: |
| aluminiumingot |         32 |
| aluminiumplate |         40 |
| aluminiumwire  |       12.5 |
| circuitboard   |     597.35 |
| coalpowder     |          4 |
| goldingot      |         55 |
| goldplate      |      68.75 |
| goldwire       |         68 |
| gunpowder      |        250 |
| ironingot      |         22 |
| ironplate      |       27.5 |
| ironwire       |         26 |
| lightbulb      |        219 |
| rawglass       |          5 |
| sulfurpowder   |          5 |
| tungsteningot  |         90 |
| tungstenplate  |      112.5 |
| tungstenwire   |       35.2 |

## T5 — Agriculture

| Item               | Base Price |
| ------------------ | ---------: |
| apple              |          8 |
| bellpepper         |          7 |
| bread              |         24 |
| breaddough         |         50 |
| butter             |         16 |
| carrot             |          5 |
| chili              |          8 |
| coconut            |         14 |
| coconutslice       |          4 |
| cookie             |         14 |
| cookiedough        |         80 |
| corncob            |          6 |
| corncobcooked      |          8 |
| eggplant           |          8 |
| flour              |         10 |
| gingerroot         |          7 |
| lettuce            |          5 |
| pear               |         10 |
| potato             |          5 |
| potatocooked       |          7 |
| pumpkin            |         14 |
| pumpkinslice       |          4 |
| pumpkinslicecooked |          5 |
| sugarbeet          |          6 |
| tomato             |          7 |
| watermelon         |         18 |
| watermelonslice    |          4 |
| wheat              |          4 |

## T6 — Animal Products

Comparable fish and meat families use one processing-state baseline until gameplay differences justify species-specific prices:

| Processing State | Base Price |
| ---------------- | ---------: |
| Raw              |         20 |
| Cooked           |         28 |
| Dried            |         22 |
| Burned           |         15 |

| Item            | Base Price |
| --------------- | ---------: |
| antlerdeer      |         12 |
| antlerdeerred   |         14 |
| antlermoose     |         18 |
| bacon           |         20 |
| bearpelt        |         20 |
| blobfishmeat    |         20 |
| boarhide        |         18 |
| cowhide         |         16 |
| deerpelt        |         16 |
| deerredpelt     |         18 |
| egg             |          8 |
| elephanthide    |         20 |
| foxpelt         |         14 |
| goatpelt        |         14 |
| guts            |          4 |
| horsehide       |         18 |
| lionpelt        |         20 |
| moosepelt       |         20 |
| penguinhide     |         12 |
| pighide         |         14 |
| polarbearpelt   |         20 |
| rhinohide       |         20 |
| salmonsteak     |         20 |
| seacucumbermeat |         20 |
| sheeppelt       |         14 |
| snakemeat       |         20 |
| steak           |         20 |
| tunasteak       |         20 |
| turtlemeat      |         20 |
| turtleshell     |         10 |
| waterskin       |         50 |
| wolfpelt        |         18 |
| zebrahide       |         18 |

## Balancing Notes

- Common terrain resources should remain low-value to avoid terrain-farming exploits.
- High-volume resources need strict daily sell limits.
- Processed resources should be more valuable than raw inputs, but not so profitable that they create infinite money loops.
- Rare ores and advanced processed metals should have lower stock targets and stricter sell limits.
- Animal hides, pelts, antlers, shells, meat, and guts are T6 animal products, not T11 animal entities.
- Small animal items are handled separately as T11 controlled entity offers.
- Saplings and seeds are classified as T5 agriculture resources; generic unresolved `plantitem` variants are invalid and unavailable.
