package org.ivanivorontsov.fotomaster.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

interface Assets {
    fun mask(ref: String, w: Int, h: Int): FloatArray?
    fun image(ref: String, w: Int, h: Int): Img?
    fun drawText(img: Img, layer: Layer.Text) {}
}

object NoAssets : Assets {
    override fun mask(ref: String, w: Int, h: Int): FloatArray? = null
    override fun image(ref: String, w: Int, h: Int): Img? = null
}

object GeometryOps {
    fun rotate90(src: Img, times: Int): Img {
        val t = ((times % 4) + 4) % 4
        if (t == 0) return src
        val w = src.w; val h = src.h
        val nw = if (t % 2 == 1) h else w; val nh = if (t % 2 == 1) w else h
        val out = Img(nw, nh)
        for (y in 0 until h) for (x in 0 until w) {
            val (nx, ny) = when (t) { 1 -> (h - 1 - y) to x; 2 -> (w - 1 - x) to (h - 1 - y); else -> y to (w - 1 - x) }
            out.px[ny * nw + nx] = src.px[y * w + x]
        }
        return out
    }

    fun flipH(src: Img): Img {
        val out = Img(src.w, src.h)
        for (y in 0 until src.h) for (x in 0 until src.w) out.px[y * src.w + x] = src.px[y * src.w + (src.w - 1 - x)]
        return out
    }

    fun straightenScale(w: Int, h: Int, deg: Float): Float {
        val a = Math.toRadians(abs(deg).toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        return max((w * c + h * s) / w, (w * s + h * c) / h)
    }

    fun straighten(src: Img, deg: Float): Img {
        if (deg == 0f) return src
        val out = Img(src.w, src.h)
        val k = straightenScale(src.w, src.h, deg)
        val a = Math.toRadians(-deg.toDouble()); val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val cx = src.w / 2f; val cy = src.h / 2f
        Par.rows(src.h) { y0, y1 ->
            for (y in y0 until y1) for (x in 0 until src.w) {
                val dx = (x - cx) / k; val dy = (y - cy) / k
                out.px[y * src.w + x] = bilinear(src, cx + dx * c - dy * s, cy + dx * s + dy * c)
            }
        }
        return out
    }

    fun crop(src: Img, r: RectN): Img {
        if (r.isFull) return src
        val x0 = (r.l * src.w).roundToInt().coerceIn(0, src.w - 1); val y0 = (r.t * src.h).roundToInt().coerceIn(0, src.h - 1)
        val x1 = (r.r * src.w).roundToInt().coerceIn(x0 + 1, src.w); val y1 = (r.b * src.h).roundToInt().coerceIn(y0 + 1, src.h)
        val nw = x1 - x0; val nh = y1 - y0
        val out = Img(nw, nh)
        for (y in 0 until nh) System.arraycopy(src.px, (y + y0) * src.w + x0, out.px, y * nw, nw)
        return out
    }

    fun apply(src: Img, g: Geometry, withCrop: Boolean = true): Img {
        var img = rotate90(src, g.rot90)
        if (g.flipH) img = flipH(img)
        img = straighten(img, g.straighten)
        if (withCrop) img = crop(img, g.crop)
        return img
    }

    fun outToSource(xo: Float, yo: Float, g: Geometry, srcW: Int, srcH: Int): PointN {
        val t = ((g.rot90 % 4) + 4) % 4
        val rw = if (t % 2 == 1) srcH else srcW; val rh = if (t % 2 == 1) srcW else srcH
        var x = (g.crop.l + xo * (g.crop.r - g.crop.l)) * rw
        var y = (g.crop.t + yo * (g.crop.b - g.crop.t)) * rh
        if (g.straighten != 0f) {
            val k = straightenScale(rw, rh, g.straighten)
            val a = Math.toRadians(-g.straighten.toDouble()); val c = cos(a).toFloat(); val s = sin(a).toFloat()
            val dx = (x - rw / 2f) / k; val dy = (y - rh / 2f) / k
            x = rw / 2f + dx * c - dy * s; y = rh / 2f + dx * s + dy * c
        }
        if (g.flipH) x = rw - x
        val (sx, sy) = when (t) { 1 -> y to (srcH - x); 2 -> (srcW - x) to (srcH - y); 3 -> (srcW - y) to x; else -> x to y }
        return PointN(sx / srcW, sy / srcH)
    }
}

object Renderer {
    fun render(source: Img, doc: Doc, assets: Assets = NoAssets, withGeometry: Boolean = true, withCrop: Boolean = true, scale: Float = 1f, upTo: Long? = null): Img {
        var img = Pipeline.global(source, doc.adj, scale)
        if (img === source) img = source.copy()
        for (layer in doc.layers) {
            if (upTo != null && layer.id > upTo) break
            if (!layer.visible || layer.opacity <= 0f) continue
            img = applyLayer(img, source, layer, assets, scale)
        }
        return if (withGeometry) GeometryOps.apply(img, doc.geometry, withCrop) else img
    }

