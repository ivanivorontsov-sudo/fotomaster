package org.ivanivorontsov.fotomaster

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.exifinterface.media.ExifInterface
import org.ivanivorontsov.fotomaster.core.*
import org.ivanivorontsov.fotomaster.io.BitmapIo
import org.ivanivorontsov.fotomaster.io.ProjectAssets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidIoRobolectricTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun bitmapImgRoundTrip() {
        val img = Img(16, 9, IntArray(144) { argb(255, it, 255 - it, (it * 3) % 256) })
        val back = BitmapIo.toImg(BitmapIo.toBitmap(img))
        assertArrayEquals(img.px, back.px)
    }

    @Test fun exifIsCopiedToExport() {
        val src = File(ctx.cacheDir, "src.jpg"); val dst = File(ctx.cacheDir, "dst.jpg")
        val bm = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        src.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        dst.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "realme"); setAttribute(ExifInterface.TAG_MODEL, "realme 15 5G")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:16 05:00:00"); setAttribute(ExifInterface.TAG_F_NUMBER, "1.8")
            setLatLong(43.115, 131.885); setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes()
        }
        BitmapIo.copyExif(ExifInterface(src.absolutePath), dst, 64, 48, keepGps = true)
        val e = ExifInterface(dst.absolutePath)
        assertEquals("realme", e.getAttribute(ExifInterface.TAG_MAKE)); assertEquals("realme 15 5G", e.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("2026:10:16 05:00:00", e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("1", e.getAttribute(ExifInterface.TAG_ORIENTATION)); assertNotNull(e.latLong)
        assertEquals("FotoMaster 1.1", e.getAttribute(ExifInterface.TAG_SOFTWARE))
        BitmapIo.copyExif(ExifInterface(src.absolutePath), dst, 64, 48, keepGps = false)
        assertEquals("realme", ExifInterface(dst.absolutePath).getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test fun projectAssetsMaskAndImageRoundTrip() {
        val a = ProjectAssets(ctx, "t1")
        a.saveMask("m", FloatArray(100) { if (it % 10 < 5) 1f else 0f }, 10, 10)
        val m = a.mask("m", 10, 10)!!
        assertEquals(1f, m[2], 0.02f); assertEquals(0f, m[8], 0.02f)
        a.saveImage("p", BitmapIo.toBitmap(Img(20, 10, IntArray(200) { argb(255, 10, 20, 30) })))
        val p = a.image("p", 10, 10)!!
        assertEquals(10, p.w); assertNear(20, p[5, 5].g(), 2)
    }
}
