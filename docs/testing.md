# Testing

Testing in Sadan strictly avoids the use of real production data.

## Execution
Run `./gradlew testDebugUnitTest` to execute the suite.

## Coverage
Unit tests (`PaymentLogicTest.kt`) cover:
- Exact payment allocations against historical gaps.
- Verifying deletion cascades (zeroing allocations correctly restores month statuses).
- Partial payments accurately transitioning a month to `PARTIALLY_PAID`.
- Edge cases preventing over-allocating money to a month past its due requirement.
