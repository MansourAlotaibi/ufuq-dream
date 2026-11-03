package com.ufuq.dream.smart

import android.content.SharedPreferences
import java.time.ZoneId

/** City-centre coordinates, never the user's location. No GPS or geocoding service. */
data class AmbientCity(val id: String, val arabic: String, val latitude: Double, val longitude: Double, val zone: String) {
    val zoneId: ZoneId get() = ZoneId.of(zone)
}
object SaudiCities {
    val all = listOf(
        AmbientCity("riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh"),
        AmbientCity("makkah", "مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh"),
        AmbientCity("madinah", "المدينة المنورة", 24.4672, 39.6111, "Asia/Riyadh"),
        AmbientCity("jeddah", "جدة", 21.5433, 39.1728, "Asia/Riyadh"),
        AmbientCity("dammam", "الدمام", 26.4207, 50.0888, "Asia/Riyadh"),
        AmbientCity("taif", "الطائف", 21.2703, 40.4158, "Asia/Riyadh"),
        AmbientCity("abha", "أبها", 18.2164, 42.5053, "Asia/Riyadh"),
        AmbientCity("tabuk", "تبوك", 28.3838, 36.5550, "Asia/Riyadh"),
        AmbientCity("alula", "العلا", 26.6084, 37.9232, "Asia/Riyadh"),
        AmbientCity("hail", "حائل", 27.5114, 41.7208, "Asia/Riyadh"),
        AmbientCity("buraydah", "بريدة", 26.3592, 43.9818, "Asia/Riyadh"),
        AmbientCity("jazan", "جازان", 16.8892, 42.5706, "Asia/Riyadh"),
        AmbientCity("najran", "نجران", 17.5656, 44.2289, "Asia/Riyadh"),
        AmbientCity("dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai"),
        AmbientCity("cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo"),
        AmbientCity("london", "لندن", 51.5074, -0.1278, "Europe/London")
    )
    fun find(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
data class SmartSettings(
    val prayers: Boolean = false, val prayerCity: String = "riyadh",
    val prayerMethod: Int = 0, val hanafiAsr: Boolean = false,
    val weather: Boolean = false, val weatherCity: String = "riyadh",
    val fahrenheit: Boolean = false, val weatherRefreshMinutes: Int = 60,
    val prayerSource: Int = 0,
    val reflectionMode: Int = 0, val football: Boolean = false, val footballTeam: String = "none"
) : java.io.Serializable {
    companion object {
        fun read(p: SharedPreferences) = SmartSettings(
            p.getBoolean("smart_prayers", false), SaudiCities.find(p.getString("smart_prayer_city", "riyadh").orEmpty()).id,
            p.getInt("smart_prayer_method", 0).coerceIn(0, 1), p.getBoolean("smart_hanafi", false),
            p.getBoolean("smart_weather", false), SaudiCities.find(p.getString("smart_weather_city", "riyadh").orEmpty()).id,
            p.getBoolean("smart_fahrenheit", false), p.getInt("smart_weather_refresh", 60).let { if (it in listOf(30, 60, 180)) it else 60 },
            p.getInt("smart_prayer_source", if(p.contains("smart_prayers"))1 else 0).coerceIn(0,1),
            p.getInt("smart_reflection_mode",0).let{if(it in 0..3)it else 0},
            p.getBoolean("smart_football",false) && FootballTeams.find(p.getString("smart_football_team","none").orEmpty())!=null,
            FootballTeams.find(p.getString("smart_football_team","none").orEmpty())?.id?:"none"
        )
    }
    fun write(e: SharedPreferences.Editor) {
        e.putInt("smart_reflection_mode",reflectionMode.let{if(it in 0..3)it else 0})
            .putBoolean("smart_football",football && FootballTeams.find(footballTeam)!=null)
            .putString("smart_football_team",FootballTeams.find(footballTeam)?.id?:"none")
            .putInt("smart_prayer_source",prayerSource.coerceIn(0,1))
            .putBoolean("smart_prayers", prayers).putString("smart_prayer_city", SaudiCities.find(prayerCity).id)
            .putInt("smart_prayer_method", prayerMethod.coerceIn(0, 1)).putBoolean("smart_hanafi", hanafiAsr)
            .putBoolean("smart_weather", weather).putString("smart_weather_city", SaudiCities.find(weatherCity).id)
            .putBoolean("smart_fahrenheit", fahrenheit).putInt("smart_weather_refresh", if (weatherRefreshMinutes in listOf(30,60,180)) weatherRefreshMinutes else 60)
    }
}
