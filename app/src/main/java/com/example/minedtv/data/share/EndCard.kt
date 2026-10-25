package com.example.minedtv.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.example.minedtv.ui.components.BRAIN_PATH
import java.io.File

/**
 * The "made with MinedTv" end card appended to every AI edit (like TikTok's outro on downloaded
 * videos): app logo, wordmark and the creator's handle on the app's dark background.
 */
object EndCard {
    private const val GOLD = 0xFFFFC53D.toInt()
    private const val BG_TOP = 0xFF15161C.toInt()
    private const val BG_BOTTOM = 0xFF0B0C10.toInt()

    /** Draws the card at the video's output size and saves it as a PNG for Transformer. */
    fun render(context: Context, width: Int, height: Int, handle: String): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = minOf(width, height) / 100f // layout in % of the short edge
        val cx = width / 2f
        val cy = height / 2f

        // Background: dark vertical gradient with a soft gold glow behind the logo.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP)
        })
        canvas.drawCircle(cx, cy - unit * 10, unit * 45, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy - unit * 10, unit * 45, 0x40FFC53D, 0x00FFC53D, Shader.TileMode.CLAMP)
        })

        // Logo tile: gold rounded square with the black brain.
        val tile = unit * 26
        val tileRect = RectF(cx - tile / 2, cy - unit * 10 - tile / 2, cx + tile / 2, cy - unit * 10 + tile / 2)
        canvas.drawRoundRect(tileRect, tile * 0.24f, tile * 0.24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD })
        val brain = PathParser.createPathFromPathData(BRAIN_PATH)
        val iconSize = tile * 0.72f
        brain.transform(Matrix().apply {
            setScale(iconSize / 24f, iconSize / 24f)
            postTranslate(tileRect.centerX() - iconSize / 2, tileRect.centerY() - iconSize / 2)
        })
        canvas.drawPath(brain, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF000000.toInt()
            strokeWidth = 1.7f * iconSize / 24f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })

        // Wordmark: "Mined" white + "Tv" gold.
        val word = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = unit * 13
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mined = "Mined"
        val tv = "Tv"
        val total = word.measureText(mined + tv)
        val baseline = tileRect.bottom + unit * 18
        word.color = 0xFFFFFFFF.toInt()
        canvas.drawText(mined, cx - total / 2, baseline, word)
        word.color = GOLD
        canvas.drawText(tv, cx - total / 2 + word.measureText(mined), baseline, word)

        // Creator handle + tagline.
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = unit * 5.2f
            color = 0xCCFFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("@" + handle.lowercase(), cx, baseline + unit * 10, small)
        small.apply {
            textSize = unit * 3.8f
            color = 0x8CFFFFFF.toInt()
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("Clipped & edited with AI on MinedTv", cx, baseline + unit * 17, small)

        val out = File(context.cacheDir, "share").apply { mkdirs() }.resolve("endcard_${width}x$height.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }
}
