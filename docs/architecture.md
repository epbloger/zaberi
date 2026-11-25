# Architecture

- MainActivity owns the safe viewport, navigation and shared screen state.
- Screen files separate orders, editing, details, calendar, statistics and settings. UiComponents contains shared formatting and controls.
- Model owns database transactions, form submission state and post-commit notification work. The form closes only after persistence succeeds. Repeated submission with the same unchanged order ID is idempotent.
- OrderValidation defines shared write/import invariants; the database schema and migrations remain unchanged.
- PickupMap and MapLocation isolate map/network/location work. OptionalAds owns the banner lifetime.
- Tests cover domain rules, storage/migrations, shared validation and safe-area UI bounds. CI is the authoritative Android compilation and device test, not a claim of universal device compatibility.
