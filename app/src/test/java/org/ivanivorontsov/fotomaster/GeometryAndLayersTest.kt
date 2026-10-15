package org.ivanivorontsov.fotomaster

import org.ivanivorontsov.fotomaster.core.*
import org.junit.Assert.*
import org.junit.Test

class GeometryAndLayersTest {
    private fun numbered(w: Int, h: Int) = Img(w, h, IntArray(w * h) { argb(255, it % w, it / w, 0) })

    @Test fun rotate90FourTimesIsIdentity() {
        val img = numbered(7, 5); var r = img; repeat(4) { r = GeometryOps.rotate90(r, 1) }
        assertArrayEquals(img.px, r.px)
        val r1 = GeometryOps.rotate90(img, 1); assertEquals(5, r1.w); assertEquals(7, r1.h)
        assertEquals(img[0, 4], r1[0, 0])
    }

    @Test fun cropKeepsExactPixels() {
        val img = numbered(100, 80); val c = GeometryOps.crop(img, RectN(0.1f, 0.25f, 0.6f, 0.75f))
        assertEquals(50, c.w); assertEquals(40, c.h); assertEquals(img[10, 20], c[0, 0])
    }

    @Test fun outToSourceInvertsGeometry() {
        val w = 100; val h = 60; val img = numbered(w, h)
        for (g in listOf(Geometry(rot90 = 1), Geometry(rot90 = 2, flipH = true), Geometry(rot90 = 3, crop = RectN(0.2f, 0.1f, 0.8f, 0.9f)), Geometry(crop = RectN(0.5f, 0.5f, 1f, 1f)))) {
            val out = GeometryOps.apply(img, g)
            for ((xo, yo) in listOf(0.1f to 0.2f, 0.5f to 0.5f, 0.9f to 0.7f)) {
                val p = out[(xo * out.w).toInt(), (yo * out.h).toInt()]
                val s = GeometryOps.outToSource(xo, yo, g, w, h)
                assertEquals("$g x", p.r().toFloat(), s.x * w, 1.5f); assertEquals("$g y", p.g().toFloat(), s.y * h, 1.5f)
            }
        }
    }

    @Test fun historyUndoRedoAndBranching() {
        val h = History(Doc())
        h.push("a", Doc(adj = Adjustments(exposure = 0.1f))); h.push("b", Doc(adj = Adjustments(exposure = 0.2f)))
        assertEquals(0.1f, h.undo().adj.exposure); assertEquals(0f, h.undo().adj.exposure); assertFalse(h.canUndo)
        assertEquals(0.1f, h.redo().adj.exposure)
        h.push("c", Doc(adj = Adjustments(contrast = 0.3f))); assertFalse(h.canRedo); assertEquals(3, h.entries.size)
        assertEquals(0f, h.jump(0).adj.exposure)
    }

    @Test fun healRemovesSpot() {
        val img = Img(60, 60, IntArray(3600) { argb(255, 120, 130, 140) })
        for (y in 27..33) for (x in 27..33) img.px[y * 60 + x] = argb(255, 0, 0, 0)
        Raster.heal(img, 0.5f, 0.5f, 0.1f)
        val p = img[30, 30]; assertTrue("healed ${p.r()}", p.r() in 105..135)
    }

    @Test fun cloneCopiesFromOffset() {
        val img = Img(40, 20, IntArray(800) { if (it % 40 < 20) argb(255, 255, 0, 0) else argb(255, 0, 0, 255) })
        Raster.clone(img, RetouchOp.Clone(-0.5f, 0f, Stroke(listOf(PointN(0.75f, 0.5f), PointN(0.76f, 0.5f)), 0.05f, hardness = 1f)))
        assertEquals(255, img[30, 10].r())
    }

    @Test fun paintLayerAndEraser() {
        val base = Img(50, 50, IntArray(2500) { argb(255, 0, 0, 0) })
        val red = 0xFFFF0000.toInt()
        val l = Layer.Paint(1, strokes = listOf(Stroke(listOf(PointN(0.1f, 0.5f), PointN(0.9f, 0.5f)), 0.05f, red, 1f),
            Stroke(listOf(PointN(0.5f, 0.3f), PointN(0.5f, 0.7f)), 0.06f, red, 1f, erase = true)))
        val out = Renderer.render(base, Doc(layers = listOf(l)))
        assertEquals(255, out[10, 25].r()); assertEquals(0, out[25, 25].r())
    }

    @Test fun radialLocalAdjustmentOnlyInside() {
        val base = Img(50, 50, IntArray(2500) { argb(255, 100, 100, 100) })
        val l = Layer.Local(1, shape = MaskShape.RADIAL, cx = 0.5f, cy = 0.5f, radius = 0.2f, feather = 0.1f, adj = Adjustments(exposure = 1f))
        val out = Renderer.render(base, Doc(layers = listOf(l)))
        assertTrue(out[25, 25].r() > 180); assertNear(100, out[2, 2].r(), 2)
        val inv = Renderer.render(base, Doc(layers = listOf(l.copy(invert = true))))
        assertTrue(inv[2, 2].r() > 180)
    }

    @Test fun cutoutTransparentAndColor() {
        val base = Img(20, 20, IntArray(400) { argb(255, 50, 60, 70) })
        val assets = object : Assets {
            override fun mask(ref: String, w: Int, h: Int) = FloatArray(w * h) { if (it % w < w / 2) 1f else 0f }
            override fun image(ref: String, w: Int, h: Int): Img? = null
        }
        val t = Renderer.render(base, Doc(layers = listOf(Layer.Cutout(1, maskRef = "m", feather = 0f))), assets)
        assertEquals(255, t[2, 5].a()); assertEquals(0, t[18, 5].a())
        val c = Renderer.render(base, Doc(layers = listOf(Layer.Cutout(1, maskRef = "m", mode = BgMode.COLOR, color = 0xFFFFFFFF.toInt(), feather = 0f))), assets)
        assertEquals(255, c[18, 5].r())
    }

    @Test fun pictureLayerOpacityAndErase() {
        val base = Img(20, 20, IntArray(400) { argb(255, 0, 0, 0) })
        val assets = object : Assets {
            override fun mask(ref: String, w: Int, h: Int): FloatArray? = null
            override fun image(ref: String, w: Int, h: Int) = Img(w, h, IntArray(w * h) { argb(255, 200, 200, 200) })
        }
        val half = Renderer.render(base, Doc(layers = listOf(Layer.Picture(1, opacity = 0.5f, imageRef = "x"))), assets)
        assertNear(100, half[5, 5].r(), 2)
        val er = Renderer.render(base, Doc(layers = listOf(Layer.Picture(1, imageRef = "x", eraseStrokes = listOf(Stroke(listOf(PointN(0.25f, 0.25f)), 0.1f, hardness = 1f))))), assets)
        assertEquals(0, er[5, 5].r()); assertEquals(200, er[15, 15].r())
    }

    @Test fun resizeDownKeepsAverage() {
        val img = Img(100, 100, IntArray(10000) { if ((it % 100 + it / 100) % 2 == 0) argb(255, 0, 0, 0) else argb(255, 255, 255, 255) })
        val r = resize(img, 10, 10); assertNear(127, r[5, 5].r(), 15)
    }
}
