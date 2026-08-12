# Trader buyback funding shortfall

## Objective

Fund only the modifier premium of an NPC-trader buyback while requiring the
trader to cover the normal-modifier payout from its own account.

## Ownership, compatibility, and rollback

Shop keeps trader orchestration while Wallet moves funds. The world account
funds only `payout - normal-modifier payout`; insufficient normal-payout capital
disables the sale and leaves all accounts unchanged.

## Risks and validation

The premium transfer precedes the sale and is rolled back only if the sale still
fails. The disabled sale action reports insufficient trader balance and updates
with the selected quantity.

## Checklist

- [x] Trace the premium rollback to a normal-payout funding error.
- [x] Fund only the modifier premium through the world account, using the same aggregate rounding as the player payout.
- [x] Block insufficient normal-payout balances in UI and transaction handling.
- [x] Package and verify the Development runtime reload.
