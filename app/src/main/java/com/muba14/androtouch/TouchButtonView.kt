package com.muba14.androtouch

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/** Butonu çizen görünüm. Hem ekranda yüzen butonda hem ayar ekranındaki önizlemelerde kullanılır. */
class TouchButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var buttonStyle: ButtonStyle = ButtonStyle.SQUARE
        set(value) { field = value; invalidate() }

    var buttonColor: ButtonColor = ButtonColor.GRAY
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        val size = minOf(w, h)
        val cx = paddingLeft + w / 2f
        val cy = paddingTop + h / 2f
        ButtonRenderer.draw(canvas, cx, cy, size, buttonStyle, buttonColor)
    }
}
