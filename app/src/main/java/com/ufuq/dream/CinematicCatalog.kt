package com.ufuq.dream
import android.graphics.RectF
object CinematicCatalog {
 val assets=listOf("diriyah_1.jpg","alula_1.jpg","riyadh_1.jpg")
 // Normalized safe fields refer to authored 16:9 artwork, not the entire screen.
 fun safe(theme:Int)=if(theme==0) RectF(.58f,.13f,.95f,.84f) else if(theme==2) RectF(.055f,.055f,.43f,.60f) else RectF(.055f,.13f,.43f,.82f)
 fun asset(theme:Int)=assets[theme.coerceIn(0,2)]
}
