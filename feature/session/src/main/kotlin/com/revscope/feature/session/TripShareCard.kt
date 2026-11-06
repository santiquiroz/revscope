package com.revscope.feature.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.core.content.FileProvider
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos

private const val WIDTH = 1080
private const val HEIGHT = 1350
private const val MARGIN = 72f

private val BG = Color.parseColor("#0A0A0F")
private val SURFACE = Color.parseColor("#12121A")
private val ACCENT = Color.parseColor("#E8FF00")
private val TEXT = Color.parseColor("#F0F0F8")
private val MUTED = Color.parseColor("#6B7089")
private val SUCCESS = Color.parseColor("#3DFF8E")
private val DANGER = Color.parseColor("#FF3D5A")

/**
 * Renders a trip as a shareable dark-theme card (Instagram portrait ratio):
 * route drawn as a racing line + headline stats + RevScope branding.
 */
object TripShareCard {

    data class CardData(
        val startedAt: Long,
        val durationMs: Long,
        val distanceKm: Float,
        val maxSpeed: Int,
        val avgSpeedKmh: Float,
        val maxRpm: Int,
        val best0to100Ms: Long?,
        val maxLateralG: Float?,
        val maxLeanDeg: Float?,
        val maxBpm: Int?,
        val lapCount: Int,
        val bestLapMs: Long?,
        val track: List<Pair<Double, Double>>,
        /** Carro: el lean no aplica y se omite del PNG aunque [maxLeanDeg] traiga dato. */
        val showLean: Boolean = true,
    )

    fun render(context: Context, resolver: StringResolver, data: CardData): Uri? = runCatching {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), data, resolver)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "revscope_trip_card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    private fun draw(canvas: Canvas, data: CardData, resolver: StringResolver) {
        canvas.drawColor(BG)
        val title = paint(ACCENT, 64f, bold = true)
        val label = paint(MUTED, 34f)
        val value = paint(TEXT, 56f, bold = true)

        // Header
        canvas.drawText(resolver.get(R.string.trips_card_brand), MARGIN, 140f, title)
        val date = SimpleDateFormat("dd MMM yyyy · HH:mm", resolver.locale).format(Date(data.startedAt)) // i18n-ignore
        canvas.drawText(date, MARGIN, 200f, label)

        // Route panel
        val mapTop = 250f
        val mapBottom = 760f
        val panel = Paint().apply { color = SURFACE }
        canvas.drawRoundRect(MARGIN, mapTop, WIDTH - MARGIN, mapBottom, 32f, 32f, panel)
        if (data.track.size >= 2) {
            drawTrack(canvas, data.track, MARGIN + 48f, mapTop + 48f, WIDTH - 2 * MARGIN - 96f, mapBottom - mapTop - 96f)
        } else {
            canvas.drawText(resolver.get(R.string.trips_no_gps_track), WIDTH / 2f - 120f, (mapTop + mapBottom) / 2f, label)
        }

        // Stats grid: 2 columns
        val stats = buildList {
            add(resolver.get(R.string.trips_card_distance) to resolver.get(
                R.string.trips_distance_value,
                LocaleNumbers.decimal(data.distanceKm.toDouble(), 1, resolver.locale),
            ))
            add(resolver.get(R.string.trips_card_duration) to formatDuration(data.durationMs, resolver))
            add(resolver.get(R.string.trips_card_max_speed) to resolver.get(R.string.trips_speed_value, data.maxSpeed))
            add(resolver.get(R.string.trips_card_max_rpm) to data.maxRpm.toString())
            data.best0to100Ms?.let { add(resolver.get(R.string.trips_card_0_100) to "%.2fs".format(Locale.ROOT, it / 1000.0)) }
            data.maxLateralG?.let {
                add(resolver.get(R.string.trips_card_g_lat) to resolver.get(
                    R.string.trips_g_value,
                    LocaleNumbers.decimal(it.toDouble(), 2, resolver.locale),
                ))
            }
            if (data.showLean) data.maxLeanDeg?.let {
                add(resolver.get(R.string.trips_card_lean_max) to resolver.get(
                    R.string.trips_lean_value,
                    LocaleNumbers.decimal(it.toDouble(), 0, resolver.locale),
                ))
            }
            data.maxBpm?.let { add(resolver.get(R.string.trips_card_heart_max) to resolver.get(R.string.trips_card_bpm_value, it)) }
            if (data.lapCount > 0) {
                add(resolver.get(R.string.trips_card_laps) to data.lapCount.toString())
                data.bestLapMs?.let { add(resolver.get(R.string.trips_card_best_lap) to formatLapTime(it)) }
            }
        }.take(8)

        val gridTop = 840f
        val rowH = 110f
        val colW = (WIDTH - 2 * MARGIN) / 2f
        stats.forEachIndexed { i, (l, v) ->
            val x = MARGIN + (i % 2) * colW
            val y = gridTop + (i / 2) * rowH
            canvas.drawText(l, x, y, label)
            canvas.drawText(v, x, y + 58f, value)
        }

        // Footer
        canvas.drawText(resolver.get(R.string.trips_card_gps_footer), MARGIN, HEIGHT - 60f, paint(MUTED, 30f))
    }

    private fun drawTrack(canvas: Canvas, track: List<Pair<Double, Double>>, x: Float, y: Float, w: Float, h: Float) {
        val midLatRad = Math.toRadians(track.map { it.first }.average())
        val lonScale = cos(midLatRad)
        val xs = track.map { it.second * lonScale }
        val ys = track.map { it.first }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).takeIf { it > 0 } ?: 1e-9
        val spanY = (maxY - minY).takeIf { it > 0 } ?: 1e-9
        val scale = minOf(w / spanX, h / spanY).toFloat()
        val ox = x + (w - (spanX * scale).toFloat()) / 2f
        val oy = y + (h - (spanY * scale).toFloat()) / 2f

        fun px(i: Int) = ox + ((xs[i] - minX) * scale).toFloat()
        fun py(i: Int) = oy + ((maxY - ys[i]) * scale).toFloat()

        val path = Path().apply {
            moveTo(px(0), py(0))
            for (i in 1 until track.size) lineTo(px(i), py(i))
        }
        canvas.drawPath(path, Paint().apply {
            color = ACCENT
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        })
        canvas.drawCircle(px(0), py(0), 16f, Paint().apply { color = SUCCESS; isAntiAlias = true })
        canvas.drawCircle(px(track.size - 1), py(track.size - 1), 16f, Paint().apply { color = DANGER; isAntiAlias = true })
    }

    private fun paint(colorInt: Int, size: Float, bold: Boolean = false) = Paint().apply {
        color = colorInt
        textSize = size
        isAntiAlias = true
        isFakeBoldText = bold
    }

    private fun formatDuration(ms: Long, resolver: StringResolver): String =
        resolver.get(
            R.string.trips_duration_format,
            TimeUnit.MILLISECONDS.toMinutes(ms),
            TimeUnit.MILLISECONDS.toSeconds(ms) % 60,
        )

    private fun formatLapTime(ms: Long): String =
        "%d:%02d.%02d".format(Locale.ROOT, ms / 60_000, (ms % 60_000) / 1000, (ms % 1000) / 10) // i18n-ignore
}
