package com.muba14.androtouch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.StringRes

/** Yüzen butonun şekli. Tasarım görselindeki A, B ve C stilleri. */
enum class ButtonStyle(@StringRes val label: Int) {
    RINGS(R.string.style_rings),        // A · Halka
    SQUARE(R.string.style_square),      // B · Kare zemin (varsayılan)
    THIN_RING(R.string.style_thin_ring) // C · İnce halka
}

/**
 * Butonun renk paleti. [layers] dıştan içe doğru sıralıdır:
 * 0 = en dış katman / zemin, 3 = merkez.
 */
enum class ButtonColor(@StringRes val label: Int, val layers: IntArray) {
    BLACK(R.string.color_black, intArrayOf(0xFF141414.toInt(), 0xFF2E2E2E.toInt(), 0xFF505050.toInt(), 0xFFF5F5F5.toInt())),
    GRAY(R.string.color_gray, intArrayOf(0xFF4D4D4D.toInt(), 0xFF808080.toInt(), 0xFFB5B5B5.toInt(), Color.WHITE)),
    WHITE(R.string.color_white, intArrayOf(0xFFFAFAFA.toInt(), 0xFFDCDCDC.toInt(), 0xFFA8A8A8.toInt(), 0xFF3A3A3A.toInt()))
}

/**
 * Butonu bir Canvas'a çizer. Ölçüler tasarımdaki 110 birimlik kareye göre
 * oranlanır (u = boyut / 110), böylece her boyutta aynı görünür.
 */
object ButtonRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    fun draw(canvas: Canvas, cx: Float, cy: Float, size: Float, style: ButtonStyle, color: ButtonColor) {
        val u = size / 110f
        val c = color.layers
        when (style) {
            ButtonStyle.RINGS -> {
                // Halkalar arasında küçük boşluklar: arkadaki ekran görünür.
                ring(canvas, cx, cy, 48f * u, 8f * u, c[0])
                ring(canvas, cx, cy, 37f * u, 8f * u, c[1])
                ring(canvas, cx, cy, 26f * u, 8f * u, c[2])
                circle(canvas, cx, cy, 19f * u, c[3])
            }
            ButtonStyle.SQUARE -> {
                fill.color = c[0]
                rect.set(cx - 55f * u, cy - 55f * u, cx + 55f * u, cy + 55f * u)
                canvas.drawRoundRect(rect, 26f * u, 26f * u, fill)
                circle(canvas, cx, cy, 40f * u, c[1])
                circle(canvas, cx, cy, 29f * u, c[2])
                circle(canvas, cx, cy, 18f * u, c[3])
            }
            ButtonStyle.THIN_RING -> {
                ring(canvas, cx, cy, 50f * u, 2.5f * u, c[2])
                circle(canvas, cx, cy, 42f * u, c[0])
                circle(canvas, cx, cy, 32f * u, c[1])
                circle(canvas, cx, cy, 20f * u, c[3])
            }
        }
    }

    private fun circle(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(cx, cy, r, fill)
    }

    private fun ring(canvas: Canvas, cx: Float, cy: Float, r: Float, width: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawCircle(cx, cy, r, stroke)
    }
}
