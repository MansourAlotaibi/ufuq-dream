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
    private val canMotion by lazy { MediaLibrary.clips.isNotEmpty() && MotionSupport.decoder()!=null }
    private val groupNames = listOf("المظهر", "الساعة والتاريخ", "العبارة")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        repository = SettingsRepository(this)
        draft = SettingsDraft(repository.load())
        // A stale saved-instance parcel from a previous APK must not block startup.
        try { (state?.getSerializable("draft") as? DisplaySettings)?.let { draft.restore(it) } }
        catch (_: RuntimeException) { /* Saved preferences remain authoritative after upgrade. */ }
        section = state?.getInt("section")?.coerceIn(0, 2) ?: 0
        ui = SettingsUi(this)
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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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
            tab.textSize = 15f
            tab.setOnFocusChangeListener { _, focus ->
                tab.background = ui.surface(focus, section == index)
                if (focus && !rebuilding && section != index) { section = index; showSection() }
            }
            tabs.add(tab)
            tabBar.addView(tab, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index < 2) marginEnd = ui.dp(5) })
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
            draft.revert(); showSection(); updateUi("أُلغيت التغييرات؛ تعرض المعاينة إعداداتك المحفوظة"); tabs[section].requestFocus()
        }, LinearLayout.LayoutParams(ui.dp(180), ui.dp(44)))
        root.addView(actions, LinearLayout.LayoutParams(-1, ui.dp(52)))
        help = ui.text("أُفُق $versionLabel  ·  اختر ما يناسب بيتك؛ لا يُحفظ شيء قبل التأكيد.", 13f, true).apply { maxLines = 2 }
        root.addView(help, LinearLayout.LayoutParams(-1, ui.dp(34)))
        help.isFocusable=true;help.isClickable=true
        help.contentDescription="الصور وحقوق الاستخدام"
        help.setOnFocusChangeListener { _,focus ->help.background=ui.surface(focus);if(focus)help.text="أُفُق $versionLabel · الصور وحقوق الاستخدام · اضغط موافق" }
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
        palette()
        when (section) {
            0 -> {
                row("theme", "الثيم", "ألوان النص والواجهة، مستقلة عن الصور.", themeName(s.theme)) {
                    choose("الثيم",listOf("الأخضر السعودي","أرض الصحراء","ليل الرياض"),s.theme){index->
                        change("theme","الثيم: ${themeName(index)}"){copy(theme=index)}
                    }
                }
                row("scene", "المشهد", "اختر مجموعة الصور؛ تلقائي يجمعها كلها.", sceneName(s.sceneChoice)) {
                    choose("اختر المشهد", listOf("تلقائي", "الدرعية", "العلا", "ليل الرياض"), s.sceneChoice + 1, true) {
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
                row("image_mode","عرض الخلفيات","تدوير هادئ أو خلفية تختارها.",if(s.imageMode==0)"تتغيّر تلقائيًا" else "خلفية محددة") {
                    choose("عرض الصور",listOf("تتغيّر تلقائيًا","خلفية محددة"),s.imageMode){index->
                        change("image_mode","عرض الصور: ${if(index==0)"تلقائي" else "صورة محددة"}"){copy(imageMode=index)}
                    }
                }
                if(s.imageMode==1 || s.rotationMinutes==0) row("image","الخلفية","من المجموعة المختارة.",MediaLibrary.selected(s,0).title){pickImage()}
                if(s.imageMode==0) row("interval","تغيير الخلفية","٣ دقائق توازن جميل للمشاهدة اليومية.",intervalName(s.rotationMinutes)) {
                    val values=listOf(1,3,5,0)
                    choose("تغيير الصورة",values.map{intervalName(it)},values.indexOf(s.rotationMinutes)){index->
                        change("interval","تغيير الصورة: ${intervalName(values[index])}"){copy(rotationMinutes=values[index])}
                    }
                }
                row("night", "خفض السطوع ليلاً", "يخفّت الصورة والنص تدريجياً بعد العاشرة مساءً.", if (s.nightDim) "مفعّل" else "متوقف") {
                    change("night", if (s.nightDim) "أُوقف خفض السطوع الليلي" else "فُعّل خفض السطوع الليلي") { copy(nightDim = !nightDim) }
                }
            }
            1 -> {
                row("time", "صيغة الوقت", "بنظام اليوم الكامل أو مع ص / م.", if (s.hour24) "٢٤ ساعة" else "١٢ ساعة") {
                    choose("صيغة الوقت", listOf("١٢ ساعة", "٢٤ ساعة"), if (s.hour24) 1 else 0) {
                        change("time", "صيغة الوقت: ${if (it == 1) "٢٤ ساعة" else "١٢ ساعة"}") { copy(hour24 = it == 1) }
                    }
                }
                row("size", "حجم الساعة", "واضحة من مكان جلوسك.", if (s.size == 2) "كبير" else "متوسط") {
                    choose("حجم الساعة", listOf("متوسط", "كبير"), s.size - 1) {
                        change("size", "حجم الساعة: ${if (it == 1) "كبير" else "متوسط"}") { copy(size = it + 1) }
                    }
                }
                row("position","موضع الساعة","الساعة والتاريخ والعبارة معًا.",ClockPlacement.label(s.clockPosition)){positionDialog()}
                row("gregorian", "التاريخ الميلادي", "اليوم والتاريخ بالعربية.", if (s.gregorian) "ظاهر" else "مخفي") {
                    change("gregorian", if (s.gregorian) "أُخفي التاريخ الميلادي" else "ظهر التاريخ الميلادي") { copy(gregorian = !gregorian) }
                }
                row("hijri", "التاريخ الهجري", "أم القرى؛ قد يختلف عن الرؤية.", if (s.hijri) "ظاهر" else "مخفي") {
                    change("hijri", if (s.hijri) "أُخفي التاريخ الهجري" else "ظهر التاريخ الهجري") { copy(hijri = !hijri) }
                }
                row("seconds","الثواني","تظهر بحجم أصغر بجانب الساعة.",if(s.seconds)"ظاهرة" else "مخفية") {
                    change("seconds",if(s.seconds)"أُخفيت الثواني" else "ظهرت الثواني"){copy(seconds=!seconds)}
                }
                row("digits", "شكل الأرقام", "عربية ٢١:٤١ أو إنجليزية 21:41.", if (s.latinDigits) "إنجليزية" else "عربية") {
                    choose("شكل الأرقام", listOf("عربية  ٠١٢٣", "إنجليزية  0123"), if (s.latinDigits) 1 else 0) {
                        change("digits", "الأرقام: ${if (it == 1) "إنجليزية" else "عربية"}") { copy(latinDigits = it == 1) }
                    }
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
        }
        if (restore != null) rows.findViewWithTag<View>(restore)?.requestFocus()
        rebuilding = false
        updateUi()
    }

    private fun row(key: String, title: String, description: String, value: String, click: () -> Unit) {
        val item = ui.row(title, description, value, { help.text = "أُفُق $versionLabel  ·  $it" }, click).apply {
            tag = key; id = View.generateViewId()
        }
        item.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT) { tabs[section].requestFocus(); true } else false
        }
        // Long custom strings belong in the editor, not a narrow badge.
        rows.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
        if (firstRow == null) firstRow = item
    }

    private fun sceneName(value: Int) = listOf("تلقائي", "الدرعية", "العلا", "ليل الرياض")[value + 1]
    private fun change(key: String, message: String, edit: DisplaySettings.() -> DisplaySettings) {
        draft.update(draft.current.edit())
        showSection(key)
        updateUi(message)
    }
    private fun palette() {
        // Automatic stays neutral even when its live image changes.
        ui.themeIndex = draft.current.theme
        root.setBackgroundColor(ThemeManager.get(ui.themeIndex).base)
        tabs.forEachIndexed { index, tab -> tab.background = ui.surface(tab.hasFocus(), index == section) }
    }
    private fun updateUi(message: String? = null) {
        if (message != null) feedback.text = message
        actions.visibility = if (draft.dirty) View.VISIBLE else View.GONE
        save.background = ui.surface(save.hasFocus(), true)
        val last = if(rows.childCount>0)rows.getChildAt(rows.childCount-1) else null
        tabs.forEach {it.nextFocusDownId=firstRow?.id ?: View.NO_ID}
        if(draft.dirty && last!=null) {
            last.nextFocusDownId=save.id
            save.nextFocusUpId=last.id
            root.findViewWithTag<View>("cancel").nextFocusUpId=last.id
        }
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
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
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
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),ui.dp(590))
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
    private fun photoCredits(){
        val data=org.json.JSONArray(assets.open("photo-provenance.json").bufferedReader().use{it.readText()})
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(ui.dp(24),ui.dp(16),ui.dp(24),ui.dp(16));setBackgroundColor(ThemeManager.get(ui.themeIndex).base)}
        panel.addView(ui.text("الصور وحقوق الاستخدام",24f))
        for(i in 0 until data.length()){
            val item=data.getJSONObject(i)
            val label=listOf(item.getString("title"),item.optString("originalTitle"),item.getString("author"),item.getString("license"),item.getString("source"),item.getString("licenseUrl"),item.optString("publisherSource"),item.optString("conditions"),"تعديل: قصّ ١٦:٩ وتصغير وضغط الصورة.").joinToString("\n")
            panel.addView(ui.text(label,15f).apply{isFocusable=true;setPadding(0,ui.dp(12),0,ui.dp(12));setOnFocusChangeListener{_,focus->background=ui.surface(focus)}})
        }
        val dialog=newDialog().apply{requestWindowFeature(Window.FEATURE_NO_TITLE)}
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),(resources.displayMetrics.widthPixels*.8f).toInt())
    }

    @android.annotation.SuppressLint("RtlHardcoded") // Physical right keeps the left Studio visible in both locales.
    private fun positionDialog(){
        val before=draft.current
        var confirmed=false
        val dialog=newDialog().apply{requestWindowFeature(Window.FEATURE_NO_TITLE)}
        val panel=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(16))
        }
        panel.addView(ui.text("موضع الساعة",22f),LinearLayout.LayoutParams(-1,ui.dp(42)))
        panel.addView(ui.text("تحرّك بين المواضع للمعاينة، واضغط موافق للاختيار.",14f,true),LinearLayout.LayoutParams(-1,ui.dp(38)))
        fun stage(value:Int){
            draft.update(before.copy(clockPosition=value))
            updateUi("معاينة الموضع: ${ClockPlacement.label(value)} · لم يُحفظ بعد")
        }
        fun confirm(value:Int){
            stage(value);confirmed=true;dialog.dismiss();showSection("position")
            updateUi("الموضع: ${ClockPlacement.label(value)} · احفظ التغييرات للتأكيد")
        }
        val automatic=button((if(before.clockPosition<0)"✓  " else "")+"تلقائي · حسب الصورة","position_auto"){confirm(-1)}
        automatic.setOnFocusChangeListener{_,focus->automatic.background=ui.surface(focus,before.clockPosition<0);if(focus)stage(-1)}
        panel.addView(automatic,LinearLayout.LayoutParams(-1,ui.dp(48)).apply{bottomMargin=ui.dp(8)})
        val tiles=mutableListOf<PositionTile>()
        repeat(3){rowIndex->
            val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_LTR}
            repeat(3){column->
                val slot=rowIndex*3+column
                val tile=PositionTile(this,slot,before.clockPosition==slot,ui,{stage(slot)},{confirm(slot)})
                tiles.add(tile)
                line.addView(tile,LinearLayout.LayoutParams(0,ui.dp(82),1f).apply{if(column<2)rightMargin=ui.dp(6)})
            }
            panel.addView(line,LinearLayout.LayoutParams(-1,-2).apply{if(rowIndex<2)bottomMargin=ui.dp(6)})
        }
        for(tile in tiles){
            val row=tile.slot/3;val column=tile.slot%3
            tile.nextFocusLeftId=if(column>0)tiles[tile.slot-1].id else tile.id
            tile.nextFocusRightId=if(column<2)tiles[tile.slot+1].id else tile.id
            tile.nextFocusUpId=if(row>0)tiles[tile.slot-3].id else automatic.id
            tile.nextFocusDownId=if(row<2)tiles[tile.slot+3].id else tile.id
        }
        automatic.nextFocusDownId=tiles[before.clockPosition.takeIf{it in 0..8}?:4].id
        dialog.setOnDismissListener{
            dialogDismissed(dialog)
            if(!confirmed&&!destroying){draft.update(before);showSection("position");updateUi("أُلغيت معاينة الموضع")}
        }
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),ui.dp(360))
        // Keep the large Studio preview visible while the remote explores physical positions.
        dialog.window?.apply {
            setGravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
            attributes=attributes.apply { horizontalMargin=.035f;dimAmount=0f }
        }
        if(before.clockPosition<0)automatic.requestFocus() else tiles[before.clockPosition].requestFocus()
    }

    private fun customText() {
        val before = draft.current
        val dialog = newDialog().apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        var confirmed = false
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20)); setBackgroundColor(ThemeManager.get(ui.themeIndex).base)
        }
        panel.addView(ui.text("عبارتك الخاصة", 24f))
        val count = ui.text("حتى ٤٠ حرفاً", 15f, true)
        val editor = EditText(this).apply {
            tag = "greeting_editor"; textSize = 23f; setTextColor(ui.ink); setSingleLine(true)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL
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
                count.text = "${text.codePointCount(0, text.length)} / ٤٠"
                updateUi("تعاين الآن عبارتك الخاصة؛ لم تُحفظ بعد")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val confirm = button("تم", "custom_done") {
            val value = GreetingText.clean(editor.text.toString())
            if (value.isBlank()) { count.text = "اكتب عبارة، أو اختر مخفية."; return@button }
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
        TvDialogSurface.show(dialog,panel,ThemeManager.get(ui.themeIndex),ui.dp(560))
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        editor.requestFocus()
        editor.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
    }

    override fun onResume() { super.onResume(); studio.start() }
    override fun onPause() { studio.stop(); super.onPause() }
    override fun onDestroy() { destroying=true;dialogs.toList().forEach{it.dismiss()};dialogs.clear();dialogFocus.clear();studio.stop();thumbs.clear();super.onDestroy() }
    override fun onBackPressed() {
        if (tabs.none { it.hasFocus() }) { tabs[section].requestFocus(); return }
        if (!draft.dirty) { super.onBackPressed(); return }
        choose("حفظ تغييراتك قبل الخروج؟", listOf("متابعة التعديل", "تجاهل والخروج", "حفظ والخروج"), 0) {
            when (it) { 1 -> { draft.revert(); finish() }; 2 -> { persist(); finish() } }
        }
    }
}
