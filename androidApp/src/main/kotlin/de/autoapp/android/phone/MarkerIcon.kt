package de.autoapp.android.phone

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import de.autoapp.shared.domain.ChargeSpeed
import kotlin.math.ceil

/** Identity of a marker pill: same (speed, label) → same pixels → one cached icon. */
data class PillKey(val speed: ChargeSpeed, val label: String?)

/**
 * The charger pill drawn straight to a bitmap for use as a plain Marker icon.
 *
 * MarkerComposable rasterizes a composable per marker on the main thread, so a
 * few hundred at once froze the UI. This draws each *distinct* pill once; the
 * caller caches by [PillKey], so N markers cost N cheap adds plus one raster per
 * appearance. Mirrors the `ChargerPill` composable — keep the two in sync.
 */
fun markerPillDescriptor(density: Density, key: PillKey): BitmapDescriptor = with(density) {
    val boltSize = 14.dp.toPx()
    val boltPitch = 6.dp.toPx()
    val boltHalo = 3.dp.toPx()
    val padH = 6.dp.toPx()
    val padV = 3.dp.toPx()
    val labelPadStart = 5.dp.toPx()
    val labelPadEnd = 2.dp.toPx()
    val border = 1.dp.toPx()

    val text = key.label?.takeIf { it.isNotBlank() }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF202124.toInt()
        textSize = 13.sp.toPx()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val fm = textPaint.fontMetrics
    val textHeight = fm.descent - fm.ascent
    val textWidth = text?.let { textPaint.measureText(it) } ?: 0f

    val boltsWidth = (key.speed.bolts - 1) * boltPitch + boltSize
    val contentHeight = maxOf(boltSize, if (text != null) textHeight else 0f)
    val contentWidth = boltsWidth + if (text != null) labelPadStart + textWidth + labelPadEnd else 0f
    val pillW = contentWidth + 2 * padH
    val pillH = contentHeight + 2 * padV

    val bmp = Bitmap.createBitmap(ceil(pillW).toInt().coerceAtLeast(1), ceil(pillH).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val rect = RectF(border / 2, border / 2, pillW - border / 2, pillH - border / 2)
    val radius = pillH / 2
    canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = border; color = 0xFFDADCE0.toInt()
    })

    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = key.speed.markerColorArgb() }
    val cy = padV + contentHeight / 2
    for (i in 0 until key.speed.bolts) {
        val cx = padH + i * boltPitch + boltSize / 2
        // White halo first so the next bolt cuts a visible edge into this one.
        canvas.drawBolt(cx, cy, boltSize + boltHalo, halo)
        canvas.drawBolt(cx, cy, boltSize, ink)
    }

    if (text != null) {
        val tx = padH + boltsWidth + labelPadStart
        val ty = padV + contentHeight / 2 - (fm.ascent + fm.descent) / 2
        canvas.drawText(text, tx, ty, textPaint)
    }

    BitmapDescriptorFactory.fromBitmap(bmp)
}

/** The ic_bolt vector path in its 24×24 viewport. */
private val BOLT_GLYPH = Path().apply {
    moveTo(13f, 2f); lineTo(4f, 14f); lineTo(10f, 14f)
    lineTo(9f, 22f); lineTo(18f, 10f); lineTo(12f, 10f); close()
}

/** Draws the bolt glyph scaled to [size], centred on ([cx], [cy]). */
private fun Canvas.drawBolt(cx: Float, cy: Float, size: Float, paint: Paint) {
    val s = size / 24f
    val m = Matrix().apply { setScale(s, s); postTranslate(cx - size / 2, cy - size / 2) }
    drawPath(Path(BOLT_GLYPH).apply { transform(m) }, paint)
}

/** Same traffic-light as ChargeSpeed.markerColor() in ChargeMap — red/amber/green. */
private fun ChargeSpeed.markerColorArgb(): Int = when (this) {
    ChargeSpeed.SLOW -> 0xFFD93025.toInt()
    ChargeSpeed.MEDIUM -> 0xFFF9AB00.toInt()
    ChargeSpeed.FAST, ChargeSpeed.ULTRA, ChargeSpeed.HYPER -> 0xFF188038.toInt()
}
