package org.ivanivorontsov.fotomaster

import org.ivanivorontsov.fotomaster.ai.Providers
import org.ivanivorontsov.fotomaster.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JsonAndProvidersTest {
    @Test fun docRoundTrip() {
        val d = Doc(Geometry(RectN(0.1f, 0.2f, 0.9f, 0.8f), 1, true, 3.5f),
            Adjustments(exposure = 0.3f, curve = listOf(0f to 0f, 0.5f to 0.6f, 1f to 1f), levels = Levels(10, 240, 1.2f, 5, 250), hsl = List(24) { it / 100f }),
            listOf(Layer.Paint(1, strokes = listOf(Stroke(listOf(PointN(0.1f, 0.2f), PointN(0.3f, 0.4f)), 0.02f, 0xFFFF0000.toInt(), 0.5f, false))),
                Layer.Text(2, text = "Привет", x = 0.3f), Layer.Retouch(3, ops = listOf(RetouchOp.Heal(0.5f, 0.5f, 0.01f), RetouchOp.Clone(0.1f, 0f, Stroke(listOf(PointN(0.2f, 0.2f)), 0.03f)))),
                Layer.Local(4, shape = MaskShape.RADIAL, adj = Adjustments(exposure = 1f), maskRef = null), Layer.Cutout(5, maskRef = "m1", mode = BgMode.BLUR),
                Layer.Picture(6, imageRef = "ai_1", opacity = 0.7f)))
        val back = DocJson.doc(DocJson.doc(d))
        assertEquals(d, back)
    }

    private fun providersFile(): File = listOf(File("src/main/assets/ai/providers.json"), File("app/src/main/assets/ai/providers.json")).first { it.exists() }

    @Test fun providersConfigComplete() {
        val list = Providers.parse(providersFile().readText())
        assertEquals(listOf("qwen", "alice", "giga"), list.map { it.id })
        for (p in list) {
            val steps = p.raw.getJSONArray("steps"); val kinds = (0 until steps.length()).map { steps.getJSONObject(it).getString("do") }
            assertTrue(p.id, kinds.containsAll(listOf("wait", "upload", "type", "send", "result")))
            assertTrue(p.startUrl.startsWith("https://"))
            for (i in 0 until steps.length()) { val s = steps.getJSONObject(i)
                listOf("itemText", "fallbackText", "activeText", "srcPattern", "text").forEach { k -> if (s.has(k)) Regex(s.getString(k)) } }
        }
    }

    @Test fun qwenOpensImageEditModeBeforeUpload() {
        val q = Providers.parse(providersFile().readText()).first { it.id == "qwen" }
        val steps = q.raw.getJSONArray("steps"); val kinds = (0 until steps.length()).map { steps.getJSONObject(it).getString("do") }
        assertTrue(kinds.indexOf("mode") in 0 until kinds.indexOf("upload"))
        val mode = steps.getJSONObject(kinds.indexOf("mode"))
        assertFalse(mode.getBoolean("optional"))
        val re = Regex(mode.getString("itemText"), RegexOption.IGNORE_CASE)
        listOf("Image Edit", "Редактирование изображения", "图像编辑").forEach { assertTrue(it, re.containsMatchIn(it)) }
        assertFalse(re.containsMatchIn("Create Image"))
        assertTrue(q.startUrl.contains("inputFeature=image_edit"))
    }

    @Test fun rewriteRulesProduceFullSizeUrls() {
        val list = Providers.parse(providersFile().readText())
        fun rw(id: String, url: String): String { val r = list.first { it.id == id }.raw.getJSONArray("steps").let { s -> (0 until s.length()).map { s.getJSONObject(it) }.first { it.getString("do") == "result" } }.getJSONArray("rewrite")
            var u = url; for (i in 0 until r.length()) { val a = r.getJSONArray(i); u = u.replace(Regex(a.getString(0)), a.getString(1).replace("\$1", "\$1")) }; return u }
        assertEquals("https://cdn.qwenlm.ai/output/abc.png", rw("qwen", "https://cdn.qwenlm.ai/output/abc.png?x-oss-process=image/resize,w_512"))
        assertEquals("https://avatars.mds.yandex.net/get-shedevrum/123/abc/orig", rw("alice", "https://avatars.mds.yandex.net/get-shedevrum/123/abc/400x400"))
    }
}
