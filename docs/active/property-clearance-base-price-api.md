# Complete-catalog base-price API

## Objective

Expose the immutable `system-offers.complete.json` base price per concrete item
unit for sibling plugins without allowing them to mutate Shop offers.

## Ownership and dependencies

- Owner: `rw-plugin-oz-shop`.
- Consumer: `rw-plugin-oz-land-claim` through the public main-plugin method.
- Source: packaged `system-offers.complete.json`; Wallet is not involved in the
  lookup.

## Checklist

- [x] Parse the complete catalog through the existing offer parser.
- [x] Cache normalized item/variant unit prices on Shop offer reload.
- [x] Return zero for missing or invalid items.
- [x] Document the public compatibility method.

## Risks and validation

The complete catalog is a packaged reference file. A missing or malformed file
produces an empty cache and therefore a zero value, never an inferred price.
Validate with the Shop Maven test/package build and Land Claim's recycling
integration build. Rollback is a Shop JAR rollback; consumers receive zero
instead of an unsafe price while unavailable.
