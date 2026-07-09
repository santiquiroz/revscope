package com.revscope.feature.workshop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.revscope.core.obd.workshop.DiagnosticRules
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WIDTH = 1080
private const val HEIGHT = 1350
private const val MARGIN = 72f
private const val MAX_ITEM_ROWS = 12
private const val ITEMS_TOP = 420f
private const val ROW_HEIGHT = 70f
private const val ELLIPSIS = "…"

private val BG = Color.parseColor("#0A0A0F")
private val ACCENT = Color.parseColor("#E8FF00")
private val TEXT = Color.parseColor("#F0F0F8")
private val MUTED = Color.parseColor("#6B7089")
private val OK_COLOR = Color.parseColor("#4CAF50")
private val WARN_COLOR = Color.parseColor("#FFC107")
private val FAIL_COLOR = Color.parseColor("#FF5252")

/**
 * Renders a health-check report as a shareable dark-theme card (Instagram portrait ratio),
 * mirroring [com.revscope.feature.session.TripShareCard]'s canvas + FileProvider mechanism.
 */
object HealthReportCard {

    // dtcCodes kept for parity with UiState.Done — already summarized as an item in [items].
    fun render(
        context: Context,
        items: List<DiagnosticRules.Diagnosis>,
        dtcCodes: List<String>,
        vehicleName: String,
        timestamp: Long,
    ): Uri? = runCatching {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), items, vehicleName, timestamp)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "revscope_health_card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    private fun draw(canvas: Canvas, items: List<DiagnosticRules.Diagnosis>, vehicleName: String, timestamp: Long) {
        canvas.drawColor(BG)
        drawHeader(canvas, vehicleName, timestamp)
        drawSummary(canvas, items)
        drawItems(canvas, items)
        canvas.drawText("github.com/santiquiroz/revscope", MARGIN, HEIGHT - 60f, paint(MUTED, 30f))
    }

    private fun drawHeader(canvas: Canvas, vehicleName: String, timestamp: Long) {
        canvas.drawText("REVSCOPE", MARGIN, 130f, paint(ACCENT, 60f, bold = true))
        canvas.drawText("Chequeo de salud", MARGIN, 180f, paint(TEXT, 38f, bold = true))
        val date = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es")).format(Date(timestamp))
        canvas.drawText("$vehicleName · $date", MARGIN, 225f, paint(MUTED, 32f))
    }

    private fun drawSummary(canvas: Canvas, items: List<DiagnosticRules.Diagnosis>) {
        canvas.drawText(summaryLine(items), MARGIN, 320f, paint(TEXT, 50f, bold = true))
    }

    private fun summaryLine(items: List<DiagnosticRules.Diagnosis>): String {
        val ok = items.count { it.nivel == DiagnosticRules.Nivel.OK }
        val atencion = items.count { it.nivel == DiagnosticRules.Nivel.ATENCION }
        val fallas = items.count { it.nivel == DiagnosticRules.Nivel.FALLA }
        return "$ok OK · $atencion atención · $fallas fallas"
    }

    private fun drawItems(canvas: Canvas, items: List<DiagnosticRules.Diagnosis>) {
        val displayCount = if (items.size > MAX_ITEM_ROWS) MAX_ITEM_ROWS - 1 else items.size
        val titlePaint = paint(TEXT, 32f, bold = true)
        val causaPaint = paint(MUTED, 26f)
        val maxTextWidth = WIDTH - 2 * MARGIN - 40f

        items.take(displayCount).forEachIndexed { index, item ->
            val rowY = ITEMS_TOP + index * ROW_HEIGHT
            drawDot(canvas, item.nivel, rowY)
            canvas.drawText(truncate(titlePaint, item.titulo, maxTextWidth), MARGIN + 38f, rowY + 26f, titlePaint)
            canvas.drawText(truncate(causaPaint, item.causaProbable, maxTextWidth), MARGIN + 38f, rowY + 56f, causaPaint)
        }

        if (items.size > MAX_ITEM_ROWS) {
            val rowY = ITEMS_TOP + displayCount * ROW_HEIGHT
            val restantes = items.size - displayCount
            canvas.drawText("… y $restantes más", MARGIN + 38f, rowY + 26f, paint(MUTED, 30f))
        }
    }

    private fun drawDot(canvas: Canvas, nivel: DiagnosticRules.Nivel, rowY: Float) {
        val color = when (nivel) {
            DiagnosticRules.Nivel.OK -> OK_COLOR
            DiagnosticRules.Nivel.ATENCION -> WARN_COLOR
            DiagnosticRules.Nivel.FALLA -> FAIL_COLOR
        }
        canvas.drawCircle(MARGIN + 9f, rowY + 12f, 9f, Paint().apply { this.color = color; isAntiAlias = true })
    }

    private fun truncate(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ELLIPSIS) > maxWidth) end--
        return text.take(end) + ELLIPSIS
    }

    private fun paint(colorInt: Int, size: Float, bold: Boolean = false) = Paint().apply {
        color = colorInt
        textSize = size
        isAntiAlias = true
        isFakeBoldText = bold
    }
}
