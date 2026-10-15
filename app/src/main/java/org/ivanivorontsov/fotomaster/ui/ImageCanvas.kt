package org.ivanivorontsov.fotomaster.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ViewTransform { var zoom by mutableFloatStateOf(1f); var pan by mutableStateOf(Offset.Zero); fun reset() { zoom = 1f; pan = Offset.Zero } }

interface CanvasHandler {
    val draws: Boolean
    fun brushRadiusFrac(): Float = 0.03f
    fun onTap(p: Offset) {}
    fun onStroke(points: List<Offset>) {}
    fun overlay(scope: androidx.compose.ui.graphics.drawscope.DrawScope, imgRect: Rect) {}
    fun onDrag(start: Offset, cur: Offset, phase: Int): Boolean = false
}

object NoHandler : CanvasHandler { override val draws = false }

@Composable
fun ImageCanvas(bitmap: Bitmap?, before: Bitmap?, showBefore: Boolean, split: Float?, vt: ViewTransform, handler: CanvasHandler, modifier: Modifier = Modifier) {
    val img = remember(bitmap) { bitmap?.asImageBitmap() }
    val bef = remember(before) { before?.asImageBitmap() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var hover by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cw = constraints.maxWidth.toFloat(); val ch = constraints.maxHeight.toFloat()
        val iw = (img?.width ?: 1).toFloat(); val ih = (img?.height ?: 1).toFloat()
        val fit = min(cw / iw, ch / ih) * 0.96f
        val s = fit * vt.zoom
        val rect = Rect(Offset(cw / 2 - iw * s / 2 + vt.pan.x, ch / 2 - ih * s / 2 + vt.pan.y), Size(iw * s, ih * s))
        fun norm(p: Offset) = Offset((p.x - rect.left) / rect.width, (p.y - rect.top) / rect.height)
        val rectState = rememberUpdatedState(rect)
        val handlerState = rememberUpdatedState(handler)
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val h = handlerState.value
                val pts = mutableListOf(down.position)
                var multi = false; var moved = false
                val dragHandled = h.onDrag(norm2(rectState.value, down.position), norm2(rectState.value, down.position), 0)
                if (h.draws && !dragHandled) { live = listOf(down.position); hover = down.position }
                while (true) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        multi = true; live = emptyList(); hover = null
                        val z = ev.calculateZoom(); val pan = ev.calculatePan(); val c = ev.calculateCentroid()
                        val nz = (vt.zoom * z).coerceIn(0.5f, 12f)
                        val k = nz / vt.zoom
                        val center = Offset(cw / 2, ch / 2)
                        vt.pan = (vt.pan + center - c) * k - (center - c) + pan
                        vt.zoom = nz
                        ev.changes.forEach { it.consume() }
                        continue
                    }
                    val c = pressed[0]
                    if (!c.positionChanged()) continue
                    if ((c.position - down.position).getDistance() > 8f) moved = true
                    if (multi) continue
                    if (dragHandled) { h.onDrag(norm2(rectState.value, down.position), norm2(rectState.value, c.position), 1); c.consume(); continue }
                    if (h.draws) { pts.add(c.position); live = pts.toList(); hover = c.position }
                    else if (vt.zoom > 1.01f) vt.pan += c.position - c.previousPosition
                    c.consume()
                }
                if (dragHandled) h.onDrag(norm2(rectState.value, down.position), norm2(rectState.value, pts.last()), 2)
                else if (!multi) {
                    if (h.draws && moved) h.onStroke(pts.map { norm2(rectState.value, it) })
                    else if (!moved) h.onTap(norm2(rectState.value, down.position))
                }
                live = emptyList(); hover = null
            }
        }) {
            val showB = showBefore && bef != null
            val main = if (showB) bef else img
            if (main != null) drawImage(main, IntOffset.Zero, IntSize(main.width, main.height), IntOffset(rect.left.toInt(), rect.top.toInt()), IntSize(rect.width.toInt(), rect.height.toInt()), filterQuality = FilterQuality.Medium)
            if (!showB && split != null && bef != null && bef.width == img?.width) {
                val sx = rect.left + rect.width * split
                clipRect(left = rect.left, top = rect.top, right = sx, bottom = rect.bottom) {
                    drawImage(bef, IntOffset.Zero, IntSize(bef.width, bef.height), IntOffset(rect.left.toInt(), rect.top.toInt()), IntSize(rect.width.toInt(), rect.height.toInt()), filterQuality = FilterQuality.Medium)
                }
                drawLine(Color.White, Offset(sx, rect.top), Offset(sx, rect.bottom), 3f)
            }
            handler.overlay(this, rect)
            if (live.size > 1) {
                val path = Path().apply { moveTo(live[0].x, live[0].y); live.drop(1).forEach { lineTo(it.x, it.y) } }
                val w = handler.brushRadiusFrac() * max(rect.width, rect.height) * 2
                drawPath(path, Color(0x664FC3F7), style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            hover?.let { drawCircle(Color.White, handler.brushRadiusFrac() * max(rect.width, rect.height), it, style = Stroke(2f)) }
        }
    }
}

private fun norm2(r: Rect, p: Offset) = Offset((p.x - r.left) / r.width, (p.y - r.top) / r.height)
