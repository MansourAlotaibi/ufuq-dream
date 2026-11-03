package com.ufuq.dream.smart

import java.util.Locale

/** Adapted from Ufuq Launcher TeamCatalog at 4e3e0b9 (MIT); factual IDs/names, no logos. */
data class FootballTeam(val id:String,val english:String,val arabic:String,val saudi:Boolean) {
    fun name(language:String)=if(language=="en")english else arabic
}
object FootballTeams {
    val all=listOf(
        FootballTeam("136013","Al Hilal","الهلال",true),
        FootballTeam("136022","Al Nassr","النصر",true),
        FootballTeam("136018","Al Ittihad","الاتحاد",true),
        FootballTeam("137721","Al Ahli","الأهلي",true),
        FootballTeam("136020","Al Shabab","الشباب",true),
        FootballTeam("136011","Al Fateh","الفتح",true),
        FootballTeam("136012","Al Taawoun","التعاون",true),
        FootballTeam("136014","Al Fayha","الفيحاء",true),
        FootballTeam("133738","Real Madrid","ريال مدريد",false),
        FootballTeam("133739","Barcelona","برشلونة",false),
        FootballTeam("133613","Manchester City","مانشستر سيتي",false),
        FootballTeam("133604","Arsenal","أرسنال",false),
        FootballTeam("133602","Liverpool","ليفربول",false),
        FootballTeam("133612","Manchester United","مانشستر يونايتد",false),
        FootballTeam("133714","Bayern Munich","بايرن ميونخ",false),
        FootballTeam("133689","Paris Saint-Germain","باريس سان جيرمان",false),
        FootballTeam("133676","Juventus","يوفنتوس",false),
    )
    fun find(id:String)=all.firstOrNull{it.id==id}
    private fun key(value:String)=value.replace('-',' ').trim().replace(Regex("\\s+")," ").lowercase(Locale.ROOT)
    private val names=all.associateBy{key(it.english)}
    fun localized(providerName:String,language:String):String {
        if(language=="en" || providerName.any{it in '\u0600'..'\u06ff'})return providerName
        return names[key(providerName)]?.arabic ?: providerName
    }
}
