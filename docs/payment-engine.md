# Payment Engine & Allocator

The `PaymentAllocator` ensures that a single payment can span multiple months, and that a single month can absorb multiple partial payments over time without losing accounting precision.

## Allocation Algorithm
1. The engine fetches all `UNPAID` or `PARTIALLY_PAID` months for the tenant.
2. It sorts them chronologically.
3. The payment amount cascades downwards through the months, satisfying each month up to its remaining balance before overflowing to the next chronological unpaid month.
4. If an overpayment occurs (more money than all due rent), it remains as an unallocated buffer on the payment record for manual adjustment or next month's allocation.
