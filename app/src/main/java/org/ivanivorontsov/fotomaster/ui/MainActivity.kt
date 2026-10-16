package org.ivanivorontsov.fotomaster.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()
        handleIntent(intent)
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: android.content.Intent?) {
        if (i == null) return
        val uri: android.net.Uri? = when (i.action) {
            android.content.Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            android.content.Intent.ACTION_EDIT -> i.data
            else -> null
        }
        if (uri != null) { vm.pendingOpen.value = uri; i.action = null }
    }

    private fun requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val d = if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
            val cur = d?.mode ?: return
            val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }.maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }
}

val Accent = Color(0xFF4FC3F7)

enum class Screen { HOME, EDITOR, ACCOUNTS }

@Composable
fun App(vm: EditorViewModel) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Color(0xFFFFB74D), background = Color(0xFF0E0F12), surface = Color(0xFF16181D))) {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        var showWeb by remember { mutableStateOf(false) }
        val offer by vm.ai.offerImage.collectAsState()
        val pick by vm.ai.pickRequest.collectAsState()
        val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u -> vm.ai.deliverPicked(u) }
        LaunchedEffect(pick) { if (pick > 0) picker.launch("image/*") }
        val openSite: (org.ivanivorontsov.fotomaster.ai.AiProvider?) -> Unit = { p -> if (p != null) vm.ai.open(p); showWeb = true }
        val pending by vm.pendingOpen.collectAsState()
        LaunchedEffect(pending) { pending?.let { u -> vm.pendingOpen.value = null; vm.openUri(u) { screen = Screen.EDITOR } } }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { c -> WebView(c).also { vm.ai.attach(it) } },
                    modifier = Modifier.fillMaxSize().then(if (showWeb) Modifier.statusBarsPadding().imePadding() else Modifier).zIndex(if (showWeb) 2f else 0f).alpha(if (showWeb) 1f else 0f))
                Box(Modifier.fillMaxSize().zIndex(1f)) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(vm, onOpen = { screen = Screen.EDITOR }, onAccounts = { screen = Screen.ACCOUNTS })
                        Screen.EDITOR -> EditorScreen(vm, onBack = { vm.refreshProjects(); screen = Screen.HOME }, showWeb = showWeb, onToggleWeb = { showWeb = !showWeb }, onAccounts = { screen = Screen.ACCOUNTS }, onOpenSite = { openSite(null) })
                        Screen.ACCOUNTS -> AccountsScreen(vm, onBack = { screen = if (vm.project != null) Screen.EDITOR else Screen.HOME }, onOpen = { p -> openSite(p) })
                    }
                }
                if (showWeb) SiteBar(vm.ai.provider, onClose = { vm.ai.close(); showWeb = false })
                offer?.let { OfferDialog(onAccept = { vm.ai.acceptOffer(); showWeb = false }, onDecline = { vm.ai.declineOffer() }) }
            }
            BackHandler(enabled = showWeb || screen != Screen.HOME) {
                when { showWeb -> { if (vm.ai.webView?.canGoBack() == true) vm.ai.webView?.goBack() else { vm.ai.close(); showWeb = false } }
                    screen == Screen.ACCOUNTS -> screen = if (vm.project != null) Screen.EDITOR else Screen.HOME
                    else -> { vm.refreshProjects(); screen = Screen.HOME } }
            }
        }
    }
}
