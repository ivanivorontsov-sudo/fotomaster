package org.ivanivorontsov.fotomaster.ai

interface KeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class MemoryKeyValue : KeyValue {
    private val m = HashMap<String, String>()
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String) { m[key] = value }
}

data class PromptResult<T>(val id: Int, val providerId: String, val providerTitle: String, val prompt: String, val image: T, val width: Int, val height: Int, val time: Long, val applied: Boolean = false, val saved: Boolean = false)

enum class LoginState { UNKNOWN, CHECKING, LOGGED_IN, LOGGED_OUT }

class PromptSession<T>(private val store: KeyValue, private val providerIds: List<String>) {
    var providerId: String = store.get(KEY)?.takeIf { it in providerIds } ?: providerIds.first(); private set
    var prompt: String = store.get(KEY_PROMPT) ?: QUICK.first().second
    val results = mutableListOf<PromptResult<T>>()
    var selectedId: Int? = null; private set
    var lastRequest: Pair<String, String>? = null; private set
    private val login = HashMap<String, LoginState>()
    private var nextId = 1

    val selected get() = results.firstOrNull { it.id == selectedId }

    fun selectProvider(id: String) { require(id in providerIds); providerId = id; store.put(KEY, id) }
    fun updatePrompt(p: String) { prompt = p; store.put(KEY_PROMPT, p) }
    fun applyChip(i: Int) = updatePrompt(QUICK[i].second)

    fun canGenerate(): Boolean = prompt.isNotBlank()

    fun startRequest(): Pair<String, String> { val r = providerId to prompt.trim(); lastRequest = r; return r }
    fun retryRequest(): Pair<String, String>? = lastRequest

    fun addResult(providerTitle: String, image: T, w: Int, h: Int, time: Long = System.currentTimeMillis()): PromptResult<T> {
        val (pid, p) = lastRequest ?: (providerId to prompt)
        val r = PromptResult(nextId++, pid, providerTitle, p, image, w, h, time)
        results.add(0, r); selectedId = r.id
        if (results.size > MAX) results.removeAt(results.size - 1)
        setLogin(pid, LoginState.LOGGED_IN)
        return r
    }

    fun select(id: Int) { if (results.any { it.id == id }) selectedId = id }
    fun markApplied(id: Int) = mark(id) { it.copy(applied = true) }
    fun markSaved(id: Int) = mark(id) { it.copy(saved = true) }
    private fun mark(id: Int, f: (PromptResult<T>) -> PromptResult<T>) { val i = results.indexOfFirst { it.id == id }; if (i >= 0) results[i] = f(results[i]) }

    var error: PromptError? = null; private set
    var scope: String? = null; private set
    fun showError(provider: String, message: String, log: List<String>) { error = PromptError(provider, message, log) }
    fun dismissError() { error = null }
    fun onProviderChanged() { error = null }
    fun beginRequest(): Pair<String, String> { error = null; return startRequest() }
    fun resetForProject(id: String) { if (scope != id) { results.clear(); selectedId = null; lastRequest = null }; scope = id; error = null }
    fun loginState(id: String = providerId) = login[id] ?: LoginState.UNKNOWN
    fun setLogin(id: String, s: LoginState) { login[id] = s }
    fun needsLogin(id: String = providerId) = loginState(id) == LoginState.LOGGED_OUT
    fun onFailure(id: String, code: String, message: String) {
        if (code == "login" || Regex("войт|вход|sign ?in|log ?in|авториз", RegexOption.IGNORE_CASE).containsMatchIn(message)) setLogin(id, LoginState.LOGGED_OUT)
    }

    companion object {
        const val KEY = "prompt_provider"
        const val KEY_PROMPT = "prompt_text"
        const val MAX = 30
        val QUICK = listOf(
            "Улучши качество" to "Улучши качество фото: чёткость, свет и цвета, ничего не добавляй и не меняй лица",
            "Удали фон" to "Удали фон, оставь только главный объект на белом фоне",
            "Аниме" to "Сделай это фото в стиле аниме, сохрани композицию и узнаваемость",
            "Убери лишних людей" to "Убери лишних людей и случайные предметы на заднем плане",
            "Восстанови старое фото" to "Восстанови старое фото: убери царапины и шум, верни чёткость и естественные цвета",
            "Портретная ретушь" to "Сделай естественную портретную ретушь: выровняй кожу, сохрани текстуру и черты лица",
            "Закат вместо неба" to "Замени небо на красивый закат, свет на фото подстрой под него",
            "Масляная живопись" to "Преврати фото в картину маслом, сохрани композицию",
            "Ч/б кино" to "Сделай кинематографичное чёрно-белое фото с глубоким контрастом",
            "Добавь снег" to "Добавь на фото падающий снег и зимнее настроение, остальное не меняй",
        )
    }
}

data class PromptError(val provider: String, val message: String, val log: List<String>)
