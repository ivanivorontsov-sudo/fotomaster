package org.ivanivorontsov.fotomaster.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Levels(val inBlack: Int = 0, val inWhite: Int = 255, val gamma: Float = 1f, val outBlack: Int = 0, val outWhite: Int = 255) {
    val isIdentity get() = inBlack == 0 && inWhite == 255 && gamma == 1f && outBlack == 0 && outWhite == 255
}

data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val sharpen: Float = 0f,
    val denoise: Float = 0f,
    val vignette: Float = 0f,
    val levels: Levels = Levels(),
    val curve: List<Pair<Float, Float>> = IDENTITY_CURVE,
    val hsl: List<Float> = List(24) { 0f },
) {
    companion object {
        val IDENTITY_CURVE = listOf(0f to 0f, 1f to 1f)
        val HUE_CENTERS = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 280f, 320f)
        val HUE_NAMES = listOf("Красный", "Оранжевый", "Жёлтый", "Зелёный", "Голубой", "Синий", "Фиолетовый", "Пурпурный")
    }
    val isIdentity get() = this == Adjustments()
    val hasHsl get() = hsl.any { it != 0f }
}

object Curve {
    fun lut(points: List<Pair<Float, Float>>): IntArray {
        val pts = points.sortedBy { it.first }
        val n = pts.size
        val xs = FloatArray(n) { pts[it].first }; val ys = FloatArray(n) { pts[it].second }
        val m = FloatArray(n)
        val d = FloatArray(max(1, n - 1)) { i -> if (n > 1) (ys[i + 1] - ys[i]) / max(1e-6f, xs[i + 1] - xs[i]) else 0f }
        for (i in 0 until n) m[i] = when (i) { 0 -> d[0]; n - 1 -> d[n - 2]; else -> if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f }
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]; val s = a * a + b * b
            if (s > 9f) { val t = 3f / sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
        }
        return IntArray(256) { v ->
            val x = v / 255f
            val y = when {
                n < 2 -> x
                x <= xs[0] -> ys[0]
                x >= xs[n - 1] -> ys[n - 1]
                else -> {
                    var k = 0; while (k < n - 2 && x > xs[k + 1]) k++
                    val hh = xs[k + 1] - xs[k]; val t = (x - xs[k]) / hh
                    val t2 = t * t; val t3 = t2 * t
                    (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * hh * m[k] + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * hh * m[k + 1]
                }
            }
            clamp255(y * 255f)
        }
    }
}

object Tone {
    fun buildLut(a: Adjustments): IntArray {
        val ev = 2f.pow(a.exposure)
        val lv = a.levels
        val curve = Curve.lut(a.curve)
        val c = 1f + a.contrast
        return IntArray(256) { v ->
            var x = v / 255f
            x = (x * ev)
            x = ((x * 255f - lv.inBlack) / max(1f, (lv.inWhite - lv.inBlack).toFloat())).coerceIn(0f, 1f)
            if (lv.gamma != 1f) x = x.pow(1f / lv.gamma)
            x = (lv.outBlack + x * (lv.outWhite - lv.outBlack)) / 255f
            x = (x - 0.5f) * c + 0.5f
            x += a.whites * 0.25f * x * x - a.blacks * 0.25f * (1 - x) * (1 - x)
            curve[clamp255(x * 255f)]
        }
    }

    fun apply(src: Img, a: Adjustments): Img {
        val out = Img(src.w, src.h)
        val lut = buildLut(a)
        val tR = 1f + a.temperature * 0.25f + a.tint * 0.08f
        val tG = 1f - a.tint * 0.18f
        val tB = 1f - a.temperature * 0.25f + a.tint * 0.08f
        val hasHsl = a.hasHsl
        val hsl = a.hsl.toFloatArray()
        Par.rows(src.h) { y0, y1 ->
            val tmp = FloatArray(3)
            for (i in y0 * src.w until y1 * src.w) {
                val p = src.px[i]
                var r = clamp255(p.r() * tR); var g = clamp255(p.g() * tG); var b = clamp255(p.b() * tB)
                r = lut[r]; g = lut[g]; b = lut[b]
                if (a.highlights != 0f || a.shadows != 0f) {
                    val l = luma(r, g, b) / 255f
                    val hw = smooth((l - 0.5f) * 2f); val sw = smooth((0.5f - l) * 2f)
                    val k = 1f + a.highlights * 0.5f * hw + a.shadows * 0.8f * sw
                    r = clamp255(r * k); g = clamp255(g * k); b = clamp255(b * k)
                }
                if (a.saturation != 0f || a.vibrance != 0f) {
                    val l = luma(r, g, b).toFloat()
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                    val curSat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
                    val s = 1f + a.saturation + a.vibrance * (1f - curSat)
                    r = clamp255(l + (r - l) * s); g = clamp255(l + (g - l) * s); b = clamp255(l + (b - l) * s)
                }
                if (hasHsl) {
                    rgbToHsl(r, g, b, tmp)
                    var dh = 0f; var ds = 0f; var dl = 0f
                    for (k in 0 until 8) {
                        val wgt = bandWeight(tmp[0], Adjustments.HUE_CENTERS[k])
                        if (wgt > 0f) { dh += hsl[k * 3] * wgt; ds += hsl[k * 3 + 1] * wgt; dl += hsl[k * 3 + 2] * wgt }
                    }
                    val satW = min(1f, tmp[1] * 4f)
                    tmp[0] = ((tmp[0] + dh * 30f) % 360f + 360f) % 360f
                    tmp[1] = clamp01(tmp[1] * (1f + ds))
                    tmp[2] = clamp01(tmp[2] + dl * 0.25f * satW)
                    val c = hslToRgb(tmp[0], tmp[1], tmp[2])
                    r = c.r(); g = c.g(); b = c.b()
                }
                out.px[i] = argb(p.a(), r, g, b)
            }
        }
        return out
    }

