package com.example.visitorcapture

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Gently shrinks the element while it is pressed: the tactile "give" premium apps have. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Amber line sweeping up and down over its content, e.g. the card photo while it is being read. */
@Composable
fun Modifier.scanSweep(color: Color, active: Boolean = true): Modifier {
    if (!active) return this
    val t by rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scanPos")
    return this.drawWithContent {
        drawContent()
        val y = size.height * t
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.28f)), startY = y - 90f, endY = y),
            topLeft = Offset(0f, (y - 90f).coerceAtLeast(0f)), size = Size(size.width, y.coerceAtMost(90f)))
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f, cap = StrokeCap.Round)
    }
}

/** Amber camera-viewfinder corners drawn over a preview area. */
fun Modifier.viewfinder(color: Color, arm: Dp = 28.dp, stroke: Dp = 4.dp, inset: Dp = 12.dp): Modifier = this.drawWithContent {
    drawContent()
    val a = arm.toPx(); val s = stroke.toPx(); val i = inset.toPx()
    val w = size.width; val h = size.height
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        val p = Path().apply { moveTo(x, y + dy * a); lineTo(x, y); lineTo(x + dx * a, y) }
        drawPath(p, color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    corner(i, i, 1f, 1f); corner(w - i, i, -1f, 1f); corner(i, h - i, 1f, -1f); corner(w - i, h - i, -1f, -1f)
}

/** Success badge: the ring pops in, then the tick draws itself. */
@Composable
fun SuccessCheck(color: Color, tick: Color, size: Dp = 96.dp) {
    val ring = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        ring.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(size).graphicsLayer { scaleX = ring.value; scaleY = ring.value }) {
        val r = this.size.minDimension / 2
        drawCircle(color.copy(alpha = 0.18f), r)
        drawCircle(color, r * 0.78f)
        val path = Path().apply {
            moveTo(r * 0.62f, r * 1.02f); lineTo(r * 0.90f, r * 1.30f); lineTo(r * 1.40f, r * 0.74f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * draw.value, part, true)
        drawPath(part, tick, style = Stroke(r * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
