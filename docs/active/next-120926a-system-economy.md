# System economy sell limits and stock semantics

## Objective

Prevent the condition-selection dialog from exceeding the requested or remaining
daily sell quantity, calculate automatic drain from current stock, and replace
percentage-based restocking with a fixed per-tick `restockMax` amount.

## Ownership

Owning repository/plugin: `rw-plugin-oz-shop`
Supporting repositories/plugins: none

## Dependencies

- Runtime: existing OZ Tools, Wallet, and SQLite integrations.
- Build: Java 20 and the existing Maven test suite.

## Risks

- Existing offer JSON may still contain the retired restock-rate field; the
  loader must tolerate and ignore it.
- A stale condition-selection quote must never remove inventory or pay Wallet
  funds beyond the originally requested amount.

## Validation Strategy

- [x] Add regression coverage for selection-quote and daily-limit boundaries.
- [x] Add regression coverage for stock-relative drain and fixed restock.
- [x] `mvn -B test`
- [x] Isolated `mvn -B clean package` (the workspace `target/` has pre-existing ownership restrictions).
- [x] Upload only OZ Shop to Development and inspect reload logs.

## Affected Repositories/Plugins

- `rw-plugin-oz-shop`

## Rollback Considerations

Restore the previous OZ Shop artifact. Old JSON remains readable; its removed
retired restock-rate field is ignored by the new version.

## Implementation Checklist

- [x] Bound condition selection and revalidate selected units before settlement.
- [x] Make drain stock-relative while retaining the configured drain cap.
- [x] Remove the retired restock-rate field from generated/default JSON, UI, code, and docs.
- [x] Retain JSON-reader compatibility for old retired restock-rate fields.
- [x] Validate and deploy.
