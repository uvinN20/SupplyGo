# Order management

Use Java 25 and Maven 3.9+. Build `feature/order-management` while reviewing this
feature; use `main` after merge. Both server and desktop clients must use this
revision. Follow [AUTHENTICATION.md](AUTHENTICATION.md) to configure TLS and
accounts, then run the same server/client commands.

## Roles

| Role | Read orders | Place an order | Approve / fulfil | Cancel |
| --- | --- | --- | --- | --- |
| SELLER | Own orders only | Yes, for themselves | No | Own pending orders |
| SELLER_MANAGER | All orders | No | Yes | Any pending or approved order |
| PRODUCTION_MANAGER | All orders | No | Yes | Any pending or approved order |
| ADMIN | All orders | No | Yes | Any pending or approved order |
| SUPPLIER / SUPPLIER_MANAGER | No | No | No | No |

All permissions are checked on the server, using the authenticated connection.
Clients cannot choose the seller identity, acting role, timestamps or order ID.
A seller requesting another seller's order receives `ORDER_NOT_FOUND`.

## Demonstrate the complete workflow

1. As an administrator or production manager, create product `APPLE` and receive
   batches of 5 and 10 units using the Inventory tab.
2. Sign out and sign in as a seller. Open **Orders → New order**, enter `APPLE`
   and 8 units. The server saves a `PENDING` order and reserves 8 units.
3. Refresh Inventory. The product shows **On hand: 15**, **Reserved: 8** and
   **Available: 7**. Another order or direct stock withdrawal cannot use those
   8 reserved units.
4. Sign in as an administrator, seller manager or production manager. Open
   **Orders → Refresh**, select the order, and click **Approve**.
5. Select the approved order, click **Fulfil**, and confirm. The server consumes
   8 physical units in FIFO receipt order and marks the order `FULFILLED` in the
   same save. Batch 1 is exhausted; batch 2 retains 7 units. Product totals become
   **On hand: 7**, **Reserved: 0**, **Available: 7**.
6. The seller can refresh Orders to see the final status. **Details** shows
   creation/update times in UTC, the last acting account, and the submission ID.
7. Place another order and cancel it to demonstrate releasing the reservation.
   Cancelling changes neither the physical batch quantities nor their receipt order.

The Orders tab shows ten records per page, in order-ID order. Refresh starts at
page 1; Next page continues. Sellers see only their records. Logout, disconnect,
or switching accounts clears displayed records. Stock tables must be refreshed
after order changes. Other clients' changes appear on refresh.

## Status rules and stock accounting

One order contains one product and a whole-unit quantity from 1 to 1,000,000.

| Action | Allowed starting status | Result | Stock effect |
| --- | --- | --- | --- |
| Place | New submission | PENDING | Reserve requested units |
| Approve | PENDING | APPROVED | Keep reservation |
| Fulfil | APPROVED | FULFILLED | Consume physical stock FIFO and release reservation |
| Cancel | PENDING or APPROVED | CANCELLED | Release reservation without consuming stock |

Sellers can cancel only their own PENDING orders. A manager can also cancel an
APPROVED order. FULFILLED and CANCELLED are final; they cannot be reopened.
Repeating an already completed action returns the current order if its status
still matches that action, without changing stock again. Competing fulfil/cancel
actions are serialized; only one final outcome is saved.

`available = onHand - reserved`. PENDING and APPROVED orders contribute to
reserved units. Batches continue to show physical stock. Reservations are per
product, with FIFO batches chosen at fulfilment; they do not promise a particular
batch. Direct withdrawals consume FIFO stock only when enough unreserved units
remain for every active order.

## Lost replies and retries

Each creation request includes a client-generated UUID `clientOrderId`. Its scope
is the seller account. The server saves it with the order. Reusing the same ID,
product and quantity returns that original order, including its current status,
even after a restart. Using that ID with different details fails with
`SUBMISSION_CONFLICT`. Request-envelope IDs are only for matching replies and do
not provide this duplicate protection.

The desktop keeps an unconfirmed draft in memory, bound to the account and server
address/port. If the connection fails, reconnect and sign in to that same server
and account, then use **Retry submission**. It reuses the original submission ID.
There is no automatic retry. **Discard draft** removes only the local draft; it
does not cancel an order that the server may have saved. A rejected draft can be
discarded to enter different details.

Drafts do not survive closing the desktop application. After reopening, inspect
your saved orders and their details before creating a replacement for an uncertain
submission. Status-action replies can also be lost: refresh the order before
repeating an action. Two orders submitted with two different submission IDs are
two separate orders, even if their product and quantity match.

## Persistence and upgrade from inventory-only data

Orders and physical stock are saved together in `data/inventory.json`, now schema
version 2. There is no second order file to become inconsistent with stock. The
same repository monitor protects inventory changes and order transactions. A
successful atomic replacement publishes the entire new snapshot; a failed save
publishes neither the order change nor its stock effect.

Before upgrading, stop the server and back up the data directory. Existing schema
version 1 inventory is loaded into memory with empty orders and unchanged products,
batches and batch sequence. The first successful mutation writes version 2. An
unsupported or inconsistent store fails startup instead of resetting data.
The older inventory-only build cannot read version 2. Do not run an old server
against the upgraded store; reverting requires a compatible backup and loses
changes made after that backup.

The existing process lock, 8 MiB file limit and atomic-replacement filesystem
requirement still apply. There is a limit of 10,000 stored orders, including
completed and cancelled orders. Records are retained for status tracking and
submission deduplication; there is no purge/archive command yet. Atomic file
replacement is not a guarantee against every power-loss scenario.

## Scope

This increment handles reservations and order status. It does not add prices,
payments, delivery tracking, multi-product carts, automatic reservation expiry,
returns, or a full history of every status change. Pending orders retain their
stock until a permitted user cancels or fulfils them. The stored record includes
creation time and the most recent update time/account.

## Verification

Run `mvn clean verify`. Tests cover legacy-data upgrade, restart persistence,
reservation accounting, FIFO fulfilment, cancellation, role and ownership checks,
concurrent order/withdrawal requests, concurrent submission retries, fulfil/cancel
races, failed saves, corrupted reservations, bounded protocol pages and real TLS
clients. A separate desktop smoke check exercises the actual Swing dialogs and
status controls against the packaged application.
