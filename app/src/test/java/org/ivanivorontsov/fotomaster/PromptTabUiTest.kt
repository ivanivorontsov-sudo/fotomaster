package org.ivanivorontsov.fotomaster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.ivanivorontsov.fotomaster.ai.LoginState
import org.ivanivorontsov.fotomaster.ui.App
import org.ivanivorontsov.fotomaster.ui.EditorViewModel
import org.ivanivorontsov.fotomaster.ui.PromptTab
import org.ivanivorontsov.fotomaster.ui.Accent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PromptTabUiTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun scene(w: Int, h: Int, warm: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), if (warm) 0xFFFF8A50.toInt() else 0xFF3A4A66.toInt(), if (warm) 0xFF6A1B9A.toInt() else 0xFF2E3B2A.toInt(), Shader.TileMode.CLAMP) })
        c.drawCircle(w * 0.72f, h * 0.25f, h * 0.09f, Paint().apply { color = if (warm) 0xFFFFF3B0.toInt() else 0xFFB0A070.toInt() })
        c.drawRect(w * 0.38f, h * 0.55f, w * 0.62f, h * 0.85f, Paint().apply { color = if (warm) 0xFFD84315.toInt() else 0xFF5D2A25.toInt() })
        return b
    }

    private fun vm(): EditorViewModel = EditorViewModel(ApplicationProvider.getApplicationContext())

    private fun host(vm: EditorViewModel, logins: MutableList<String>) = rule.setContent {
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color(0xFF0E0F12), surface = Color(0xFF16181D))) {
            Surface(Modifier.fillMaxSize()) { PromptTab(vm, { logins.add(it) }, false, {}) }
        }
    }

    @Test fun tabShowsControlsChipsAndProviders() {
        val v = vm(); host(v, mutableListOf())
        rule.onNodeWithTag("prompt_field").assertExists()
        rule.onNodeWithTag("btn_generate").assertExists()
        rule.onNodeWithText("Удали фон").performScrollTo().performClick()
        assertTrue(v.prompt.prompt.contains("Удали фон"))
        rule.onNodeWithTag("provider_alice").performScrollTo().performClick()
        assertEquals("alice", v.prompt.providerId)
        assertEquals("alice", vm().prompt.providerId)
        rule.onNodeWithText("Сгенерировать в Алиса AI").assertExists()
    }

    @Test fun inlineLoginWhenLoggedOut() {
        val v = vm(); v.prompt.selectProvider("giga"); v.prompt.setLogin("giga", LoginState.LOGGED_OUT)
        val logins = mutableListOf<String>(); host(v, logins)
        rule.onNodeWithText("Вход в ГигаЧат не выполнен").assertExists()
        rule.onNodeWithTag("btn_login").performClick()
        assertEquals(listOf("https://giga.chat/"), logins)
    }

    @Test fun resultShownImmediatelyWithActionsHistoryAndScreenshot() {
        val v = vm()
        v.prompt.selectProvider("qwen"); v.prompt.updatePrompt("Сделай фото в стиле заката")
        v.prompt.setLogin("qwen", LoginState.LOGGED_IN)
        v.prompt.startRequest(); v.prompt.addResult("Qwen", scene(800, 600, false), 800, 600)
        v.prompt.startRequest(); val r = v.prompt.addResult("Qwen", scene(800, 600, true), 800, 600)
        v.putPromptBefore(r.id, scene(800, 600, false))
        host(v, mutableListOf())
        rule.onNodeWithTag("btn_apply").assertExists().assertIsEnabled()
        rule.onNodeWithTag("btn_retry").assertExists()
        rule.onNodeWithTag("btn_save").assertExists()
        rule.onNodeWithText("История промптов (2)").assertExists()
        rule.onNodeWithText("ПОСЛЕ · Qwen · 800×600").assertExists()
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val img = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(Canvas(img)) }
        val out = listOf(File("../docs/screens"), File("docs/screens")).first { it.parentFile?.exists() == true }.apply { mkdirs() }
        File(out, "05-prompt-tab.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(img.width > 0)
    }
    @Test fun errorDoesNotHideInputAndIsResetOnProviderSwitchAndProject() {
        val v = vm(); v.prompt.resetForProject("p1")
        v.prompt.updatePrompt("сделай аниме")
        v.prompt.showError("Qwen", "Не нашёл кнопку режима", List(30) { "строка журнала $it" })
        host(v, mutableListOf())
        rule.onNodeWithTag("ai_error").assertExists()
        rule.onNodeWithTag("prompt_field").assertExists()
        rule.onNodeWithTag("btn_generate").assertExists()
        rule.onNodeWithTag("btn_error_retry").assertExists()
        rule.onNodeWithTag("ai_log").assertDoesNotExist()
        rule.onNodeWithTag("btn_log").performClick(); rule.onNodeWithTag("ai_log").assertExists()
        rule.onNodeWithTag("btn_log").performClick(); rule.onNodeWithTag("ai_log").assertDoesNotExist()
        rule.onNodeWithTag("btn_error_close").performClick(); rule.onNodeWithTag("ai_error").assertDoesNotExist()
        v.prompt.showError("Qwen", "x", emptyList()); v.promptTick.value++
        rule.onNodeWithTag("ai_error").assertExists()
        rule.onNodeWithTag("provider_alice").performScrollTo().performClick()
        rule.onNodeWithTag("ai_error").assertDoesNotExist()
        assertEquals(org.ivanivorontsov.fotomaster.ai.AiState.Idle, v.ai.state.value)
        v.prompt.showError("Алиса", "y", emptyList()); v.prompt.resetForProject("p2")
        assertEquals(null, v.prompt.error)
        assertEquals(0, v.prompt.results.size)
    }
}
