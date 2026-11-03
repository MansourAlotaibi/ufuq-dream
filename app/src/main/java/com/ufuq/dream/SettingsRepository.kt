package com.ufuq.dream

import android.content.Context

/** Legacy fields stay serializable/source-compatible; the 0.4 presentation normalizes them. */
data class DisplaySettings(
    val hour24: Boolean, val gregorian: Boolean, val hijri: Boolean, val seconds: Boolean,
    val hijriFirst: Boolean, val size: Int, val mode: Int, val intensity: Int,
    val theme: Int, val greeting: Boolean, val alternate: Boolean, val dim: Boolean,
    val background: Int,
    val sceneChoice: Int = theme,
    val greetingMode: Int = if (greeting) 0 else 3,
    val greetingPreset: Int = 0,
    val greetingText: String = "",
    val latinDigits: Boolean = false,
    val nightDim: Boolean = false,
    val imageMode: Int = 0,
    val imageId: String = "alula_1",
    val rotationMinutes: Int = 3,
    val photoCategory: Int = theme,
    val backgroundType: Int = 0,
    val fixedAssetId: String = "",
    val clockPosition: Int = ClockPlacement.AUTOMATIC,
    val smart: com.ufuq.dream.smart.SmartSettings = com.ufuq.dream.smart.SmartSettings(),
    val language: String = "ar",
    val fontArabic: String = "default",
    val fontEnglish: String = "default"
) : java.io.Serializable

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("dream_settings", Context.MODE_PRIVATE)
    init {
        MediaLibrary.initialize(context.applicationContext)
        if (!prefs.contains("scene_choice")) {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val existing = prefs.all.isNotEmpty() || info.lastUpdateTime > info.firstInstallTime
            val fixed = prefs.getInt("theme", if (existing) 0 else 1).coerceIn(0, 2)
            val edit = prefs.edit().putInt("scene_choice", if (existing) fixed else -1)
                .putInt("greeting_mode", if (prefs.getBoolean("greeting", true)) 0 else 3)
                .putInt("schema", 5)
            listOf("mode", "size", "intensity", "background").forEach { key ->
                if (prefs.contains(key) && !prefs.contains("legacy_$key")) edit.putInt("legacy_$key", prefs.getInt(key, 0))
            }
            edit.apply()
        }
    }
    fun load() = DisplaySettings(
        prefs.getBoolean("hour_24", true), prefs.getBoolean("gregorian", true), prefs.getBoolean("hijri", true),
        prefs.getBoolean("show_seconds", false), prefs.getBoolean("hijri_first", false), prefs.getInt("size", 1).coerceIn(1, 2),
        0, 1, prefs.getInt("theme", 1).coerceIn(0, 2), prefs.getInt("greeting_mode", 0) != 3,
        false, false, 0, prefs.getInt("scene_choice", -1).coerceIn(-1, 2),
        prefs.getInt("greeting_mode", 0).coerceIn(0, 3), prefs.getInt("greeting_preset", 0).coerceIn(0, 3),
        GreetingText.clean(prefs.getString("greeting_text", "").orEmpty()),
        (prefs.getString("app_language","ar")=="en"), prefs.getBoolean("night_dim", false),
        prefs.getInt("image_mode",0).coerceIn(0,1),
        prefs.getString("image_id",BackgroundRepository.first(prefs.getInt("scene_choice",-1)).id).orEmpty().let{BackgroundRepository.find(it).id},
        prefs.getInt("rotation_minutes",3).let{if(it in listOf(0,1,3,5))it else 3},
        backgroundType=prefs.getInt("background_type",0).coerceIn(0,2),
        fixedAssetId=prefs.getString("fixed_asset_id","").orEmpty(),
        clockPosition=ClockPlacement.side(prefs.getInt("clock_position",ClockPlacement.AUTOMATIC)),
        smart=com.ufuq.dream.smart.SmartSettings.read(prefs),
        language=AppLanguage.normalize(prefs.getString("app_language","ar").orEmpty()),
        fontArabic=UfuqFonts.normalize(prefs.getString("font_ar_family","default").orEmpty(),"ar"),
        fontEnglish=UfuqFonts.normalize(prefs.getString("font_en_family","default").orEmpty(),"en")
    )
    /** Atomic snapshot write; unexposed legacy choices are not overwritten. */
    fun save(value: DisplaySettings) {
        val edit = prefs.edit().putBoolean("hour_24", value.hour24)
            .putBoolean("gregorian", value.gregorian).putBoolean("hijri", value.hijri)
            .putInt("size", value.size).putInt("scene_choice", value.sceneChoice)
            .putInt("greeting_mode", value.greetingMode).putInt("greeting_preset", value.greetingPreset)
            .putString("greeting_text", GreetingText.clean(value.greetingText))
            .putBoolean("greeting", value.greetingMode != 3)
            .putInt("theme",value.theme.coerceIn(0,2))
            .putInt("image_mode",value.imageMode.coerceIn(0,1)).putString("image_id",BackgroundRepository.find(value.imageId).id)
            .putInt("clock_position",ClockPlacement.side(value.clockPosition))
            .putString("font_ar_family",UfuqFonts.normalize(value.fontArabic,"ar"))
            .putString("font_en_family",UfuqFonts.normalize(value.fontEnglish,"en"))
            .putInt("background_type",value.backgroundType.coerceIn(0,2)).putString("fixed_asset_id",value.fixedAssetId)
            .putInt("rotation_minutes",value.rotationMinutes).putBoolean("show_seconds",value.seconds)
            .putString("app_language",AppLanguage.normalize(value.language))
            .putBoolean("latin_digits", value.language=="en").putBoolean("night_dim", value.nightDim)
        // Mirror only a user-confirmed fixed scene for old-version compatibility.
        // Theme is independent of scene/category from 0.6 onward.
        val previous=prefs.getInt("clock_position",ClockPlacement.AUTOMATIC)
        if(previous!=ClockPlacement.side(previous)&&!prefs.contains("legacy_clock_position"))edit.putInt("legacy_clock_position",previous)
        value.smart.write(edit)
        edit.apply()
    }

    /**
     * Which scene Automatic mode opens with for the next dream session (0, 1, 2, 0, ...).
     * Sessions are usually shorter than the 15-minute rotation, so without this only Diriyah was ever seen.
     */
    @Synchronized fun nextAutoStart(): Int {
        // Called ONLY by actual DreamService start, never preview or fixed scenes.
        val current = prefs.getInt("auto_cursor", 0).coerceIn(0, 2)
        prefs.edit().putInt("auto_cursor", (current + 1) % 3).apply()
        return current
    }
    fun peekAutoStart(): Int = prefs.getInt("auto_cursor", 0).coerceIn(0, 2)
}
