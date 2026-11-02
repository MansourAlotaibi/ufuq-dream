# Privacy

Ufuq Dream's application requests only the normal INTERNET permission for explicitly enabled weather and includes no advertising, analytics, account or third-party networking SDK. It does not send greetings,usage or local media to a server. Optional saved weather selection sends only the chosen city-centre coordinates to the provider,as detailed below.

Preferences (including custom greeting text) and user-selected videos and generated local posters are stored in app-private internal storage. The current app disables Android backup through its manifest; manufacturer-specific transfer behavior has not been certified. Uninstalling removes app-private data. There is no in-app cloud sync or export.

Photo credit links are displayed as text; the app does not fetch their contents. Android Studio/Gradle use the network to obtain development dependencies; this is separate from runtime behavior. Debug builds can emit decoder state/error logs for development. Do not post unrestricted logcat or preference backups in public issues.

The GitHub project and other external services have their own privacy policies. No claim of universal behavior across all Android TV vendors is made.

The scoped Android document picker grants access only to the selected video. The app copies it internally and does not retain a persistent URI grant or request broad storage access. Import does not publish or upload the file. The original selected file is not edited. A document provider may independently use network access. See docs/LOCAL-VIDEO.md.

## 0.9 development: optional smart information
Prayer calculations are offline. Both prayer and weather widgets default off and are staged until Save. Weather uses HTTPS requests directly to api.open-meteo.com only while the Dream/Studio is visible and the selected city was previously saved as enabled. The provider necessarily receives IP address and the selected city-centre coordinates,not a measured device location. Provider policies: https://open-meteo.com/en/terms . No location permission,accounts,API keys,analytics,ad SDK,background tracking or hidden polling is added. City selection/C/F are local preferences; latest weather is cached in app-private preferences. Disabling weather stops new requests; canceled in-flight I/O can take finite time to unwind. Staged city/toggle changes use cached or explicitly labeled sample data and do not silently grant network consent.
