package org.ivanivorontsov.fotomaster.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Img(val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {
    init { require(px.size == w * h) }
    fun copy() = Img(w, h, px.copyOf())
    operator fun get(x: Int, y: Int) = px[y * w + x]
}

fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
fun Int.a() = (this ushr 24) and 255
fun Int.r() = (this shr 16) and 255
fun Int.g() = (this shr 8) and 255
fun Int.b() = this and 255
fun clamp255(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
fun clamp255(v: Float) = clamp255(v.roundToInt())
fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
fun luma(r: Int, g: Int, b: Int) = (r * 299 + g * 587 + b * 114) / 1000

object Par {
    var threads = max(1, Runtime.getRuntime().availableProcessors())
    fun rows(h: Int, body: (Int, Int) -> Unit) {
        val n = min(threads, max(1, h / 32))
        if (n <= 1) { body(0, h); return }
        val step = (h + n - 1) / n
        val ts = (0 until n).map { i ->
            Thread { body(i * step, min(h, (i + 1) * step)) }.apply { start() }
        }
        ts.forEach { it.join() }
    }
}

fun bilinear(src: Img, fx: Float, fy: Float): Int {
    val x = fx.coerceIn(0f, src.w - 1f); val y = fy.coerceIn(0f, src.h - 1f)
    val x0 = x.toInt(); val y0 = y.toInt()
    val x1 = min(x0 + 1, src.w - 1); val y1 = min(y0 + 1, src.h - 1)
    val dx = x - x0; val dy = y - y0
    val p00 = src[x0, y0]; val p10 = src[x1, y0]; val p01 = src[x0, y1]; val p11 = src[x1, y1]
    fun ch(s: Int): Int {
        val a = (p00 shr s) and 255; val b = (p10 shr s) and 255
        val c = (p01 shr s) and 255; val d = (p11 shr s) and 255
        val top = a + (b - a) * dx; val bot = c + (d - c) * dx
        return clamp255(top + (bot - top) * dy)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

fun resize(src: Img, nw: Int, nh: Int): Img {
    val out = Img(nw, nh)
    val sx = src.w.toFloat() / nw; val sy = src.h.toFloat() / nh
    Par.rows(nh) { y0, y1 ->
        for (y in y0 until y1) for (x in 0 until nw) {
            out.px[y * nw + x] = if (sx > 1.5f || sy > 1.5f) areaSample(src, x * sx, y * sy, sx, sy)
            else bilinear(src, (x + 0.5f) * sx - 0.5f, (y + 0.5f) * sy - 0.5f)
        }
    }
    return out
}

private fun areaSample(src: Img, fx: Float, fy: Float, sx: Float, sy: Float): Int {
    val x0 = fx.toInt(); val y0 = fy.toInt()
    val x1 = min(src.w, (fx + sx).toInt().coerceAtLeast(x0 + 1)); val y1 = min(src.h, (fy + sy).toInt().coerceAtLeast(y0 + 1))
    var a = 0L; var r = 0L; var g = 0L; var b = 0L; var n = 0
    for (y in y0 until y1) for (x in x0 until x1) { val p = src[x, y]; a += p.a(); r += p.r(); g += p.g(); b += p.b(); n++ }
    if (n == 0) return src[min(x0, src.w - 1), min(y0, src.h - 1)]
    return argb((a / n).toInt(), (r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

fun boxBlur(src: Img, radius: Int): Img {
    if (radius <= 0) return src.copy()
    val w = src.w; val h = src.h
    val tmp = IntArray(w * h); val out = IntArray(w * h)
    val div = radius * 2 + 1
    Par.rows(h) { y0, y1 ->
        for (y in y0 until y1) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) { val p = src.px[y * w + i.coerceIn(0, w - 1)]; a += p.a(); r += p.r(); g += p.g(); b += p.b() }
            for (x in 0 until w) {
                tmp[y * w + x] = argb(a / div, r / div, g / div, b / div)
                val pOut = src.px[y * w + (x - radius).coerceIn(0, w - 1)]
                val pIn = src.px[y * w + (x + radius + 1).coerceIn(0, w - 1)]
                a += pIn.a() - pOut.a(); r += pIn.r() - pOut.r(); g += pIn.g() - pOut.g(); b += pIn.b() - pOut.b()
            }
        }
    }
    val cols = w
    Par.rows(cols) { x0, x1 ->
        for (x in x0 until x1) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) { val p = tmp[i.coerceIn(0, h - 1) * w + x]; a += p.a(); r += p.r(); g += p.g(); b += p.b() }
            for (y in 0 until h) {
                out[y * w + x] = argb(a / div, r / div, g / div, b / div)
                val pOut = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                val pIn = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                a += pIn.a() - pOut.a(); r += pIn.r() - pOut.r(); g += pIn.g() - pOut.g(); b += pIn.b() - pOut.b()
            }
        }
    }
    return Img(w, h, out)
}

fun gaussLike(src: Img, radius: Int): Img {
    val r = max(1, radius / 2)
    return boxBlur(boxBlur(src, r), r)
}
