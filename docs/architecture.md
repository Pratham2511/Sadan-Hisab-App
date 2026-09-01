# Architecture Overview

Sadan is built using a modern Android offline-first architecture:
- **UI:** Jetpack Compose natively utilizing Material 3.
- **State Management:** Jetpack ViewModel + Kotlin Coroutines (StateFlow).
- **Data Layer:** Room Database managing SQLite interactions completely asynchronously.
- **Business Logic:** Encapsulated within domain engines (e.g. `LedgerEngine` and `PaymentAllocator`).

This separation of concerns ensures UI is strictly a reflection of the single source of truth in the database.
