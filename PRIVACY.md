# Privacy

Ufuq Dream's application requests no permissions and includes no advertising, analytics, account or networking SDK. It does not send preferences, greetings, usage or local media to a server.

Preferences (including custom greeting text) and user-selected videos and generated local posters are stored in app-private internal storage. The current app disables Android backup through its manifest; manufacturer-specific transfer behavior has not been certified. Uninstalling removes app-private data. There is no in-app cloud sync or export.

Photo credit links are displayed as text; the app does not fetch their contents. Android Studio/Gradle use the network to obtain development dependencies; this is separate from runtime behavior. Debug builds can emit decoder state/error logs for development. Do not post unrestricted logcat or preference backups in public issues.

The GitHub project and other external services have their own privacy policies. No claim of universal behavior across all Android TV vendors is made.

The scoped Android document picker grants access only to the selected video. The app copies it internally and does not retain a persistent URI grant or request broad storage access. Import does not publish or upload the file. The original selected file is not edited. A document provider may independently use network access. See docs/LOCAL-VIDEO.md.
