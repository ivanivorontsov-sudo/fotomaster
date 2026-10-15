package org.ivanivorontsov.fotomaster.core

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Raster {
    fun strokeCoverage(w: Int, h: Int, strokes: List<Stroke>, into: FloatArray = FloatArray(w * h)): FloatArray {
        val dim = max(w, h).toFloat()
        for (s in strokes) {
            val rad = max(1f, s.radius * dim)
            val pts = densify(s.points.map { PointN(it.x * w, it.y * h) }, rad / 4f)
            for (p in pts) stamp(into, w, h, p.x, p.y, rad, s.hardness, if (s.erase) -1f else 1f)
        }
        return into
    }

    fun densify(pts: List<PointN>, step: Float): List<PointN> {
        if (pts.size < 2) return pts
        val out = ArrayList<PointN>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val d = hypot(b.x - a.x, b.y - a.y); val n = max(1, (d / max(0.5f, step)).toInt())
            for (k in 0 until n) { val t = k / n.toFloat(); out.add(PointN(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)) }
        }
        out.add(pts.last()); return out
    }

    fun stamp(cov: FloatArray, w: Int, h: Int, cx: Float, cy: Float, rad: Float, hardness: Float, sign: Float) {
        val x0 = max(0, (cx - rad).toInt()); val x1 = min(w - 1, (cx + rad).toInt())
        val y0 = max(0, (cy - rad).toInt()); val y1 = min(h - 1, (cy + rad).toInt())
        val core = rad * hardness.coerceIn(0f, 0.99f)
        for (y in y0..y1) for (x in x0..x1) {
            val d = hypot(x - cx, y - cy); if (d > rad) continue
            val a = if (d <= core) 1f else 1f - (d - core) / (rad - core)
            val i = y * w + x
            cov[i] = if (sign > 0) max(cov[i], a) else min(cov[i], 1f - a)
        }
    }

    fun paintLayer(base: Img, l: Layer.Paint) {
        val w = base.w; val h = base.h; val dim = max(w, h).toFloat()
        val colorBuf = IntArray(w * h); val alpha = FloatArray(w * h)
        for (s in l.strokes) {
            val rad = max(1f, s.radius * dim)
            val pts = densify(s.points.map { PointN(it.x * w, it.y * h) }, rad / 4f)
            val cov = FloatArray(w * h)
            for (p in pts) stamp(cov, w, h, p.x, p.y, rad, s.hardness, 1f)
            val ca = s.color.a() / 255f
            for (i in cov.indices) {
                val c = cov[i]; if (c <= 0f) continue
                if (s.erase) alpha[i] = alpha[i] * (1f - c)
                else { val na = c * ca; colorBuf[i] = mix(colorBuf[i], s.color, if (alpha[i] + na > 0) na / min(1f, alpha[i] + na) else 1f); alpha[i] = min(1f, alpha[i] + na * (1 - alpha[i])) }
            }
        }
        for (i in base.px.indices) {
            val a = alpha[i] * l.opacity; if (a <= 0f) continue
            base.px[i] = blendPx(base.px[i], colorBuf[i], a, l.blend)
        }
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val tt = clamp01(t)
        return argb(clamp255(a.a() + (b.a() - a.a()) * tt), clamp255(a.r() + (b.r() - a.r()) * tt), clamp255(a.g() + (b.g() - a.g()) * tt), clamp255(a.b() + (b.b() - a.b()) * tt))
    }

    fun blendPx(dst: Int, src: Int, a: Float, mode: Blend): Int {
        fun ch(d: Int, s: Int): Int = when (mode) {
            Blend.NORMAL -> s
            Blend.MULTIPLY -> d * s / 255
            Blend.SCREEN -> 255 - (255 - d) * (255 - s) / 255
            Blend.OVERLAY -> if (d < 128) 2 * d * s / 255 else 255 - 2 * (255 - d) * (255 - s) / 255
        }
        val r = ch(dst.r(), src.r()); val g = ch(dst.g(), src.g()); val b = ch(dst.b(), src.b())
        return argb(max(dst.a(), clamp255(a * 255f)), clamp255(dst.r() + (r - dst.r()) * a), clamp255(dst.g() + (g - dst.g()) * a), clamp255(dst.b() + (b - dst.b()) * a))
    }

    fun heal(img: Img, cxN: Float, cyN: Float, rN: Float) {
        val w = img.w; val h = img.h
        val rad = max(2f, rN * max(w, h)); val cx = cxN * w; val cy = cyN * h
        val x0 = max(0, (cx - rad - 2).toInt()); val x1 = min(w - 1, (cx + rad + 2).toInt())
        val y0 = max(0, (cy - rad - 2).toInt()); val y1 = min(h - 1, (cy + rad + 2).toInt())
        val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
        if (bw < 3 || bh < 3) return
        val inside = BooleanArray(bw * bh)
        val rr = FloatArray(bw * bh); val gg = FloatArray(bw * bh); val bb = FloatArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            val i = y * bw + x; val p = img[x + x0, y + y0]
            rr[i] = p.r().toFloat(); gg[i] = p.g().toFloat(); bb[i] = p.b().toFloat()
            inside[i] = hypot(x + x0 - cx, y + y0 - cy) <= rad
        }
        val ring = ArrayList<Int>()
        for (y in 0 until bh) for (x in 0 until bw) { val i = y * bw + x; if (!inside[i]) ring.add(i) }
        if (ring.isEmpty()) return
        var mr = 0f; var mg = 0f; var mb = 0f
        for (i in ring) { mr += rr[i]; mg += gg[i]; mb += bb[i] }
        mr /= ring.size; mg /= ring.size; mb /= ring.size
        for (i in inside.indices) if (inside[i]) { rr[i] = mr; gg[i] = mg; bb[i] = mb }
        repeat(max(40, rad.toInt() * 3)) {
            for (y in 1 until bh - 1) for (x in 1 until bw - 1) {
                val i = y * bw + x; if (!inside[i]) continue
                rr[i] = (rr[i - 1] + rr[i + 1] + rr[i - bw] + rr[i + bw]) / 4f
                gg[i] = (gg[i - 1] + gg[i + 1] + gg[i - bw] + gg[i + bw]) / 4f
                bb[i] = (bb[i - 1] + bb[i + 1] + bb[i - bw] + bb[i + bw]) / 4f
            }
        }
        val seed = (cx * 31 + cy * 17).toInt()
        for (y in 0 until bh) for (x in 0 until bw) {
            val i = y * bw + x; if (!inside[i]) continue
            val d = hypot(x + x0 - cx, y + y0 - cy) / rad
            val t = if (d < 0.75f) 1f else (1f - (d - 0.75f) / 0.25f)
            val n = (((x * 73856093) xor (y * 19349663) xor seed) and 7) - 3.5f
            val o = img[x + x0, y + y0]
            img.px[(y + y0) * w + x + x0] = argb(o.a(), clamp255(o.r() + (rr[i] + n - o.r()) * t), clamp255(o.g() + (gg[i] + n - o.g()) * t), clamp255(o.b() + (bb[i] + n - o.b()) * t))
        }
    }

    fun clone(img: Img, op: RetouchOp.Clone) {
        val w = img.w; val h = img.h
        val cov = strokeCoverage(w, h, listOf(op.stroke))
        val src = img.px.copyOf()
        val ox = (op.dx * w).toInt(); val oy = (op.dy * h).toInt()
        for (y in 0 until h) for (x in 0 until w) {
            val c = cov[y * w + x]; if (c <= 0f) continue
            val sx = (x + ox).coerceIn(0, w - 1); val sy = (y + oy).coerceIn(0, h - 1)
            img.px[y * w + x] = mix(img.px[y * w + x], src[sy * w + sx], c)
        }
    }

    fun shapeMask(w: Int, h: Int, l: Layer.Local, external: FloatArray?): FloatArray {
        val m = when (l.shape) {
            MaskShape.BRUSH -> strokeCoverage(w, h, l.strokes)
            MaskShape.RADIAL -> FloatArray(w * h).also { m ->
                val cx = l.cx * w; val cy = l.cy * h; val rad = l.radius * max(w, h); val inner = rad * (1f - l.feather)
                for (y in 0 until h) for (x in 0 until w) { val d = hypot(x - cx, y - cy)
                    m[y * w + x] = if (d <= inner) 1f else if (d >= rad) 0f else 1f - (d - inner) / max(1f, rad - inner) }
            }
            MaskShape.LINEAR -> FloatArray(w * h).also { m ->
                val ang = Math.toRadians(l.angle.toDouble()); val nx = cos(ang).toFloat(); val ny = sin(ang).toFloat()
                val cx = l.cx * w; val cy = l.cy * h; val span = max(1f, l.feather * max(w, h) * 0.5f)
                for (y in 0 until h) for (x in 0 until w) { val d = ((x - cx) * nx + (y - cy) * ny) / span
                    m[y * w + x] = clamp01(0.5f - d * 0.5f) }
            }
            MaskShape.PERSON -> external?.copyOf() ?: FloatArray(w * h)
            MaskShape.SKY -> FloatArray(w * h)
        }
        if (l.shape != MaskShape.BRUSH && l.strokes.isNotEmpty()) {
            val add = strokeCoverage(w, h, l.strokes.filter { !it.erase })
            val sub = strokeCoverage(w, h, l.strokes.filter { it.erase }.map { it.copy(erase = false) })
            for (i in m.indices) m[i] = clamp01(max(m[i], add[i]) - sub[i])
        }
        if (l.invert) for (i in m.indices) m[i] = 1f - m[i]
        return m
    }

    fun skyMask(img: Img): FloatArray {
        val m = FloatArray(img.w * img.h)
        val tmp = FloatArray(3)
        for (i in m.indices) {
            val p = img.px[i]; rgbToHsl(p.r(), p.g(), p.b(), tmp)
            val y = (i / img.w).toFloat() / img.h
            val blue = Tone.bandWeight(tmp[0], 210f).coerceAtLeast(Tone.bandWeight(tmp[0], 190f))
            val bright = clamp01((tmp[2] - 0.35f) * 3f)
            val pos = clamp01(1.2f - y * 1.6f)
            val overcast = if (tmp[1] < 0.15f && tmp[2] > 0.75f) 0.8f else 0f
            m[i] = clamp01(max(blue * bright, overcast) * pos * 1.4f)
        }
        return m
    }

    fun blurMask(m: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r <= 0) return m
        val img = Img(w, h, IntArray(w * h) { argb(255, clamp255(m[it] * 255f), 0, 0) })
        val b = boxBlur(img, r)
        return FloatArray(w * h) { b.px[it].r() / 255f }
    }

    fun resizeMask(m: FloatArray, mw: Int, mh: Int, w: Int, h: Int): FloatArray {
        if (mw == w && mh == h) return m
        val out = FloatArray(w * h)
        val sx = mw.toFloat() / w; val sy = mh.toFloat() / h
        for (y in 0 until h) for (x in 0 until w) {
            val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, mw - 1f); val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, mh - 1f)
            val x0 = fx.toInt(); val y0 = fy.toInt(); val x1 = min(x0 + 1, mw - 1); val y1 = min(y0 + 1, mh - 1)
            val dx = fx - x0; val dy = fy - y0
            val top = m[y0 * mw + x0] * (1 - dx) + m[y0 * mw + x1] * dx
            val bot = m[y1 * mw + x0] * (1 - dx) + m[y1 * mw + x1] * dx
            out[y * w + x] = top * (1 - dy) + bot * dy
        }
        return out
    }

    fun distanceSoft(t: Float) = 1f / (1f + exp(-t))
    fun sq(v: Float) = sqrt(v)
}
