package org.ivanivorontsov.fotomaster.core

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class ImageStats(val p01: Int, val p99: Int, val meanLuma: Float, val meanR: Float, val meanG: Float, val meanB: Float, val meanSat: Float, val noise: Float)

object AutoEnhance {
    fun stats(img: Img): ImageStats {
        val hist = IntArray(256)
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0; var ss = 0.0; var noise = 0.0; var nn = 0
        val step = max(1, (img.w * img.h) / 250_000)
        var i = 0
        while (i < img.px.size) {
            val p = img.px[i]; val r = p.r(); val g = p.g(); val b = p.b()
            val l = luma(r, g, b); hist[l]++
            if (l in 25..230) { sr += r; sg += g; sb += b; n++ }
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            ss += if (mx == 0) 0.0 else (mx - mn) / mx.toDouble()
            if (i + 1 < img.px.size && (i + 1) % img.w != 0) { val q = img.px[i + 1]; noise += kotlin.math.abs(l - luma(q.r(), q.g(), q.b())); nn++ }
            i += step
        }
        val total = hist.sum()
        fun pct(f: Double): Int { var acc = 0; for (v in 0..255) { acc += hist[v]; if (acc >= total * f) return v }; return 255 }
        var lsum = 0.0; for (v in 0..255) lsum += v.toDouble() * hist[v]
        return ImageStats(pct(0.005), pct(0.995), (lsum / max(1, total) / 255.0).toFloat(),
            if (n > 0) (sr / n).toFloat() else 128f, if (n > 0) (sg / n).toFloat() else 128f, if (n > 0) (sb / n).toFloat() else 128f,
            (ss / max(1, total)).toFloat(), (noise / max(1, nn)).toFloat())
    }

    fun suggest(img: Img, hasFaces: Boolean = false): Adjustments {
        val s = stats(img)
        val inBlack = min(s.p01, 40)
        val inWhite = max(s.p99, 200)
        val stretchedMean = ((s.meanLuma * 255f - inBlack) / max(1, inWhite - inBlack)).coerceIn(0.02f, 0.98f)
        val target = 0.46f
        val exposure = (ln(target / stretchedMean) / ln(2f)).coerceIn(-0.8f, 1.0f) * 0.4f
        val gray = (s.meanR + s.meanG + s.meanB) / 3f
        val temperature = ((s.meanB - s.meanR) / max(1f, gray) * 1.6f).coerceIn(-0.35f, 0.35f) * 0.5f
        val tint = ((s.meanR + s.meanB) / 2f - s.meanG).div(max(1f, gray)).times(2.2f).coerceIn(-0.25f, 0.25f) * 0.6f
        val vibrance = if (s.meanSat < 0.25f) 0.3f else if (s.meanSat < 0.4f) 0.15f else 0.03f
        val shadows = if (stretchedMean < 0.35f) 0.35f else 0.12f
        val highlights = if (s.p99 >= 250) -0.35f else -0.1f
        val denoise = if (s.noise > 9f) 0.35f else 0f
        return Adjustments(
            exposure = exposure, contrast = 0.08f, shadows = shadows, highlights = highlights,
            temperature = temperature, tint = tint, vibrance = vibrance,
            saturation = if (hasFaces) -0.03f else 0.04f,
            clarity = if (hasFaces) 0.05f else 0.18f, sharpen = if (hasFaces) 0.2f else 0.35f, denoise = denoise,
            levels = Levels(inBlack = inBlack, inWhite = inWhite),
        )
    }
}
