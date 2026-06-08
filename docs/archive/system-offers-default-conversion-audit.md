# System Offers Default Conversion Audit

## Objective

Convert `src/system-offers.default.json` from legacy `sellEnabled`/`buyEnabled` flags to the `isEnabled` offer activation model without changing existing default-offer availability or stock modes.

## Source Counts

Total offers converted: 3,632

Legacy `(stockMode, sellEnabled, buyEnabled)` counts:

| stockMode       | sellEnabled | buyEnabled | count |
| --------------- | ----------: | ---------: | ----: |
| HYBRID          |       false |      false | 3,383 |
| HYBRID          |        true |      false |     1 |
| HYBRID          |        true |       true |     2 |
| PLAYER_SUPPLIED |        true |       true |   226 |
| SYSTEM_SUPPLIED |        true |      false |    20 |

New `(stockMode, isEnabled)` counts:

| stockMode       | isEnabled | count |
| --------------- | --------: | ----: |
| HYBRID          |     false | 3,383 |
| HYBRID          |      true |     3 |
| PLAYER_SUPPLIED |      true |   226 |
| SYSTEM_SUPPLIED |      true |    20 |

## Field Changes

- Removed legacy fields: 7,264 (`sellEnabled` and `buyEnabled` from 3,632 offers).
- Added `isEnabled` to all 3,632 offers.
- `isEnabled` policy: `sellEnabled || buyEnabled`.
- Preserved all existing `stockMode`, price, stock, metadata, and limit fields.

## Restock Adjustments

`SYSTEM_SUPPLIED` restock adjustments: 0

All enabled `SYSTEM_SUPPLIED` offers already had explicit restock configuration, so no default JSON restock fields were changed.

## Skips And Flags

- Offers skipped: 0
- Offers manually flagged: 0
