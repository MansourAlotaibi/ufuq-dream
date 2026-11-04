package com.ufuq.dream.reflection

import android.view.View
import android.widget.TextView
import com.ufuq.dream.*
import com.ufuq.dream.smart.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A future reviewed bundle must pass the native reading-layout gate, not only signature checks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewedLibraryLayoutTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){
        context.getSharedPreferences("dream_settings",0).edit().clear().commit()
        context.getSharedPreferences("reading_update_state",0).edit().clear().commit()
        ContentRepository.file(context).delete();ContentRepository.invalidate()
    }
    @Test fun everyReviewedTextAndCitationFitsTheSharedEditorialRegion(){
        val baseline=SettingsRepository(context).load()
        for(language in listOf("ar","en"))for(theme in 0..2){
            var settings=baseline.copy(language=language,latinDigits=language=="en",theme=theme,smart=SmartSettings(prayers=true,weather=true,reflectionMode=7,football=true,footballTeam="136013"))
            val display=AmbientDisplay(context,{settings},640,sceneTransitions=false)
            try {
                for(font in UfuqFonts.choices(language)){
                    settings=settings.copy(fontArabic=if(language=="ar")font.id else "default",fontEnglish=if(language=="en")font.id else "default")
                    display.refresh()
                    for(entry in ContentRepository.entries(context)){
                        display.findViewWithTag<ReflectionView>("reflection_block").render(entry,language,ThemeManager.get(theme),1f)
                        UfuqFonts.apply(display,settings)
                        display.requestLayout()
                        display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
                        val body=display.findViewWithTag<TextView>("reflection_original");val ref=display.findViewWithTag<TextView>("reflection_reference")
                        assertEquals(entry.text,body.text.toString())
                        assertTrue("Text clipped: $language ${font.id} ${entry.source}",body.layout.height<=body.height)
                        assertTrue("Citation clipped: $language ${font.id} ${entry.source}",ref.layout.height<=ref.height)
                        assertEquals(entry.text.length,body.layout.getLineEnd(body.layout.lineCount-1))
                    }
                }
            }finally{display.stop()}
        }
    }
    @Test fun bodyAndCitationStaySeparateAfterDrawingAndReplacingLongVerses() {
        val metrics=context.resources.displayMetrics
        for(widthDp in listOf(400,590))for(scale in listOf(1f,1.3f)) {
            val config=android.content.res.Configuration(context.resources.configuration).apply { fontScale=scale }
            val themed=context.createConfigurationContext(config)
            val view=ReflectionView(themed)
            val width=(widthDp*metrics.density).toInt()
            val height=(230*metrics.density).toInt()
            val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
            try {
                for(entry in ContentRepository.entries(context).sortedByDescending{it.text.length}) {
                    view.render(entry,"ar",ThemeManager.get(1),1f)
                    repeat(2) {
                        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                        view.layout(0,0,width,height)
                        view.draw(android.graphics.Canvas(bitmap))
                        val body=view.findViewWithTag<TextView>("reflection_original")
                        val ref=view.findViewWithTag<TextView>("reflection_reference")
                        assertEquals(entry.text,body.text.toString())
                        assertTrue("Body overlaps reference: ${entry.source} $widthDp / $scale",body.bottom<ref.top)
                        assertTrue("Body clipped: ${entry.source} $widthDp / $scale: ${body.layout.height} > ${body.height}",body.layout.height<=body.height)
                        assertEquals(entry.text.length,body.layout.getLineEnd(body.layout.lineCount-1))
                        assertTrue("Reference outside container",ref.bottom<=view.height)
                    }
                }
            }finally{bitmap.recycle()}
        }
    }

}
