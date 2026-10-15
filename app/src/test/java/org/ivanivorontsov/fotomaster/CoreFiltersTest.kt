package org.ivanivorontsov.fotomaster

import org.ivanivorontsov.fotomaster.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CoreFiltersTest {
    private fun solid(w: Int, h: Int, c: Int) = Img(w, h, IntArray(w * h) { c })
    private fun gradient(w: Int = 64, h: Int = 48) = Img(w, h, IntArray(w * h) { i -> val x = i % w; val y = i / w; argb(255, x * 255 / (w - 1), y * 255 / (h - 1), 128) })
    private fun meanLuma(img: Img) = img.px.map { luma(it.r(), it.g(), it.b()) }.average()

    @Test fun identityAdjustmentsReturnSameImage() {
        val g = gradient(); val out = Pipeline.global(g, Adjustments())
        assertArrayEquals(g.px, out.px)
    }

    @Test fun exposurePlusOneBrightens() {
        val g = solid(8, 8, argb(255, 60, 60, 60))
        val out = Tone.apply(g, Adjustments(exposure = 1f))
        assertNear(120, out[0, 0].r(), 2)
    }

    @Test fun contrastIncreasesSpread() {
        val g = gradient(); val out = Tone.apply(g, Adjustments(contrast = 0.5f))
        val sd = { im: Img -> val m = meanLuma(im); im.px.map { val l = luma(it.r(), it.g(), it.b()) - m; l * l }.average() }
        assertTrue(sd(out) > sd(g))
    }

    @Test fun saturationMinusOneIsGray() {
        val out = Tone.apply(solid(4, 4, argb(255, 200, 50, 30)), Adjustments(saturation = -1f))
        val p = out[0, 0]; assertTrue(abs(p.r() - p.g()) <= 1 && abs(p.g() - p.b()) <= 1)
    }

    @Test fun temperatureWarmsAndCools() {
        val gray = solid(4, 4, argb(255, 128, 128, 128))
        val warm = Tone.apply(gray, Adjustments(temperature = 0.8f))[0, 0]
        val cold = Tone.apply(gray, Adjustments(temperature = -0.8f))[0, 0]
        assertTrue(warm.r() > warm.b()); assertTrue(cold.b() > cold.r())
    }

    @Test fun levelsStretchAndCurveIdentity() {
        val lut = Tone.buildLut(Adjustments(levels = Levels(inBlack = 50, inWhite = 200)))
        assertEquals(0, lut[50]); assertEquals(255, lut[200]); assertEquals(0, lut[10])
        val id = Curve.lut(Adjustments.IDENTITY_CURVE); for (v in 0..255) assertEquals(v, id[v])
    }

    @Test fun curveIsMonotonicForSCurve() {
        val lut = Curve.lut(listOf(0f to 0f, 0.25f to 0.18f, 0.75f to 0.85f, 1f to 1f))
        for (v in 1..255) assertTrue("at $v", lut[v] >= lut[v - 1])
        assertTrue(lut[64] < 64); assertTrue(lut[192] > 192)
    }

    @Test fun hslShiftsOnlyTargetBand() {
        val red = argb(255, 220, 30, 30); val blue = argb(255, 30, 30, 220)
        val img = Img(2, 1, intArrayOf(red, blue))
        val hsl = MutableList(24) { 0f }; hsl[0 * 3 + 1] = -1f
        val out = Tone.apply(img, Adjustments(hsl = hsl))
        val r = out.px[0]; assertTrue("red desaturated", abs(r.r() - r.g()) < 30)
        assertEquals(blue, out.px[1])
    }

    @Test fun hslRoundTrip() {
        val t = FloatArray(3)
        for (c in listOf(argb(255, 10, 200, 90), argb(255, 255, 0, 0), argb(255, 33, 33, 200))) {
            rgbToHsl(c.r(), c.g(), c.b(), t); val back = hslToRgb(t[0], t[1], t[2])
            assertNear(c.r(), back.r(), 1); assertNear(c.g(), back.g(), 1); assertNear(c.b(), back.b(), 1)
        }
    }

    @Test fun boxBlurPreservesSolidAndSmoothsEdge() {
        val s = solid(20, 20, argb(255, 100, 150, 200)); assertArrayEquals(s.px, boxBlur(s, 3).px)
        val e = Img(20, 1, IntArray(20) { if (it < 10) argb(255, 0, 0, 0) else argb(255, 255, 255, 255) })
        val b = boxBlur(e, 2); assertTrue(b.px[9].r() in 1..254)
    }

    @Test fun sharpenIncreasesEdgeContrastAndDenoiseReducesNoise() {
        val e = Img(40, 40, IntArray(1600) { if (it % 40 < 20) argb(255, 80, 80, 80) else argb(255, 170, 170, 170) })
        val sh = Detail.sharpen(e, 1f, 2)
        assertTrue(sh[19, 20].r() < 80 && sh[20, 20].r() > 170)
        val rnd = java.util.Random(1)
        val noisy = Img(64, 64, IntArray(4096) { val v = 128 + rnd.nextInt(21) - 10; argb(255, v, v, v) })
        val sd = { im: Img -> val m = im.px.map { it.r() }.average(); im.px.map { (it.r() - m) * (it.r() - m) }.average() }
        assertTrue(sd(Detail.denoise(noisy, 0.8f)) < sd(noisy) * 0.6)
    }

    @Test fun autoEnhanceFixesDarkWarmImage() {
        val rnd = java.util.Random(2)
        val dark = Img(80, 60, IntArray(4800) { val v = 20 + rnd.nextInt(60); argb(255, (v * 1.3).toInt().coerceAtMost(255), v, (v * 0.7).toInt()) })
        val a = AutoEnhance.suggest(dark)
        assertTrue("exposure up", a.exposure > 0f); assertTrue("cooler", a.temperature < 0f)
        assertTrue(meanLuma(Pipeline.global(dark, a)) > meanLuma(dark) + 15)
    }

    @Test fun autoEnhanceLeavesGoodImageMostlyAlone() {
        val a = AutoEnhance.suggest(gradient(128, 128))
        assertTrue(abs(a.exposure) < 0.35f); assertTrue(abs(a.temperature) < 0.15f)
    }

    @Test fun presetsAreValidAndDistinct() {
        assertTrue(Presets.builtIn.size >= 10)
        assertEquals(Presets.builtIn.size, Presets.builtIn.map { it.name }.toSet().size)
        Presets.builtIn.forEach { assertFalse(it.name, it.adj.isIdentity); Pipeline.global(gradient(), it.adj) }
    }
}
