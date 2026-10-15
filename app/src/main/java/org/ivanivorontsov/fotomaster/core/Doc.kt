package org.ivanivorontsov.fotomaster.core

data class PointN(val x: Float, val y: Float)

data class Stroke(
    val points: List<PointN>,
    val radius: Float,
    val color: Int = 0xFFFFFFFF.toInt(),
    val hardness: Float = 0.7f,
    val erase: Boolean = false,
)

data class RectN(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val isFull get() = l <= 0f && t <= 0f && r >= 1f && b >= 1f
}

data class Geometry(val crop: RectN = RectN(), val rot90: Int = 0, val flipH: Boolean = false, val straighten: Float = 0f) {
    val isIdentity get() = crop.isFull && rot90 % 4 == 0 && !flipH && straighten == 0f
}

enum class Blend(val title: String) { NORMAL("Обычный"), MULTIPLY("Умножение"), SCREEN("Экран"), OVERLAY("Перекрытие") }

enum class BgMode(val title: String) { TRANSPARENT("Прозрачный"), BLUR("Размыть фон"), COLOR("Цвет"), GRAY("Ч/б фон") }

enum class MaskShape(val title: String) { BRUSH("Кисть"), RADIAL("Радиальная"), LINEAR("Градиент"), PERSON("Человек (ИИ на телефоне)"), SKY("Небо (по цвету)") }

sealed class Layer {
    abstract val id: Long
    abstract val name: String
    abstract val visible: Boolean
    abstract val opacity: Float
    abstract fun withVisibility(v: Boolean): Layer
    abstract fun withOpacity(o: Float): Layer

    data class Paint(override val id: Long, override val name: String = "Рисунок", override val visible: Boolean = true,
                     override val opacity: Float = 1f, val blend: Blend = Blend.NORMAL, val strokes: List<Stroke> = emptyList()) : Layer() {
        override fun withVisibility(v: Boolean) = copy(visible = v)
        override fun withOpacity(o: Float) = copy(opacity = o)
    }

    data class Text(override val id: Long, override val name: String = "Текст", override val visible: Boolean = true,
                    override val opacity: Float = 1f, val text: String, val x: Float = 0.5f, val y: Float = 0.5f,
                    val size: Float = 0.06f, val color: Int = 0xFFFFFFFF.toInt(), val shadow: Boolean = true, val bold: Boolean = true) : Layer() {
        override fun withVisibility(v: Boolean) = copy(visible = v)
        override fun withOpacity(o: Float) = copy(opacity = o)
    }

    data class Retouch(override val id: Long, override val name: String = "Ретушь", override val visible: Boolean = true,
                       override val opacity: Float = 1f, val ops: List<RetouchOp> = emptyList()) : Layer() {
        override fun withVisibility(v: Boolean) = copy(visible = v)
        override fun withOpacity(o: Float) = copy(opacity = o)
    }

    data class Local(override val id: Long, override val name: String = "Маска", override val visible: Boolean = true,
                     override val opacity: Float = 1f, val shape: MaskShape = MaskShape.BRUSH, val strokes: List<Stroke> = emptyList(),
                     val cx: Float = 0.5f, val cy: Float = 0.5f, val radius: Float = 0.3f, val feather: Float = 0.5f,
                     val angle: Float = 90f, val invert: Boolean = false, val maskRef: String? = null,
                     val adj: Adjustments = Adjustments()) : Layer() {
        override fun withVisibility(v: Boolean) = copy(visible = v)
        override fun withOpacity(o: Float) = copy(opacity = o)
    }

    data class Cutout(override val id: Long, override val name: String = "Фон", override val visible: Boolean = true,
                      override val opacity: Float = 1f, val maskRef: String, val mode: BgMode = BgMode.TRANSPARENT,
                      val color: Int = 0xFFFFFFFF.toInt(), val feather: Float = 0.3f, val fixStrokes: List<Stroke> = emptyList()) : Layer() {
        override fun withVisibility(v: Boolean) = copy(visible = v)
        override fun withOpacity(o: Float) = copy(opacity = o)
    }

    data class Picture(override val id: Long, override val name: String = "Изображение", override val visible: Boolean = true,
                       override val opacity: Float = 1f, val imageRef: String, val blend: Blend = Blend.NORMAL,
                       val eraseStrokes: List<Stroke> = emptyList()) : Layer() {
        override fun withVisibility(v: Boolean) = copy(visible = v)
        override fun withOpacity(o: Float) = copy(opacity = o)
    }
}

sealed class RetouchOp {
    data class Heal(val x: Float, val y: Float, val r: Float) : RetouchOp()
    data class Clone(val dx: Float, val dy: Float, val stroke: Stroke) : RetouchOp()
}

data class Doc(
    val geometry: Geometry = Geometry(),
    val adj: Adjustments = Adjustments(),
    val layers: List<Layer> = emptyList(),
) {
    fun nextId(): Long = (layers.maxOfOrNull { it.id } ?: 0L) + 1
    fun replaceLayer(l: Layer) = copy(layers = layers.map { if (it.id == l.id) l else it })
}

class History(initial: Doc = Doc(), private val limit: Int = 300) {
    private val states = ArrayList<Pair<String, Doc>>().apply { add("Открыто" to initial) }
    var index = 0; private set
    val current get() = states[index].second
    val canUndo get() = index > 0
    val canRedo get() = index < states.size - 1
    val entries: List<String> get() = states.map { it.first }

    fun push(label: String, doc: Doc) {
        if (doc == current) return
        while (states.size > index + 1) states.removeAt(states.size - 1)
        states.add(label to doc)
        if (states.size > limit) states.removeAt(1)
        index = states.size - 1
    }
    fun replaceTop(label: String, doc: Doc) {
        if (index == 0) { push(label, doc); return }
        while (states.size > index + 1) states.removeAt(states.size - 1)
        states[index] = label to doc
    }
    fun undo(): Doc { if (canUndo) index--; return current }
    fun redo(): Doc { if (canRedo) index++; return current }
    fun jump(i: Int): Doc { index = i.coerceIn(0, states.size - 1); return current }
}
