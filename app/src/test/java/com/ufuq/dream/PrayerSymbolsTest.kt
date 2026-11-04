package com.ufuq.dream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import com.ufuq.dream.smart.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrayerSymbolsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val now=Instant.parse("2026-11-02T17:00:00Z")
    @Before fun setup(){
        context.getSharedPreferences("dream_settings",0).edit().clear().commit();DeviceTime.reset()
        PrayerCalendarCache(context).write(PrayerRequest(LocalDate.of(2026,11,2),SaudiCities.find("riyadh"),0,false),javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()},now.toEpochMilli())
    }
    @Test fun inlinePeriodSymbolsKeepNamesTimesAndAccessibilityIntact(){
        for(language in listOf("ar","en")) {
            val settings=SettingsRepository(context).load().copy(language=language,latinDigits=language=="en",smart=SmartSettings(prayers=true))
            val display=AmbientDisplay(context,{settings},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh()
            try {
                display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
                for(i in 0..4){
                    val label=display.findViewWithTag<TextView>("prayer_$i")
                    assertTrue(label.text.contains('\n'));assertTrue(label.text.toString().contains(if(language=="ar")"٠" else "0") || label.text.toString().any{it.isDigit()})
                    val styled=label.text as Spanned;val spans=styled.getSpans(0,styled.length,ImageSpan::class.java)
                    assertEquals(1,spans.size);assertTrue(spans[0].drawable is PrayerGlyph)
                    assertTrue(label.layout.height<=label.height);assertFalse(label.contentDescription.contains('\uFFFC'))
                }
            }finally{display.stop()}
        }
    }
    @Test fun fiveSmallSymbolsAreDistinctAndTheSharedCardCollapsesWithoutEmptySections(){
        val pixels=(0..4).map { i ->
            val image=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
            try{PrayerGlyph(i,android.graphics.Color.WHITE).apply{setBounds(0,0,48,48)}.draw(Canvas(image));val values=IntArray(48*48);image.getPixels(values,0,48,0,0,48,48);assertTrue(values.any{it!=0});values.toList()}finally{image.recycle()}
        }
        assertEquals(5,pixels.distinct().size)
        val base=SettingsRepository(context).load()
        for(reading in listOf(false,true))for(match in listOf(false,true)) {
            val s=base.copy(smart=SmartSettings(reflectionMode=if(reading)7 else 0,football=match,footballTeam="136013"))
            val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh()
            try {
                display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
                val card=display.findViewWithTag<android.widget.LinearLayout>("information_card")
                val divider=display.findViewWithTag<View>("information_divider")
                assertEquals(if(reading||match)View.VISIBLE else View.GONE,card.visibility)
                assertEquals(if(reading&&match)View.VISIBLE else View.GONE,divider.visibility)
                assertEquals(!reading&&!match,(card.background as ContentBackdrop).contentBounds().isEmpty)
            }finally{display.stop()}
        }
    }
}
