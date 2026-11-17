package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AvatarAccessory
import com.example.model.AvatarConfig
import com.example.model.AvatarFace
import com.example.model.AvatarHair
import kotlin.math.cos
import kotlin.math.sin

private val InkColor = Color(0xFF1D1633)

@Composable
fun CartoonAvatar(
    config: AvatarConfig,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    animated: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_anim")
    val wobblePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "googly_wobble"
    )
    val phase = if (animated) wobblePhase else 0.7f

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h * 0.56f
        val r = (minOf(w, h) * 0.34f)
        val strokeW = (r * 0.11f).coerceAtLeast(1.8f)

        // Soft backdrop halo
        drawCircle(
            color = Color(config.color.fillHex).copy(alpha = 0.22f),
            radius = r * 1.38f,
            center = Offset(cx, h * 0.52f)
        )

        // Hair drawn behind/top of head
        drawHair(
            hair = config.hair,
            cx = cx,
            cy = cy,
            r = r,
            strokeW = strokeW,
            phase = phase
        )

        // Cartoon chubby head
        val bodyColor = Color(config.color.fillHex)
        drawCircle(
            color = bodyColor,
            radius = r,
            center = Offset(cx, cy)
        )
        // Specular highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.32f),
            radius = r * 0.24f,
            center = Offset(cx - r * 0.36f, cy - r * 0.36f)
        )
        // Bold cartoon outline
        drawCircle(
            color = InkColor,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeW)
        )

        // Rosy cheeks
        drawCircle(
            color = Color(0xFFFF6B8B).copy(alpha = 0.45f),
            radius = r * 0.16f,
            center = Offset(cx - r * 0.52f, cy + r * 0.18f)
        )
        drawCircle(
            color = Color(0xFFFF6B8B).copy(alpha = 0.45f),
            radius = r * 0.16f,
            center = Offset(cx + r * 0.52f, cy + r * 0.18f)
        )

        // Expressive Face
        drawFace(
            face = config.face,
            accessory = config.accessory,
            cx = cx,
            cy = cy,
            r = r,
            strokeW = strokeW,
            phase = phase
        )

        // Wacky Accessory
        drawAccessory(
            accessory = config.accessory,
            cx = cx,
            cy = cy,
            r = r,
            strokeW = strokeW,
            phase = phase
        )
    }
}

