package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AvatarConfig
import com.example.model.CelebrationEvent
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val xFraction: Float,
    val startYOffset: Float,
    val speed: Float,
    val sizeDp: Float,
    val color: Color,
    val spinFactor: Float
)

@Composable
fun CelebrationOverlay(
    event: CelebrationEvent?,
    avatarConfig: AvatarConfig,
    displayName: String,
    onDismiss: () -> Unit
) {
    if (event == null) return

    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "celebration_dance")

    val jumpProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dance_jump"
    )
    val wiggleAngle by infiniteTransition.animateFloat(
        initialValue = -11f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dance_wiggle"
    )
    val confettiClock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_clock"
    )

    val confetti = remember {
        val palette = listOf(
            Color(0xFFFF5A1F),
            Color(0xFFB8F22E),
            Color(0xFF7C3AED),
            Color(0xFFFACC15),
            Color(0xFF38BDF8),
            Color(0xFFF472B6)
        )
        List(44) { idx ->
            ConfettiPiece(
                xFraction = Random(idx * 31).nextFloat(),
                startYOffset = Random(idx * 17).nextFloat(),
                speed = 0.6f + Random(idx * 13).nextFloat() * 0.9f,
                sizeDp = 7f + Random(idx * 7).nextFloat() * 7f,
                color = palette[idx % palette.size],
                spinFactor = (if (idx % 2 == 0) 1f else -1f) * (180f + idx * 14f)
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC130F21)),
            contentAlignment = Alignment.Center
        ) {
            // Live Confetti Storm Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                confetti.forEach { p ->
                    val yProgress = (confettiClock * p.speed + p.startYOffset) % 1f
                    val x = p.xFraction * size.width + sin(yProgress * 10f) * 24.dp.toPx()
                    val y = yProgress * size.height
                    val pxSize = p.sizeDp.dp.toPx()
                    rotate(degrees = confettiClock * p.spinFactor, pivot = Offset(x, y)) {
                        drawRect(
                            color = p.color,
                            topLeft = Offset(x - pxSize / 2f, y - pxSize / 2f),
                            size = Size(pxSize, pxSize * 0.65f)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .padding(20.dp)
                        .widthIn(max = 460.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${event.milestone.badgeEmoji} MILESTONE UNLOCKED",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = event.milestone.headline,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Dancing Characters Stage!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(146.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Backup Dancer 1: Disco Duck
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .offset { IntOffset(0, (-18 * (1f - jumpProgress)).roundToInt()) }
                                        .rotate(-wiggleAngle)
                                ) {
                                    Text(text = "🎵", fontSize = 16.sp)
                                    Text(text = "🦆", fontSize = 40.sp)
                                }

                                // Star of the show: User's Custom Cartoon Avatar!
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(0, (-28 * jumpProgress).roundToInt()) }
                                        .rotate(wiggleAngle * 0.7f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CartoonAvatar(
                                        config = avatarConfig,
                                        size = 96.dp,
                                        animated = true
                                    )
                                }

                                // Backup Dancer 2: Hype Frog
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .offset { IntOffset(0, (-18 * (1f - jumpProgress)).roundToInt()) }
                                        .rotate(wiggleAngle)
                                ) {
                                    Text(text = "🎉", fontSize = 16.sp)
                                    Text(text = "🐸", fontSize = 40.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$displayName • ${formatStepCount(event.stepValue)} steps!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = event.milestone.subline,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${event.milestone.shareBlurb} (${formatStepCount(event.stepValue)} steps)"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Brag about your steps"))
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("celebration_share_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Brag")
                            }

                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(50.dp)
                                    .testTag("celebration_dismiss_button")
                            ) {
                                Text("Keep Strutting!")
                            }
                        }
                    }
                }
            }
        }
    }
}
