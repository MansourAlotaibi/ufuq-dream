package com.ufuq.dream

import android.view.View
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceLabelsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){context.getSharedPreferences("dream_settings",0).edit().clear().commit()}
    private fun layout(v:View,w:Int=1920){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(w*9/16,View.MeasureSpec.EXACTLY));v.layout(0,0,w,w*9/16)}
    @Test fun everyPhotoHasBilingualSourceMetadataAndLabelsAreNotThemeOrCategoryNames(){
        val raw=context.assets.open("photo-provenance.json").bufferedReader().use{it.readText()};val labels=PlaceLabels.parse(raw)
        assertEquals(BackgroundRepository.images.map{it.id}.toSet(),labels.keys)
        for(photo in BackgroundRepository.images)for(lang in listOf("ar","en")){
            val label=PlaceLabels.forMedia(context,MediaItem(photo),lang)
            assertEquals(if(lang=="en")labels.getValue(photo.id).en else labels.getValue(photo.id).ar,label)
            assertFalse(label.contains("ليل الرياض"));assertFalse(label.contains("Riyadh Night"))
        }
        assertTrue(PlaceLabels.forMedia(context,MediaItem(BackgroundRepository.find("diriyah_1")),"ar").contains("الطريف"))
        assertTrue(PlaceLabels.forMedia(context,MediaItem(BackgroundRepository.find("alula_4")),"en").contains("Elephant Rock"))
    }
    @Test fun personalVideoDoesNotInheritItsStockPosterLocationOrFilename(){
        val poster=BackgroundRepository.find("alula_4")
        for(category in -1..2){
            val clip=MotionClip("personal_test","private-filename",category,"private.mp4",poster.id,true)
            assertEquals("فيديو شخصي",PlaceLabels.forMedia(context,MediaItem(poster,clip),"ar"))
            assertEquals("Personal video",PlaceLabels.forMedia(context,MediaItem(poster,clip),"en"))
        }
        val unknown=BackgroundImage("unknown","Unknown",2,"unknown.jpg")
        assertEquals("Saudi scenes",PlaceLabels.forMedia(context,MediaItem(unknown),"en"))
    }
    @Test fun selectedBackgroundAndPreviewBothShowActualPlaceIncludingFilterFallback(){
        val base=SettingsRepository(context).load()
        for(language in listOf("ar","en"))for(theme in 0..2){
            var s=base.copy(language=language,latinDigits=language=="en",theme=theme,sceneChoice=-1,imageMode=1)
            val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(Instant.parse("2026-11-02T17:00:00Z"))
            for(photo in BackgroundRepository.images){
                s=s.copy(imageId=photo.id,fixedAssetId=photo.id);display.refresh();layout(display)
                val label=display.findViewWithTag<TextView>("place_label")
                assertTrue(label.text.toString().endsWith(PlaceLabels.forMedia(context,MediaItem(photo),language)))
                assertTrue(label.layout.height<=label.height)
                assertEquals(0,label.layout.getEllipsisCount(label.layout.lineCount-1))
                for(line in 0 until label.layout.lineCount)assertTrue(label.layout.getLineMax(line)<=label.layout.width+1)
            }
            s=s.copy(sceneChoice=0,imageId="riyadh_4",fixedAssetId="riyadh_4");display.refresh();layout(display)
            assertTrue(display.findViewWithTag<TextView>("place_label").text.contains(if(language=="en")"At-Turaif" else "الطريف"))
            val studio=PreviewStudio(context){s};studio.refresh();layout(studio.display)
            assertEquals(display.findViewWithTag<TextView>("place_label").text,studio.display.findViewWithTag<TextView>("place_label").text)
            studio.stop();display.stop()
        }
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-tvdpi")
    fun longestLabelsFit720pWithEveryBundledFont(){
        val base=SettingsRepository(context).load()
        for(language in listOf("ar","en"))for(font in UfuqFonts.choices(language)){
            val s=base.copy(language=language,latinDigits=language=="en",fontArabic=if(language=="ar")font.id else "default",fontEnglish=if(language=="en")font.id else "default",sceneChoice=2,imageMode=1,imageId="riyadh_3",fixedAssetId="riyadh_3",size=2,seconds=true)
            val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.refresh();layout(display,1280)
            val label=display.findViewWithTag<TextView>("place_label")
            assertTrue("${font.id} clips",label.layout.height<=label.height)
            assertTrue(label.layout.lineCount<=2)
            for(line in 0 until label.layout.lineCount)assertTrue(label.layout.getLineMax(line)<=label.layout.width+1)
            display.stop()
        }
    }
    @Test fun incompleteOrUnsafeMetadataCannotBecomeAVisiblePlace(){
        val parsed=PlaceLabels.parse("""[{"id":"missing"},{"id":"bad","displayLocation":{"ar":"الرياض","en":"bad\u202evalue"}}]""")
        assertTrue(parsed.isEmpty())
    }
}
