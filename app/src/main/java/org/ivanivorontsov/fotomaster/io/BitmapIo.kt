package org.ivanivorontsov.fotomaster.io

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.ivanivorontsov.fotomaster.core.Img
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BitmapIo {
    fun toImg(b: Bitmap): Img {
        val bm = if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)
        val img = Img(bm.width, bm.height)
        bm.getPixels(img.px, 0, bm.width, 0, 0, bm.width, bm.height)
        return img
    }

    fun toBitmap(img: Img): Bitmap {
        val b = Bitmap.createBitmap(img.w, img.h, Bitmap.Config.ARGB_8888)
        b.setPixels(img.px, 0, img.w, 0, 0, img.w, img.h)
        return b
    }

    fun bounds(ctx: Context, uri: Uri): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        val rot = orientation(ctx, uri)
        return if (rot == 90 || rot == 270) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }

    fun orientation(ctx: Context, uri: Uri): Int = try {
        ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    } catch (e: Exception) { 0 }

    fun decode(ctx: Context, uri: Uri, maxSide: Int = 0): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        if (maxSide > 0) while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bm = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Не удалось прочитать изображение")
        val rot = orientation(ctx, uri)
        if (rot != 0) bm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        if (maxSide > 0 && maxOf(bm.width, bm.height) > maxSide) {
            val s = maxSide.toFloat() / maxOf(bm.width, bm.height)
            bm = Bitmap.createScaledBitmap(bm, (bm.width * s).toInt().coerceAtLeast(1), (bm.height * s).toInt().coerceAtLeast(1), true)
        }
        return bm
    }

    val EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
    )

    fun copyExif(from: ExifInterface?, toFile: File, w: Int, h: Int, keepGps: Boolean) {
        val dst = ExifInterface(toFile.absolutePath)
        if (from != null) for (t in EXIF_TAGS) {
            if (!keepGps && t.startsWith("GPS")) continue
            from.getAttribute(t)?.let { dst.setAttribute(t, it) }
        }
        dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        dst.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, w.toString())
        dst.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, h.toString())
        dst.setAttribute(ExifInterface.TAG_SOFTWARE, "FotoMaster 1.0")
        dst.saveAttributes()
    }

    enum class Format(val ext: String, val mime: String) { JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp") }

    fun save(ctx: Context, bm: Bitmap, srcUri: Uri?, format: Format, quality: Int, keepExif: Boolean, keepGps: Boolean): Uri {
        val name = "FotoMaster_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + format.ext
        val tmp = File(ctx.cacheDir, name)
        tmp.outputStream().use { os ->
            val cf = when (format) { Format.JPEG -> Bitmap.CompressFormat.JPEG; Format.PNG -> Bitmap.CompressFormat.PNG
                Format.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP }
            bm.compress(cf, quality, os)
        }
        if (keepExif && format != Format.PNG) {
            val srcExif = srcUri?.let { u -> try { ctx.contentResolver.openInputStream(u)?.use { ExifInterface(it) } } catch (e: Exception) { null } }
            try { copyExif(srcExif, tmp, bm.width, bm.height, keepGps) } catch (_: Exception) { }
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FotoMaster"); put(MediaStore.Images.Media.IS_PENDING, 1) }
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Не удалось создать файл в галерее")
        ctx.contentResolver.openOutputStream(uri)!!.use { os -> tmp.inputStream().use { it.copyTo(os) } }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        tmp.delete()
        return uri
    }
}
