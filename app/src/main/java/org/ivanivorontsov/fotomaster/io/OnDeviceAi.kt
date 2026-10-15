package org.ivanivorontsov.fotomaster.io

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

object OnDeviceAi {
    private val segmenter by lazy {
        Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).enableRawSizeMask().build())
    }
    private val faces by lazy {
        FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
    }

    data class Mask(val w: Int, val h: Int, val data: FloatArray)

    fun personMask(bm: Bitmap): Mask {
        val res = Tasks.await(segmenter.process(InputImage.fromBitmap(bm, 0)))
        val buf = res.buffer; buf.rewind()
        val w = res.width; val h = res.height
        val out = FloatArray(w * h) { buf.float }
        return Mask(w, h, out)
    }

    fun faceCount(bm: Bitmap): Int = try { Tasks.await(faces.process(InputImage.fromBitmap(bm, 0))).size } catch (e: Exception) { 0 }
}
