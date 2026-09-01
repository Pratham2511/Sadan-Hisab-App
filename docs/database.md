# Database

The Room DB contains several interdependent tables:
1. **TenantEntity:** Master records containing tenant name, room number, and phone number.
2. **MonthlyLedgerEntity:** One record per tenant per month, recording the status of that month's rent.
3. **PaymentEntity:** Immutable transaction record of a payment event.
4. **PaymentAllocationEntity:** Relational mapping linking exactly how many rupees of a `PaymentEntity` satisfied a specific `MonthlyLedgerEntity`.
5. **RentChangeEntity:** Historical tracking mapping exactly when a tenant's rent changed, allowing historical ledger months to maintain their original required rents rather than blindly multiplying the new rent.
