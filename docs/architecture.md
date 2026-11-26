# Architecture

- MainActivity owns the safe viewport, navigation and shared screen state.
- Screen files separate orders, editing, details, calendar, statistics and settings. UiComponents contains shared formatting and controls.
- Model owns database transactions, form submission state and post-commit notification work. The form closes only after persistence succeeds. Repeated submission with the same unchanged order ID is idempotent.
- OrderValidation defines shared write/import invariants; the database schema and migrations remain unchanged.
- PickupMap and MapLocation isolate map/network/location work. OptionalAds owns the banner lifetime.
- Tests cover domain rules, storage/migrations, shared validation and safe-area UI bounds. CI is the authoritative Android compilation and device test, not a claim of universal device compatibility.

## Технологии и сборка

- Kotlin, Jetpack Compose, Room, WorkManager, Android SDK 35, JDK 17, Gradle 8.9.
- Рекламная интеграция: Yandex Mobile Ads SDK. Подробности в [документе о рекламе](../app/src/main/assets/legal/ads.md).
- [Проверки и сборки](https://github.com/epbloger/zaberi/actions), [история разработки](https://github.com/epbloger/zaberi/commits/release-1.0-dev/), [исходники сайта](https://github.com/epbloger/zaberi/tree/gh-pages).
- Доступность исходников не означает предоставление отдельной лицензии на их распространение или использование.
