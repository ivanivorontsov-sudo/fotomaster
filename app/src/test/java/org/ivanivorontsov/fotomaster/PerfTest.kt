package org.ivanivorontsov.fotomaster

import org.ivanivorontsov.fotomaster.core.*
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfTest {
    @Test fun previewRenderIsFastEnoughForSliders() {
        val rnd = java.util.Random(3)
        val img = Img(1600, 1200, IntArray(1600 * 1200) { argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) })
        val toneOnly = Adjustments(exposure = 0.3f, contrast = 0.1f, temperature = 0.1f, vibrance = 0.2f, shadows = 0.2f)
        val full = AutoEnhance.suggest(img).copy(clarity = 0.2f, denoise = 0.3f, sharpen = 0.3f)
        repeat(2) { Pipeline.global(img, toneOnly); Pipeline.global(img, full) }
        val t0 = System.nanoTime(); repeat(5) { Pipeline.global(img, toneOnly) }; val tone = (System.nanoTime() - t0) / 5e6
        val t1 = System.nanoTime(); repeat(3) { Pipeline.global(img, full) }; val all = (System.nanoTime() - t1) / 3e6
        println("PERF 1600x1200 tone-only=${"%.0f".format(tone)} ms, full(auto+clarity+denoise+sharpen)=${"%.0f".format(all)} ms, threads=${Par.threads}")
        assertTrue(tone < 400); assertTrue(all < 2500)
    }
}
