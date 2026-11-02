package com.ufuq.dream

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup

/** A true scaled presentation viewport; same renderer/layout coordinates as the dream. */
class PreviewStudio @JvmOverloads constructor(context: Context, source: () -> DisplaySettings = { SettingsRepository(context).load() }) : ViewGroup(context) {
    internal val display = AmbientDisplay(context, source, 640, sceneTransitions=false)
    private val logicalWidth get() = resources.displayMetrics.widthPixels.coerceAtLeast(960)
    init {
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        addView(display)
    }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val height = width * 9 / 16
        display.measure(MeasureSpec.makeMeasureSpec(logicalWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(logicalWidth*9/16,MeasureSpec.EXACTLY))
        setMeasuredDimension(width,height)
    }
    override fun onLayout(changed: Boolean,l:Int,t:Int,r:Int,b:Int) { display.layout(0,0,logicalWidth,logicalWidth*9/16) }
    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        val scale = width.toFloat()/logicalWidth
        canvas.scale(scale,scale)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
    fun start() = display.start()
    fun refresh() { display.refresh(); display.reschedule() }
    fun stop() = display.stop()
}
