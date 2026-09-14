# Administrator-managed system-offer order

## Objective

Allow administrators to move an editable system offer earlier or later through
the existing in-game offer editor. Persist the explicit order in the selected
non-packaged JSON offer file and use it everywhere system offers are shown.

## Ownership

Owning repository/plugin: `rw-plugin-oz-shop`
Supporting repositories/plugins: none

## Dependencies

- Runtime: existing administrator editor and JSON offer-file writer.
- Build: Java 20 and existing Gson/Tools UI helpers.

## Risks

- The packaged default file remains read-only.
- Existing files without an order remain stable in their current file order.
- Reordering must not alter offer IDs, economy scopes, stock, or Wallet settlement.

## Validation Strategy

- [ ] Add focused order read/write and move-boundary tests.
- [ ] `mvn -B test`, `mvn -B -DskipTests package`, and PluginAPI check.
- [ ] Inspect the packaged defaults and DE/EN catalogues.
- [ ] Scoped Development upload and reload/log verification.

## Affected Repositories/Plugins

- `rw-plugin-oz-shop`

## Rollback Considerations

Removing the optional order field restores file-order presentation. Existing
offer data and stock remain unchanged.

## Implementation Checklist

- [x] Preserve JSON-file order for system offer lists.
- [x] Use deterministic order for global and scoped system offer lists.
- [x] Add editor move controls with localized boundary state.
- [x] Validate and deploy only Shop to Development.