    private fun withOpacity(before: Img, after: Img, opacity: Float): Img {
        if (opacity >= 1f) return after
        for (i in after.px.indices) after.px[i] = Raster.mix(before.px[i], after.px[i], opacity)
        return after
    }

    fun applyLayer(img: Img, source: Img, layer: Layer, assets: Assets, scale: Float): Img {
        val w = img.w; val h = img.h
        return when (layer) {
            is Layer.Retouch -> {
                val before = img.copy()
                for (op in layer.ops) when (op) {
                    is RetouchOp.Heal -> Raster.heal(img, op.x, op.y, op.r)
                    is RetouchOp.Clone -> Raster.clone(img, op)
                }
                withOpacity(before, img, layer.opacity)
            }
            is Layer.Local -> {
                val ext = when (layer.shape) {
                    MaskShape.PERSON -> layer.maskRef?.let { assets.mask(it, w, h) }
                    MaskShape.SKY -> Raster.skyMask(source.let { if (it.w == w && it.h == h) it else resize(it, w, h) })
                    else -> null
                }
                var m = if (layer.shape == MaskShape.SKY) ext!! else Raster.shapeMask(w, h, layer, ext)
                if (layer.shape == MaskShape.SKY) {
                    if (layer.strokes.isNotEmpty()) {
                        val add = Raster.strokeCoverage(w, h, layer.strokes.filter { !it.erase })
                        val sub = Raster.strokeCoverage(w, h, layer.strokes.filter { it.erase }.map { it.copy(erase = false) })
                        for (i in m.indices) m[i] = clamp01(max(m[i], add[i]) - sub[i])
                    }
                    if (layer.invert) for (i in m.indices) m[i] = 1f - m[i]
                }
                m = Raster.blurMask(m, w, h, max(1, (2 * scale).roundToInt()))
                val adjusted = Pipeline.global(img, layer.adj, scale)
                val out = Img(w, h)
                for (i in out.px.indices) out.px[i] = Raster.mix(img.px[i], adjusted.px[i], m[i] * layer.opacity)
                out
            }
            is Layer.Cutout -> {
                var m = assets.mask(layer.maskRef, w, h) ?: return img
                if (layer.fixStrokes.isNotEmpty()) {
                    val add = Raster.strokeCoverage(w, h, layer.fixStrokes.filter { !it.erase })
                    val sub = Raster.strokeCoverage(w, h, layer.fixStrokes.filter { it.erase }.map { it.copy(erase = false) })
                    for (i in m.indices) m[i] = clamp01(max(m[i], add[i]) - sub[i])
                }
                m = Raster.blurMask(m, w, h, max(1, (layer.feather * 6 * scale).roundToInt()))
                val bg: Img? = when (layer.mode) {
                    BgMode.BLUR -> gaussLike(img, max(6, max(w, h) / 40))
                    BgMode.GRAY -> Img(w, h, IntArray(w * h) { val p = img.px[it]; val l = luma(p.r(), p.g(), p.b()); argb(255, l, l, l) })
                    else -> null
                }
                val out = Img(w, h)
                for (i in out.px.indices) {
                    val k = 1f - (1f - m[i]) * layer.opacity
                    val p = img.px[i]
                    out.px[i] = when (layer.mode) {
                        BgMode.TRANSPARENT -> argb(clamp255(p.a() * k), p.r(), p.g(), p.b())
                        BgMode.COLOR -> Raster.mix(layer.color or (0xFF shl 24), p, k)
                        else -> Raster.mix(bg!!.px[i], p, k)
                    }
                }
                out
            }
            is Layer.Picture -> {
                val pic = assets.image(layer.imageRef, w, h) ?: return img
                val erase = if (layer.eraseStrokes.isEmpty()) null else Raster.strokeCoverage(w, h, layer.eraseStrokes.map { it.copy(erase = false) })
                for (i in img.px.indices) {
                    val a = layer.opacity * (pic.px[i].a() / 255f) * (1f - (erase?.get(i) ?: 0f))
                    if (a > 0f) img.px[i] = Raster.blendPx(img.px[i], pic.px[i], a, layer.blend)
                }
                img
            }
            is Layer.Paint -> { Raster.paintLayer(img, layer); img }
            is Layer.Text -> { assets.drawText(img, layer); img }
        }
    }

    fun previewSize(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        val s = min(1f, maxSide.toFloat() / max(w, h))
        return max(1, (w * s).roundToInt()) to max(1, (h * s).roundToInt())
    }
}
