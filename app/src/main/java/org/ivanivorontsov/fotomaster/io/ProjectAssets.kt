package org.ivanivorontsov.fotomaster.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import org.ivanivorontsov.fotomaster.core.Assets
import org.ivanivorontsov.fotomaster.core.Img
import org.ivanivorontsov.fotomaster.core.Layer
import org.ivanivorontsov.fotomaster.core.Raster
import org.ivanivorontsov.fotomaster.core.resize
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ProjectAssets(ctx: Context, projectId: String) : Assets {
    val dir = File(ctx.filesDir, "projects/$projectId").apply { mkdirs() }
    private val maskCache = ConcurrentHashMap<String, FloatArray>()
    private val imgCache = ConcurrentHashMap<String, Img>()

    fun saveMask(ref: String, m: FloatArray, w: Int, h: Int) {
        val px = IntArray(w * h) { val v = (m[it].coerceIn(0f, 1f) * 255).toInt(); Color.argb(255, v, v, v) }
        val bm = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        File(dir, "$ref.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun saveImage(ref: String, bm: Bitmap) {
        File(dir, "$ref.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    override fun mask(ref: String, w: Int, h: Int): FloatArray? = maskCache.getOrPut("$ref@${w}x$h") {
        val f = File(dir, "$ref.png"); if (!f.exists()) return null
        val bm = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        val img = BitmapIo.toImg(bm)
        val m = FloatArray(img.w * img.h) { ((img.px[it] shr 16) and 255) / 255f }
        Raster.resizeMask(m, img.w, img.h, w, h)
    }.copyOf()

    override fun image(ref: String, w: Int, h: Int): Img? = imgCache.getOrPut("$ref@${w}x$h") {
        val f = File(dir, "$ref.png"); if (!f.exists()) return null
        val bm = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        var img = BitmapIo.toImg(bm)
        val target = w.toFloat() / h; val cur = img.w.toFloat() / img.h
        if (kotlin.math.abs(target - cur) / target > 0.02f) {
            img = if (cur > target) { val nw = (img.h * target).toInt().coerceIn(1, img.w); org.ivanivorontsov.fotomaster.core.GeometryOps.crop(img, org.ivanivorontsov.fotomaster.core.RectN((1f - nw.toFloat() / img.w) / 2f, 0f, (1f + nw.toFloat() / img.w) / 2f, 1f)) }
            else { val nh = (img.w / target).toInt().coerceIn(1, img.h); org.ivanivorontsov.fotomaster.core.GeometryOps.crop(img, org.ivanivorontsov.fotomaster.core.RectN(0f, (1f - nh.toFloat() / img.h) / 2f, 1f, (1f + nh.toFloat() / img.h) / 2f)) }
        }
        if (img.w == w && img.h == h) img else resize(img, w, h)
    }

    override fun drawText(img: Img, layer: Layer.Text) {
        val bm = BitmapIo.toBitmap(img)
        val c = Canvas(bm)
        val size = layer.size * maxOf(img.w, img.h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; color = layer.color; alpha = (layer.opacity * 255).toInt()
            typeface = if (layer.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
            if (layer.shadow) setShadowLayer(size * 0.08f, size * 0.03f, size * 0.03f, Color.argb(160, 0, 0, 0))
        }
        val lines = layer.text.split("\n")
        val lh = size * 1.2f
        val y0 = layer.y * img.h - (lines.size - 1) * lh / 2f
        lines.forEachIndexed { i, s -> c.drawText(s, layer.x * img.w, y0 + i * lh + size / 3f, p) }
        bm.getPixels(img.px, 0, img.w, 0, 0, img.w, img.h)
    }
}
