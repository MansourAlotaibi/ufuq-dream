package com.ufuq.dream

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*

/** Consumer studio: three groups, one large renderer, one explicit save transaction. */
class SettingsActivity : Activity() {
    private lateinit var repository: SettingsRepository
    private lateinit var ui: SettingsUi
    internal lateinit var draft: SettingsDraft
    private lateinit var root: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var studio: PreviewStudio
    private lateinit var feedback: TextView
    private lateinit var help: TextView
    private lateinit var actions: LinearLayout
    private lateinit var save: TextView
    private val tabs = mutableListOf<View>()
    private var firstRow: View? = null
    private var section = 0
    private var rebuilding = false
    private val thumbs = LinkedHashMap<String, Bitmap>()
    private val dialogs=mutableSetOf<Dialog>()
    private data class DialogFocus(val view:View?,val section:Int)
    private val dialogFocus=mutableMapOf<Dialog,DialogFocus>()
    private var destroying=false
    private fun newDialog():Dialog {
        val dialog=Dialog(this,R.style.UfuqDialogTheme)
        dialogs.add(dialog);dialogFocus[dialog]=DialogFocus(currentFocus?:root.findFocus(),section)
        dialog.setOnDismissListener{dialogDismissed(dialog)}
        return dialog
    }
    private fun dialogDismissed(dialog:Dialog){
        val saved=dialogFocus.remove(dialog);dialogs.remove(dialog)
        if(destroying||isFinishing||saved==null||saved.section!=section||dialogs.any{it.isShowing})return
        // OnDismiss is already delivered by the UI handler after the selection callback.
        // Never enqueue another restoration that could undo subsequent remote navigation.
        val anchor=saved.view
        val replacement=anchor?.tag?.let{root.findViewWithTag<View>(it)}
        val original=anchor?.takeIf{it.isAttachedToWindow&&it.visibility==View.VISIBLE}
        (replacement?:original?:tabs[section]).requestFocus()
    }
    private val versionLabel by lazy {
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }
    private val videoPickerAvailable by lazy { VideoPicker.resolve(this)!=null }
    private fun tr(value:String)=AppLanguage.text(value,draft.current.language)
    private val canMotion get() = MediaLibrary.clips.isNotEmpty() && MotionSupport.decoder()!=null
    private val importWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var importing = false
    private val groupNames = listOf("المظهر", "الساعة والتاريخ", "العبارة", "المعلومات الذكية")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        repository = SettingsRepository(this)
        draft = SettingsDraft(repository.load())
        // A stale saved-instance parcel from a previous APK must not block startup.
        try { (state?.getSerializable("draft") as? DisplaySettings)?.let { draft.restore(it) } }
        catch (_: RuntimeException) { /* Saved preferences remain authoritative after upgrade. */ }
        section = state?.getInt("section")?.coerceIn(0, 3) ?: 0
        ui = SettingsUi(this).apply { language=draft.current.language }
        buildScreen()
        showSection()
        tabs[section].requestFocus()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putSerializable("draft", draft.current)
        state.putInt("section", section)
        super.onSaveInstanceState(state)
    }

    private fun buildScreen() {
        ui.language=draft.current.language
        ui.typography=draft.current
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppLanguage.direction(draft.current.language)
            setPadding(ui.dp(32), ui.dp(20), ui.dp(32), ui.dp(16))
        }
        root.addView(ui.text("أُفُق   /   هدوء المكان", 25f), LinearLayout.LayoutParams(-1, ui.dp(42)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(controls, LinearLayout.LayoutParams(0, -1, .40f).apply { marginEnd = ui.dp(24) })
        val tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(tabBar, LinearLayout.LayoutParams(-1, ui.dp(58)).apply { bottomMargin = ui.dp(12) })
        groupNames.forEachIndexed { index, name ->
            val tab = button(name, "section_$index") { firstRow?.requestFocus() }
            tab.id = View.generateViewId()
            tab.textSize = 13f
            tab.setOnFocusChangeListener { _, focus ->
                tab.background = ui.surface(focus, section == index)
                if (focus && !rebuilding && section != index) { section = index; showSection() }
            }
            tabs.add(tab)
            tabBar.addView(tab, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index < 3) marginEnd = ui.dp(5) })
        }
        val scroll = ScrollView(this).apply { isFocusable = false; isFillViewport = false }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        controls.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val presentation = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        body.addView(presentation, LinearLayout.LayoutParams(0, -1, .60f))
        studio = PreviewStudio(this) { draft.current }.apply { tag = "studio" }
        presentation.addView(studio, LinearLayout.LayoutParams(-1, -2))
        feedback = ui.text("معاينة حيّة · إعداداتك المحفوظة", 15f, true).apply { maxLines = 2; setPadding(0, ui.dp(12), 0, 0) }
        presentation.addView(feedback, LinearLayout.LayoutParams(-1, ui.dp(52)))
        actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; visibility = View.GONE }
        save = button("حفظ التغييرات", "save") { persist(); updateUi("حُفظت إعداداتك") }
        actions.addView(save, LinearLayout.LayoutParams(ui.dp(210), ui.dp(44)).apply { marginEnd = ui.dp(10) })
        actions.addView(button("إلغاء التغييرات", "cancel") {
            draft.revert(); rebuildStudio(); updateUi("أُلغيت التغييرات؛ تعرض المعاينة إعداداتك المحفوظة"); tabs[section].requestFocus()
        }, LinearLayout.LayoutParams(ui.dp(180), ui.dp(44)))
        root.addView(actions, LinearLayout.LayoutParams(-1, ui.dp(52)))
        help = ui.text("أُفُق $versionLabel  ·  اختر ما يناسب بيتك؛ لا يُحفظ شيء قبل التأكيد.", 13f, true).apply { maxLines = 2 }
        root.addView(help, LinearLayout.LayoutParams(-1, ui.dp(34)))
        help.isFocusable=true;help.isClickable=true
        help.contentDescription=tr("الصور وحقوق الاستخدام")
        help.setOnFocusChangeListener { _,focus ->help.background=ui.surface(focus);if(focus)help.text=tr("أُفُق $versionLabel · الصور وحقوق الاستخدام · اضغط موافق") }
        help.setOnClickListener { photoCredits() }
        setContentView(root)
    }

    private fun button(title: String, name: String, click: () -> Unit) = ui.text(title, 17f).apply {
        id = View.generateViewId(); tag = name; isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        gravity = Gravity.CENTER; setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7))
        background = ui.surface(false)
        setOnFocusChangeListener { _, focused -> background = ui.surface(focused) }
        setOnClickListener { click() }
    }

    private fun showSection(restore: String? = null) {
        rebuilding = true
        tabs[section].requestFocus()
        rows.removeAllViews(); firstRow = null
        val s = draft.current
        ui.language=s.language
        ui.typography=s
        palette()
        when (section) {
            0 -> {
                row("language","اللغة","اختر لغة التطبيق؛ الأرقام تتبع اللغة تلقائيًا.",if(s.language=="en")"English" else "العربية") {
                    choose("اللغة",listOf("العربية","English"),if(s.language=="en")1 else 0){index ->
                        draft.update(draft.current.copy(language=if(index==1)"en" else "ar",latinDigits=index==1))
                        rebuildStudio("language");updateUi("تغيّرت لغة المعاينة؛ احفظ للتأكيد")
                    }
                }
                if(s.language=="en") row("font_en","الخط الإنجليزي","خط اللغة المختارة؛ يتغير في المعاينة قبل الحفظ.",UfuqFonts.name(s.fontEnglish,"en")) { fontDialog("en") }
                else row("font_ar","الخط العربي","خط اللغة المختارة؛ يتغير في المعاينة قبل الحفظ.",UfuqFonts.name(s.fontArabic,"ar")) { fontDialog("ar") }
                row("theme", "الثيم", "ألوان النص والواجهة، مستقلة عن الصور.", themeName(s.theme)) {
                    choose("الثيم",listOf("الأخضر السعودي","أرض الصحراء","ليل الرياض"),s.theme){index->
                        change("theme","الثيم: ${themeName(index)}"){copy(theme=index)}
                    }
                }
                row("scene", "المشهد", "اختر مجموعة الصور؛ تلقائي يجمعها كلها.", sceneName(s.sceneChoice)) {
                    choose("اختر المشهد", listOf("تلقائي", "الدرعية", "العلا", "الرياض"), s.sceneChoice + 1, true) {
                        change("scene", "المشهد: ${sceneName(it - 1)}") { copy(sceneChoice = it - 1,imageId=BackgroundRepository.first(it-1).id,fixedAssetId="") }
                    }
                }
                if(canMotion){
                val kinds=listOf("صور فقط","خلفيات متحركة فقط","صور وخلفيات متحركة")
                val sceneHasMotion=MediaLibrary.clips.any{s.sceneChoice<0||it.category==s.sceneChoice}
                row("background_type","نوع الخلفية",if(sceneHasMotion)"صور هادئة أو حركة سينمائية؛ بلا صوت." else "لا توجد مقاطع لهذه المجموعة؛ ستظهر صورها.",if(sceneHasMotion)kinds[s.backgroundType] else "صور فقط لهذه المجموعة") {
                    choose("نوع الخلفية",kinds,if(sceneHasMotion)s.backgroundType else 0,available=if(sceneHasMotion)null else setOf(0)){index->
                        change("background_type","نوع الخلفية: ${kinds[index]}"){copy(backgroundType=index,fixedAssetId="")}
                    }
                }
                }
                row("image_mode","عرض الخلفيات","تدوير هادئ أو خلفية تختارها.",if(s.imageMode==0 && s.rotationMinutes>0)"تتغيّر تلقائيًا" else "خلفية محددة") {
                    choose("عرض الصور",listOf("تتغيّر تلقائيًا","خلفية محددة"),if(s.imageMode==0 && s.rotationMinutes>0)0 else 1){index->
                        change("image_mode","عرض الصور: ${if(index==0)"تلقائي" else "صورة محددة"}"){copy(imageMode=index,rotationMinutes=if(index==0 && rotationMinutes==0)3 else rotationMinutes)}
                    }
                }
                if(s.imageMode==1 || s.rotationMinutes==0) row("image","الخلفية","من المجموعة المختارة.",MediaLibrary.selected(s,0).title){pickImage()}
                if(s.imageMode==0 && s.rotationMinutes>0) row("interval","تغيير الخلفية","٣ دقائق توازن جميل للمشاهدة اليومية.",intervalName(s.rotationMinutes)) {
                    val values=listOf(1,3,5)
                    choose("تغيير الصورة",values.map{intervalName(it)},values.indexOf(s.rotationMinutes)){index->
                        change("interval","تغيير الصورة: ${intervalName(values[index])}"){copy(rotationMinutes=values[index])}
                    }
                }
                row("night", "خفض السطوع ليلاً", "يخفّت الصورة والنص تدريجياً بعد العاشرة مساءً.", if (s.nightDim) "مفعّل" else "متوقف") {
                    change("night", if (s.nightDim) "أُوقف خفض السطوع الليلي" else "فُعّل خفض السطوع الليلي") { copy(nightDim = !nightDim) }
                }
                if(videoPickerAvailable && (s.backgroundType!=0 || MediaLibrary.clips.isEmpty())) row("import_video", "إضافة فيديو من ملفاتك", "MP4 صامت · H.264 · حتى ١٠٨٠p و٣ دقائق و١٢٨ MiB.", if (importing) "جارٍ الفحص" else "اختيار") {
                    if (!importing) choose("لدي حق استخدام الفيديو محليًا", listOf("رجوع", "اختيار فيديو"), 0) {
                        if (it == 1) {
                            val picker=VideoPicker.resolve(this)
                            if(picker==null) {
                                android.app.AlertDialog.Builder(this)
                                    .setTitle(tr("اختيار الفيديو غير متاح"))
                                    .setMessage("هذا التلفزيون لا يتضمن منتقي ملفات فعليًا. يلزم تطبيق موثوق يدعم اختيار ملفات الفيديو. لم يتغير أي فيديو أو إعداد؛ المقاطع المضافة سابقًا ما زالت متاحة.")
                                    .setPositiveButton(tr("فهمت"),null).show()
                            } else try { startActivityForResult(picker,730) }
                            catch (_: android.content.ActivityNotFoundException) { updateUi("تعذّر فتح منتقي الملفات؛ لم يتغير شيء") }
                            catch (_: SecurityException) { updateUi("منتقي الملفات لا يسمح بالوصول؛ لم يتغير شيء") }
                        }
                    }
                }
            }
            1 -> {
                row("time", "صيغة الوقت", "تُطبّق على الساعة والصلاة وموعد المباراة.", if (s.hour24) "٢٤ ساعة" else "١٢ ساعة") {
                    choose("صيغة الوقت", listOf("١٢ ساعة", "٢٤ ساعة"), if (s.hour24) 1 else 0) {
                        change("time", "صيغة الوقت: ${if (it == 1) "٢٤ ساعة" else "١٢ ساعة"}") { copy(hour24 = it == 1) }
                    }
                }
                row("size", "حجم الساعة", "واضحة من مكان جلوسك.", if (s.size == 2) "كبير" else "متوسط") {
                    choose("حجم الساعة", listOf("متوسط", "كبير"), s.size - 1) {
                        change("size", "حجم الساعة: ${if (it == 1) "كبير" else "متوسط"}") { copy(size = it + 1) }
                    }
                }
                row("position","مكان الساعة","الساعة في جهة، والمعلومات في الجهة المقابلة.",ClockPlacement.label(s.clockPosition)){positionDialog()}
                row("gregorian", "التاريخ الميلادي", "اليوم والتاريخ بلغة التطبيق.", if (s.gregorian) "ظاهر" else "مخفي") {
                    change("gregorian", if (s.gregorian) "أُخفي التاريخ الميلادي" else "ظهر التاريخ الميلادي") { copy(gregorian = !gregorian) }
                }
                row("hijri", "التاريخ الهجري", "أم القرى؛ قد يختلف عن الرؤية.", if (s.hijri) "ظاهر" else "مخفي") {
                    change("hijri", if (s.hijri) "أُخفي التاريخ الهجري" else "ظهر التاريخ الهجري") { copy(hijri = !hijri) }
                }
                row("seconds","الثواني","تظهر بحجم أصغر بجانب الساعة.",if(s.seconds)"ظاهرة" else "مخفية") {
                    change("seconds",if(s.seconds)"أُخفيت الثواني" else "ظهرت الثواني"){copy(seconds=!seconds)}
                }

            }
            2 -> {
                val kinds = listOf("تلقائية", "عبارة سعودية", "نص خاص", "مخفية")
                row("greeting", "العبارة", "لمسة ترحيب تناسب البيت.", kinds[s.greetingMode]) {
                    choose("كيف تحب الترحيب؟", kinds, s.greetingMode) { index ->
                        if (index == 2) customText()
                        else change("greeting", "العبارة: ${kinds[index]}") { copy(greetingMode = index) }
                    }
                }
                if (s.greetingMode == 1) row("preset", "اختر عبارتك", "ترحيب سعودي بسيط.", GreetingText.presets[s.greetingPreset]) {
                    choose("عبارتك", GreetingText.presets, s.greetingPreset) { index ->
                        change("preset", "العبارة: ${GreetingText.presets[index]}") { copy(greetingPreset = index) }
                    }
                }
                if (s.greetingMode == 2) row("custom", "نصك الخاص", "حتى ٤٠ حرفاً؛ عربي أو إنجليزي.", if (s.greetingText.isBlank()) "اكتب" else "تعديل") { customText() }
            }
            3 -> {
                val smart=s.smart
                fun city(key:String,title:String,value:String,prayer:Boolean) {
                    row(key,title,"مدينة تختارها؛ بلا إذن موقع.",com.ufuq.dream.smart.SaudiCities.find(value).arabic) {
                        val cities=com.ufuq.dream.smart.SaudiCities.all
                        choose(title,cities.map{it.arabic},cities.indexOfFirst{it.id==value}.coerceAtLeast(0)){index ->
                            change(key,"المدينة: ${cities[index].arabic}"){copy(smart=if(prayer)smart.copy(prayerCity=cities[index].id) else smart.copy(weatherCity=cities[index].id))}
                        }
                    }
                }
                row("prayers","مواقيت الصلاة","أوقات اليوم والعدّ التنازلي للصلاة القادمة.",if(smart.prayers)"مفعّلة" else "متوقفة") {
                    change("prayers","تغيّرت معاينة مواقيت الصلاة"){copy(smart=smart.copy(prayers=!smart.prayers))}
                }
                if(smart.prayers){
                    row("prayer_source","مصدر المواقيت","الخدمة تحسب المواقيت أيضًا؛ ليست جدولًا رسميًا معتمدًا.",if(smart.prayerSource==0)"عبر الإنترنت" else "دون إنترنت") {
                        choose("مصدر المواقيت",listOf("AlAdhan · يتصل بعد الحفظ","حساب محلي تقريبي"),smart.prayerSource){i -> change("prayer_source","تغيّر مصدر المواقيت؛ لن يُحفظ حتى تختار الحفظ"){copy(smart=smart.copy(prayerSource=i))} }
                    }
                    city("prayer_city","مدينة الصلاة",smart.prayerCity,true)
                    row("prayer_method","طريقة الحساب","اختر الطريقة المتبعة لديك؛ لا تتغير تلقائيًا.",if(smart.prayerMethod==0)"أم القرى" else "رابطة العالم الإسلامي") {
                        choose("حساب تقريبي؛ طابق جدول مسجدك",listOf("أم القرى · فجر ١٨٫٥°","رابطة العالم الإسلامي"),smart.prayerMethod){i -> change("prayer_method","تغيّرت طريقة حساب الصلاة"){copy(smart=smart.copy(prayerMethod=i))} }
                    }
                    row("asr","حساب العصر","ظل واحد للجمهور أو ظلان للمذهب الحنفي.",if(smart.hanafiAsr)"ظلان" else "ظل واحد") {
                        change("asr","تغيّر حساب العصر"){copy(smart=smart.copy(hanafiAsr=!smart.hanafiAsr))}
                    }
                }
                row("weather","الطقس","درجة الحرارة وحالة الجو للمدينة المختارة.",if(smart.weather)"مفعّل" else "متوقف") {
                    change("weather","المعاينة لا ترسل طلب طقس قبل حفظ الاختيار"){copy(smart=smart.copy(weather=!smart.weather))}
                }
                if(smart.weather){
                    city("weather_city","مدينة الطقس",smart.weatherCity,false)
                    row("weather_units","درجة الحرارة","تغيير الوحدة لا يجلب بيانات جديدة.",if(smart.fahrenheit)"فهرنهايت" else "مئوية") {
                        change("weather_units","تغيّرت وحدة الحرارة"){copy(smart=smart.copy(fahrenheit=!smart.fahrenheit))}
                    }
                    row("weather_refresh","تحديث الطقس","عندما تكون المعاينة أو شاشة التوقف ظاهرة فقط.","${smart.weatherRefreshMinutes} دقيقة") {
                        val minutes=listOf(30,60,180)
                        choose("تحديث الطقس",listOf("كل نصف ساعة","كل ساعة","كل ٣ ساعات"),minutes.indexOf(smart.weatherRefreshMinutes)){i -> change("weather_refresh","تغيّر تكرار التحديث"){copy(smart=smart.copy(weatherRefreshMinutes=minutes[i]))} }
                    }
                }
                val reflectionNames=listOf("مخفية","آية","حديث","آية وحديث","أذكار","آية وأذكار","حديث وأذكار","آية وحديث وأذكار")
                row("reflection","القرآن والحديث والأذكار","نصوص موثّقة دون إنترنت؛ تتناوب الأنواع المختارة في مساحة واحدة.",reflectionNames[smart.reflectionChoice]) {
                    choose("القرآن والحديث والأذكار",reflectionNames,smart.reflectionChoice){index ->
                        change("reflection","تغيّرت معاينة المحتوى"){copy(smart=smart.copy(reflectionMode=index,adhkar=false))}
                    }
                }
                val occasionIds=listOf(0,6,7,1,2,3,4,5)
                val occasions=listOf("مخفية","أقرب مناسبة تلقائيًا","مناسبة مخصصة","رمضان","عيد الفطر","عيد الأضحى","اليوم الوطني","يوم التأسيس")
                row("occasion","المناسبات","أقرب مناسبة تلقائيًا أو مناسبة تختارها؛ تظهر مع التاريخ.",occasions[occasionIds.indexOf(smart.occasion).coerceAtLeast(0)]) {
                    choose("المناسبات",occasions,occasionIds.indexOf(smart.occasion).coerceAtLeast(0)){index->
                        if(occasionIds[index]==7)customOccasion()
                        else change("occasion","تغيّرت معاينة المناسبة"){copy(smart=smart.copy(occasion=occasionIds[index]))}
                    }
                }
                if(smart.occasion==7)row("occasion_custom","تفاصيل المناسبة","الاسم والتاريخ الميلادي؛ يُحفظان بعد التأكيد.","تعديل") { customOccasion() }
                row("football_team","الفريق المفضل","مباراته القادمة من TheSportsDB؛ يتصل بعد حفظ اختيارك.",if(smart.football)com.ufuq.dream.smart.FootballTeams.find(smart.footballTeam)?.name(s.language).orEmpty() else "بدون") { pickFootballTeam() }
            }
        }
        if (restore != null) rows.findViewWithTag<View>(restore)?.requestFocus()
        rebuilding = false
        updateUi()
    }

    private fun row(key: String, title: String, description: String, value: String, click: () -> Unit) {
        val item = ui.row(title, description, value, { help.text = tr("أُفُق $versionLabel  ·  $it") }, click).apply {
            tag = key; id = View.generateViewId()
        }
        item.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) { tabs[section].requestFocus(); true } else false
        }
        // Long custom strings belong in the editor, not a narrow badge.
        val dependent=key in setOf("reading_updates","font_ar","font_en","image","interval","preset","custom","prayer_source","prayer_city","prayer_method","asr","weather_city","weather_units","weather_refresh")
        rows.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = ui.dp(if(dependent)6 else 8)
            if(dependent)marginStart=ui.dp(14)
        })
        if (firstRow == null) firstRow = item
    }

    private fun sceneName(value: Int) = listOf("تلقائي", "الدرعية", "العلا", "الرياض")[value + 1]
    private fun change(key: String, message: String, edit: DisplaySettings.() -> DisplaySettings) {
        draft.update(draft.current.edit())
        showSection(key)
        updateUi(message)
    }
    private fun rebuildStudio(restore:String?=null) {
        studio.stop();tabs.clear();buildScreen();showSection(restore);studio.start()
    }
    private fun palette() {
        // Automatic stays neutral even when its live image changes.
        ui.themeIndex = draft.current.theme
        root.setBackgroundColor(ThemeManager.get(ui.themeIndex).base)
        tabs.forEachIndexed { index, tab -> tab.background = ui.surface(tab.hasFocus(), index == section) }
    }
    private fun updateUi(message: String? = null) {
        if (message != null) feedback.text = tr(message)
        actions.visibility = if (draft.dirty) View.VISIBLE else View.GONE
        save.background = ui.surface(save.hasFocus(), true)
        val last = if(rows.childCount>0)rows.getChildAt(rows.childCount-1) else null
        tabs.forEach {it.nextFocusDownId=firstRow?.id ?: View.NO_ID}
        if(draft.dirty && last!=null) {
            last.nextFocusDownId=save.id
            save.nextFocusUpId=last.id
            root.findViewWithTag<View>("cancel").nextFocusUpId=last.id
        }
        UfuqFonts.apply(root,draft.current)
        studio.refresh()
    }
    private fun persist() {
        repository.save(draft.current)
        draft.update(repository.load())
        draft.markSaved()
        tabs[section].requestFocus()
    }

    /** Bounded native dialog; scrolls rather than exceeding the safe area. */
    private fun choose(title: String, labels: List<String>, selected: Int, scenes: Boolean = false, photos:List<BackgroundImage>?=null, available:Set<Int>?=null, commit: (Int) -> Unit) {
        val dialog = newDialog().apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = AppLanguage.direction(draft.current.language)
            setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16)); setBackgroundColor(ThemeManager.get(ui.themeIndex).base)
        }
        panel.addView(ui.text(title, 22f), LinearLayout.LayoutParams(-1, ui.dp(46)))
        var focused: View? = null
        labels.forEachIndexed { index, label ->
            val item = ui.row(label, if (scenes && index == 0) "صور من المجموعات الثلاث." else if (index == selected) "اختيارك الحالي" else "اضغط موافق للاختيار", if (index == selected) "✓" else "", {}) {
                dialog.dismiss(); commit(index)
            }.apply { tag = "choice_$index" }
            if(available!=null && index !in available){item.setOnClickListener(null);item.isEnabled=false;item.isFocusable=false;item.isClickable=false;item.alpha=.4f}
            if (scenes || photos!=null) {
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                val asset = if (index == 0) 0 else index - 1
                image.setImageBitmap(thumbnail(photos?.get(index)?.fileName ?: BackgroundRepository.first(asset).fileName))
                item.addView(image, 0, LinearLayout.LayoutParams(ui.dp(100), ui.dp(56)).apply { marginEnd = ui.dp(10) })
            }
            panel.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = if(index<labels.lastIndex)ui.dp(6) else 0 })
            if (index == selected) focused = item
        }
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),ui.dp(590),AppLanguage.direction(draft.current.language))
        focused?.requestFocus()
    }

    /** Decoded once per Activity; the picker used to re-decode four JPEGs on the UI thread every time it opened. */
    private fun thumbnail(asset: String): Bitmap? {
        require(asset.startsWith("personal:") || asset in CinematicCatalog.assets || BackgroundRepository.images.any{it.fileName==asset}) { "Unknown thumbnail" }
        thumbs[asset]?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize=if(asset.startsWith("personal:"))8 else 1; inScaled=false }
        val bitmap = try { (if(asset.startsWith("personal:"))PersonalMotionStore.openPoster(this,asset)else assets.open("thumbnails/$asset")).use { BitmapFactory.decodeStream(it,null,options) } }
            catch (_: java.io.IOException) { null } catch (_: IllegalArgumentException) { null } catch (_: OutOfMemoryError) { null }
        if(bitmap!=null && bitmap.allocationByteCount<=240*135*4){
            if(thumbs.size>=16)thumbs.remove(thumbs.keys.first())
            thumbs[asset]=bitmap
        }
        return bitmap
    }


    private fun themeName(value:Int)=listOf("الأخضر السعودي","أرض الصحراء","ليل الرياض")[value.coerceIn(0,2)]
    private fun intervalName(value:Int)=when(value){1->"دقيقة";3->"٣ دقائق · موصى به";5->"٥ دقائق";else->"ثابتة"}
    private fun pickImage(){
        val items=MediaLibrary.items(draft.current.sceneChoice,draft.current.backgroundType)
        val selected=MediaLibrary.selected(draft.current,0).id
        choose("اختر الخلفية",items.map{it.title+if(it.clip!=null)" · متحركة" else ""},items.indexOfFirst{it.id==selected}.coerceAtLeast(0),photos=items.map{it.photo}){index->
            change("image","الخلفية: ${items[index].title}"){copy(imageId=items[index].photo.id,fixedAssetId=items[index].id)}
        }
    }
    private fun pickFootballTeam() {
        val current=com.ufuq.dream.smart.FootballTeams.find(draft.current.smart.footballTeam)
        choose("الفريق المفضل",listOf("بدون","أندية سعودية","أندية عالمية"),if(!draft.current.smart.football)0 else if(current?.saudi==true)1 else 2){group ->
            if(group==0)change("football_team","أُخفيت مباريات الفريق"){copy(smart=smart.copy(football=false,footballTeam="none"))}
            else {
                val teams=com.ufuq.dream.smart.FootballTeams.all.filter{it.saudi==(group==1)}
                choose(if(group==1)"أندية سعودية" else "أندية عالمية",teams.map{it.name(draft.current.language)},teams.indexOfFirst{it.id==current?.id}.coerceAtLeast(0)){index ->
                    change("football_team","تغيّر الفريق المفضل؛ احفظ للتأكيد"){copy(smart=smart.copy(football=true,footballTeam=teams[index].id))}
                }
            }
        }
    }
    private fun photoCredits(){
        val data=org.json.JSONArray(assets.open("photo-provenance.json").bufferedReader().use{it.readText()})
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(ui.dp(24),ui.dp(16),ui.dp(24),ui.dp(16));setBackgroundColor(ThemeManager.get(ui.themeIndex).base)}
        panel.addView(ui.text("الصور وحقوق الاستخدام",24f))
        for(i in 0 until data.length()){
            val item=data.getJSONObject(i)
            val label=listOf(tr(item.getString("title")),item.optString("originalTitle"),item.getString("author"),item.getString("license"),item.getString("source"),item.getString("licenseUrl"),item.optString("publisherSource"),item.optString("conditions"),tr("تعديل: قصّ ١٦:٩ وتصغير وضغط الصورة.")).joinToString("\n")
            panel.addView(ui.text("",15f).apply{text=label;isFocusable=true;setPadding(0,ui.dp(12),0,ui.dp(12));setOnFocusChangeListener{_,focus->background=ui.surface(focus)}})
        }
        panel.addView(ui.text("الطقس: Open-Meteo · بيانات CC BY 4.0\nhttps://open-meteo.com/\nhttps://creativecommons.org/licenses/by/4.0/\nالخدمة المجانية للاستخدام غير التجاري.\nمواقيت الإنترنت: AlAdhan · https://aladhan.com/prayer-times-api\nأوقات محسوبة وليست جدول مسجد رسميًا.\nالحساب المحلي: Adhan Java 1.2.1 · Batoul Apps · MIT\nhttps://github.com/batoulapps/adhan-kotlin/tree/v1.2.1",15f).apply{isFocusable=true})
        panel.addView(ui.text("مصادر الآية والحديث والمباريات",22f))
        val reflectionEntries=com.ufuq.dream.reflection.ContentRepository.entries(this)
        val reflectionNotices=reflectionEntries.joinToString("\n\n"){it.source+"\n"+it.attribution+"\n"+it.sourceUrl+"\n"+it.licenseUrl+if(it.kind=="hadith")"\n"+it.notice else ""}+"\n\n"+reflectionEntries.first{it.kind=="quran"}.notice
        panel.addView(ui.text("",14f).apply{text=reflectionNotices;isFocusable=true})
        panel.addView(ui.text("المباريات: TheSportsDB · بيانات ومواعيد قابلة للتغيير. لا شعارات أو نتائج مباشرة.\nhttps://www.thesportsdb.com/\nhttps://www.thesportsdb.com/docs_terms_of_use.php\nالإصدار المجاني على GitHub يستخدم واجهة المزود العامة؛ النشر في متجر تطبيقات يتطلب اشتراكًا مدفوعًا.",14f).apply{isFocusable=true})
        panel.addView(ui.text("الخطوط · SIL Open Font License 1.1",22f))
        listOf("cairo","tajawal","ibm-plex-sans-arabic","readex-pro","almarai","lato").forEach { name ->
            val notice=assets.open("licenses/$name-OFL.txt").bufferedReader().use{it.readText()}
            panel.addView(ui.text("",14f).apply{text=notice;isFocusable=true})
        }
        val diagnostics=com.ufuq.dream.smart.SmartDiagnostics(this)
        panel.addView(ui.text("تفاصيل المصادر: الصلاة أوقات محسوبة وليست جدولًا رسميًا للمسجد.\nحالة الصلاة: ${diagnostics.detail("prayer")}\nحالة الطقس: ${diagnostics.detail("weather")}\nحالة المباريات: ${diagnostics.detail("football")}\nأوقات الصلاة تُحفظ لنحو ٣٠ يومًا. يُخفى الطقس إذا تجاوز عمر القراءة ٣ ساعات.",15f).apply{isFocusable=true})
        val dialog=newDialog().apply{requestWindowFeature(Window.FEATURE_NO_TITLE)}
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),(resources.displayMetrics.widthPixels*.8f).toInt(),AppLanguage.direction(draft.current.language))
    }

    private fun fontDialog(language:String) {
        val choices=UfuqFonts.choices(language)
        val id=if(language=="en")draft.current.fontEnglish else draft.current.fontArabic
        choose(if(language=="en")"الخط الإنجليزي" else "الخط العربي",choices.map{it.name},choices.indexOfFirst{it.id==id}.coerceAtLeast(0)) { index ->
            draft.update(if(language=="en")draft.current.copy(fontEnglish=choices[index].id) else draft.current.copy(fontArabic=choices[index].id))
            rebuildStudio(if(language=="en")"font_en" else "font_ar")
            updateUi("تغيّر الخط؛ احفظ للتأكيد")
        }
    }

    @android.annotation.SuppressLint("RtlHardcoded")
    private fun positionDialog(){
        val before=draft.current
        var confirmed=false
        val dialog=newDialog().apply{requestWindowFeature(Window.FEATURE_NO_TITLE)}
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(16))}
        panel.addView(ui.text("مكان الساعة",22f),LinearLayout.LayoutParams(-1,ui.dp(42)))
        panel.addView(ui.text("الساعة في جهة، والمعلومات في الجهة المقابلة.",14f,true),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(10)})
        fun stage(value:Int){draft.update(before.copy(clockPosition=value));updateUi("معاينة الموضع: ${ClockPlacement.label(value)} · لم يُحفظ بعد")}
        val values=listOf(-1,5,3)
        val buttons=values.map { value ->
            button((if(ClockPlacement.side(before.clockPosition)==value)"✓  " else "")+ClockPlacement.label(value),if(value<0)"position_auto" else "position_$value") {
                stage(value);confirmed=true;dialog.dismiss();showSection("position")
                updateUi("الموضع: ${ClockPlacement.label(value)} · احفظ التغييرات للتأكيد")
            }.apply {
                setOnFocusChangeListener{_,focus->background=ui.surface(focus,ClockPlacement.side(before.clockPosition)==value);if(focus)stage(value)}
                panel.addView(this,LinearLayout.LayoutParams(-1,ui.dp(54)).apply{bottomMargin=ui.dp(6)})
            }
        }
        buttons.forEachIndexed{index,view->view.nextFocusUpId=buttons[(index-1).coerceAtLeast(0)].id;view.nextFocusDownId=buttons[(index+1).coerceAtMost(2)].id;view.nextFocusLeftId=view.id;view.nextFocusRightId=view.id}
        dialog.setOnDismissListener{dialogDismissed(dialog);if(!confirmed&&!destroying){draft.update(before);showSection("position");updateUi("أُلغيت معاينة الموضع")}}
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),ui.dp(360),AppLanguage.direction(draft.current.language))
        dialog.window?.apply{setGravity((if(draft.current.language=="en")Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL);attributes=attributes.apply{horizontalMargin=.035f;dimAmount=0f}}
        buttons[values.indexOf(ClockPlacement.side(before.clockPosition)).coerceAtLeast(0)].requestFocus()
    }

    private fun customOccasion() {
        val dialog=newDialog().apply{requestWindowFeature(Window.FEATURE_NO_TITLE)}
        val before=draft.current
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;layoutDirection=AppLanguage.direction(before.language)
            setPadding(ui.dp(24),ui.dp(20),ui.dp(24),ui.dp(20))
            setBackgroundColor(ThemeManager.get(ui.themeIndex).base)
        }
        panel.addView(ui.text("مناسبة مخصصة",24f))
        panel.addView(ui.text("اسم المناسبة",16f,true))
        val name=EditText(this).apply {
            tag="occasion_name_editor";setSingleLine(true);textSize=21f;setTextColor(ui.ink)
            filters=arrayOf(InputFilter.LengthFilter(40));setText(before.smart.occasionName)
            textDirection=if(before.language=="en")View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL
        }
        panel.addView(name,LinearLayout.LayoutParams(-1,ui.dp(56)))
        panel.addView(ui.text("التاريخ الميلادي: سنة-شهر-يوم",16f,true))
        val date=EditText(this).apply {
            tag="occasion_date_editor";setSingleLine(true);textSize=21f;setTextColor(ui.ink)
            textDirection=View.TEXT_DIRECTION_LTR;filters=arrayOf(InputFilter.LengthFilter(10))
            hint="2027-02-22";setHintTextColor(ui.muted);setText(before.smart.occasionDate)
        }
        panel.addView(date,LinearLayout.LayoutParams(-1,ui.dp(56)))
        val error=ui.text("لن تُحفظ التغييرات قبل التأكيد.",15f,true).apply{tag="occasion_editor_error"}
        panel.addView(error)
        panel.addView(button("تم","occasion_done") {
            val clean=GreetingText.clean(name.text.toString())
            val parsed=com.ufuq.dream.reflection.Occasions.customDate(date.text.toString())
            if(clean.isBlank()||parsed==null){error.text=tr("اكتب اسمًا وتاريخًا صحيحًا مثل 2027-02-22");return@button}
            draft.update(draft.current.copy(smart=draft.current.smart.copy(occasion=7,occasionName=clean,occasionDate=parsed.toString())))
            dialog.dismiss();showSection("occasion_custom");updateUi("تغيّرت معاينة المناسبة")
        },LinearLayout.LayoutParams(-1,ui.dp(48)))
        dialog.setOnDismissListener {
            dialogDismissed(dialog)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(name.windowToken,0)
        }
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),ui.dp(560),AppLanguage.direction(before.language))
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        name.requestFocus()
    }

    private fun customText() {
        val before = draft.current
        val dialog = newDialog().apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        var confirmed = false
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = AppLanguage.direction(draft.current.language)
            setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20)); setBackgroundColor(ThemeManager.get(ui.themeIndex).base)
        }
        panel.addView(ui.text("عبارتك الخاصة", 24f))
        val count = ui.text("حتى ٤٠ حرفاً", 15f, true)
        val editor = EditText(this).apply {
            tag = "greeting_editor"; textSize = 23f; setTextColor(ui.ink); setSingleLine(true)
            textDirection = if(draft.current.language=="en")View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
                val candidate = dest.substring(0, dstart) + source.subSequence(start, end) + dest.substring(dend)
                val available = GreetingText.LIMIT - (dest.substring(0, dstart) + dest.substring(dend)).codePointCount(0, dest.length - (dend - dstart))
                val input = source.subSequence(start, end).toString()
                if (candidate.codePointCount(0, candidate.length) <= GreetingText.LIMIT) null
                else if (available <= 0) "" else input.substring(0, input.offsetByCodePoints(0, available.coerceAtMost(input.codePointCount(0, input.length))))
            })
            setText(before.greetingText); setSelection(text.length)
        }
        panel.addView(editor, LinearLayout.LayoutParams(-1, ui.dp(64)))
        panel.addView(count)
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, beforeCount: Int, added: Int) {
                val text = GreetingText.clean(s.toString())
                draft.update(draft.current.copy(greetingMode = 2, greetingText = text))
                count.text = tr("${text.codePointCount(0, text.length)} / ٤٠")
                updateUi("تعاين الآن عبارتك الخاصة؛ لم تُحفظ بعد")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val confirm = button("تم", "custom_done") {
            val value = GreetingText.clean(editor.text.toString())
            if (value.isBlank()) { count.text = tr("اكتب عبارة، أو اختر مخفية."); return@button }
            confirmed = true
            draft.update(draft.current.copy(greetingMode = 2, greetingText = value))
            dialog.dismiss(); showSection("custom"); updateUi("عبارتك جاهزة؛ اختر حفظ التغييرات لتأكيدها")
        }
        panel.addView(confirm, LinearLayout.LayoutParams(-1, ui.dp(48)))
        dialog.setOnDismissListener {
            dialogDismissed(dialog)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0)
            if (!confirmed && !destroying) { draft.update(before); showSection("greeting"); updateUi("أُلغي تعديل العبارة") }
        }
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),ui.dp(560),AppLanguage.direction(draft.current.language))
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        editor.requestFocus()
        editor.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 730 || resultCode != RESULT_OK || importing) return
        val uri = data?.data ?: return
        importing = true; showSection(); updateUi("جارٍ فحص الفيديو وإضافته؛ إعدادات العرض لم تتغير")
        val app = applicationContext
        importWorker.submit {
            val message = try { LocalVideoImport.import(app, uri) }
            catch (_: Exception) { "لم يُضف الفيديو؛ تحقق من الصيغة والحجم والمساحة. لم تتغير إعداداتك" }
            runOnUiThread {
                importing = false
                if (!destroying && !isFinishing) { showSection("import_video"); updateUi(message) }
            }
        }
    }
    override fun onResume() { super.onResume(); studio.start() }
    override fun onPause() { studio.stop(); super.onPause() }
    override fun onDestroy() { destroying=true;importWorker.shutdownNow();dialogs.toList().forEach{it.dismiss()};dialogs.clear();dialogFocus.clear();studio.stop();thumbs.clear();super.onDestroy() }
    override fun onBackPressed() {
        if (tabs.none { it.hasFocus() }) { tabs[section].requestFocus(); return }
        if (!draft.dirty) { super.onBackPressed(); return }
        choose("حفظ تغييراتك قبل الخروج؟", listOf("متابعة التعديل", "تجاهل والخروج", "حفظ والخروج"), 0) {
            when (it) { 1 -> { draft.revert(); finish() }; 2 -> { persist(); finish() } }
        }
    }
}
