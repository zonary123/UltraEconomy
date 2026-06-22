# Changelog

## [1.2.0] - 2026-06-23

### Added

- Added /ultraeconomy reset command to reset a player's balance.

### Changed

- Project structure and dependencies updated.

## [1.1.0] - 2026-05-02

### Added

- Backup system for economy data.
- Discord webhook notifications for transactions.
- More detailed transaction history with advanced filtering.
- Web dashboard view with statistics and management tools.

## [1.0.1] - 2026-05-02

### Added

- Cached account retrieval method to improve lookup performance.
- Command cooldown check in PayCommand to prevent command spam.
- Payment validation in PayCommand to prevent negative or zero transactions.
- Enhanced transaction state tracking in MongoDBClient.
- Improved account retrieval logic with better caching mechanisms.

### Changed

- Refactored transaction methods in ImpactorAccountMixin to use builder pattern for better maintainability.
- Enhanced chart rendering stability and improved currency selection handling in web UI.
- Refactored various components to improve code consistency and logging functionality.
- Updated Gradle wrapper to version 9.3.1 and fabric-loom plugin to version 1.14.10.
- Improved build.gradle with enhanced HASH_ID handling and GitHub Actions fallback support.
- Streamlined connection logic in DatabaseFactory by removing redundant null checks.
- Simplified balance management methods in BeconomyServiceMixin for cleaner code.
- Refactored account disconnection logic with improved safety checks.
- Optimized account saving on player quit by using cached account retrieval.
- Enhanced MongoDBClient connection management with atomic state tracking.

### Fixed

- Fixed import statement for BlanketEconomyAPI in BeconomyServiceMixin.
- Fixed transaction history processing in MongoDBClient to ensure proper server readiness checks.
- Fixed transaction handling to ensure server is running before processing.
- Improved transaction processing checks to prevent race conditions.
- Fixed null check in account saving to prevent potential NPE.

## [1.0.0] - 2025-12-01

### Added

- Initial release of **UltraEconomy**.
- Basic economy system with coins and shop.
- Integration with **Modrinth** and **Discord** notifications.
- Initial setup for user permissions and roles.
- Added web page. This need activate in the config. (http://yourserver:port).
- Improved the migration system from previous economy mods.
- Improvements in web interface for better user experience.
- Added commands cooldown system.

### Changed

- Project structure and dependencies updated.
- Gradle build optimizations.

### Fixed

- Minor bug fixes in user data saving.
- Fixed Injection issue in Impactor Mixins.
- Fixed SQLite logic when player is offline.
- Fixed issue with transaction history not processing correctly.
