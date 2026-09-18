package org.julakali.chargeahead.android.phone

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
import org.julakali.chargeahead.shared.domain.AvailabilityLevel
import org.julakali.chargeahead.shared.domain.ChargeSpeed
import org.julakali.chargeahead.shared.domain.SiteAvailability
import kotlin.math.ceil

/**
 * Identity of a marker pill: same key → same pixels → one cached icon.
 * [outOfOrderText] is only drawn for [SiteAvailability.OutOfOrder].
 */
data class PillKey(
    val speed: ChargeSpeed,
    val label: String?,
    val availability: SiteAvailability? = null,
    val outOfOrderText: String = "",
)

/**
 * The charger pill drawn straight to a bitmap for use as a plain Marker icon:
 * a white chip with one-to-three bolts plus the operator's short name, and
 * with live data a second line with the free charge points.
 *
 * Drawn by hand rather than with MarkerComposable for performance; the caller
 * caches by [PillKey].
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
    val lineGap = 1.dp.toPx()
    val dotSize = 7.dp.toPx()
    val dotPadEnd = 4.dp.toPx()

    val text = key.label?.takeIf { it.isNotBlank() }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = 13.sp.toPx()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val fm = textPaint.fontMetrics
    val textHeight = fm.descent - fm.ascent
    val textWidth = text?.let { textPaint.measureText(it) } ?: 0f

    val outOfOrder = key.availability is SiteAvailability.OutOfOrder
    val live = key.availability as? SiteAvailability.Live
    val statusText = when {
        live != null -> "${live.free}/${live.total}"
        outOfOrder -> key.outOfOrderText
        else -> null
    }
    val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (outOfOrder) RED else TEXT_COLOR
        textSize = 12.sp.toPx()
        typeface = if (outOfOrder) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val sfm = statusPaint.fontMetrics
    val statusHeight = if (statusText != null) sfm.descent - sfm.ascent else 0f
    val statusWidth = when {
        statusText == null -> 0f
        live != null -> dotSize + dotPadEnd + statusPaint.measureText(statusText)
        else -> statusPaint.measureText(statusText)
    }

    val boltsWidth = (key.speed.bolts - 1) * boltPitch + boltSize
    val firstLineHeight = maxOf(boltSize, if (text != null) textHeight else 0f)
    val firstLineWidth = boltsWidth + if (text != null) labelPadStart + textWidth + labelPadEnd else 0f
    val contentHeight = firstLineHeight + if (statusText != null) lineGap + statusHeight else 0f
    val contentWidth = maxOf(firstLineWidth, statusWidth)
    val pillW = contentWidth + 2 * padH
    val pillH = contentHeight + 2 * padV

    val bmp = Bitmap.createBitmap(ceil(pillW).toInt().coerceAtLeast(1), ceil(pillH).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val rect = RectF(border / 2, border / 2, pillW - border / 2, pillH - border / 2)
    // One line is a capsule; two lines would make that a blob.
    val radius = if (statusText != null) 10.dp.toPx() else pillH / 2
    canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = if (outOfOrder) 2 * border else border
        color = if (outOfOrder) RED else 0xFFDADCE0.toInt()
    })

    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (outOfOrder) GREY else key.speed.markerColorArgb()
    }
    val cy = padV + firstLineHeight / 2
    for (i in 0 until key.speed.bolts) {
        val cx = padH + i * boltPitch + boltSize / 2
        // White halo first so the next bolt cuts a visible edge into this one.
        canvas.drawBolt(cx, cy, boltSize + boltHalo, halo)
        canvas.drawBolt(cx, cy, boltSize, ink)
    }

    if (text != null) {
        val tx = padH + boltsWidth + labelPadStart
        val ty = padV + firstLineHeight / 2 - (fm.ascent + fm.descent) / 2
        canvas.drawText(text, tx, ty, textPaint)
    }

    if (statusText != null) {
        val lineCy = padV + firstLineHeight + lineGap + statusHeight / 2
        // Right edge flush with the operator name above.
        val lineEnd = pillW - padH - if (text != null) labelPadEnd else 0f
        var tx = (lineEnd - statusWidth).coerceAtLeast(padH)
        if (live != null) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = live.level.colorArgb() }
            canvas.drawCircle(tx + dotSize / 2, lineCy, dotSize / 2, dot)
            tx += dotSize + dotPadEnd
        }
        canvas.drawText(statusText, tx, lineCy - (sfm.ascent + sfm.descent) / 2, statusPaint)
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
    ChargeSpeed.SLOW -> RED
    ChargeSpeed.MEDIUM -> AMBER
    ChargeSpeed.FAST, ChargeSpeed.ULTRA, ChargeSpeed.HYPER -> GREEN
}

private fun AvailabilityLevel.colorArgb(): Int = when (this) {
    AvailabilityLevel.GOOD -> GREEN
    AvailabilityLevel.LOW -> AMBER
    AvailabilityLevel.NONE -> RED
}

private const val RED = 0xFFD93025.toInt()
private const val AMBER = 0xFFF9AB00.toInt()
private const val GREEN = 0xFF188038.toInt()
private const val GREY = 0xFF9AA0A6.toInt()
private const val TEXT_COLOR = 0xFF202124.toInt()
