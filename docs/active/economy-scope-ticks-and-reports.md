# Economy scope ticks and reports

## Objective

Make automatic economy reconciliation atomic per global shop, zone, or trader scope, and optionally send localized, quota-exempt OZ Mail reports to configured recipients.

## Scope

- Shop: persisted scope tick timestamp, report switches, recipient settings, localized report assembly, and Wallet reasons with quantities.
- Mail: trusted plugin system-report delivery that does not consume the recipient mailbox quota.

## Implementation plan

### Ownership and contract

- Shop owns one durable reconciliation clock and report preference per economy
  scope (`global`, `zone:<areaId>`, `trader:<npcId>`). A scope is reconciled in
  one SQLite transaction: all stock changes and the completed tick timestamp
  commit together, or none do.
- Shop report recipients are a comma-separated administrator setting of exact
  player names. Resolve and validate names through the player database during
  settings reload; unknown names are logged and excluded. Global, zone, and
  trader reports each have an independently persisted enabled switch.
- A report is emitted only after a committed scope tick with stock changes. It
  lists changed offers; trader reports additionally include the trader-account
  balance delta/total available from the completed settlement.
- Mail owns delivery and idempotency. Its additive trusted-system-report entry
  bypasses only mailbox-capacity checks, never sender trust, validation, or the
  durable operation journal. Existing player and plugin mail semantics remain
  unchanged.

### Migration, rollback, and validation

- Additive Shop/Mail SQLite tables or columns default report switches to off;
  no existing scope is reported until explicitly enabled. Existing per-offer
  `last_tick_at` values seed the new scope clock on first reconciliation.
- Missing or older Mail bridge APIs degrade to no report delivery and do not
  prevent reconciliation. Repeated timer runs reuse a deterministic report
  correlation ID.
- Rollback uses the preceding plugin artifacts; new persistence is ignored by
  old versions. Validate transaction rollback, scope isolation, recipient
  filtering, localized content, idempotency, quota bypass, and unchanged
  normal-mail capacity enforcement.

## Checklist

- [x] Persist one tick timestamp per scope and reconcile all offers in that scope together.
- [x] Persist report switches for global, zones, and traders.
- [x] Add configured named report recipients and validate them against the player database.
- [x] Add quota-exempt, idempotent Mail bridge delivery.
- [x] Add DE/EN report text and regression tests.

## Validation

- Shop and Mail Maven tests/package, PluginAPI checks, and diff checks.
- Verify a report contains only changed offers and the trader balance totals.

## Implementation evidence (2026-08-09)

- Shop tests: 28 passed, including scope-wide tick persistence and change
  reporting input coverage.
- Mail tests: 21 passed. The temporary validation repository used the locally
  built Tools bridge artifact because Mail currently declares Tools `0.23.12`
  while Shop declares `0.23.13`.
- Runtime delivery remains a Dev acceptance step: configure a trusted `OZ -
  Shop` Mail sender, one exact recipient, and the desired report switches.

## Cadence correction (2026-08-10)

- Each Global, Zone, and Trader scope receives a durable initial tick at plugin
  enable/reload only when it lacks one. The five-second scheduler checks due
  scopes; it advances every due scope even when no stock changes, keeping the
  configured cadence independent of player purchases.
- The admin trigger schedules only the viewed scope by backdating its durable
  tick by one interval. The scheduler performs the work on its next pass.

## Follow-up correction (2026-08-10)

- Trader reconciliation now treats the shared scope clock as authoritative, so
  an administrator-triggered due tick processes the trader's automatic stock
  movement even when legacy per-offer timestamps are still in the future.
- Dev offer files are resolved from the Development server upload root when
  the runtime reports the paired Dedicated server root.
- Mail stores `quota_exempt` reports separately from mailbox usage; reports
  remain visible and idempotent without causing a full-mailbox warning.
