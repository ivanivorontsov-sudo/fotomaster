package org.ivanivorontsov.fotomaster

import org.ivanivorontsov.fotomaster.core.*
import org.junit.Test
import java.io.File

class RealPhotoTest {
    @Test fun autoEnhanceOnTestPhoto() {
        val f = listOf(File("../screens/test-photo.rgb"), File("screens/test-photo.rgb")).firstOrNull { it.exists() } ?: return
        val b = f.readBytes(); val w = 1600; val h = 1200
        val img = Img(w, h, IntArray(w * h) { argb(255, b[it * 3].toInt() and 255, b[it * 3 + 1].toInt() and 255, b[it * 3 + 2].toInt() and 255) })
        val a = AutoEnhance.suggest(img)
        val t = System.nanoTime(); val out = Renderer.render(img, Doc(adj = a), scale = 1600f / 2400f)
        println("REAL adj=$a ms=${(System.nanoTime() - t) / 1e6}")
        val ob = ByteArray(w * h * 3); for (i in 0 until w * h) { val p = out.px[i]; ob[i * 3] = p.r().toByte(); ob[i * 3 + 1] = p.g().toByte(); ob[i * 3 + 2] = p.b().toByte() }
        File(f.parentFile, "jvm-out.rgb").writeBytes(ob)
    }
}
