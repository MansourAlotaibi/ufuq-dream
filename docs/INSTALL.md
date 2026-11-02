# التثبيت / Installation

## العربية
1. حمّل APK من إصدار GitHub الرسمي فقط، وقارن SHA-256 بملف SHA256SUMS وبصمة شهادة التوقيع المنشورة.
2. انقل APK إلى تلفزيون Android TV / Google TV (Android 9 أو أحدث) بالطريقة المعتادة أو افتح صفحة الإصدار من متصفح التلفزيون. استخدم مثبّت APK موثوقًا. قد يطلب Android منك السماح لهذا المثبّت تحديدًا بتثبيت تطبيقات غير معروفة؛ ألغِ السماح بعد التثبيت إن رغبت.
3. افتح «أُفُق» من التطبيقات. اختر الثيم والمشهد والوقت؛ أكّد «حفظ التغييرات». المعاينة لا تحفظ اختياراتك تلقائيًا.
4. من إعدادات التلفزيون: تفضيلات الجهاز ← شاشة التوقف (قد تسمى Daydream أو Ambient Mode) ← أُفُق. استخدم معاينة النظام للاختبار. بعض أجهزة Google TV تحصر اختيار شاشة التوقف؛ التطبيق لا يتجاوز قيود الشركة.
5. لا تحتاج Android Studio أو ADB للاستخدام العادي. لا تغيّر المشغّل أو بيانات التطبيقات أو التخزين المتبنّى.

**نسخ التطوير السابقة:** APK العام يحمل توقيع إنتاج جديدًا؛ لا يمكنه تحديث APK debug بتوقيع مختلف. لا تحذف نسخة قائمة ولا تمسح بياناتها لتجاوز الخطأ. احتفظ بها وراجع [دليل الترقية](UPGRADE-ROLLBACK.md).

## English
Download the APK and SHA256SUMS from the official GitHub Release. Verify the checksum and published signing certificate fingerprint. Transfer/open the APK using your TV's trusted installer; Android may require per-installer permission for unknown apps. Open Ufuq Dream, customize and explicitly Save. Select Ufuq in the TV's native Screensaver / Daydream settings; names and availability vary by manufacturer. ADB and Android Studio are not required. The app never replaces a launcher or selects itself automatically.

Minimum Android 9 / API 28; TV/landscape and D-pad. Mi Box S first-generation baseline was physically accepted. This new release candidate's file picker and production-signed APK still require physical-TV acceptance. Modern Google TV and long-run testing are not certified.
