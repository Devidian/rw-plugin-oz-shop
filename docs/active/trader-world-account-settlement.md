# Trader-world-account settlement

## Objective

Ensure every trader-capital and stock movement is funded by the Wallet world account or settles into it, including modifier premiums and administrator replenishment.

## Ownership, compatibility, and rollback

Shop owns quotes, trader stock, and orchestration; Wallet owns all money movement. Existing trader account ids and economy scopes remain compatible. Reverting the plugin artifact restores prior behavior.

## Risks and validation

All transfers need stable idempotency keys. A failed transfer must prevent the stock update or player payout. Validate normal and modifier buybacks, restock/drain, startup capital, replenishment, and trader dissolution.

## Checklist

- [x] Fund startup capital from the world account.
- [x] Settle automatic drain/restock through the world account.
- [x] Split modifier premium from trader balance and world account for buybacks.
- [x] Add administrator start-capital replenishment.
- [x] Test, package, and deploy to Dev.
