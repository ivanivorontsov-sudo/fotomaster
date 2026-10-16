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

    @Test fun providersConfigHasNoAutomation() {
        val text = providersFile().readText()
        val list = Providers.parse(text)
        assertEquals(listOf("qwen", "alice", "giga"), list.map { it.id })
        for (p in list) { assertTrue(p.startUrl.startsWith("https://")); assertTrue(p.hint.contains("сами")) }
        listOf("steps", "selectors", "\"do\"", "rewrite").forEach { assertFalse(it, text.contains(it)) }
    }

    @Test fun noInjectedScriptsInApp() {
        val root = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
        assertFalse(File(root, "assets/ai/runner.js").exists())
        val code = root.walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("evaluateJavascript", "addJavascriptInterface", "JavascriptInterface", "dispatchEvent", "DataTransfer").forEach { assertFalse(it, code.contains(it)) }
    }
}
