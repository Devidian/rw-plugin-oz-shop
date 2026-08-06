# NPC Traders

## Objective

Add registered NPC traders to OZ - Shop. Each trader has a stable Wallet system
account, isolated offer/economy scope, and an NPC interaction entry point.

## Ownership

- `rw-plugin-oz-shop`: trader registration, NPC lifecycle, trader offer/economy
  behavior, commands, UI, and persistence.
- `rw-plugin-oz-wallet`: system-account balances, atomic transfers, and the
  narrow owner-authorized issuance API required for trader seed/drain revenue.

## Dependencies

- Rising World NPC interaction and lookup APIs.
- Optional runtime Wallet integration; trader trading is unavailable without
  the required system-account transfer and issuance APIs.

## Decisions and constraints

- Trader account IDs are deterministic (`trader::<npcGlobalId>`) and owned by
  `oz-shop`; account mutations remain Wallet operations.
- Initial capital is 1000 in the configured/default Shop currency. Automatic
  restock debits the trader at the offer base price; automatic drain credits
  the trader at that same price. Both operations are auditable and idempotent.
- Trader offers, stock, and trade statistics use a trader-specific scope and
  never share Shop-Zone state.
- Existing Shop-Zone behavior and public Shop APIs remain compatible.

## Risks and rollback

- An unavailable or old Wallet disables trader money operations rather than
  falling back to player-balance mutations.
- Persistence is additive. Rolling Shop back leaves inactive trader records;
  rolling Wallet back requires rolling Shop back first.
- NPC lookup failure on startup is logged and does not delete trader data.

## Validation

- Unit tests for registration metadata, economy settlement decisions, and
  Wallet issuance authorization/idempotency.
- Maven tests/package, API verification, entrypoint verification, ZIP check,
  and targeted Dev runtime reload/log inspection.

## Affected repositories/plugins

- `rw-plugin-oz-shop`
- `rw-plugin-oz-wallet`

## Checklist

- [x] Add the Wallet owner-scoped issuance contract and tests.
- [x] Persist traders and their isolated offer/economy settings.
- [x] Add `/shop maketrader` and NPC interaction/lifecycle handling.
- [x] Adapt the Shop overlay for trader player/admin contexts.
- [x] Add DE/EN text, empty default trader offer file, README/HISTORY notes.
- [x] Validate, upload only Shop and Wallet to Dev, reload, and inspect logs.