private fun DrawScope.drawHair(
    hair: AvatarHair,
    cx: Float,
    cy: Float,
    r: Float,
    strokeW: Float,
    phase: Float
) {
    when (hair) {
        AvatarHair.BALD -> {
            // Little comic shine curved glint handled on head
        }
        AvatarHair.TUFT -> {
            val path = Path().apply {
                moveTo(cx - r * 0.18f, cy - r * 0.88f)
                quadraticTo(cx - r * 0.25f, cy - r * 1.45f, cx + r * 0.02f, cy - r * 1.25f)
                quadraticTo(cx + r * 0.25f, cy - r * 1.52f, cx + r * 0.22f, cy - r * 0.88f)
                close()
            }
            drawPath(path, Color(0xFFFFD166))
            drawPath(path, InkColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))
        }
        AvatarHair.MOHAWK -> {
            val path = Path().apply {
                moveTo(cx - r * 0.22f, cy - r * 0.85f)
                lineTo(cx - r * 0.30f, cy - r * 1.42f)
                lineTo(cx - r * 0.05f, cy - r * 1.22f)
                lineTo(cx + r * 0.08f, cy - r * 1.55f)
                lineTo(cx + r * 0.22f, cy - r * 1.20f)
                lineTo(cx + r * 0.34f, cy - r * 1.38f)
                lineTo(cx + r * 0.24f, cy - r * 0.85f)
                close()
            }
            drawPath(path, Color(0xFFFF3D7F))
            drawPath(path, InkColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))
        }
        AvatarHair.AFRO -> {
            val cloudColor = Color(0xFF3B2C5A)
            val puffs = listOf(
                Offset(cx - r * 0.65f, cy - r * 0.65f) to r * 0.50f,
                Offset(cx + r * 0.65f, cy - r * 0.65f) to r * 0.50f,
                Offset(cx, cy - r * 0.92f) to r * 0.56f,
                Offset(cx - r * 0.85f, cy - r * 0.18f) to r * 0.42f,
                Offset(cx + r * 0.85f, cy - r * 0.18f) to r * 0.42f
            )
            puffs.forEach { (center, rad) ->
                drawCircle(InkColor, radius = rad + strokeW * 0.7f, center = center)
            }
            puffs.forEach { (center, rad) ->
                drawCircle(cloudColor, radius = rad, center = center)
            }
        }
        AvatarHair.SPROUT -> {
            val sway = sin(phase) * r * 0.08f
            drawLine(
                color = InkColor,
                start = Offset(cx, cy - r * 0.92f),
                end = Offset(cx + sway, cy - r * 1.38f),
                strokeWidth = strokeW * 1.1f,
                cap = StrokeCap.Round
            )
            val leafLeft = Path().apply {
                moveTo(cx + sway, cy - r * 1.35f)
                quadraticTo(cx - r * 0.48f, cy - r * 1.55f, cx - r * 0.35f, cy - r * 1.18f)
                close()
            }
            val leafRight = Path().apply {
                moveTo(cx + sway, cy - r * 1.35f)
                quadraticTo(cx + r * 0.48f, cy - r * 1.55f, cx + r * 0.35f, cy - r * 1.18f)
                close()
            }
            drawPath(leafLeft, Color(0xFF4ADE80))
            drawPath(leafLeft, InkColor, style = Stroke(width = strokeW * 0.85f, join = StrokeJoin.Round))
            drawPath(leafRight, Color(0xFF22C55E))
            drawPath(leafRight, InkColor, style = Stroke(width = strokeW * 0.85f, join = StrokeJoin.Round))
        }
        AvatarHair.CURLS -> {
            val curlColor = Color(0xFFFB7185)
            val offsets = listOf(
                Offset(cx - r * 0.55f, cy - r * 0.85f),
                Offset(cx - r * 0.18f, cy - r * 1.02f),
                Offset(cx + r * 0.22f, cy - r * 1.02f),
                Offset(cx + r * 0.58f, cy - r * 0.82f)
            )
            offsets.forEach { o ->
                drawCircle(curlColor, radius = r * 0.26f, center = o)
                drawCircle(InkColor, radius = r * 0.26f, center = o, style = Stroke(width = strokeW * 0.85f))
            }
        }
        AvatarHair.POMPADOUR -> {
            val pompColor = Color(0xFF4C1D95)
            val path = Path().apply {
                moveTo(cx - r * 0.65f, cy - r * 0.55f)
                cubicTo(
                    cx - r * 0.85f, cy - r * 1.48f,
                    cx + r * 0.92f, cy - r * 1.58f,
                    cx + r * 0.78f, cy - r * 0.65f
                )
                quadraticTo(cx, cy - r * 0.90f, cx - r * 0.65f, cy - r * 0.55f)
                close()
            }
            drawPath(path, pompColor)
            drawPath(path, InkColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawFace(
    face: AvatarFace,
    accessory: AvatarAccessory,
    cx: Float,
    cy: Float,
    r: Float,
    strokeW: Float,
    phase: Float
) {
    val leftEye = Offset(cx - r * 0.34f, cy - r * 0.12f)
    val rightEye = Offset(cx + r * 0.34f, cy - r * 0.12f)

    // Skip default eyes if Googly Eyes or Cool Shades cover them
    if (accessory != AvatarAccessory.GOOGLY && accessory != AvatarAccessory.SHADES) {
        when (face) {
            AvatarFace.GRIN -> {
                drawCircle(Color.White, r * 0.17f, leftEye)
                drawCircle(InkColor, r * 0.09f, leftEye)
                drawCircle(InkColor, r * 0.17f, leftEye, style = Stroke(strokeW * 0.8f))

                drawCircle(Color.White, r * 0.17f, rightEye)
                drawCircle(InkColor, r * 0.09f, rightEye)
                drawCircle(InkColor, r * 0.17f, rightEye, style = Stroke(strokeW * 0.8f))
            }
            AvatarFace.HYPED -> {
                drawCircle(Color(0xFFFFFBEB), r * 0.20f, leftEye)
                drawCircle(Color(0xFF7C3AED), r * 0.11f, leftEye)
                drawCircle(Color.White, r * 0.05f, Offset(leftEye.x - r * 0.04f, leftEye.y - r * 0.04f))
                drawCircle(InkColor, r * 0.20f, leftEye, style = Stroke(strokeW * 0.8f))

                drawCircle(Color(0xFFFFFBEB), r * 0.20f, rightEye)
                drawCircle(Color(0xFF7C3AED), r * 0.11f, rightEye)
                drawCircle(Color.White, r * 0.05f, Offset(rightEye.x - r * 0.04f, rightEye.y - r * 0.04f))
                drawCircle(InkColor, r * 0.20f, rightEye, style = Stroke(strokeW * 0.8f))
            }
            AvatarFace.DERPY -> {
                drawCircle(Color.White, r * 0.19f, leftEye)
                drawCircle(InkColor, r * 0.09f, Offset(leftEye.x - r * 0.06f, leftEye.y + r * 0.04f))
                drawCircle(InkColor, r * 0.19f, leftEye, style = Stroke(strokeW * 0.8f))

                drawCircle(Color.White, r * 0.15f, rightEye)
                drawCircle(InkColor, r * 0.08f, Offset(rightEye.x + r * 0.05f, rightEye.y - r * 0.04f))
                drawCircle(InkColor, r * 0.15f, rightEye, style = Stroke(strokeW * 0.8f))
            }
            AvatarFace.SMUG -> {
                // Half-lidded cool eyes + raised eyebrow
                drawArc(
                    color = Color.White,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(leftEye.x - r * 0.17f, leftEye.y - r * 0.14f),
                    size = Size(r * 0.34f, r * 0.28f)
                )
                drawCircle(InkColor, r * 0.07f, Offset(leftEye.x + r * 0.03f, leftEye.y + r * 0.02f))
                drawLine(
                    InkColor,
                    Offset(leftEye.x - r * 0.20f, leftEye.y - r * 0.02f),
                    Offset(leftEye.x + r * 0.20f, leftEye.y - r * 0.08f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )

                drawArc(
                    color = Color.White,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(rightEye.x - r * 0.17f, rightEye.y - r * 0.14f),
                    size = Size(r * 0.34f, r * 0.28f)
                )
                drawCircle(InkColor, r * 0.07f, Offset(rightEye.x + r * 0.03f, rightEye.y + r * 0.02f))
                drawLine(
                    InkColor,
                    Offset(rightEye.x - r * 0.20f, rightEye.y - r * 0.08f),
                    Offset(rightEye.x + r * 0.20f, rightEye.y - r * 0.02f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
            AvatarFace.DIZZY -> {
                listOf(leftEye, rightEye).forEachIndexed { idx, eye ->
                    drawCircle(Color.White, r * 0.18f, eye)
                    drawCircle(InkColor, r * 0.18f, eye, style = Stroke(strokeW * 0.75f))
                    val dir = if (idx == 0) 1f else -1f
                    val dotOffset = Offset(
                        eye.x + cos(phase * dir) * r * 0.07f,
                        eye.y + sin(phase * dir) * r * 0.07f
                    )
                    drawCircle(InkColor, r * 0.07f, dotOffset)
                }
            }
            AvatarFace.WINK -> {
                drawCircle(Color.White, r * 0.18f, leftEye)
                drawCircle(InkColor, r * 0.09f, leftEye)
                drawCircle(InkColor, r * 0.18f, leftEye, style = Stroke(strokeW * 0.8f))

                // Happy winking arc
                drawArc(
                    color = InkColor,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(rightEye.x - r * 0.18f, rightEye.y - r * 0.08f),
                    size = Size(r * 0.36f, r * 0.22f),
                    style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round)
                )
            }
        }
    }

    // Mouths
    val mouthY = cy + r * 0.24f
    when (face) {
        AvatarFace.GRIN, AvatarFace.HYPED -> {
            val mouthRect = Size(r * 0.68f, r * 0.42f)
            val topLeft = Offset(cx - mouthRect.width / 2f, mouthY - r * 0.05f)
            drawArc(
                color = Color(0xFF991B1B),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = topLeft,
                size = mouthRect
            )
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - mouthRect.width * 0.42f, mouthY - r * 0.05f),
                size = Size(mouthRect.width * 0.84f, mouthRect.height * 0.36f)
            )
            drawArc(
                color = InkColor,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = topLeft,
                size = mouthRect,
                style = Stroke(width = strokeW * 0.9f, join = StrokeJoin.Round)
            )
        }
        AvatarFace.DERPY -> {
            drawLine(
                color = InkColor,
                start = Offset(cx - r * 0.30f, mouthY + r * 0.06f),
                end = Offset(cx + r * 0.30f, mouthY + r * 0.02f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            val tonguePath = Path().apply {
                moveTo(cx + r * 0.02f, mouthY + r * 0.04f)
                cubicTo(
                    cx + r * 0.02f, mouthY + r * 0.36f,
                    cx + r * 0.28f, mouthY + r * 0.36f,
                    cx + r * 0.26f, mouthY + r * 0.03f
                )
                close()
            }
            drawPath(tonguePath, Color(0xFFFB7185))
            drawPath(tonguePath, InkColor, style = Stroke(width = strokeW * 0.85f, join = StrokeJoin.Round))
        }
        AvatarFace.SMUG -> {
            val smirk = Path().apply {
                moveTo(cx - r * 0.24f, mouthY + r * 0.14f)
                quadraticTo(cx + r * 0.08f, mouthY + r * 0.20f, cx + r * 0.32f, mouthY - r * 0.02f)
            }
            drawPath(smirk, InkColor, style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round))
        }
        AvatarFace.DIZZY -> {
            val wavy = Path().apply {
                moveTo(cx - r * 0.30f, mouthY + r * 0.12f)
                quadraticTo(cx - r * 0.14f, mouthY - r * 0.02f, cx, mouthY + r * 0.12f)
                quadraticTo(cx + r * 0.14f, mouthY + r * 0.26f, cx + r * 0.30f, mouthY + r * 0.10f)
            }
            drawPath(wavy, InkColor, style = Stroke(width = strokeW * 1.05f, cap = StrokeCap.Round))
        }
        AvatarFace.WINK -> {
            drawArc(
                color = InkColor,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.28f, mouthY - r * 0.04f),
                size = Size(r * 0.56f, r * 0.32f),
                style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawAccessory(
    accessory: AvatarAccessory,
    cx: Float,
    cy: Float,
    r: Float,
    strokeW: Float,
    phase: Float
) {
    val leftEye = Offset(cx - r * 0.34f, cy - r * 0.12f)
    val rightEye = Offset(cx + r * 0.34f, cy - r * 0.12f)

    when (accessory) {
        AvatarAccessory.NONE -> Unit
        AvatarAccessory.GOOGLY -> {
            val lRad = r * 0.28f
            val rRad = r * 0.25f
            drawCircle(Color.White, lRad, leftEye)
            drawCircle(InkColor, lRad, leftEye, style = Stroke(strokeW))
            drawCircle(
                InkColor,
                lRad * 0.45f,
                Offset(leftEye.x + cos(phase) * lRad * 0.32f, leftEye.y + sin(phase) * lRad * 0.32f)
            )

            drawCircle(Color.White, rRad, rightEye)
            drawCircle(InkColor, rRad, rightEye, style = Stroke(strokeW))
            drawCircle(
                InkColor,
                rRad * 0.45f,
                Offset(rightEye.x - sin(phase) * rRad * 0.32f, rightEye.y + cos(phase) * rRad * 0.32f)
            )
        }
        AvatarAccessory.SHADES -> {
            val lensW = r * 0.44f
            val lensH = r * 0.28f
            drawLine(
                InkColor,
                Offset(cx - r * 0.08f, leftEye.y - r * 0.04f),
                Offset(cx + r * 0.08f, rightEye.y - r * 0.04f),
                strokeWidth = strokeW * 1.4f
            )
            listOf(leftEye, rightEye).forEach { eye ->
                val tl = Offset(eye.x - lensW / 2f, eye.y - lensH / 2f)
                drawRoundRect(
                    color = InkColor,
                    topLeft = tl,
                    size = Size(lensW, lensH),
                    cornerRadius = CornerRadius(r * 0.08f, r * 0.08f)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(tl.x + lensW * 0.18f, tl.y + lensH * 0.25f),
                    end = Offset(tl.x + lensW * 0.45f, tl.y + lensH * 0.70f),
                    strokeWidth = strokeW * 0.8f,
                    cap = StrokeCap.Round
                )
            }
        }
        AvatarAccessory.MONOCLE -> {
            val mRadius = r * 0.25f
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = 0.35f),
                radius = mRadius,
                center = rightEye
            )
            drawCircle(
                color = Color(0xFFEAB308),
                radius = mRadius,
                center = rightEye,
                style = Stroke(width = strokeW * 1.15f)
            )
            drawLine(
                color = Color(0xFFEAB308),
                start = Offset(rightEye.x + mRadius * 0.9f, rightEye.y + mRadius * 0.2f),
                end = Offset(rightEye.x + mRadius * 1.15f, cy + r * 0.75f),
                strokeWidth = strokeW * 0.7f,
                cap = StrokeCap.Round
            )
        }
        AvatarAccessory.SWEATBAND -> {
            val bandTop = cy - r * 0.64f
            val bandRect = Size(r * 1.88f, r * 0.24f)
            val tl = Offset(cx - bandRect.width / 2f, bandTop)
            drawRoundRect(
                color = Color(0xFFFF5A1F),
                topLeft = tl,
                size = bandRect,
                cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(tl.x + r * 0.08f, bandTop + bandRect.height * 0.35f),
                size = Size(bandRect.width - r * 0.16f, bandRect.height * 0.30f),
                cornerRadius = CornerRadius(r * 0.04f, r * 0.04f)
            )
            drawRoundRect(
                color = InkColor,
                topLeft = tl,
                size = bandRect,
                cornerRadius = CornerRadius(r * 0.12f, r * 0.12f),
                style = Stroke(width = strokeW * 0.9f)
            )
        }
        AvatarAccessory.TINY_HAT -> {
            rotate(degrees = -12f, pivot = Offset(cx + r * 0.12f, cy - r * 0.88f)) {
                val brimW = r * 0.78f
                val brimH = r * 0.14f
                val hatW = r * 0.48f
                val hatH = r * 0.52f
                val baseCx = cx + r * 0.12f
                val baseY = cy - r * 0.86f
                drawRoundRect(
                    color = Color(0xFF2D234A),
                    topLeft = Offset(baseCx - hatW / 2f, baseY - hatH),
                    size = Size(hatW, hatH),
                    cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
                )
                drawRect(
                    color = Color(0xFFFACC15),
                    topLeft = Offset(baseCx - hatW / 2f, baseY - brimH * 1.6f),
                    size = Size(hatW, brimH * 0.75f)
                )
                drawRoundRect(
                    color = InkColor,
                    topLeft = Offset(baseCx - hatW / 2f, baseY - hatH),
                    size = Size(hatW, hatH),
                    cornerRadius = CornerRadius(r * 0.06f, r * 0.06f),
                    style = Stroke(width = strokeW * 0.85f)
                )
                drawRoundRect(
                    color = Color(0xFF2D234A),
                    topLeft = Offset(baseCx - brimW / 2f, baseY - brimH / 2f),
                    size = Size(brimW, brimH),
                    cornerRadius = CornerRadius(brimH / 2f, brimH / 2f)
                )
                drawRoundRect(
                    color = InkColor,
                    topLeft = Offset(baseCx - brimW / 2f, baseY - brimH / 2f),
                    size = Size(brimW, brimH),
                    cornerRadius = CornerRadius(brimH / 2f, brimH / 2f),
                    style = Stroke(width = strokeW * 0.85f)
                )
            }
        }
        AvatarAccessory.CROWN -> {
            val crownPath = Path().apply {
                val base = cy - r * 0.82f
                moveTo(cx - r * 0.52f, base)
                lineTo(cx - r * 0.56f, base - r * 0.48f)
                lineTo(cx - r * 0.22f, base - r * 0.22f)
                lineTo(cx, base - r * 0.58f)
                lineTo(cx + r * 0.22f, base - r * 0.22f)
                lineTo(cx + r * 0.56f, base - r * 0.48f)
                lineTo(cx + r * 0.52f, base)
                close()
            }
            drawPath(crownPath, Color(0xFFFACC15))
            drawPath(crownPath, InkColor, style = Stroke(width = strokeW * 0.9f, join = StrokeJoin.Round))
            drawCircle(Color(0xFFEF4444), r * 0.08f, Offset(cx, cy - r * 1.00f))
        }
        AvatarAccessory.PROPELLER -> {
            val capY = cy - r * 0.80f
            drawArc(
                color = Color(0xFF38BDF8),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - r * 0.62f, capY - r * 0.45f),
                size = Size(r * 1.24f, r * 0.80f)
            )
            drawArc(
                color = InkColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - r * 0.62f, capY - r * 0.45f),
                size = Size(r * 1.24f, r * 0.80f),
                style = Stroke(width = strokeW * 0.85f)
            )
            // Spinner mast & blade
            drawLine(
                InkColor,
                Offset(cx, capY - r * 0.45f),
                Offset(cx, capY - r * 0.66f),
                strokeWidth = strokeW
            )
            val spinHalfW = (r * 0.48f) * (0.35f + 0.65f * kotlin.math.abs(cos(phase * 2f)))
            drawRoundRect(
                color = Color(0xFFB8F22E),
                topLeft = Offset(cx - spinHalfW, capY - r * 0.72f),
                size = Size(spinHalfW * 2f, r * 0.12f),
                cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
            )
            drawRoundRect(
                color = InkColor,
                topLeft = Offset(cx - spinHalfW, capY - r * 0.72f),
                size = Size(spinHalfW * 2f, r * 0.12f),
                cornerRadius = CornerRadius(r * 0.06f, r * 0.06f),
                style = Stroke(width = strokeW * 0.75f)
            )
        }
    }
}
