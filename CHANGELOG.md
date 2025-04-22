## [1.0.0] - 2025-04-21

### Added
- iOS: Location permission is now automatically requested on plugin initialization instead of app init.
- Android: Added support for Android 10+ background location permissions.
    - Added required permissions to `AndroidManifest.xml`:
        - `ACCESS_FINE_LOCATION`
        - `ACCESS_COARSE_LOCATION`
        - `ACCESS_BACKGROUND_LOCATION` (Android 10+)
    - Handles runtime permission requests for background location (Android 11+ compliant).

### Changed
- Initialization behavior: LocationManager now starts as soon as the plugin is loaded, prompting the OS to show the permission dialog instead of immediately on app start (iOS).
- Improved internal permission handling logic for Android 11+ (API level 30 and above).

### Notes
- Developers should ensure location permission is handled gracefully in their app logic.
- iOS apps must include both `NSLocationWhenInUseUsageDescription`,  `NSLocationAlwaysAndWhenInUseUsageDescription`, `NSLocationAlwaysUsageDescription` in `Info.plist` to prevent crashes.
- Android apps targeting API level 30+ must explicitly request background location permission after requesting fine location.

