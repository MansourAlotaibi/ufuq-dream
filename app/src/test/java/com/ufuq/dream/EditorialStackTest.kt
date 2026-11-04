package com.ufuq.dream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ufuq.dream.smart.*
import com.ufuq.dream.reflection.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorialStackTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val now=Instant.parse("2026-11-02T17:00:00Z")
    @Before fun reset(){
        listOf("dream_settings","smart_weather_cache","smart_football_cache").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
        DeviceTime.reset()
        WeatherCache(context).write(WeatherReading("riyadh",31.6,0,true,now.toEpochMilli(),now.toEpochMilli()))
        FootballCache(context).write("136013",javaClass.getResourceAsStream("/football/captured-hilal.json")!!.bufferedReader().use{it.readText()},now.toEpochMilli())
    }
    private fun layout(v:View,w:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(w*9/16,View.MeasureSpec.EXACTLY));v.layout(0,0,w,w*9/16)}
    @Test fun independentCardsFollowWeatherMatchReadingOrderAndKeepCompleteText()=verifyCards(1920)
    @Test @Config(qualifiers="w960dp-h540dp-land-tvdpi")
    fun native720pCardsKeepCompleteText()=verifyCards(1280)
    private fun verifyCards(width:Int){
        for(language in listOf("ar","en"))for(side in listOf(0,2)) {
            val settings=SettingsRepository(context).load().copy(language=language,latinDigits=language=="en",clockPosition=side,smart=SmartSettings(prayers=true,weather=true,reflectionMode=7,football=true,footballTeam="136013"))
            val display=AmbientDisplay(context,{settings},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh()
            try {
                for(entry in ContentRepository.entries(context)) {
                    display.findViewWithTag<ReflectionView>("reflection_block").render(entry,language,ThemeManager.get(settings.theme),1f)
                    layout(display,width)
                    val column=display.smartPanel.supplements
                    val weather=column.findViewWithTag<LinearLayout>("weather_row")
                    val match=column.findViewWithTag<LinearLayout>("football_card")
                    val reading=column.findViewWithTag<ReflectionView>("reflection_block")
                    val card=column.findViewWithTag<LinearLayout>("information_card")
                    assertTrue(weather.bottom<=card.top);assertTrue(match.bottom<reading.top)
                    assertNull(match.background);assertNull(reading.background)
                    assertEquals(View.VISIBLE,column.findViewWithTag<View>("information_divider").visibility)
                    assertTrue(reading.bottom<=card.height)
                    val cardBounds=(card.background as ContentBackdrop).contentBounds()
                    assertEquals(0,cardBounds.left);assertEquals(card.width,cardBounds.right)
                    assertEquals(match.width,reading.width)
                    assertTrue("Weather must remain compact",weather.width<column.width*.86f)
                    if(side==0)assertEquals(column.width-column.paddingRight,weather.right)
                    else assertEquals(column.paddingLeft,weather.left)
                    val fixture=match.findViewWithTag<TextView>("football_widget")
                    val styled=fixture.text as android.text.Spanned
                    val spans=styled.getSpans(0,styled.length,android.text.style.RelativeSizeSpan::class.java)
                    assertEquals(1,spans.size)
                    assertEquals(fixture.text.indexOf('\n')+1,styled.getSpanStart(spans[0]))
                    assertEquals(.72f,spans[0].sizeChange,.001f)
                    assertTrue(fixture.textSize>fixture.textSize*spans[0].sizeChange)

                    val body=reading.findViewWithTag<TextView>("reflection_original");val reference=reading.findViewWithTag<TextView>("reflection_reference")
                    assertEquals(entry.text,body.text.toString());assertTrue("${entry.source} clipped at $width",body.layout.height<=body.height)
                    assertTrue(body.bottom<reference.top)
                }
                if(width==1920&&language=="ar"&&side==2&&android.os.Build.VERSION.SDK_INT==28){
                    val image=Bitmap.createBitmap(width,width*9/16,Bitmap.Config.ARGB_8888)
                    try{display.draw(Canvas(image));val file=java.io.File("artwork/editorial-stack/approved-stack.png");file.parentFile!!.mkdirs();file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}
                }
            }finally{display.stop()}
        }
    }
    @Test fun staleWeatherIsHiddenInsteadOfRelabelledAsCurrent(){
        val settings=SettingsRepository(context).load().copy(smart=SmartSettings(weather=true))
        SettingsRepository(context).save(settings)
        val panel=SmartPanel(context)
        panel.update(now,settings,ThemeManager.get(0))
        val weather=panel.supplements.findViewWithTag<TextView>("weather_reading")
        assertTrue(weather.text.contains("٣٢°"));assertNotNull(weather.compoundDrawablesRelative[0])
        panel.update(now.plusSeconds(3*3600+1),settings,ThemeManager.get(0))
        assertFalse(weather.text.contains("٣٢°"));assertFalse(weather.text.contains("قراءة سابقة"));assertTrue(weather.text.contains("غير متاح"))
        assertNull(weather.compoundDrawablesRelative[0]);panel.stop()
    }
    @Test fun weatherColorsDistinguishSunNightCloudRainSnowAndStorm(){
        assertNotEquals(WeatherGlyph.colorFor(0,true),WeatherGlyph.colorFor(0,false))
        assertNotEquals(WeatherGlyph.colorFor(0,true),WeatherGlyph.colorFor(3,true))
        assertNotEquals(WeatherGlyph.colorFor(3,true),WeatherGlyph.colorFor(61,true))
        assertNotEquals(WeatherGlyph.colorFor(61,true),WeatherGlyph.colorFor(73,true))
        assertNotEquals(WeatherGlyph.colorFor(73,true),WeatherGlyph.colorFor(95,true))
    }
}