    private fun smooth(t: Float): Float { val x = clamp01(t); return x * x * (3 - 2 * x) }

    fun bandWeight(h: Float, center: Float): Float {
        var d = abs(h - center); if (d > 180f) d = 360f - d
        return max(0f, 1f - d / 40f)
    }
}

fun rgbToHsl(r: Int, g: Int, b: Int, out: FloatArray) {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
    val l = (mx + mn) / 2f
    if (mx == mn) { out[0] = 0f; out[1] = 0f; out[2] = l; return }
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) { rf -> (gf - bf) / d + (if (gf < bf) 6f else 0f); gf -> (bf - rf) / d + 2f; else -> (rf - gf) / d + 4f } * 60f
    out[0] = h; out[1] = s; out[2] = l
}

fun hslToRgb(h: Float, s: Float, l: Float): Int {
    if (s == 0f) { val v = clamp255(l * 255f); return argb(255, v, v, v) }
    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    fun hue(t0: Float): Float { var t = t0; if (t < 0) t += 1f; if (t > 1) t -= 1f
        return when { t < 1f / 6 -> p + (q - p) * 6 * t; t < 0.5f -> q; t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6; else -> p } }
    val hh = h / 360f
    return argb(255, clamp255(hue(hh + 1f / 3) * 255f), clamp255(hue(hh) * 255f), clamp255(hue(hh - 1f / 3) * 255f))
}

object Detail {
    fun sharpen(src: Img, amount: Float, radius: Int = 2): Img {
        if (amount <= 0f) return src
        val bl = gaussLike(src, radius * 2)
        val out = Img(src.w, src.h)
        Par.rows(src.h) { y0, y1 ->
            for (i in y0 * src.w until y1 * src.w) {
                val p = src.px[i]; val q = bl.px[i]
                out.px[i] = argb(p.a(), clamp255(p.r() + (p.r() - q.r()) * amount), clamp255(p.g() + (p.g() - q.g()) * amount), clamp255(p.b() + (p.b() - q.b()) * amount))
            }
        }
        return out
    }

    fun clarity(src: Img, amount: Float): Img {
        if (amount == 0f) return src
        val r = max(4, max(src.w, src.h) / 60)
        val bl = gaussLike(src, r)
        val out = Img(src.w, src.h)
        Par.rows(src.h) { y0, y1 ->
            for (i in y0 * src.w until y1 * src.w) {
                val p = src.px[i]; val q = bl.px[i]
                val l = luma(p.r(), p.g(), p.b()) / 255f
                val mid = 1f - abs(l - 0.5f) * 2f
                val k = amount * 0.6f * mid
                out.px[i] = argb(p.a(), clamp255(p.r() + (p.r() - q.r()) * k), clamp255(p.g() + (p.g() - q.g()) * k), clamp255(p.b() + (p.b() - q.b()) * k))
            }
        }
        return out
    }

    fun denoise(src: Img, amount: Float): Img {
        if (amount <= 0f) return src
        val bl = boxBlur(src, max(1, (amount * 3).roundToInt()))
        val thr = 6f + amount * 30f
        val out = Img(src.w, src.h)
        Par.rows(src.h) { y0, y1 ->
            for (i in y0 * src.w until y1 * src.w) {
                val p = src.px[i]; val q = bl.px[i]
                val d = (abs(p.r() - q.r()) + abs(p.g() - q.g()) + abs(p.b() - q.b())) / 3f
                val wgt = amount * exp(-(d / thr) * (d / thr))
                out.px[i] = argb(p.a(), clamp255(p.r() + (q.r() - p.r()) * wgt), clamp255(p.g() + (q.g() - p.g()) * wgt), clamp255(p.b() + (q.b() - p.b()) * wgt))
            }
        }
        return out
    }

    fun vignette(src: Img, amount: Float): Img {
        if (amount == 0f) return src
        val out = Img(src.w, src.h)
        val cx = src.w / 2f; val cy = src.h / 2f; val maxD = sqrt(cx * cx + cy * cy)
        Par.rows(src.h) { y0, y1 ->
            for (y in y0 until y1) for (x in 0 until src.w) {
                val i = y * src.w + x; val p = src.px[i]
                val dx = x - cx; val dy = y - cy
                val d = sqrt(dx * dx + dy * dy) / maxD
                val f = 1f - amount * 0.8f * (d * d).coerceIn(0f, 1f)
                out.px[i] = argb(p.a(), clamp255(p.r() * f), clamp255(p.g() * f), clamp255(p.b() * f))
            }
        }
        return out
    }
}

object Pipeline {
    fun global(src: Img, a: Adjustments, scale: Float = 1f): Img {
        if (a.isIdentity) return src
        var img = Tone.apply(src, a)
        img = Detail.denoise(img, a.denoise)
        img = Detail.clarity(img, a.clarity)
        img = Detail.sharpen(img, a.sharpen, max(1, (2 * scale).roundToInt()))
        img = Detail.vignette(img, a.vignette)
        return img
    }
}
