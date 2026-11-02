# فيديوك المحلي / Your own video

The public APK includes 13 licensed Saudi photographs and the working hardware-video engine. **No cinematic video file is bundled.** The privately accepted 85-second clip is not owned by this project. Its Pexels license restricts redistribution through stock/wallpaper platforms; explicit permission for this public screensaver remains unresolved. Neither personal possession nor free download establishes redistribution rights. Obtain written permission covering downloadable APK/source distribution, modification/transcoding, attribution and any audio before shipping that clip. See https://www.pexels.com/license/ . Pending Adobe clips also remain excluded; no license was acquired.

## بدون ADB
من المظهر اختر «إضافة فيديو من ملفاتك»، وأكّد أن لك حق استخدامه. اختر ملفك من منتقي مستندات Android. يحتفظ التطبيق بنسخة خاصة ولا يرفعها. قد لا تتضمن بعض تلفزيونات Android منتقي مستندات؛ يظهر تنبيه ولا يتغير شيء. يمكن استخدام مزوّد مستندات موثوق متوافق مع التلفزيون؛ لا يُطلب تثبيته تلقائيًا.

- MP4 with a single H.264 video track; no audio or additional tracks.
- Landscape 16:9, up to 1920×1080 and 30 fps, 8–180 seconds; at most 128 MiB per file, 12 local clips.
- Reserve at least 160 MiB free internal storage before import. Local copies use internal app storage, not adopted-storage migration.
- Hardware AVC decoder required; older devices use a conservative vendor allowlist. Unsupported formats fail safely; the app does not transcode files.
- Imports are placed in All/Automatic, not misrepresented as AlUla/Diriyah/Riyadh. Choose Automatic category, then Motion or Photos + Motion and explicitly Save. Fixed selection is also supported.
- The import is an explicit library action. Canceling display changes does not remove an imported file; importing does not silently change saved display preferences.
- Copies contain the user's chosen file, potentially its original metadata. They are never bundled in later public APKs, uploaded, or exposed through an exported provider. Uninstalling removes them; no import/export or in-app deletion library is promised in this RC.
- Canceling the picker changes nothing. Invalid files leave the old catalog intact. Import runs off the UI thread; at most one poster frame (640×360) is created and recycled. On Activity destruction the worker is interrupted. A killed process can leave an unreferenced temporary file; no automatic deletion of user library files is performed.

A system document provider can use its own network connection; Ufuq itself has no Internet permission. A non-responsive provider may delay background import; this must be tested on real TVs. Host-side tests cannot validate native media extraction, codecs, picker availability or seamless loops.
